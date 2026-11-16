package com.wmm.ledger.data

import java.math.BigDecimal
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class Kind(val title: String) {
    EXPENSE("支出"), INCOME("收入");
}

enum class AccountType(val title: String) {
    SAVINGS("储蓄卡"), HOUSING("公积金"), PREPAID("充值卡"), DEPOSIT("保证金"), INVESTMENT("理财账户");
}

enum class Period(val title: String) {
    DAY("日"), WEEK("周"), MONTH("月"), YEAR("年");

    fun start(day: LocalDate): LocalDate = when (this) {
        DAY -> day
        WEEK -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        MONTH -> day.withDayOfMonth(1)
        YEAR -> day.withDayOfYear(1)
    }

    fun step(day: LocalDate, amount: Long): LocalDate = when (this) {
        DAY -> day.plusDays(amount)
        WEEK -> day.plusWeeks(amount)
        MONTH -> day.plusMonths(amount)
        YEAR -> day.plusYears(amount)
    }
}

data class LedgerSnapshot(
    val entries: List<Entry> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val reports: List<Report> = emptyList(),
    val profile: Profile = Profile(),
    val scenes: List<Scene> = emptyList(),
    val scheduledBills: List<ScheduledBill> = emptyList(),
)

data class TagTotal(val tag: Tag, val cents: Long, val count: Int)

data class SceneTotal(val scene: Scene, val cents: Long, val count: Int)
data class SceneTypeTotal(val scene: Scene, val tag: Tag, val cents: Long, val count: Int)

data class PeriodStats(
    val period: Period,
    val start: LocalDate,
    val entries: List<Entry>,
    val income: Long,
    val expense: Long,
    val tagTotals: List<TagTotal>,
    val inProgress: Boolean,
    val sceneTotals: List<SceneTotal>,
    val sceneTypeTotals: List<SceneTypeTotal>,
) {
    val net get() = income - expense

}

const val MAX_CENTS = 99_999_999_999L
const val MAX_NAME = 30
const val MAX_NOTE = 200
val FIRST_DAY: LocalDate = LocalDate.of(1900, 1, 1)
val LAST_DAY: LocalDate = LocalDate.of(2199, 12, 31)

private val moneyPattern = Regex("""-?(?:\d+(?:\.\d{0,2})?|\.\d{1,2})""")
fun parseCents(text: String, signed: Boolean = false): Long? {
    val normalized = text.trim().replace('。', '.').replace('．', '.')
    if (!moneyPattern.matches(normalized) || normalized.length > 18) return null
    val value = runCatching { normalized.toBigDecimal().movePointRight(2).longValueExact() }.getOrNull()
        ?: return null
    return value.takeIf { if (signed) it in -MAX_CENTS..MAX_CENTS else it in 1..MAX_CENTS }
}

fun currency(cents: Long): String =
    (if (cents < 0) "−¥" else "¥") + String.format(Locale.CHINA, "%,.2f", BigDecimal.valueOf(cents, 2).abs())

fun editableAmount(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString()

fun validName(name: String): Boolean =
    name.isNotBlank() && name.length <= MAX_NAME && name.none { it.isISOControl() }

fun LedgerSnapshot.stats(period: Period, day: LocalDate, today: LocalDate): PeriodStats {
    val start = period.start(day)
    val end = period.step(start, 1).toString()
    val startDay = start.toString()
    val selected = entries.filter { it.day >= startDay && it.day < end }
    val byTag = tags.associateBy { it.id }
    val totals = selected.groupBy { it.tagId }.mapNotNull { (id, entries) ->
        byTag[id]?.let { TagTotal(it, entries.sumOf(Entry::cents), entries.size) }
    }.sortedWith(compareBy<TagTotal> { it.tag.kind }.thenByDescending { it.cents }.thenBy { it.tag.name })
    val byScene = scenes.associateBy { it.id }
    val expenses = selected.filter { it.kind == Kind.EXPENSE.name }
    val bySceneEntries = expenses.mapNotNull { entry -> byScene[entry.sceneId]?.let { it to entry } }
        .groupBy({ it.first }, { it.second })
    val sceneTotals = bySceneEntries.map { (scene, records) -> SceneTotal(scene, records.sumOf(Entry::cents), records.size) }
        .sortedWith(compareByDescending<SceneTotal> { it.cents }.thenBy { it.scene.name })
    val sceneTypeTotals = bySceneEntries.flatMap { (scene, records) ->
        records.groupBy { it.tagId }.mapNotNull { (tagId, group) ->
            byTag[tagId]?.let { SceneTypeTotal(scene, it, group.sumOf(Entry::cents), group.size) }
        }
    }.sortedWith(compareByDescending<SceneTypeTotal> { it.cents }.thenBy { it.scene.name }.thenBy { it.tag.name })
    return PeriodStats(
        period, start, selected,
        selected.filter { it.kind == Kind.INCOME.name }.sumOf(Entry::cents),
        expenses.sumOf(Entry::cents),
        totals, period.start(today) == start, sceneTotals, sceneTypeTotals,
    )
}

fun LedgerSnapshot.accountBalances(): Map<String, Long> {
    val accountById = accounts.associateBy { it.id }
    val balances = accounts.associate { it.id to it.baselineCents }.toMutableMap()
    // 余额由流水推算，不在编辑或删除时反向写另一笔，避免校准点前后的变更被重复扣减。
    for (entry in entries) {
        val account = accountById[entry.accountId] ?: continue
        if (entry.occurredAt <= account.calibratedAt) continue
        val delta = if (entry.kind == Kind.INCOME.name) entry.cents else -entry.cents
        balances[account.id] = balances.getValue(account.id) + delta
    }
    return balances
}

internal fun LedgerSnapshot.netAssetTotal(balances: Map<String, Long> = accountBalances()): Long =
    accounts.sumOf { account -> if (account.archived) 0L else balances[account.id] ?: 0L }

fun LedgerSnapshot.orderedTags(kind: Kind): List<Tag> {
    val usage = entries.groupingBy { it.tagId }.eachCount()
    return tags.filter { it.kind == kind.name && !it.archived }
        .sortedWith(compareBy<Tag> { it.sortOrder ?: Int.MAX_VALUE }
            .thenByDescending { usage[it.id] ?: 0 }.thenBy { it.name })
}

fun LedgerSnapshot.orderedScenes(): List<Scene> {
    val usage = entries.mapNotNull { it.sceneId }.groupingBy { it }.eachCount()
    return scenes.sortedWith(compareBy<Scene> { it.sortOrder ?: Int.MAX_VALUE }
        .thenByDescending { usage[it.id] ?: 0 }.thenBy { it.name })
}
