package com.wmm.ledger.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wmm.ledger.data.Entry
import com.wmm.ledger.data.FIRST_DAY
import com.wmm.ledger.data.Kind
import com.wmm.ledger.data.LedgerSnapshot
import com.wmm.ledger.data.Period
import com.wmm.ledger.data.PeriodStats
import com.wmm.ledger.data.Provider
import com.wmm.ledger.data.Report
import com.wmm.ledger.data.TagTotal
import com.wmm.ledger.data.currency
import com.wmm.ledger.data.stats
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun LedgerScreen(data: LedgerSnapshot, today: LocalDate, openSettings: () -> Unit, openStats: () -> Unit,
    record: () -> Unit, edit: (Entry) -> Unit) {
    val stats = remember(data, today) { data.stats(Period.MONTH, today, today) }
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        PageHeader("账本", mark = Glyph.BOOK) { UserAvatar(data.profile, openSettings) }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = PagePadding, end = PagePadding,
            top = 5.dp, bottom = 16.dp)) {
            item("cover") {
                BalancePanel {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("本月结余", Modifier.weight(1f), color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge)
                        SummaryLabel("${today.year}.${today.monthValue.toString().padStart(2, '0')}", Glyph.CALENDAR)
                    }
                    AmountText(currency(stats.net), modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
                        style = HeroNumber.copy(textAlign = TextAlign.Center))
                    Spacer(Modifier.height(15.dp))
                    Hairline()
                    Row(Modifier.fillMaxWidth().padding(top = 13.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        SummaryMetric(Kind.INCOME, stats.income, Modifier.weight(1f))
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
            if (data.entries.isEmpty()) {
                item("empty") { GroupCard { EmptyPanel("还没有流水") } }
            } else {
                ledgerRows(data.entries, data, today, edit)
            }
        }
        PrimaryAction("记一笔", record, Modifier.fillMaxWidth().padding(horizontal = PagePadding, vertical = 10.dp), Glyph.PLUS)
    }
}

@Composable
fun SummaryLabel(text: String, icon: Glyph? = null) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(9.dp), color = colors.surface.copy(alpha = .75f),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = .62f))) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (icon != null) LucideIcon(icon, colors.primary, size = 15)
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
        AmountText(currency(cents), color = if (income) colors.primary else colors.onSurface)
    }
}

fun LazyListScope.ledgerRows(entries: List<Entry>, data: LedgerSnapshot, today: LocalDate, edit: (Entry) -> Unit) {
    val tagById = data.tags.associateBy { it.id }
    val accountById = data.accounts.associateBy { it.id }
    entries.groupBy { it.day }.toSortedMap(reverseOrder()).forEach { (date, records) ->
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
        itemsIndexed(records, key = { _, entry -> "entry:${entry.id}" }) { position, entry ->
            val first = if (position == 0) 18.dp else 0.dp
            val last = if (position == records.lastIndex) 18.dp else 0.dp
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = first, topEnd = first, bottomStart = last, bottomEnd = last),
            ) {
                Column {
                    LedgerRow(entry, tagById[entry.tagId]?.name.orEmpty(), entry.accountId?.let { accountById[it]?.name }, edit)
                    if (position < records.lastIndex) Hairline(14)
                }
            }
        }
    }
}

@Composable
private fun LedgerRow(entry: Entry, tag: String, account: String?, edit: (Entry) -> Unit) {
    val income = entry.kind == Kind.INCOME.name
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { edit(entry) }.padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        IconBadge(if (income) Glyph.INCOME else Glyph.EXPENSE, emphasis = income)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(tag, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val time = Instant.ofEpochMilli(entry.occurredAt).atZone(ZoneId.systemDefault()).format(TimeLabel)
            val subtitle = listOfNotNull(time, account, entry.note.takeIf { it.isNotBlank() }).joinToString(" · ")
            Text(subtitle, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        AmountText((if (income) "+" else "−") + currency(entry.cents),
            if (income) colors.primary else colors.onSurface, Modifier.weight(.70f),
            style = RowNumber.copy(textAlign = TextAlign.End))
    }
}

@Composable
fun StatsScreen(data: LedgerSnapshot, today: LocalDate, period: Period, day: LocalDate, choosePeriod: (Period) -> Unit,
    chooseDay: (LocalDate) -> Unit, drill: (String) -> Unit, busy: Boolean, hasKey: Boolean, consent: Boolean,
    generate: (Boolean) -> Unit, openModel: () -> Unit) {
    val summary = remember(data, period, day, today) { data.stats(period, day, today) }
    val report = data.reports.firstOrNull { it.period == period.name && it.startDay == summary.start.toString() }
    val calendar = CalendarAction(day, today, chooseDay)
    var consentDialog by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        PageHeader("统计")
        LazyColumn(contentPadding = PaddingValues(horizontal = PagePadding, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item("filters") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LedgerSegments(Period.entries, period, { it.title }, choosePeriod)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        val previous = period.step(summary.start, -1)
                        IconButton(onClick = { chooseDay(previous) }, enabled = previous >= FIRST_DAY) {
                            LucideIcon(Glyph.LEFT, description = "上一${period.title}")
                        }
                        val label = periodLabel(period, summary.start)
                        TextButton(onClick = calendar, modifier = Modifier.weight(1f)) {
                            LucideIcon(Glyph.CALENDAR, color = colors.primary, size = 17)
                            Text(label, Modifier.padding(start = 7.dp), style = MaterialTheme.typography.labelLarge)
                        }
                        val next = period.step(summary.start, 1)
                        IconButton(onClick = { chooseDay(next) }, enabled = next <= today) {
                            LucideIcon(Glyph.RIGHT, color = if (next <= today) colors.onSurface else colors.outlineVariant,
                                description = "下一${period.title}")
                        }
                    }
                }
            }
            item("rollup") { StatsCover(summary) }
            if (summary.tagTotals.isEmpty()) {
                item("empty-tags") { GroupCard { EmptyPanel("这个期间没有流水", glyph = Glyph.CHART) } }
            } else {
                Kind.entries.forEach { kind ->
                    val tags = summary.tagTotals.filter { it.tag.kind == kind.name }
                    if (tags.isNotEmpty()) {
                        item("tag-title:${kind.name}") { SectionTitle("${kind.title}标签") }
                        val total = if (kind == Kind.INCOME) summary.income else summary.expense
                        items(tags, key = { "tag:${it.tag.id}" }) { tag -> TagRank(tag, total) { drill(tag.tag.id) } }
                    }
                }
            }
            if (period != Period.YEAR) {
                item("report") {
                    ReportPanel(summary, report, busy, hasKey, openModel) {
                        if (consent) generate(false) else consentDialog = true
                    }
                }
            }
        }
    }
    if (consentDialog) {
        ConfirmDialog("允许发送汇总？",
            "仅向所选模型发送期间、笔数、收支金额和标签排行（含标签名）。不会发送流水明细、备注、账户或个人资料。",
            "同意并生成", { consentDialog = false }) {
            consentDialog = false
            generate(true)
        }
    }
}

