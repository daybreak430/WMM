package com.wmm.ledger.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerQueryTest {
    private val bank = Account("bank", "SAVINGS", "光大银行", 100_000, 1_000)
    private val credit = Account("credit", "CREDIT", "花呗", -20_000, 1_000)
    private val metro = Tag("metro", "EXPENSE", "地铁")
    private val taxi = Tag("taxi", "EXPENSE", "打车")
    private val salary = Tag("salary", "INCOME", "工资")
    private val commute = Scene("commute", "通勤")
    private val trip = Scene("trip", "旅游")
    private val data = LedgerSnapshot(accounts = listOf(bank, credit), tags = listOf(metro, taxi, salary),
        scenes = listOf(commute, trip), entries = listOf(
            entry("m1", 300, metro.id, "2026-01-01", "上班", commute.id),
            entry("m2", 300, metro.id, "2026-05-01", "下班", commute.id),
            entry("m3", 300, metro.id, "2026-11-19", "回家", commute.id),
            entry("taxi", 3_000, taxi.id, "2026-11-18", "加班晚归", commute.id),
            entry("tour", 1_234_50, metro.id, "2026-08-01", "机场", trip.id),
            entry("prior", 99_999, taxi.id, "2025-12-31", "旧记录", commute.id),
            entry("income", 1_000_000, salary.id, "2026-11-01", "发薪", null).copy(kind = "INCOME"),
        ), transfers = listOf(Transfer("repayment", 50_000, bank.id, credit.id, "信用还款", 100, "2026-11-19")))

    private fun entry(id: String, cents: Long, tag: String, day: String, note: String, scene: String?) =
        Entry(id, "EXPENSE", cents, tag, bank.id, note, 100, day, scene)

    @Test fun searchCoversAllFieldsAndCombinesTermsWithoutIncludingOtherAmounts() {
        assertEquals(4, data.searchLedger("地铁").entries.size)
        assertEquals(5, data.searchLedger("通勤").entries.size)
        assertEquals(7, data.searchLedger("光大银行").entries.size)
        assertEquals(listOf("taxi"), data.searchLedger("加班晚归").entries.map { it.id })
        assertEquals(listOf("taxi"), data.searchLedger("光大银行 通勤 30.00").entries.map { it.id })
        assertEquals(0, data.searchLedger("30.01").entries.size)
        assertEquals(listOf("tour"), data.searchLedger("¥ １，２３４．５０ 元").entries.map { it.id })
        assertEquals(listOf("repayment"), data.searchLedger("花呗 500").transfers.map { it.id })
        assertTrue(data.searchLedger("不存在的消费").entries.isEmpty())
    }

    @Test fun yearLargestExpenseUsesAllRecordsAndExcludesIncomeAndTransfers() {
        val result = LedgerQueryEngine(data).execute(LedgerQuery(mode = QueryMode.RECORDS,
            filter = QueryFilter(from = "2026-01-01", to = "2026-12-31", kinds = setOf("EXPENSE")), limit = 1))
        assertEquals("tour", result.records.single().id)
        assertEquals(5, result.stats.count)
        assertEquals(127_350L, result.stats.expenseCents)
        assertEquals(0L, result.stats.incomeCents)
        assertEquals(listOf(LedgerReference(QuerySource.ENTRIES, "tour")), result.evidence)
    }

    @Test fun commuteFrequencyAndSpendingAreDifferentAndOrFiltersRemainLocal() {
        val result = LedgerQueryEngine(data).execute(LedgerQuery(mode = QueryMode.GROUPS,
            filter = QueryFilter(from = "2026-01-01", kinds = setOf("EXPENSE"), anyText = listOf("通勤", "上班", "下班")),
            groupBy = listOf(QueryGroupBy.TYPE), sort = QuerySort.COUNT))
        assertEquals(4, result.stats.count)
        assertEquals(listOf("地铁"), result.groups.first().labels)
        assertEquals(3, result.groups.first().stats.count)
        assertEquals(900L, result.groups.first().stats.totalCents)
        assertEquals(3_000L, result.groups.last().stats.totalCents)
        assertEquals(975L, result.stats.averageCents)
    }

    @Test fun aggregatesStayCompleteWhenOnlyOneGroupIsDisplayed() {
        val result = LedgerQueryEngine(data).execute(LedgerQuery(mode = QueryMode.GROUPS,
            filter = QueryFilter(kinds = setOf("EXPENSE")), groupBy = listOf(QueryGroupBy.YEAR), limit = 1))
        assertEquals(6, result.stats.count)
        assertEquals(2, result.groupCount)
        assertEquals(1, result.groups.size)
        assertEquals(227_349L, result.stats.totalCents)
    }

    @Test fun activeAssetBalancesUseDebtSignsAndKeepLegacyArchivedAssetsOut() {
        val engine = LedgerQueryEngine(data.copy(accounts = data.accounts + Account("hidden", "SAVINGS", "旧归档", 999_999, 0, archived = true)))
        assertEquals(80_000L, engine.execute(LedgerQuery(source = QuerySource.ACCOUNTS)).stats.totalCents)
        assertEquals(-20_000L, engine.execute(LedgerQuery(source = QuerySource.ACCOUNTS,
            filter = QueryFilter(maxCents = -1))).stats.totalCents)
        assertEquals(1_079_999L, engine.execute(LedgerQuery(source = QuerySource.ACCOUNTS, includeArchived = true)).stats.totalCents)
    }
}
