package com.wmm.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wmm.ledger.data.Cashflow
import com.wmm.ledger.data.Entry
import com.wmm.ledger.data.FIRST_DAY
import com.wmm.ledger.data.Kind
import com.wmm.ledger.data.LabelStyle
import com.wmm.ledger.data.LedgerSnapshot
import com.wmm.ledger.data.Period
import com.wmm.ledger.data.Transfer
import com.wmm.ledger.data.weekCashflow
import com.wmm.ledger.data.currency
import com.wmm.ledger.data.stats
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun LedgerScreen(data: LedgerSnapshot, today: LocalDate, openSettings: () -> Unit, openStats: () -> Unit,
    edit: (Entry) -> Unit, bottomInset: Dp, editTransfer: (Transfer) -> Unit,
    search: () -> Unit, ask: () -> Unit) {
    val stats = remember(data, today) { data.stats(Period.MONTH, today, today) }
    val colors = MaterialTheme.colorScheme
    val inset = LedgerPageInset()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = inset, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(Triple(Glyph.SEARCH, "搜索流水", search), Triple(Glyph.SPARK, "问 AI", ask)).forEach { (icon, label, action) ->
                IconButton(onClick = action, modifier = Modifier.size(44.dp)) {
                    Surface(shape = CircleShape, color = colors.surface, modifier = Modifier.size(38.dp)) {
                        Box(contentAlignment = Alignment.Center) { LucideIcon(icon, colors.primary, label, size = 20) }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            UserAvatar(data.profile, openSettings)
        }
        // 流水绘制到悬浮底栏背后，末尾留出悬浮控件的高度，便于滑出并点中最后一笔。
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = inset, end = inset, top = 4.dp, bottom = bottomInset + 14.dp,
            ),
        ) {
            item("cover") {
                BalancePanel {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("本月结余", Modifier.weight(1f), color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge)
                        SummaryLabel("${today.year}.${today.monthValue.toString().padStart(2, '0')}", Glyph.CALENDAR)
                    }
                    AmountText(currency(stats.net), modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
                        style = LedgerBalanceNumber)
                    Spacer(Modifier.height(15.dp))
                    Hairline()
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        SummaryMetric(Kind.INCOME, stats.income, Modifier.weight(1f))
                        VerticalDivider(Modifier.height(30.dp), color = colors.outlineVariant)
                        SummaryMetric(Kind.EXPENSE, stats.expense, Modifier.weight(1f))
                    }
                }
            }
            item("recent") {
                SectionTitle("最近流水", Modifier.padding(top = 14.dp, bottom = 2.dp)) {
                    TextButton(onClick = openStats) {
                        Text("收支统计")
                        LucideIcon(Glyph.RIGHT, color = colors.primary, size = 15)
                    }
                }
            }
            if (data.entries.isEmpty() && data.transfers.isEmpty()) {
                item("empty") { GroupCard { EmptyPanel("还没有流水") } }
            } else {
                ledgerRows(data.entries, data, today, edit, editTransfer)
            }
        }
    }
}