@Composable
private fun StatsCover(summary: PeriodStats) {
    val colors = MaterialTheme.colorScheme
    GroupCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("期间结余", Modifier.weight(1f), color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge)
                if (summary.inProgress) SummaryLabel("截至当前")
            }
            AmountText(currency(summary.net), modifier = Modifier.fillMaxWidth(), style = HeroNumber)
            if (summary.income + summary.expense > 0) {
                val fraction = summary.income.toFloat() / (summary.income + summary.expense).toFloat()
                Box(Modifier.fillMaxWidth().padding(vertical = 7.dp).height(6.dp)
                    .clip(RoundedCornerShape(3.dp)).background(colors.secondaryContainer)) {
                    Box(Modifier.fillMaxWidth(fraction).height(6.dp).background(colors.primary))
                }
            } else {
                Hairline()
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SummaryMetric(Kind.INCOME, summary.income, Modifier.weight(1f))
                SummaryMetric(Kind.EXPENSE, summary.expense, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TagRank(total: TagTotal, kindTotal: Long, click: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val income = total.tag.kind == Kind.INCOME.name
    Surface(onClick = click, shape = FieldShape, color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = .68f))) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(total.tag.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                AmountText(currency(total.cents), modifier = Modifier.weight(.72f),
                    style = RowNumber.copy(textAlign = TextAlign.End))
                LucideIcon(Glyph.RIGHT, color = colors.onSurfaceVariant, size = 16)
            }
            val fraction = total.cents.toFloat() / kindTotal.toFloat()
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.outlineVariant)) {
                Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(4.dp)
                    .background(if (income) colors.primary else colors.outline))
            }
        }
    }
}

@Composable
private fun ReportPanel(summary: PeriodStats, report: Report?, busy: Boolean, hasKey: Boolean, openModel: () -> Unit,
    generate: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    GroupCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconBadge(Glyph.SPARK, emphasis = true)
                Text(if (summary.period == Period.DAY) "AI 日报" else "AI 月报", Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium)
                if (report != null && remember(summary) { summary.signature() } != report.snapshotId) {
                    Text("汇总已更新", color = colors.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (report != null) {
                val generated = Instant.ofEpochMilli(report.createdAt).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.SIMPLIFIED_CHINESE))
                Text(report.content.replace("**", ""), style = MaterialTheme.typography.bodyLarge)
                Text("${Provider.valueOf(report.provider).title} · $generated", style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant)
            }
            if (!hasKey) {
                OutlinedButton(onClick = openModel) { Text("设置模型") }
            } else {
                Button(onClick = generate, enabled = !busy && summary.entries.isNotEmpty(), shape = FieldShape) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(when { busy -> "正在生成"; report == null -> "生成报告"; else -> "重新生成" })
                }
            }
        }
    }
}

fun periodLabel(period: Period, date: LocalDate): String = when (period) {
    Period.DAY -> "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
    Period.MONTH -> "${date.year}年${date.monthValue}月"
    Period.YEAR -> "${date.year}年"
}

@Composable
fun TagDrillScreen(data: LedgerSnapshot, today: LocalDate, period: Period, day: LocalDate, id: String, back: () -> Unit,
    edit: (Entry) -> Unit) {
    val stat = remember(data, today, period, day) { data.stats(period, day, today) }
    val filtered = stat.entries.filter { it.tagId == id }
    Column {
        PageHeader(data.tags.firstOrNull { it.id == id }?.name ?: "标签流水", back)
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
