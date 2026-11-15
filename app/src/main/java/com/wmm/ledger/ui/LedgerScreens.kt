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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
        PageHeader("账本", end = { UserAvatar(data.profile, openSettings) })
        Box(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = PagePadding, end = PagePadding,
                top = 8.dp, bottom = 84.dp)) {
                item("cover") {
                    InkCard {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("本月结余", Modifier.weight(1f), color = CoverInk.copy(alpha = .77f),
                                style = MaterialTheme.typography.labelLarge)
                            Text("${today.year} / ${today.monthValue.toString().padStart(2, '0')}",
                                color = Lime, style = MaterialTheme.typography.labelMedium)
                        }
                        AmountText(currency(stats.net), CoverInk, style = HeroNumber)
                        Spacer(Modifier.height(5.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CoverMetric("收入", stats.income, Modifier.weight(1f))
                            CoverMetric("支出", stats.expense, Modifier.weight(1f))
                        }
                    }
                }
                item("recent") {
                    SectionTitle("最近流水", Modifier.padding(top = 18.dp, bottom = 3.dp)) {
                        TextButton(onClick = openStats) {
                            Text("收支统计")
                            LineIcon(Glyph.RIGHT, color = colors.primary, size = 15)
                        }
                    }
                }
                if (data.entries.isEmpty()) {
                    item("empty") {
                        GroupCard { EmptyPanel("还没有流水", "点「记一笔」记下第一笔收支。") }
                    }
                } else {
                    ledgerRows(data.entries, data, today, edit)
                }
            }
            Button(onClick = record, modifier = Modifier.align(Alignment.BottomEnd)
                .padding(end = PagePadding, bottom = 14.dp).height(54.dp),
                shape = RoundedCornerShape(18.dp), elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp),
                contentPadding = PaddingValues(horizontal = 17.dp)) {
                LineIcon(Glyph.PLUS, colors.onPrimary, size = 21)
                Text("记一笔", Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun CoverMetric(title: String, cents: Long, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = CoverInk.copy(alpha = .74f), style = MaterialTheme.typography.bodySmall)
        AmountText(currency(cents), color = CoverInk)
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
            Text(label, Modifier.padding(top = 15.dp, bottom = 5.dp), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(records, key = { "entry:${it.id}" }) { entry ->
            LedgerRow(entry, tagById[entry.tagId]?.name.orEmpty(), entry.accountId?.let { accountById[it]?.name }, edit)
            Hairline()
        }
    }
}

@Composable
private fun LedgerRow(entry: Entry, tag: String, account: String?, edit: (Entry) -> Unit) {
    val income = entry.kind == Kind.INCOME.name
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { edit(entry) }.padding(vertical = 11.dp),
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
            if (income) colors.primary else colors.onSurface, Modifier.weight(0.68f, fill = false))
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
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        Period.entries.forEachIndexed { index, option ->
                            SegmentedButton(selected = period == option, onClick = { choosePeriod(option) },
                                shape = SegmentedButtonDefaults.itemShape(index, Period.entries.size),
                                icon = {}, label = { Text(option.title) })
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        val previous = period.step(summary.start, -1)
                        IconButton(onClick = { chooseDay(previous) }, enabled = previous >= FIRST_DAY) {
                            LineIcon(Glyph.LEFT, description = "上一${period.title}")
                        }
                        val label = periodLabel(period, summary.start)
                        TextButton(onClick = calendar, modifier = Modifier.weight(1f)) {
                            LineIcon(Glyph.CALENDAR, color = colors.primary, size = 17)
                            Text(label, Modifier.padding(start = 7.dp), style = MaterialTheme.typography.labelLarge)
                        }
                        val next = period.step(summary.start, 1)
                        IconButton(onClick = { chooseDay(next) }, enabled = next <= today) {
                            LineIcon(Glyph.RIGHT, color = if (next <= today) colors.onSurface else colors.outlineVariant,
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
    InkCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("期间结余", Modifier.weight(1f), color = CoverInk.copy(alpha = .77f), style = MaterialTheme.typography.labelLarge)
            if (summary.inProgress) Text("截至当前", color = Lime, style = MaterialTheme.typography.labelMedium)
        }
        AmountText(currency(summary.net), CoverInk, style = HeroNumber)
        if (summary.income + summary.expense > 0) {
            val split = summary.income.toFloat() / (summary.income + summary.expense).toFloat()
            Row(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Clay)) {
                Box(Modifier.fillMaxWidth(split).height(5.dp).background(Lime))
            }
        }
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CoverMetric("收入", summary.income, Modifier.weight(1f))
            CoverMetric("支出", summary.expense, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TagRank(total: TagTotal, kindTotal: Long, click: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val income = total.tag.kind == Kind.INCOME.name
    Surface(onClick = click, shape = RoundedCornerShape(17.dp), color = colors.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(total.tag.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                AmountText(currency(total.cents))
                LineIcon(Glyph.RIGHT, color = colors.onSurfaceVariant, size = 16)
            }
            val fraction = total.cents.toFloat() / kindTotal.toFloat()
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.outlineVariant)) {
                Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(4.dp)
                    .background(if (income) colors.primary else Clay))
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
                FilledTonalButton(onClick = generate, enabled = !busy && summary.entries.isNotEmpty()) {
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
                InkCard {
                    Text(periodLabel(period, stat.start), color = CoverInk, style = MaterialTheme.typography.labelLarge)
                    AmountText(currency(filtered.sumOf { it.cents }), CoverInk, style = HeroNumber)
                    Text("${filtered.size} 笔", color = CoverInk, style = MaterialTheme.typography.bodySmall)
                }
            }
            ledgerRows(filtered, data, today, edit)
        }
    }
}
