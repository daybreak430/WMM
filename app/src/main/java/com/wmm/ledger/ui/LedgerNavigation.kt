package com.wmm.ledger.ui

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
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
import com.kyant.backdrop.shadow.Shadow
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal enum class Tab(val label: String, val icon: Glyph) {
    LEDGER("账本", Glyph.BOOK), STATS("统计", Glyph.CHART), ASSETS("资产", Glyph.WALLET)
}

internal val NavigationBottomGap = 6.dp
internal val NavigationTopGap = 5.dp

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
    onSelect: (Tab) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val dark = LocalLedgerDark.current
    val glassTint = if (dark) Color(0xFF203D61).copy(alpha = .28f) else Color(0xFFE8F3FF).copy(alpha = .20f)
    val glass = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // 沿用 Backdrop 示例的模糊和折射顺序，让滚动内容透过底栏边缘。
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { CircleShape },
            effects = {
                vibrancy()
                blur(8.dp.toPx())
                lens(21.dp.toPx(), 20.dp.toPx())
            },
            highlight = { Highlight(width = .75.dp, alpha = if (dark) .66f else .90f) },
            shadow = {
                Shadow(radius = 9.dp, offset = DpOffset(0.dp, 3.dp),
                    color = Color(0xFF16396B).copy(alpha = if (dark) .26f else .13f))
            },
            onDrawSurface = { drawRect(glassTint) },
        )
    } else {
        Modifier.shadow(5.dp, CircleShape)
            .background(colors.surface.copy(alpha = .83f), CircleShape)
            .border(1.dp, colors.outlineVariant.copy(alpha = .65f), CircleShape)
    }
    BoxWithConstraints(
        modifier.widthIn(max = 364.dp).fillMaxWidth().height(height).then(glass).padding(4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val gap = 4.dp
        val capsuleWidth = (maxWidth - gap * (Tab.entries.size - 1)) / Tab.entries.size
        val step = with(LocalDensity.current) { (capsuleWidth + gap).toPx().coerceAtLeast(1f) }
        val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1f else -1f
        val currentTab by rememberUpdatedState(selected)
        val selectTab by rememberUpdatedState(onSelect)
        val scope = rememberCoroutineScope()
        var dragPosition by remember { mutableStateOf<Float?>(null) }
        var dragging by remember { mutableStateOf(false) }
        var settleJob by remember { mutableStateOf<Job?>(null) }
        val expansion by animateFloatAsState(if (dragging) 1f else 0f,
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "玻璃胶囊伸展")

        fun settle(tab: Tab) {
            val start = dragPosition ?: return
            dragging = false
            settleJob = scope.launch {
                animate(start, tab.ordinal.toFloat(),
                    animationSpec = spring(dampingRatio = .8f, stiffness = Spring.StiffnessMedium)) { value, _ ->
                    dragPosition = value
                }
                dragPosition = null
            }
        }

        GlassSelectionCapsule(backdrop, Modifier
            .offset { IntOffset(((dragPosition ?: currentTab.ordinal.toFloat()) * step).roundToInt(), 0) }
            .width(capsuleWidth)
            .height(height - 8.dp),
            expansion = { expansion })

        // 横向拖动只更新胶囊，松手提交页面切换。手势取消时回到当前页。
        Row(
            Modifier.fillMaxWidth().selectableGroup().pointerInput(step, direction) {
                try {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            settleJob?.cancel()
                            dragging = true
                            dragPosition = dragPosition ?: currentTab.ordinal.toFloat()
                        },
                        onHorizontalDrag = { _, delta ->
                            dragPosition = ((dragPosition ?: currentTab.ordinal.toFloat()) + delta * direction / step)
                                .coerceIn(0f, Tab.entries.lastIndex.toFloat())
                        },
                        onDragEnd = {
                            val target = Tab.entries[(dragPosition ?: currentTab.ordinal.toFloat()).roundToInt()]
                            selectTab(target)
                            settle(target)
                        },
                        onDragCancel = { settle(currentTab) },
                    )
                } finally {
                    if (dragging) {
                        dragging = false
                        dragPosition = null
                    }
                }
            },
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            val highlighted = dragPosition?.roundToInt() ?: selected.ordinal
            for (tab in Tab.entries) {
                val active = tab == selected
                val focusedByCapsule = tab.ordinal == highlighted
                val tint = if (focusedByCapsule) colors.primary else colors.onSurfaceVariant
                Column(
                    Modifier.weight(1f).height(height - 8.dp).clip(CircleShape)
                        .selectable(active, interactionSource = null, indication = null, role = Role.Tab) {
                            settleJob?.cancel()
                            dragPosition = null
                            onSelect(tab)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                ) {
                    LucideIcon(tab.icon, tint, size = 21)
                    Text(tab.label, color = tint, style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (focusedByCapsule) FontWeight.SemiBold else FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun GlassSelectionCapsule(backdrop: Backdrop, modifier: Modifier, expansion: () -> Float) {
    val accent = MaterialTheme.colorScheme.primary
    val dark = LocalLedgerDark.current
    val tint = accent.copy(alpha = if (dark) .22f else .14f)
    val scale: GraphicsLayerScope.() -> Unit = {
        scaleX = 1f + .08f * expansion()
        scaleY = 1f + .065f * expansion()
    }
    val glass = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { CircleShape },
            effects = {
                blur(6.dp.toPx())
                lens((8.dp + 3.dp * expansion()).toPx(), (8.dp + 7.dp * expansion()).toPx())
            },
            highlight = { Highlight(width = .85.dp, alpha = .75f) },
            shadow = { Shadow(radius = 5.dp, offset = DpOffset(0.dp, 1.dp), color = accent.copy(alpha = .18f)) },
            layerBlock = scale,
            onDrawSurface = { drawRect(tint) },
        )
    } else {
        Modifier.graphicsLayer(scale)
            .background(tint, CircleShape)
            .border(1.dp, accent.copy(alpha = .22f), CircleShape)
    }
    Box(modifier.then(glass))
}
