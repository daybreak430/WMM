package com.wmm.ledger.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class LedgerRulesTest {
    @Test fun integerCentsRejectRoundingZeroAndOutOfRange() {
        assertEquals(123_456L, parseCents("1234.56"))
        assertEquals(9L, parseCents(".09"))
        assertEquals(-105L, parseCents("-1.05", signed = true))
        assertEquals(0L, parseCents("0", signed = true))
        listOf("1.001", "1e3", "0", "-1.01", "1000000000.00", "9999999999999999999").forEach { assertNull(parseCents(it)) }
    }

    @Test fun balanceRecomputesAtEachAccountsCutoffOnTheSameDay() {
        val first = Account("first", "SAVINGS", "卡", 500_000, 100)
        val second = Account("second", "PREPAID", "充值卡", 20_000, 120)
        val older = entry("before", 10_000, 99, first.id)
        val equal = entry("at", 10_000, 100, first.id)
        val expense = entry("recent", 2_000, 110, first.id)
        val income = entry("income", 1_000, 111, first.id).copy(kind = "INCOME")
        val snapshot = LedgerSnapshot(entries = listOf(older, equal, expense, income), accounts = listOf(first, second))
        assertEquals(mapOf(first.id to 499_000L, second.id to 20_000L), snapshot.accountBalances())
        val edited = snapshot.copy(entries = listOf(older.copy(cents = 9), expense.copy(accountId = second.id), income))
        assertEquals(mapOf(first.id to 501_000L, second.id to 20_000L), edited.accountBalances())
        val afterSecond = edited.copy(entries = listOf(expense.copy(accountId = second.id, occurredAt = 121)))
        assertEquals(mapOf(first.id to 500_000L, second.id to 18_000L), afterSecond.accountBalances())
    }

    @Test fun periodsUseRecordedDatesAndIncludeArchivedTags() {
        val yearEnd = LocalDate.of(2025, 12, 31)
        val newYear = LocalDate.of(2026, 1, 1)
        val base = entry("previous", 200, 5, null).copy(day = yearEnd.toString())
        val records = listOf(base, base.copy(id = "current", cents = 300, day = newYear.toString()),
            base.copy(id = "income", cents = 500, tagId = "income", kind = "INCOME", day = newYear.toString()))
        val snapshot = LedgerSnapshot(entries = records, tags = listOf(Tag("tag", "EXPENSE", "支出标签", true), Tag("income", "INCOME", "收入标签")))
        assertEquals(200L, snapshot.stats(Period.DAY, yearEnd, newYear).expense)
        assertEquals(200L, snapshot.stats(Period.MONTH, yearEnd, newYear).tagTotals.single().cents)
        assertEquals(200L, snapshot.stats(Period.YEAR, newYear, newYear).net)
        assertEquals(emptyList<Tag>(), snapshot.frequentTags(Kind.EXPENSE))
        assertEquals("tag", snapshot.frequentTags(Kind.EXPENSE, "tag").single().id)
        val report = snapshot.stats(Period.MONTH, newYear, newYear)
        val noteEdit = snapshot.copy(entries = records.map { it.copy(note = "变动") })
        assertEquals(report.signature(), noteEdit.stats(Period.MONTH, newYear, newYear).signature())
        assertNotEquals(report.signature(), snapshot.stats(Period.MONTH, newYear, newYear.plusMonths(1)).signature())
        assertNotEquals(report.signature(), snapshot.copy(tags = snapshot.tags.map { it.copy(name = it.name + "改") })
            .stats(Period.MONTH, newYear, newYear).signature())
    }

    private fun entry(id: String, amount: Long, time: Long, account: String?) =
        Entry(id, "EXPENSE", amount, "tag", account, "", time, "2026-01-01")
}
