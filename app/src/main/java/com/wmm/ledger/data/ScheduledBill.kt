package com.wmm.ledger.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

@Entity(tableName = "scheduled_bills")
data class ScheduledBill(
    @PrimaryKey val id: String,
    val kind: String,
    val cents: Long,
    val tagId: String,
    val accountId: String?,
    val sceneId: String?,
    val note: String,
    val period: String,
    val startDay: String,
    val lastRecordedDay: String? = null,
) {
    internal fun nextDay(after: LocalDate? = lastRecordedDay?.let(LocalDate::parse)): LocalDate? {
        val start = LocalDate.parse(startDay)
        val earliest = maxOf(start, after?.plusDays(1) ?: start)
        val frequency = Period.valueOf(period)
        // 始终从首次日期推算，避免 31 日经过短月后永久漂到 28 日。
        val steps = when (frequency) {
            Period.DAY -> ChronoUnit.DAYS.between(start, earliest)
            Period.WEEK -> ChronoUnit.DAYS.between(start, earliest) / 7
            Period.MONTH -> ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(earliest))
            Period.YEAR -> (earliest.year - start.year).toLong()
        }
        val candidate = frequency.step(start, steps)
        val next = if (candidate < earliest) frequency.step(start, steps + 1) else candidate
        return next.takeIf { it <= LAST_DAY }
    }

    internal fun entryId(day: LocalDate) = "repeat-${sha256("$id|$day".encodeToByteArray()).take(48)}"
}

internal fun ScheduledBill.unavailableReason(data: LedgerSnapshot): String? =
    when {
        data.tags.none { it.id == tagId && it.kind == kind && !it.archived } -> "标签不可用"
        accountId != null && data.accounts.none { it.id == accountId && !it.archived } -> "账户不可用"
        else -> null
    }
