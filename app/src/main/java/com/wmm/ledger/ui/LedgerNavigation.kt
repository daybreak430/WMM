package com.wmm.ledger.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.kyant.backdrop.effects.vibrancy
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
    Row(
        modifier.widthIn(max = 364.dp).fillMaxWidth().height(height).then(glass).padding(4.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (tab in Tab.entries) {
            val active = tab == selected
            val tint = if (active) colors.primary else colors.onSurfaceVariant
            Column(
                Modifier.weight(1f).height(height - 8.dp).clip(CircleShape)
                    .selectable(active, interactionSource = null, indication = null, role = Role.Tab) { onSelect(tab) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
            ) {
                LucideIcon(tab.icon, tint, size = 21)
                Text(tab.label, color = tint, style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium)
                Box(Modifier.width(10.dp).height(2.dp)
                    .background(if (active) colors.primary else Color.Transparent, CircleShape))
            }
        }
    }
}
