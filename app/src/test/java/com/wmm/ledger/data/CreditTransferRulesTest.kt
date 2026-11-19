package com.wmm.ledger.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditTransferRulesTest {
    private val day = LocalDate.of(2026, 11, 19)
    private val bank = Account("bank", AccountType.SAVINGS.name, "银行卡", 100_000, 100)
    private val credit = Account("credit", AccountType.CREDIT_CARD.name, "信用卡", -20_000, 100)
    private val repayment = Transfer("repayment", 5_000, bank.id, credit.id, "还款", 120, day.toString())

    @Test fun creditExpenseAndRepaymentHaveSeparateCashflowMeanings() {
        val expense = Entry("expense", Kind.EXPENSE.name, 3_000, "meal", credit.id, "", 110, day.toString())
        val data = LedgerSnapshot(entries = listOf(expense), accounts = listOf(bank, credit),
            tags = listOf(Tag("meal", Kind.EXPENSE.name, "餐饮")), transfers = listOf(repayment))
        assertEquals(mapOf(bank.id to 95_000L, credit.id to -18_000L), data.accountBalances())
        assertEquals(77_000L, data.netAssetTotal())
        val stats = data.stats(Period.MONTH, day, day)
        assertEquals(3_000L, stats.expense)
        assertEquals(0L, stats.income)
        assertEquals(1, stats.entries.size)
        assertEquals(3_000L, stats.tagTotals.single().cents)
        val onlyRepayment = data.copy(entries = emptyList())
        assertEquals(80_000L, onlyRepayment.netAssetTotal())
        assertEquals(0L, onlyRepayment.reportAnalysis(Period.MONTH, day, day).current.expense)
        assertEquals(0L, onlyRepayment.weekCashflow(day).sumOf { it.amount.expense })
    }

    @Test fun transferRespectsEachAccountsOwnCalibrationAndRecomputesEdits() {
        val calibratedCredit = credit.copy(calibratedAt = 130)
        val data = LedgerSnapshot(accounts = listOf(bank, calibratedCredit), transfers = listOf(repayment))
        assertEquals(mapOf(bank.id to 95_000L, credit.id to -20_000L), data.accountBalances())
        val changed = data.copy(transfers = listOf(repayment.copy(cents = 7_000, occurredAt = 140)))
        assertEquals(mapOf(bank.id to 93_000L, credit.id to -13_000L), changed.accountBalances())
        val reversed = changed.copy(transfers = listOf(repayment.copy(fromAccountId = credit.id,
            toAccountId = bank.id, occurredAt = 140)))
        assertEquals(mapOf(bank.id to 105_000L, credit.id to -25_000L), reversed.accountBalances())
        assertEquals(mapOf(bank.id to 100_000L, credit.id to -20_000L),
            data.copy(transfers = emptyList()).accountBalances())
    }

    @Test fun creditInputUsesPositiveDebtAndSupportsOverpayment() {
        for (type in listOf(AccountType.CREDIT_CARD, AccountType.MEITUAN_CREDIT, AccountType.HUABEI, AccountType.CREDIT)) {
            val account = credit.copy(type = type.name)
            assertTrue(account.isCredit)
            assertEquals(-18_000L, type.balanceFromInput(18_000))
            assertEquals(18_000L, account.balanceInput(-18_000))
            assertEquals(1_000L, type.balanceFromInput(-1_000))
            assertEquals("待还 ¥180.00", account.balanceDescription(-18_000))
            assertEquals("溢缴 ¥10.00", account.balanceDescription(1_000))
        }
        assertFalse(bank.isCredit)
        assertEquals(18_000L, AccountType.SAVINGS.balanceFromInput(18_000))
    }
}
