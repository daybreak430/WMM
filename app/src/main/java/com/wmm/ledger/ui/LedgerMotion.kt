package com.wmm.ledger.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.clearAndSetSemantics

internal const val PAGE_TRANSITION_MS = 230
private const val EXIT_MS = 160

internal fun pageMotion(direction: Int): ContentTransform {
    val enter = fadeIn(tween(PAGE_TRANSITION_MS)) +
        slideInHorizontally(tween(PAGE_TRANSITION_MS, easing = FastOutSlowInEasing)) { direction * it / 7 }
    val exit = fadeOut(tween(EXIT_MS)) +
        slideOutHorizontally(tween(EXIT_MS, easing = FastOutSlowInEasing)) { -direction * it / 14 }
    return ContentTransform(enter, exit, sizeTransform = null)
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
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { placed = true }
    val reveal = animateFloatAsState(if (placed) 1f else 0f, tween(170), label = "弹窗入场")
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        text = text,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        shape = shape,
        modifier = Modifier.graphicsLayer {
            val progress = reveal.value
            alpha = progress
            scaleX = .97f + progress * .03f
            scaleY = scaleX
        },
    )
}
