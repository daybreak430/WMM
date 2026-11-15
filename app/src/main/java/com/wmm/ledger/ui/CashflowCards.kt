package com.wmm.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wmm.ledger.data.DailyCashflow
import com.wmm.ledger.data.Period
import com.wmm.ledger.data.Cashflow
import com.wmm.ledger.data.currency
import java.time.LocalDate

@Composable
internal fun PeriodCashflowBoard(period: Period, start: LocalDate, current: Boolean, cashflow: Cashflow) {
    val colors = MaterialTheme.colorScheme
    val yearly = period == Period.YEAR
    val title = when {
        yearly -> if (current) "本年收支看板" else "年度收支看板"
        else -> if (current) "本月收支看板" else "月度收支看板"
    }
    val badge = if (yearly) "${start.year}" else "${start.year}.${start.monthValue.toString().padStart(2, '0')}"
    GroupCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionTitle(title) { SummaryLabel(badge, Glyph.CALENDAR) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                CashflowMetric("收入", cashflow.income, Modifier.weight(1f), colors.primary)
                VerticalDivider(Modifier.height(32.dp), color = colors.outlineVariant)
                CashflowMetric("支出", cashflow.expense, Modifier.weight(1f))
                VerticalDivider(Modifier.height(32.dp), color = colors.outlineVariant)
                CashflowMetric("结余", cashflow.income - cashflow.expense, Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun SevenDayTrend(days: List<DailyCashflow>) {
    val colors = MaterialTheme.colorScheme
    val expenseColor = if (LocalLedgerDark.current) Color(0xFFA7B9D2) else Color(0xFF637895)
    val peak = days.maxOf { maxOf(it.amount.income, it.amount.expense) }
    val end = days.last().date
    var selected by rememberSaveable(end) { mutableIntStateOf(days.lastIndex) }
    val selectedDay = days[selected]
    GroupCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("近 7 天收支趋势")
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                TrendLegend("收入", colors.primary)
                TrendLegend("支出", expenseColor)
                Text(if (peak > 0) currency(peak) else "¥0", Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, textAlign = TextAlign.End)
            }
            Row(Modifier.fillMaxWidth().selectableGroup().drawBehind {
                // 共同的金额刻度和零基线让两组柱可以直接比较。
                val plotHeight = 108.dp.toPx()
                for (fraction in listOf(0f, .5f, 1f)) {
                    val y = (plotHeight * fraction).coerceIn(.5f, plotHeight - .5f)
                    drawLine(colors.outlineVariant, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
            }) {
                days.forEachIndexed { index, item ->
                    val active = selected == index
                    val date = item.date.shortDate()
                    Column(
                        Modifier.weight(1f).clip(FieldShape)
                            .selectable(active, role = Role.Tab) { selected = index }
                            .semantics {
                                contentDescription = "${item.date}，收入 ${currency(item.amount.income)}，支出 ${currency(item.amount.expense)}"
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.fillMaxWidth().height(108.dp),
                            contentAlignment = Alignment.BottomCenter) {
                            if (active) {
                                Box(Modifier.fillMaxSize().background(colors.primaryContainer.copy(alpha = .36f), FieldShape))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                                TrendBar(item.amount.income, peak, colors.primary)
                                TrendBar(item.amount.expense, peak, expenseColor)
                            }
                        }
                        Text(date, Modifier.padding(vertical = 6.dp), style = MaterialTheme.typography.labelSmall,
                            color = if (active) colors.primary else colors.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
            Hairline()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(selectedDay.date.shortDate(), color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                CashflowMetric("收入", selectedDay.amount.income, Modifier.weight(1f), colors.primary)
                CashflowMetric("支出", selectedDay.amount.expense, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TrendLegend(label: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(3.dp)))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TrendBar(amount: Long, peak: Long, color: Color) {
    if (amount == 0L || peak <= 0L) {
        Spacer(Modifier.width(8.dp))
        return
    }
    val height = (108f * (amount.toDouble() / peak)).toFloat().dp.coerceAtLeast(1.dp)
    Box(Modifier.width(8.dp).height(height)
        .background(color, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)))
}

@Composable
private fun CashflowMetric(label: String, cents: Long, modifier: Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier.heightIn(min = 42.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AmountText(currency(cents), tint, Modifier.fillMaxWidth(), LedgerFlowNumber)
    }
}

private fun LocalDate.shortDate() = "$monthValue/$dayOfMonth"
