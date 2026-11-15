package com.wmm.ledger.data

import java.time.LocalDate

internal data class Cashflow(val income: Long, val expense: Long) {
    val net get() = income - expense
}

internal data class DailyCashflow(val date: LocalDate, val amount: Cashflow)

internal fun LedgerSnapshot.recentCashflow(end: LocalDate): List<DailyCashflow> {
    val first = end.minusDays(6)
    val startDay = first.toString()
    val endDay = end.toString()
    val days = entries.filter { it.day in startDay..endDay }.groupBy { it.day }
    return List(7) { index ->
        val date = first.plusDays(index.toLong())
        DailyCashflow(date, days[date.toString()].orEmpty().cashflow())
    }
}

internal fun LedgerSnapshot.monthCashflow(today: LocalDate): Cashflow {
    val start = today.withDayOfMonth(1).toString()
    val end = today.toString()
    return entries.asSequence().filter { it.day in start..end }.asIterable().cashflow()
}

private fun Iterable<Entry>.cashflow(): Cashflow {
    var income = 0L
    var expense = 0L
    for (entry in this) {
        if (entry.kind == Kind.INCOME.name) income += entry.cents else expense += entry.cents
    }
    return Cashflow(income, expense)
}
