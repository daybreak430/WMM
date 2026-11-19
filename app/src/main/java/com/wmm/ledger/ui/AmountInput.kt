package com.wmm.ledger.ui

import com.wmm.ledger.data.MAX_CENTS
import com.wmm.ledger.data.parseCents

// 输入只处理加减和整数分，避免浮点计算让金额出现误差；未完成的算式不能保存。
internal fun amountResult(expression: String): Long? {
    if (expression.isEmpty() || expression.last() in "+-") return null
    return expression.split(Regex("(?=[+-])")).filter { it.isNotEmpty() }.fold(0L as Long?) { total, term ->
        val negative = term.startsWith('-')
        val cents = parseCents(term.removePrefix("+").removePrefix("-"), signed = true)
        if (total == null || cents == null) null else total + if (negative) -cents else cents
    }
}

internal fun amountKey(expression: String, key: String): String {
    if (key == "clear") return ""
    if (key == "backspace") return expression.dropLast(1)
    if (key in listOf("+", "-")) {
        if (expression.isEmpty()) return expression
        return if (expression.last() in "+-") expression.dropLast(1) + key else expression + key
    }
    if (expression.length >= 64) return expression
    val start = expression.indexOfLast { it in "+-" } + 1
    val prefix = expression.take(start)
    val operand = expression.substring(start)
    val updated = when {
        key == "." -> if ('.' in operand) return expression else operand.ifEmpty { "0" } + "."
        key.length == 1 && key[0] in '0'..'9' -> if (operand == "0") key else operand + key
        else -> return expression
    }
    val cents = parseCents(updated, signed = true) ?: return expression
    if (cents !in 0..MAX_CENTS) return expression
    return prefix + updated
}
