package com.wmm.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wmm.ledger.LedgerViewModel
import com.wmm.ledger.data.Kind
import com.wmm.ledger.data.LedgerRepository
import com.wmm.ledger.data.LedgerSnapshot
import com.wmm.ledger.data.Transfer
import com.wmm.ledger.data.balanceDescription
import com.wmm.ledger.data.accountBalances
import com.wmm.ledger.data.editableAmount
import com.wmm.ledger.data.isCredit
import com.wmm.ledger.data.changedOccurrenceTime
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
internal fun TransferEditor(
    data: LedgerSnapshot, initial: Transfer?, model: LedgerViewModel, back: () -> Unit,
    onSelectKind: ((Kind) -> Unit)? = null,
) {
    val activeAccounts = data.accounts.filterNot { it.archived }
    val defaultFrom = activeAccounts.firstOrNull { it.isDefault && !it.isCredit }
        ?: activeAccounts.firstOrNull { !it.isCredit } ?: activeAccounts.firstOrNull()
    var fromId by rememberSaveable { mutableStateOf(initial?.fromAccountId ?: defaultFrom?.id) }
    var toId by rememberSaveable { mutableStateOf(initial?.toAccountId) }
    var amount by rememberSaveable { mutableStateOf(initial?.cents?.let(::editableAmount).orEmpty()) }
    var note by rememberSaveable { mutableStateOf(initial?.note.orEmpty()) }
    var at by rememberSaveable { mutableStateOf(initial?.occurredAt ?: System.currentTimeMillis()) }
    var dayText by rememberSaveable { mutableStateOf(initial?.day ?: LedgerRepository.bookingDay(at).toString()) }
    var hasTime by rememberSaveable { mutableStateOf(initial?.hasTime ?: false) }
    var choosingFrom by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var dateEditor by rememberSaveable { mutableStateOf(false) }
    var noteEditor by rememberSaveable { mutableStateOf(false) }
    var deleteDialog by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val zone = ZoneId.systemDefault()
    val day = LocalDate.parse(dayText)
    val time = at.takeIf { hasTime }?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() }
    val cents = amountResult(amount)?.takeIf { it in 1..com.wmm.ledger.data.MAX_CENTS }
    val accountsById = data.accounts.associateBy { it.id }
    val from = accountsById[fromId]
    val to = accountsById[toId]
    val balances = remember(data) { data.accountBalances() }
    val isNew = initial == null
    val inset = LedgerPageInset()
    val colors = MaterialTheme.colorScheme
    fun dismissKeyboard() {
        focus.clearFocus()
        keyboard?.hide()
    }
    val fromAvailable = from != null && (!from.archived || initial?.fromAccountId == from.id)
    val toAvailable = to != null && (!to.archived || initial?.toAccountId == to.id)

    val save: () -> Unit = save@{
        val saveCents = amountResult(amount)?.takeIf { it in 1..com.wmm.ledger.data.MAX_CENTS } ?: return@save
        val fromAccount = from ?: return@save
        val toAccount = to ?: return@save
        saving = true
        dismissKeyboard()
        model.saveTransfer(initial?.id, saveCents, fromAccount.id, toAccount.id, note, at, day, hasTime, back)
            .invokeOnCompletion { saving = false }
    }
    EntryEditorLayout(header = {
        if (isNew && onSelectKind != null) EntryEditorHeader(null, onSelectKind, onTransfer = {}, back = back)
        else PageHeader("编辑转账", back, centered = true) {
            IconButton(onClick = { deleteDialog = true }) { LucideIcon(Glyph.DELETE, colors.error, "删除转账") }
        }
    }, content = {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = inset, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            GroupCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        EntryDetailRow("转出账户", from?.name ?: "请选择", Glyph.EXPENSE) {
                            dismissKeyboard()
                            choosingFrom = true
                        }
                        Hairline(14)
                        EntryDetailRow("转入账户", to?.name ?: "请选择", Glyph.INCOME) {
                            dismissKeyboard()
                            choosingFrom = false
                        }
                    }
                    TextButton(
                        modifier = Modifier.padding(end = 8.dp)
                            .semantics { contentDescription = "交换转出和转入账户" },
                        onClick = {
                            dismissKeyboard()
                            val previousFrom = fromId
                            fromId = toId
                            toId = previousFrom
                        },
                    ) { Text("交换") }
                }
            }
            if (activeAccounts.size < 2 && isNew) {
                Text("转账需要两个账户，可在个人设置的资产页添加。",
                    color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }, composer = { compact ->
        EntryComposer(amount, { amount = amountKey(amount, it) }, "保存转账", cents != null && fromAvailable && toAvailable &&
            fromId != toId && !saving, saving, compact, save) {
            val dateLabel = (if (day.year == LocalDate.now().year) "" else "${day.year}/") +
                "${day.monthValue}/${day.dayOfMonth}" + (time?.format(TimeLabel)?.let { " $it" } ?: "")
            EntryMetaButton("日期时间", dateLabel, Glyph.CALENDAR) { dateEditor = true }
            EntryMetaButton("备注", note.ifBlank { "选填" }, Glyph.EDIT) { noteEditor = true }
        }
    })
    choosingFrom?.let { outgoing ->
        val originalId = if (outgoing) initial?.fromAccountId else initial?.toAccountId
        val otherId = if (outgoing) toId else fromId
        val options = data.accounts.filter { (!it.archived || it.id == originalId) && it.id != otherId }
        LedgerAlertDialog(onDismissRequest = { choosingFrom = null },
            title = { Text(if (outgoing) "转出账户" else "转入账户") }, text = {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(options, key = { it.id }) { account ->
                        SettingsRow(account.name, description = account.balanceDescription(balances[account.id] ?: 0)) {
                            if (outgoing) fromId = account.id else toId = account.id
                            choosingFrom = null
                        }
                    }
                    if (options.isEmpty()) item { Text("暂无其他可用账户", Modifier.padding(14.dp)) }
                }
            }, confirmButton = { TextButton(onClick = { choosingFrom = null }) { Text("关闭") } })
    }
    if (dateEditor) {
        EntryDateDialog(day, time, onDismiss = { dateEditor = false }) { changedDay, changedTime ->
            if (changedDay != day || changedTime != time) {
                dayText = changedDay.toString()
                hasTime = changedTime != null
                at = changedOccurrenceTime(at, day, changedDay, changedTime, zone)
            }
            dateEditor = false
        }
    }
    if (noteEditor) {
        EntryNoteDialog(note, onDismiss = { noteEditor = false }) {
            note = it
            noteEditor = false
        }
    }
    if (deleteDialog && initial != null) {
        ConfirmDialog("删除转账？", "删除后，转出和转入账户的余额都会重新计算。", "删除",
            onDismiss = { deleteDialog = false }) {
            deleteDialog = false
            model.deleteTransfer(initial.id, back)
        }
    }
}
