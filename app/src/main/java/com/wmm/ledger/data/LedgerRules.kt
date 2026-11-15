package com.wmm.ledger.data

import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDate
import java.util.Locale

enum class Kind(val title: String) {
    EXPENSE("支出"), INCOME("收入");
}

enum class AccountType(val title: String) {
    SAVINGS("储蓄卡"), HOUSING("公积金"), PREPAID("充值卡"), DEPOSIT("保证金"), INVESTMENT("理财账户");
}

enum class Period(val title: String) {
    DAY("日"), MONTH("月"), YEAR("年");

    fun start(day: LocalDate): LocalDate = when (this) {
        DAY -> day
        MONTH -> day.withDayOfMonth(1)
        YEAR -> day.withDayOfYear(1)
    }

    fun step(day: LocalDate, amount: Long): LocalDate = when (this) {
        DAY -> day.plusDays(amount)
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
)

data class TagTotal(val tag: Tag, val cents: Long, val count: Int)

data class PeriodStats(
    val period: Period,
    val start: LocalDate,
    val entries: List<Entry>,
    val income: Long,
    val expense: Long,
    val tagTotals: List<TagTotal>,
    val inProgress: Boolean,
) {
    val net get() = income - expense

    // 签名只覆盖报告实际使用的汇总字段。改备注或关联账户不会使旧报告失效。
    fun signature(): String {
        val value = buildString {
            append("${period.name}|${start}|${entries.size}|$income|$expense|$inProgress")
            tagTotals.forEach {
                append("|${it.tag.kind}|${it.tag.name.length}:${it.tag.name}|${it.cents}|${it.count}")
            }
        }
        return MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}

const val MAX_CENTS = 99_999_999_999L
const val MAX_NAME = 30
const val MAX_NOTE = 200
const val MAX_REPORT = 8_000
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
    return PeriodStats(
        period, start, selected,
        selected.filter { it.kind == Kind.INCOME.name }.sumOf(Entry::cents),
        selected.filter { it.kind == Kind.EXPENSE.name }.sumOf(Entry::cents),
        totals, period.start(today) == start,
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

fun LedgerSnapshot.frequentTags(kind: Kind, keepId: String? = null): List<Tag> {
    val usage = entries.groupingBy { it.tagId }.eachCount()
    return tags.filter { it.kind == kind.name && (!it.archived || it.id == keepId) }
        .sortedWith(compareByDescending<Tag> { usage[it.id] ?: 0 }.thenBy { it.name })
}
