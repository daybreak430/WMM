package com.wmm.ledger.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transfers",
    foreignKeys = [
        ForeignKey(entity = Account::class, parentColumns = ["id"], childColumns = ["fromAccountId"]),
        ForeignKey(entity = Account::class, parentColumns = ["id"], childColumns = ["toAccountId"]),
    ],
    indices = [Index("fromAccountId"), Index("toAccountId"), Index("day")],
)
data class Transfer(
    @PrimaryKey val id: String,
    val cents: Long,
    val fromAccountId: String,
    val toAccountId: String,
    val note: String,
    val occurredAt: Long,
    val day: String,
    val hasTime: Boolean = false,
)

val Account.isCredit: Boolean get() = AccountType.valueOf(type).isCredit

// 账本内部统一使用带符号余额。信用账户的正数欠款输入转成负余额，负数输入表示溢缴。
internal fun AccountType.balanceFromInput(amount: Long): Long = if (isCredit) -amount else amount

internal fun Account.balanceInput(balance: Long): Long = if (isCredit) -balance else balance

internal fun Account.balanceDescription(balance: Long): String = when {
    !isCredit -> currency(balance)
    balance > 0 -> "溢缴 ${currency(balance)}"
    else -> "待还 ${currency(-balance)}"
}
