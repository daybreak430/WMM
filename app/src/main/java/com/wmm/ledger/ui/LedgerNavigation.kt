package com.wmm.ledger.ui

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal enum class Tab(val label: String, val icon: Glyph) {
    LEDGER("账本", Glyph.BOOK), STATS("统计", Glyph.CHART)
}

internal val NavigationBottomGap = 6.dp
internal val NavigationTopGap = 5.dp
private const val RECORD_DESCRIPTION = "记一笔"
private const val RECORD_ICON_SIZE = 19
private val RecordButtonWidth = 44.dp
private val RecordButtonHeight = 34.dp

@Composable
internal fun ledgerNavigationHeight(): Dp {
    val lineHeight = MaterialTheme.typography.labelMedium.lineHeight
    return maxOf(62.dp, with(LocalDensity.current) { lineHeight.toDp() } + 42.dp)
}

@Composable
internal fun LedgerNavigation(
    selected: Tab,
    backdrop: Backdrop,
    height: Dp,
    modifier: Modifier = Modifier,
    onRecord: () -> Unit,
    onSelect: (Tab) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val dark = LocalLedgerDark.current
    val glassTint = if (dark) Color(0xFF243D5D).copy(alpha = .34f) else Color(0xFFEAF4FF).copy(alpha = .30f)
    val glass = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { CircleShape },
            effects = {
                vibrancy()
                blur(11.dp.toPx())
                lens(20.dp.toPx(), 17.dp.toPx())
            },
            highlight = { Highlight(width = .55.dp, alpha = if (dark) .56f else .66f) },
            shadow = {
                Shadow(radius = 9.dp, offset = DpOffset(0.dp, 3.dp),
                    color = Color(0xFF16396B).copy(alpha = if (dark) .26f else .12f))
            },
            onDrawSurface = { drawRect(glassTint) },
        )
    } else {
        Modifier.shadow(5.dp, CircleShape)
            .background(colors.surface.copy(alpha = .83f), CircleShape)
            .border(1.dp, colors.outlineVariant.copy(alpha = .65f), CircleShape)
    }
    GlassNavigationControls(
        selected, backdrop,
        modifier.widthIn(max = 224.dp).fillMaxWidth().height(height).then(glass).padding(5.dp),
        onRecord, onSelect,
    )
}

@Composable
private fun GlassNavigationControls(
    selected: Tab, backdrop: Backdrop, modifier: Modifier, onRecord: () -> Unit, onSelect: (Tab) -> Unit,
) {
    val dark = LocalLedgerDark.current
    val activeInk = if (dark) Color(0xFFB6DAFF) else Color(0xFF075AD0)
    val idleInk = if (dark) Color(0xFFBDCEE4) else Color(0xFF405977)
    val interactions = remember { Tab.entries.associateWith { MutableInteractionSource() } }
    val ledgerPressed by interactions.getValue(Tab.LEDGER).collectIsPressedAsState()
    val statsPressed by interactions.getValue(Tab.STATS).collectIsPressedAsState()
    val currentTab by rememberUpdatedState(selected)
    val selectTab by rememberUpdatedState(onSelect)
    val scope = rememberCoroutineScope()
    var capsulePosition by remember { mutableStateOf<Float?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var dragSpeed by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val expansion by animateFloatAsState(
        if (dragging || ledgerPressed || statsPressed) 1f else 0f,
        spring(dampingRatio = .72f, stiffness = Spring.StiffnessMedium),
        label = "玻璃触摸形变",
    )
    val stretch by animateFloatAsState(
        dragSpeed, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh),
        label = "玻璃拖动惯性",
    )
    val highlighted by remember {
        derivedStateOf { capsulePosition?.roundToInt()?.coerceIn(0, Tab.entries.lastIndex) ?: currentTab.ordinal }
    }

    fun settle(tab: Tab) {
        val start = capsulePosition ?: currentTab.ordinal.toFloat()
        dragging = false
        dragSpeed = 0f
        settleJob?.cancel()
        val end = tab.ordinal.toFloat()
        capsulePosition = start
        settleJob = scope.launch {
            animate(start, end, animationSpec = spring(dampingRatio = .78f, stiffness = Spring.StiffnessMedium)) { value, _ ->
                capsulePosition = value
            }
            capsulePosition = null
        }
    }

    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val gap = 5.dp
        val available = maxWidth - gap * 2
        val density = LocalDensity.current
        val width = (available - RecordButtonWidth) / 2
        val step = with(density) { (width + RecordButtonWidth + gap * 2).toPx().coerceAtLeast(1f) }
        val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1f else -1f
        GlassSelectionCapsule(
            backdrop,
            Modifier
                .offset { IntOffset(((capsulePosition ?: currentTab.ordinal.toFloat()) * step).roundToInt(), 0) }
                .width(width)
                .fillMaxHeight(),
            expansion = { expansion.coerceIn(0f, 1f) },
            stretch = { stretch },
        )

        // 中间预留给浮在上方的按钮。两侧等宽，拖动胶囊可以跨过按钮而不触发录入。
        Row(
            Modifier.fillMaxWidth().selectableGroup().pointerInput(step, direction) {
                val tracker = VelocityTracker()
                var translation = 0f
                try {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            settleJob?.cancel()
                            tracker.resetTracking()
                            translation = capsulePosition ?: currentTab.ordinal.toFloat()
                            dragging = true
                            capsulePosition = translation
                        },
                        onHorizontalDrag = { change, delta ->
                            val last = Tab.entries.lastIndex.toFloat()
                            // 超出两侧时压缩位移，胶囊只在底栏边缘轻微伸展。
                            translation = (translation + delta * direction / step).coerceIn(-.35f, last + .35f)
                            val within = translation.coerceIn(0f, last)
                            capsulePosition = within + (translation - within) * .12f
                            tracker.addPosition(change.uptimeMillis, change.position)
                            dragSpeed = (abs(tracker.calculateVelocity().x) / (step * 9f)).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            val target = Tab.entries[translation.roundToInt().coerceIn(0, Tab.entries.lastIndex)]
                            settle(target)
                            selectTab(target)
                        },
                        onDragCancel = { settle(currentTab) },
                    )
                } finally {
                    if (dragging) {
                        dragging = false
                        capsulePosition = null
                        dragSpeed = 0f
                    }
                }
            },
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            for (tab in Tab.entries) {
                if (tab == Tab.STATS) Spacer(Modifier.width(RecordButtonWidth))
                val active = tab == selected
                val focused = tab.ordinal == highlighted
                val tint = if (focused) activeInk else idleInk
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                        .selectable(active, interactionSource = interactions.getValue(tab),
                            indication = null, role = Role.Tab) {
                            if (tab != currentTab) {
                                settle(tab)
                                onSelect(tab)
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                ) {
                    LucideIcon(tab.icon, tint, size = 22)
                    Text(tab.label, color = tint, style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold)
                }
            }
        }
        RecordNavigationButton(
            onRecord,
            Modifier.align(Alignment.Center).width(RecordButtonWidth).height(RecordButtonHeight),
        )
    }
}

