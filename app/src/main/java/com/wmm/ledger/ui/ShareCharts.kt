package com.wmm.ledger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wmm.ledger.data.currency
import java.util.Locale

internal data class RankedAmount(val id: String?, val label: String, val amount: Long)

private const val CHART_CATEGORY_LIMIT = 5

internal fun LazyListScope.shareSection(
    title: String,
    icon: Glyph,
    key: String,
    totalLabel: String,
    total: Long,
    ranks: List<RankedAmount>,
    open: (String) -> Unit,
    expanded: Boolean = true,
    toggleExpanded: (() -> Unit)? = null,
) {
    if (ranks.isEmpty() || total <= 0) return
    val named = ranks.filter { it.id != null }
    val other = named.drop(CHART_CATEGORY_LIMIT)
    val otherAmount = other.sumOf { it.amount }
    // 只折叠明细行，环形图、其余项及百分比始终使用完整排名和总额。
    val collapsible = toggleExpanded != null && ranks.size > CHART_CATEGORY_LIMIT
    val visibleRanks = if (collapsible && !expanded) ranks.take(CHART_CATEGORY_LIMIT) else ranks
    item("chart:$key") {
        val colors = MaterialTheme.colorScheme
        val otherColor = shareOtherColor()
        Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.padding(start = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                LucideIcon(icon, color = colors.primary, size = 17)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Surface(Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp), color = colors.surface) {
                val palette = shareColors()
                val slices = named.take(CHART_CATEGORY_LIMIT).mapIndexed { index, row ->
                    ShareSlice(row.amount, palette[index])
                } + listOfNotNull(
                    ShareSlice(otherAmount, otherColor).takeIf { otherAmount > 0 },
                    ranks.firstOrNull { it.id == null }?.let { ShareSlice(it.amount, colors.onSurfaceVariant) },
                )
                Column(Modifier.padding(top = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ShareRing(title, totalLabel, total, slices)
                    if (otherAmount > 0) {
                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            ShareDot(otherColor)
                            Text("其余 ${other.size} 项 · ${formatShare(otherAmount, total)}",
                                color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Box(Modifier.padding(top = 13.dp)) { Hairline(16) }
                }
            }
        }
    }
    itemsIndexed(visibleRanks, key = { _, row -> "share:$key:${row.id?.let { "id:$it" } ?: "unassigned"}" }) { index, row ->
        val colors = MaterialTheme.colorScheme
        val dotColor = if (row.id == null) colors.onSurfaceVariant
            else shareColors().getOrNull(index) ?: shareOtherColor()
        ShareRow(row, total, dotColor, index == visibleRanks.lastIndex && !collapsible, row.id?.let { id -> { open(id) } })
    }
    if (collapsible) item("share-toggle:$key") {
        Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp)) {
            TextButton(onClick = { toggleExpanded?.invoke() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("share-toggle:$key")
                    .semantics { stateDescription = if (expanded) "已展开" else "已收起" }) {
                Text(if (expanded) "收起" else "查看更多")
            }
        }
    }
}

private data class ShareSlice(val amount: Long, val color: Color)

@Composable
private fun ShareRing(title: String, totalLabel: String, total: Long, slices: List<ShareSlice>) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.size(142.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize().semantics { contentDescription = "${title}环形图" }) {
            val width = 14.dp.toPx()
            val origin = Offset(width / 2, width / 2)
            val diameter = Size(size.width - width, size.height - width)
            val stroke = Stroke(width)
            drawArc(colors.outlineVariant.copy(alpha = .45f), 0f, 360f, false, origin, diameter, style = stroke)
            var start = -90f
            for (slice in slices) {
                val sweep = (slice.amount.toDouble() * 360 / total).toFloat()
                val gap = if (slices.size == 1) 0f else minOf(3f, sweep * .23f)
                drawArc(slice.color, start + gap / 2, sweep - gap, false, origin, diameter, style = stroke)
                start += sweep
            }
        }
        Column(Modifier.width(105.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(totalLabel, color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            AmountText(currency(total), modifier = Modifier.fillMaxWidth(),
                style = LedgerFlowNumber.copy(textAlign = TextAlign.Center))
        }
    }
}

@Composable
private fun ShareRow(row: RankedAmount, total: Long, dot: Color, last: Boolean, click: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    val bottom = if (last) 18.dp else 0.dp
    val action = if (click == null) Modifier.semantics(mergeDescendants = true) {}
        else Modifier.clickable(role = Role.Button, onClick = click)
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(bottomStart = bottom, bottomEnd = bottom),
        color = colors.surface) {
        Column {
            Row(Modifier.fillMaxWidth().heightIn(min = 57.dp).then(action).padding(horizontal = 16.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ShareDot(dot)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(row.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatShare(row.amount, total), color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
                AmountText(currency(row.amount), modifier = Modifier.weight(.8f),
                    style = LedgerFlowNumber.copy(textAlign = TextAlign.End))
                Box(Modifier.width(15.dp), contentAlignment = Alignment.Center) {
                    if (click != null) LucideIcon(Glyph.RIGHT, colors.onSurfaceVariant, size = 15)
                }
            }
            if (!last) Hairline(16)
        }
    }
}

@Composable
private fun ShareDot(color: Color) {
    Box(Modifier.size(8.dp).background(color, CircleShape))
}

@Composable
private fun shareColors(): List<Color> {
    val blue = MaterialTheme.colorScheme.primary
    return if (LocalLedgerDark.current) {
        listOf(blue, Color(0xFFB9A2F2), Color(0xFFE5A76C), Color(0xFF68C7DB), Color(0xFFDE96B7))
    } else {
        listOf(blue, Color(0xFF825CD1), Color(0xFFB76B21), Color(0xFF1785A2), Color(0xFFB95686))
    }
}

@Composable
private fun shareOtherColor(): Color {
    val colors = MaterialTheme.colorScheme
    return colors.surfaceTint.copy(alpha = .36f).compositeOver(colors.surface)
}

private fun formatShare(amount: Long, total: Long): String {
    val fraction = amount.toDouble() / total
    return if (fraction in 0.0..<.001 && amount > 0) "<0.1%"
        else String.format(Locale.CHINA, "%.1f", fraction * 100).removeSuffix(".0") + "%"
}
