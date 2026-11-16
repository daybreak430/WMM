package com.wmm.ledger.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class ReportAnalysis(
    val current: PeriodStats,
    val previous: PeriodStats?,
    val buckets: List<CashflowBucket>,
    val through: LocalDate,
)

data class CashflowBucket(val label: String, val income: Long, val expense: Long, val count: Int, val coveredDays: Int)

fun LedgerSnapshot.reportAnalysis(period: Period, day: LocalDate, today: LocalDate): ReportAnalysis {
    val current = stats(period, day, today)
    val periodEnd = period.step(current.start, 1).minusDays(1)
    val through = if (current.inProgress) minOf(today, periodEnd) else periodEnd
    val previousStart = period.step(current.start, -1)
    // 只比较两个完整且均有流水的自然期间，避免把未结束的周期与整期直接比较。
    val previous = if (!current.inProgress && previousStart >= FIRST_DAY) {
        stats(period, previousStart, today).takeIf { it.entries.isNotEmpty() }
    } else null
    val buckets = when (period) {
        Period.DAY -> emptyList()
        Period.WEEK -> current.entries.groupBy { it.day }.toSortedMap().map { (date, records) ->
            cashflowBucket(date, records, 1)
        }
        Period.MONTH -> current.entries.groupBy { (LocalDate.parse(it.day).dayOfMonth - 1) / 7 }
            .toSortedMap().map { (week, records) ->
                val first = week * 7 + 1
                val last = (first + 6).coerceAtMost(current.start.lengthOfMonth())
                cashflowBucket("${first}–${last}日", records,
                    (minOf(last, through.dayOfMonth) - first + 1).coerceAtLeast(0))
            }
        Period.YEAR -> current.entries.groupBy { it.day.substring(0, 7) }
            .toSortedMap().map { (month, records) ->
                val start = LocalDate.parse("$month-01")
                val end = minOf(start.plusMonths(1).minusDays(1), through)
                cashflowBucket(month, records, (ChronoUnit.DAYS.between(start, end) + 1).coerceAtLeast(0).toInt())
            }
    }
    return ReportAnalysis(current, previous, buckets, through)
}

private fun cashflowBucket(label: String, records: List<Entry>, coveredDays: Int): CashflowBucket {
    val income = records.filter { it.kind == Kind.INCOME.name }.sumOf(Entry::cents)
    return CashflowBucket(label, income, records.sumOf(Entry::cents) - income, records.size, coveredDays)
}
