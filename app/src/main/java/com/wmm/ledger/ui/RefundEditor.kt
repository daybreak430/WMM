package com.wmm.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.wmm.ledger.LedgerViewModel
import com.wmm.ledger.data.Entry
import com.wmm.ledger.data.LedgerRepository
import com.wmm.ledger.data.LedgerSnapshot
import com.wmm.ledger.data.MAX_CENTS
import com.wmm.ledger.data.PaymentCapture
import com.wmm.ledger.data.changedOccurrenceTime
import com.wmm.ledger.data.currency
import com.wmm.ledger.data.editableAmount
import com.wmm.ledger.data.refundTotals
import java.time.LocalDate

@Composable
internal fun RefundEditor(data: LedgerSnapshot, model: LedgerViewModel, initial: Entry? = null,
    capture: PaymentCapture? = null, captureAccountId: String? = null, back: () -> Unit) {
    var originalId by rememberSaveable { mutableStateOf(initial?.refundOfId) }
    var accountId by rememberSaveable { mutableStateOf(initial?.accountId) }
    var accountChosen by rememberSaveable { mutableStateOf(initial != null) }
    var amount by rememberSaveable { mutableStateOf((initial?.cents ?: capture?.cents)?.let(::editableAmount).orEmpty()) }
    var note by rememberSaveable { mutableStateOf(initial?.note ?: capture?.merchant.orEmpty()) }
    var at by rememberSaveable { mutableStateOf(initial?.occurredAt ?: capture?.bookingAt ?: System.currentTimeMillis()) }
    var dayText by rememberSaveable { mutableStateOf(initial?.day ?: LedgerRepository.bookingDay(at).toString()) }
    var originalPicker by rememberSaveable { mutableStateOf(false) }
    var accountPicker by rememberSaveable { mutableStateOf(false) }
    var deleteDialog by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val day = LocalDate.parse(dayText)
    val totals = remember(data.entries, initial?.id) { data.refundTotals(initial?.id) }
    val tags = remember(data.tags) { data.tags.associate { it.id to it.name } }
    val accounts = remember(data.accounts, initial?.accountId) {
        data.accounts.filter { !it.archived || it.id == initial?.accountId }
    }
    val original = data.entries.firstOrNull { it.id == originalId && it.isOriginalExpense }
    val selectedAccount = if (accountChosen) accountId else (captureAccountId ?: original?.accountId)
    val account = accounts.firstOrNull { it.id == selectedAccount }
    val remaining = original?.let { it.cents - (totals[it.id] ?: 0) } ?: 0
    val cents = amountResult(amount)?.takeIf { it in 1..MAX_CENTS }
    fun title(entry: Entry) = entry.note.ifBlank { tags[entry.tagId] ?: "原支出" }
    fun hideKeyboard() { focus.clearFocus(); keyboard?.hide() }
    val chooseDate = CalendarAction(day) { changedDay ->
        at = changedOccurrenceTime(at, day, changedDay, null)
        dayText = changedDay.toString()
    }
    val save: () -> Unit = save@{
        val selected = original ?: return@save
        val value = cents?.takeIf { it <= remaining && selected.day <= dayText } ?: return@save
        saving = true
        hideKeyboard()
        model.saveRefund(initial?.id, selected.id, value, account?.id, note, at, day, capture?.id, back)
            .invokeOnCompletion { saving = false }
    }

    EntryEditorLayout(header = {
        PageHeader(if (initial == null) "核对支付宝退款" else "编辑退款", back, centered = true) {
            if (initial != null) IconButton(onClick = { deleteDialog = true }) {
                LucideIcon(Glyph.DELETE, MaterialTheme.colorScheme.error, "删除退款")
            }
        }
    }, content = { _ ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = PagePadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GroupCard {
                Column {
                    SettingsRow("原支出", description = original?.let { "${title(it)} · ${it.day} · ${currency(it.cents)}" },
                        icon = Glyph.BOOK, end = if (original == null) "请选择" else null) {
                        hideKeyboard(); originalPicker = true
                    }
                    Hairline(14)
                    SettingsRow("到账账户", icon = Glyph.WALLET, end = account?.name ?: "不关联") {
                        hideKeyboard(); accountPicker = true
                    }
                }
            }
            if (original != null && cents != null && (cents > remaining || original.day > dayText)) {
                Text("核对退款日期和剩余可退金额 ${currency(remaining)}。", color = MaterialTheme.colorScheme.error)
            }
        }
    }, composer = { compact ->
        EntryComposer(amount, { amount = amountKey(amount, it) }, "保存退款",
            original != null && original.day <= dayText && cents != null && cents <= remaining && !saving,
            saving, compact, save, note, { note = it }) {
            EntryMetaButton("日期", "${day.year}/${day.monthValue}/${day.dayOfMonth}", Glyph.CALENDAR) { hideKeyboard(); chooseDate() }
        }
    })

    if (originalPicker) LedgerAlertDialog({ originalPicker = false }, title = { Text("选择原支出") }, text = {
        val choices = remember(data.entries, totals, query, dayText, cents, tags) {
            data.entries.filter { entry -> entry.isOriginalExpense && entry.day <= dayText &&
                entry.cents - (totals[entry.id] ?: 0) >= (cents ?: 1) &&
                (query.isBlank() || listOf(title(entry), entry.day, editableAmount(entry.cents)).any { it.contains(query.trim(), true) }) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("搜索备注、日期或金额") }, singleLine = true)
            LazyColumn(Modifier.heightIn(max = 300.dp)) {
                items(choices, key = { it.id }) { entry ->
                    SettingsRow(title(entry), description = entry.day, end = currency(entry.cents)) {
                        originalId = entry.id; originalPicker = false
                    }
                }
                if (choices.isEmpty()) item { Text("没有可关联的原支出。可先补记原支出，再回到待核对。") }
            }
        }
    }, confirmButton = { TextButton(onClick = { originalPicker = false }) { Text("关闭") } })
    if (accountPicker) LedgerAlertDialog({ accountPicker = false }, title = { Text("到账账户") }, text = {
        LazyColumn(Modifier.heightIn(max = 360.dp)) {
            item { SettingsRow("不关联") { accountId = null; accountChosen = true; accountPicker = false } }
            items(accounts, key = { it.id }) { option ->
                SettingsRow(option.name) { accountId = option.id; accountChosen = true; accountPicker = false }
            }
        }
    }, confirmButton = { TextButton(onClick = { accountPicker = false }) { Text("关闭") } })
    if (deleteDialog && initial != null) ConfirmDialog("删除退款？", "删除后会恢复原支出的相应金额，并重新计算到账账户余额。",
        "删除", { deleteDialog = false }) { deleteDialog = false; model.deleteEntry(initial.id, back) }
}