@Composable
private fun GlassSelectionCapsule(
    backdrop: Backdrop, modifier: Modifier, expansion: () -> Float, stretch: () -> Float,
) {
    val dark = LocalLedgerDark.current
    val tint = if (dark) Color(0xFF4677B3).copy(alpha = .42f) else Color(0xFFD4E8FF).copy(alpha = .67f)
    val scale: GraphicsLayerScope.() -> Unit = {
        scaleX = 1f + .045f * expansion() + .085f * stretch()
        scaleY = 1f + .035f * expansion() - .038f * stretch()
    }
    val glass = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // 延续上游选中镜片的动态折射、内阴影和速度形变，压低静止时的白色边缘。
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { CircleShape },
            effects = {
                blur(11.dp.toPx())
                val pressed = expansion()
                lens((7.dp + 4.dp * pressed).toPx(), (7.dp + 9.dp * pressed).toPx(),
                    chromaticAberration = pressed > .2f)
            },
            highlight = { Highlight(width = .6.dp, alpha = .42f + .4f * expansion()) },
            shadow = {
                Shadow(radius = 5.dp, offset = DpOffset(0.dp, 1.dp),
                    color = Color(0xFF407CBD).copy(alpha = .14f + .10f * expansion()))
            },
            innerShadow = {
                InnerShadow(radius = 7.dp, offset = DpOffset(0.dp, 3.dp),
                    color = Color(0xFF3D75B6).copy(alpha = .22f), alpha = expansion())
            },
            layerBlock = scale,
            onDrawSurface = {
                drawRect(tint)
                val pressed = expansion()
                if (pressed > 0f) {
                    drawRect(Brush.radialGradient(
                        listOf(Color.White.copy(alpha = .22f * pressed), Color.Transparent),
                        center = Offset(size.width * .43f, 0f), radius = size.width * .68f,
                    ))
                }
            },
        )
    } else {
        Modifier.graphicsLayer(scale)
            .background(tint, CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .22f), CircleShape)
    }
    Box(modifier.then(glass))
}

@Composable
private fun RecordNavigationButton(onRecord: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .96f else 1f,
        spring(dampingRatio = .8f, stiffness = Spring.StiffnessMedium), label = "记一笔按压")
    Box(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(colors.primary)
            .semantics { contentDescription = RECORD_DESCRIPTION }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onRecord),
        contentAlignment = Alignment.Center,
    ) {
        LucideIcon(
            Glyph.PLUS,
            colors.onPrimary,
            size = RECORD_ICON_SIZE,
        )
    }
}