@Composable
fun SummaryLabel(text: String, icon: Glyph? = null) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = CircleShape, color = colors.surfaceVariant) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (icon != null) LucideIcon(icon, colors.onSurfaceVariant, size = 13)
            Text(text, color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SummaryMetric(kind: Kind, cents: Long, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val income = kind == Kind.INCOME
    val tint = if (income) colors.primary else colors.onSurfaceVariant
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            LucideIcon(if (income) Glyph.INCOME else Glyph.EXPENSE, tint, size = 16)
            Text(kind.title, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        AmountText(currency(cents), color = if (income) colors.primary else colors.onSurface,
            style = if (LocalLedgerRefinement.current) LedgerFlowNumber.copy(fontWeight = FontWeight(650)) else RowNumber)
    }
}

private sealed class LedgerListItem(val id: String, val day: String, val occurredAt: Long) {
    class EntryItem(val entry: Entry) : LedgerListItem("entry:${entry.id}", entry.day, entry.occurredAt)
    class TransferItem(val transfer: Transfer) : LedgerListItem("transfer:${transfer.id}", transfer.day, transfer.occurredAt)
}

fun LazyListScope.ledgerRows(
    entries: List<Entry>, data: LedgerSnapshot, today: LocalDate, edit: (Entry) -> Unit,
    editTransfer: ((Transfer) -> Unit)? = null,
) {
    val tagById = data.tags.associateBy { it.id }
    val sceneById = data.scenes.associateBy { it.id }
    val accountById = data.accounts.associateBy { it.id }
    val rows = entries.map { LedgerListItem.EntryItem(it) } +
        if (editTransfer != null) data.transfers.map { LedgerListItem.TransferItem(it) } else emptyList()
    rows.groupBy { it.day }.toSortedMap(reverseOrder()).forEach { (date, daily) ->
        val records = daily.sortedWith(compareByDescending<LedgerListItem> { it.occurredAt }.thenBy { it.id })
        item("day:$date") {
            val day = LocalDate.parse(date)
            val label = when (day) {
                today -> "今天"
                today.minusDays(1) -> "昨天"
                else -> if (day.year == today.year) day.format(DateLabel) else date
            }
            Text(label, Modifier.padding(start = 2.dp, top = 11.dp, bottom = 6.dp), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        itemsIndexed(records, key = { _, record -> record.id }) { position, record ->
            val first = if (position == 0) 18.dp else 0.dp
            val last = if (position == records.lastIndex) 18.dp else 0.dp
            val colors = MaterialTheme.colorScheme
            val refined = LocalLedgerRefinement.current
            val shape = RoundedCornerShape(topStart = first, topEnd = first, bottomStart = last, bottomEnd = last)
            // 流水逐行加载，首尾圆角和组内分隔线组成连续的卡面。
            Surface(color = colors.surface, shape = shape) {
                Column {
                    when (record) {
                        is LedgerListItem.EntryItem -> {
                            val entry = record.entry
                            val type = tagById[entry.tagId]
                            val scene = sceneById[entry.sceneId]?.name
                            val name = type?.name.orEmpty()
                            val label = if (scene == null) name else "$scene · $name"
                            LedgerRow(entry, label, LabelStyle.from(type?.iconKey, type?.iconColor), edit)
                        }
                        is LedgerListItem.TransferItem -> {
                            val transfer = record.transfer
                            val from = accountById[transfer.fromAccountId]?.name.orEmpty()
                            val to = accountById[transfer.toAccountId]?.name.orEmpty()
                            LedgerRecordRow("$from → $to", "转账", transfer.note, currency(transfer.cents),
                                income = false, leading = { IconBadge(Glyph.WALLET) }) { editTransfer?.invoke(transfer) }
                        }
                    }
                    if (position < records.lastIndex) {
                        if (refined) HorizontalDivider(Modifier.padding(start = 60.dp, end = 14.dp), color = colors.outlineVariant)
                        else Hairline(14)
                    }
                }
            }
        }
    }
}

@Composable
private fun LedgerRow(entry: Entry, tag: String, icon: LabelStyle?, edit: (Entry) -> Unit) {
    val income = entry.kind == Kind.INCOME.name
    val refined = LocalLedgerRefinement.current
    LedgerRecordRow(tag, if (income) "收入" else "支出", entry.note,
        (if (income) "+" else "−") + currency(entry.cents), income,
        leading = { LabelBadge(icon, if (refined) 36 else 40) }) { edit(entry) }
}

@Composable
private fun LedgerRecordRow(
    title: String, badge: String, note: String, amount: String, income: Boolean,
    leading: @Composable () -> Unit, onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val refined = LocalLedgerRefinement.current
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 14.dp, vertical = if (refined) 10.dp else 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        leading()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(badge,
                    Modifier.background(if (income) colors.primaryContainer else colors.surfaceVariant, CircleShape)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                    color = if (income) colors.primary else colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            if (note.isNotBlank()) {
                Text(note, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        AmountText(amount, if (income) colors.primary else colors.onSurface, Modifier.weight(.70f),
            style = (if (refined) LedgerFlowNumber else RowNumber).copy(textAlign = TextAlign.End))
    }
}

@Composable
fun StatsScreen(data: LedgerSnapshot, today: LocalDate, period: Period, day: LocalDate, choosePeriod: (Period) -> Unit,
    chooseDay: (LocalDate) -> Unit, drill: (String) -> Unit, drillScene: (String) -> Unit,
    bottomInset: Dp) {
    val summary = remember(data, period, day, today) { data.stats(period, day, today) }
    var expenseTypesExpanded by rememberSaveable(period, summary.start) { mutableStateOf(false) }
    val calendar = CalendarAction(day, today, onSelect = chooseDay)
    val colors = MaterialTheme.colorScheme
    val trend = remember(data.entries, summary.start, period) {
        if (period == Period.WEEK) data.weekCashflow(summary.start) else emptyList()
    }
    val expenses = remember(summary) { summary.tagTotals.filter { it.tag.kind == Kind.EXPENSE.name } }
    val income = remember(summary) { summary.tagTotals.filter { it.tag.kind == Kind.INCOME.name } }
    val sceneRanks = remember(summary) {
        val named = summary.sceneTotals.map { RankedAmount(it.scene.id, it.scene.name, it.cents) }
        val unassigned = summary.expense - named.sumOf { it.amount }
        if (unassigned > 0) named + RankedAmount(null, "未标场景", unassigned) else named
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader("统计")
        LazyColumn(contentPadding = PaddingValues(
            start = PagePadding, end = PagePadding, top = 10.dp, bottom = bottomInset + 10.dp,
        )) {
            item("filters") {
                Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LedgerSegments(listOf(Period.WEEK, Period.MONTH, Period.YEAR), period, { it.title }, onSelect = choosePeriod)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        val previous = period.step(summary.start, -1)
                        IconButton(onClick = { chooseDay(previous) }, enabled = previous >= FIRST_DAY) {
                            LucideIcon(Glyph.LEFT, description = "上一${period.title}")
                        }
                        TextButton(onClick = calendar, modifier = Modifier.weight(1f)) {
                            LucideIcon(Glyph.CALENDAR, color = colors.primary, size = 17)
                            Text(periodLabel(period, summary.start), Modifier.padding(start = 7.dp),
                                style = MaterialTheme.typography.labelLarge)
                        }
                        val next = period.step(summary.start, 1)
                        IconButton(onClick = { chooseDay(next) }, enabled = next <= today) {
                            LucideIcon(Glyph.RIGHT, color = if (next <= today) colors.onSurface else colors.outlineVariant,
                                description = "下一${period.title}")
                        }
                    }
                }
            }
            item("rollup") {
                PeriodCashflowBoard(period, summary.start, summary.inProgress, Cashflow(summary.income, summary.expense))
            }
            if (period == Period.WEEK) item("week-trend") {
                Box(Modifier.padding(top = 12.dp)) {
                    WeekTrend(trend, day, today)
                }
            }
            if (summary.entries.isEmpty()) {
                item("empty-tags") {
                    Box(Modifier.padding(top = 16.dp)) {
                        GroupCard { EmptyPanel("这个期间没有流水", glyph = Glyph.CHART) }
                    }
                }
            }
            shareSection("支出类型占比", Glyph.TAG, "expense", "支出合计", summary.expense,
                expenses.map { RankedAmount(it.tag.id, it.tag.name, it.cents) }, drill,
                expanded = expenseTypesExpanded, toggleExpanded = { expenseTypesExpanded = !expenseTypesExpanded })
            shareSection("支出场景占比", Glyph.BOX, "scene", "支出合计", summary.expense, sceneRanks, drillScene)
            shareSection("收入类型占比", Glyph.INCOME, "income", "收入合计", summary.income,
                income.map { RankedAmount(it.tag.id, it.tag.name, it.cents) }, drill)
        }
    }
}

fun periodLabel(period: Period, date: LocalDate): String = when (period) {
    Period.DAY -> "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
    Period.WEEK -> {
        val start = Period.WEEK.start(date)
        val end = start.plusDays(6)
        val last = if (end.year == start.year) {
            if (end.month == start.month) "${end.dayOfMonth}日" else "${end.monthValue}月${end.dayOfMonth}日"
        } else periodLabel(Period.DAY, end)
        "${periodLabel(Period.DAY, start)}–$last"
    }
    Period.MONTH -> "${date.year}年${date.monthValue}月"
    Period.YEAR -> "${date.year}年"
}

@Composable
fun TagDrillScreen(data: LedgerSnapshot, today: LocalDate, period: Period, day: LocalDate, id: String,
    scene: Boolean, back: () -> Unit, edit: (Entry) -> Unit) {
    val stat = remember(data, today, period, day) { data.stats(period, day, today) }
    val filtered = stat.entries.filter { if (scene) it.sceneId == id else it.tagId == id }
    val title = if (scene) data.scenes.firstOrNull { it.id == id }?.name else data.tags.firstOrNull { it.id == id }?.name
    Column {
        PageHeader(title ?: "标签流水", back)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = PagePadding, vertical = 10.dp)) {
            item("summary") {
                BalancePanel {
                    val colors = MaterialTheme.colorScheme
                    Text(periodLabel(period, stat.start), color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                    AmountText(currency(filtered.sumOf { it.cents }), modifier = Modifier.fillMaxWidth(), style = HeroNumber)
                    Text("${filtered.size} 笔", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
            ledgerRows(filtered, data, today, edit)
        }
    }
}
