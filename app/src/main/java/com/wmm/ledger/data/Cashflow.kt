package com.wmm.ledger.data

import java.time.LocalDate

internal data class Cashflow(val income: Long, val expense: Long)

internal data class DailyCashflow(val date: LocalDate, val amount: Cashflow)

internal fun LedgerSnapshot.weekCashflow(day: LocalDate): List<DailyCashflow> {
    val first = Period.WEEK.start(day)
    val startDay = first.toString()
    val endDay = first.plusDays(6).toString()
    val days = entries.filter { it.day in startDay..endDay }.groupBy { it.day }
    return List(7) { index ->
        val date = first.plusDays(index.toLong())
        DailyCashflow(date, days[date.toString()].orEmpty().cashflow())
    }
}

private fun Iterable<Entry>.cashflow(): Cashflow {
    var income = 0L
    var expense = 0L
    for (entry in this) {
        if (entry.kind == Kind.INCOME.name) income += entry.cents else expense += entry.cents
    }
    return Cashflow(income, expense)
}
