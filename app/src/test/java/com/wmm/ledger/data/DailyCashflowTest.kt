package com.wmm.ledger.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DailyCashflowTest {
    @Test fun totalsUseNetExpensesOnThePurchaseDayAndExcludeTransfers() {
        val original = Entry("purchase", "EXPENSE", 3500, "meal", null, "", 1, "2026-11-01")
        val refund = Entry("refund", "EXPENSE", 500, "meal", null, "", 2, "2026-11-02", refundOfId = original.id)
        val income = Entry("salary", "INCOME", 50000, "salary", null, "", 1, original.day)
        val data = LedgerSnapshot(entries = listOf(original, refund, income),
            transfers = listOf(Transfer("transfer", 2000, "bank", "credit", "", 3, "2026-11-03")))
        val totals = data.dailyCashflow()
        assertEquals(Cashflow(50000, 3000), totals[original.day])
        assertNull(totals[refund.day])
        assertNull(totals["2026-11-03"])
        assertEquals(Cashflow(50000, 0), data.copy(entries = listOf(original, refund.copy(cents = 3500), income))
            .dailyCashflow()[original.day])
    }
}
