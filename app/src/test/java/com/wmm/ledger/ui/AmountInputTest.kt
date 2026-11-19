package com.wmm.ledger.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountInputTest {
    private fun enter(keys: String, initial: String = ""): String = keys.fold(initial) { value, key ->
        amountKey(value, key.toString())
    }

    @Test fun decimalAndLeadingZeroInputStaysWithinMoneyPrecision() {
        assertEquals("12.34", enter("0012.345"))
        assertEquals("0.50", enter(".50"))
        assertEquals("1.23", enter("1..23"))
        assertEquals("999999999.99", enter("9999999999.999"))
        assertEquals("1.2", amountKey("1.23", "backspace"))
        assertEquals("", amountKey("1.23", "clear"))
    }

    @Test fun additionAndSubtractionUseExactCents() {
        assertEquals(30L, amountResult(enter("0.1+0.2")))
        assertEquals(1_235L, amountResult(enter("20-7.65")))
        assertEquals(-500L, amountResult(enter("5-10")))
        assertEquals(0L, amountResult(enter("1-1")))
        assertEquals(100_000_000_000L, amountResult(enter("999999999.99+0.01")))
    }

    @Test fun incompleteExpressionsCannotBeSavedAndOperatorsCanBeCorrected() {
        assertNull(amountResult(""))
        assertNull(amountResult("12+"))
        assertNull(amountResult("12-"))
        assertEquals("12-", enter("+-", "12"))
        assertEquals("", enter("+-"))
        assertEquals(1_200L, amountResult(amountKey("12+", "backspace")))
    }
}
