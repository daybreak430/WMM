package com.wmm.ledger.data

import java.time.LocalDate

data class ReportAnalysis(
    val current: PeriodStats,
    val previous: PeriodStats?,
    val buckets: List<CashflowBucket>,
)

data class CashflowBucket(val label: String, val income: Long, val expense: Long, val count: Int)

fun LedgerSnapshot.reportAnalysis(period: Period, day: LocalDate, today: LocalDate): ReportAnalysis {
    val current = stats(period, day, today)
    val previousStart = period.step(current.start, -1)
    // 只比较两个完整且均有流水的自然期间，避免把未结束的周期与整期直接比较。
    val previous = if (!current.inProgress && previousStart >= FIRST_DAY) {
        stats(period, previousStart, today).takeIf { it.entries.isNotEmpty() }
    } else null
    val buckets = when (period) {
        Period.DAY -> emptyList()
        Period.MONTH -> current.entries.groupBy { (LocalDate.parse(it.day).dayOfMonth - 1) / 7 }
            .toSortedMap().map { (week, records) ->
                val first = week * 7 + 1
                val last = (first + 6).coerceAtMost(current.start.lengthOfMonth())
                cashflowBucket("${first}–${last}日", records)
            }
        Period.YEAR -> current.entries.groupBy { it.day.substring(0, 7) }
            .toSortedMap().map { (month, records) -> cashflowBucket(month, records) }
    }
    return ReportAnalysis(current, previous, buckets)
}

private fun cashflowBucket(label: String, records: List<Entry>): CashflowBucket {
    val income = records.filter { it.kind == Kind.INCOME.name }.sumOf(Entry::cents)
    return CashflowBucket(label, income, records.sumOf(Entry::cents) - income, records.size)
}
