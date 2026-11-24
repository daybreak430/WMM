package com.wmm.ledger.ui

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.window.DialogWindowProvider
import com.wmm.ledger.R

internal const val PAGE_TRANSITION_MS = 260
internal const val MOTION_FEEDBACK_MS = 120
internal const val MOTION_CONTENT_MS = 180
internal val LedgerMotionEasing = CubicBezierEasing(.2f, .8f, .2f, 1f)
internal val LocalLedgerMotionEnabled = staticCompositionLocalOf { true }

@Composable
internal fun rememberLedgerMotionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun enabled() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    var motionEnabled by remember(resolver) { mutableStateOf(enabled()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { motionEnabled = enabled() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return motionEnabled
}

internal fun <T> ledgerSpring() = spring<T>(dampingRatio = 1f, stiffness = 700f)

@Composable
internal fun ledgerColor(target: Color): Color {
    val color = animateColorAsState(target, tween(MOTION_FEEDBACK_MS, easing = LedgerMotionEasing), label = "状态颜色")
    return if (LocalLedgerMotionEnabled.current) color.value else target
}

@Composable
internal fun Modifier.ledgerPress(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val enabled = LocalLedgerMotionEnabled.current
    val scale = animateFloatAsState(if (pressed && enabled) .975f else 1f, ledgerSpring(), label = "按压回弹")
    // 只缩放控件内部视觉；调用方将手势和语义保留在外层，触控边界不跟着缩小。
    return if (!enabled) this else graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}

@Composable
internal fun Modifier.ledgerContentChange(value: Any?): Modifier {
    val enabled = LocalLedgerMotionEnabled.current
    val progress = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(value) }
    LaunchedEffect(value, enabled) {
        if (!enabled) { previous = value; progress.snapTo(1f) }
        else if (previous != value) {
            previous = value
            // 内容和语义立即更新，不保留旧金额或旧筛选结果；连点时从当前进度接续，避免反复闪白。
            if (!progress.isRunning && progress.value == 1f) progress.snapTo(0f)
            progress.animateTo(1f, tween(MOTION_CONTENT_MS, easing = LedgerMotionEasing))
        }
    }
    return if (!enabled) this else graphicsLayer { alpha = .72f + .28f * progress.value }
}

@Composable
internal fun Modifier.ledgerSizeMotion(): Modifier =
    if (LocalLedgerMotionEnabled.current) animateContentSize(ledgerSpring()) else this

@Composable
internal fun LazyItemScope.ledgerItemMotion(modifier: Modifier = Modifier): Modifier =
    if (LocalLedgerMotionEnabled.current) modifier.animateItem(
        fadeInSpec = tween(MOTION_FEEDBACK_MS), placementSpec = ledgerSpring(), fadeOutSpec = tween(MOTION_FEEDBACK_MS),
    ) else modifier

@Composable
internal fun LedgerVisibility(visible: Boolean, content: @Composable ColumnScope.() -> Unit) {
    if (!LocalLedgerMotionEnabled.current) {
        if (visible) Column(content = content)
        return
    }
    AnimatedVisibility(visible,
        enter = fadeIn(tween(MOTION_FEEDBACK_MS)) + expandVertically(ledgerSpring()),
        exit = fadeOut(tween(MOTION_FEEDBACK_MS)) + shrinkVertically(ledgerSpring()),
    ) {
        Column(Modifier.blockExitingPage(visible), content = content)
    }
}

private const val EXIT_MS = 160
private const val ENTRY_DRAWER_EXIT_MS = 340

internal fun pageMotion(direction: Int, enabled: Boolean = true): ContentTransform {
    if (!enabled) return ContentTransform(androidx.compose.animation.EnterTransition.None, ExitTransition.None, sizeTransform = null)
    val enter = fadeIn(tween(PAGE_TRANSITION_MS)) +
        slideInHorizontally(tween(PAGE_TRANSITION_MS, easing = LedgerMotionEasing)) { direction * it / 10 }
    val exit = fadeOut(tween(EXIT_MS)) +
        slideOutHorizontally(tween(EXIT_MS, easing = LedgerMotionEasing)) { -direction * it / 20 }
    return ContentTransform(enter, exit, sizeTransform = null)
}

internal fun entryDrawerEnterMotion(enabled: Boolean = true): ContentTransform {
    if (!enabled) return ContentTransform(androidx.compose.animation.EnterTransition.None, ExitTransition.None, sizeTransform = null)
    val enter = fadeIn(tween(180)) +
        slideInVertically(tween(320, easing = LedgerMotionEasing)) { height -> height }
    return ContentTransform(enter, ExitTransition.None, sizeTransform = null)
}

internal fun entryDrawerExitMotion(enabled: Boolean = true): ContentTransform {
    if (!enabled) return ContentTransform(androidx.compose.animation.EnterTransition.None, ExitTransition.None, sizeTransform = null)
    // 让记账页在发起页上方向下滑动，滑出一段距离后再淡出。
    val fadeDelay = 80
    val exit = fadeOut(tween(ENTRY_DRAWER_EXIT_MS - fadeDelay, delayMillis = fadeDelay)) +
        slideOutVertically(tween(ENTRY_DRAWER_EXIT_MS, easing = LedgerMotionEasing)) { height -> height }
    return ContentTransform(fadeIn(tween(120)), exit, targetContentZIndex = -1f, sizeTransform = null)
}

internal fun Modifier.blockExitingPage(active: Boolean): Modifier {
    if (active) return this
    // 离场页还在画面里，手势只交给当前页，避免误按旧页面的按钮。
    return clearAndSetSemantics { hideFromAccessibility() }.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}

@Composable
internal fun LedgerAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.extraLarge,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
            val enabled = LocalLedgerMotionEnabled.current
            // 由窗口统一负责进退场，确认、取消和系统返回都能完成离场，避免叠加两套缩放。
            SideEffect { window?.setWindowAnimations(if (enabled) R.style.LedgerDialogAnimation else 0) }
            title()
        },
        text = text,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        shape = shape,
    )
}
