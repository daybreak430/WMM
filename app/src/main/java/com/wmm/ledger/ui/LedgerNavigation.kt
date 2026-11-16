package com.wmm.ledger.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow

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
    val glassTint = if (dark) Color(0xFF253B58) else Color(0xFFE8F3FF)
    val glassTop = if (dark) Color(0xFF355170) else Color.White
    val glass = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // 沿用 Backdrop 示例的模糊和折射顺序，让滚动内容透过底栏边缘。
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { CircleShape },
            effects = {
                blur(13.dp.toPx())
                lens(17.dp.toPx(), 26.dp.toPx())
            },
            highlight = { Highlight(width = 1.dp, alpha = if (dark) .78f else 1f) },
            shadow = {
                Shadow(radius = 12.dp, offset = DpOffset(0.dp, 4.dp),
                    color = Color(0xFF16396B).copy(alpha = if (dark) .34f else .17f))
            },
            onDrawSurface = {
                drawRect(Brush.verticalGradient(listOf(
                    glassTop.copy(alpha = if (dark) .75f else .76f),
                    glassTint.copy(alpha = if (dark) .66f else .60f),
                )))
            },
        )
    } else {
        Modifier.shadow(5.dp, CircleShape)
            .background(Brush.verticalGradient(listOf(glassTop, glassTint)), CircleShape)
            .border(1.dp, Color.White.copy(alpha = if (dark) .18f else .9f), CircleShape)
    }
    Row(
        modifier.widthIn(max = 364.dp).fillMaxWidth().height(height).then(glass).padding(4.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (tab in Tab.entries) {
            val active = tab == selected
            val tint = if (active) colors.primary else colors.onSurfaceVariant
            val lens = if (active) {
                Modifier.background(Brush.verticalGradient(listOf(
                    colors.primary.copy(alpha = if (dark) .06f else .025f),
                    colors.primary.copy(alpha = if (dark) .20f else .12f),
                )), CircleShape)
                    .border(1.dp, Brush.verticalGradient(listOf(
                        Color.White.copy(alpha = if (dark) .25f else .9f),
                        colors.primary.copy(alpha = .23f),
                    )), CircleShape)
            } else Modifier
            Column(
                Modifier.weight(1f).height(height - 8.dp).clip(CircleShape).then(lens)
                    .selectable(active, interactionSource = null, indication = null, role = Role.Tab) { onSelect(tab) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
            ) {
                LucideIcon(tab.icon, tint, size = 21)
                Text(tab.label, color = tint, style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium)
            }
        }
    }
}
