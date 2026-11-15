package com.wmm.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wmm.ledger.LedgerViewModel
import com.wmm.ledger.data.Entry
import com.wmm.ledger.data.Kind
import com.wmm.ledger.data.LedgerRepository
import com.wmm.ledger.data.LedgerSnapshot
import com.wmm.ledger.data.MAX_NAME
import com.wmm.ledger.data.MAX_NOTE
import com.wmm.ledger.data.editableAmount
import com.wmm.ledger.data.frequentTags
import com.wmm.ledger.data.parseCents
import com.wmm.ledger.data.validName
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryEditor(data: LedgerSnapshot, initial: Entry?, model: LedgerViewModel, back: () -> Unit) {
    var kindName by rememberSaveable { mutableStateOf(initial?.kind ?: Kind.EXPENSE.name) }
    val kind = Kind.valueOf(kindName)
    var amountText by rememberSaveable { mutableStateOf(initial?.let { editableAmount(it.cents) } ?: "") }
    val cents = parseCents(amountText)
    var tagId by rememberSaveable { mutableStateOf(initial?.tagId) }
    var accountId by rememberSaveable { mutableStateOf(initial?.accountId) }
    var note by rememberSaveable { mutableStateOf(initial?.note.orEmpty()) }
    var occurredAt by rememberSaveable { mutableStateOf(initial?.occurredAt ?: System.currentTimeMillis()) }
    var bookedDay by rememberSaveable { mutableStateOf(initial?.day ?: LedgerRepository.bookingDay(occurredAt).toString()) }
    val day = LocalDate.parse(bookedDay)
    val zone = ZoneId.systemDefault()
    val time = Instant.ofEpochMilli(occurredAt).atZone(zone).toLocalTime()
    var tagDialog by rememberSaveable { mutableStateOf(false) }
    var accountDialog by rememberSaveable { mutableStateOf(false) }
    var deleteDialog by rememberSaveable { mutableStateOf(false) }
    var showTags by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val tags = remember(data, kind, initial?.tagId) { data.frequentTags(kind, initial?.tagId) }
    val account = data.accounts.firstOrNull { it.id == accountId }
    val currentTag = tags.firstOrNull { it.id == tagId }
    val visibleTags = if (showTags) tags else (tags.take(8) + listOfNotNull(currentTag)).distinctBy { it.id }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val amountFocus = remember { FocusRequester() }
    var requested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!requested && initial == null) {
            requested = true
            amountFocus.requestFocus()
            delay(150)
            if (!tagDialog) keyboard?.show()
        }
    }
    val chooseDate = CalendarAction(day) { date ->
        bookedDay = date.toString()
        occurredAt = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
    }
    val chooseTime = TimeAction(time) { changed ->
        occurredAt = day.atTime(changed).atZone(zone).toInstant().toEpochMilli()
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        PageHeader(if (initial == null) "记一笔" else "编辑流水", back) {
            if (initial != null) IconButton(onClick = { deleteDialog = true }) {
                LineIcon(Glyph.DELETE, MaterialTheme.colorScheme.error, "删除流水")
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
            .padding(horizontal = PagePadding, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Kind.entries.forEachIndexed { index, option ->
                    SegmentedButton(selected = option == kind, onClick = {
                        if (option != kind) { kindName = option.name; tagId = null; showTags = false }
                    }, shape = SegmentedButtonDefaults.itemShape(index, Kind.entries.size), icon = {}, label = { Text(option.title) })
                }
            }
            OutlinedTextField(value = amountText, onValueChange = { if (it.length <= 17) amountText = it },
                modifier = Modifier.fillMaxWidth().focusRequester(amountFocus).testTag("entry-amount"),
                label = { Text("金额") }, prefix = { Text("¥ ", style = MaterialTheme.typography.titleLarge) },
                textStyle = HeroNumber.copy(color = MaterialTheme.colorScheme.onSurface),
                singleLine = true, isError = amountText.isNotBlank() && cents == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                shape = CardShape)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionTitle("标签") {
                    if (tags.size > 8) TextButton(onClick = { showTags = !showTags }) { Text(if (showTags) "收起" else "全部") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    visibleTags.forEach { tag ->
                        FilterChip(selected = tagId == tag.id, onClick = {
                            tagId = tag.id
                            focus.clearFocus()
                            keyboard?.hide()
                        }, label = { Text(tag.name) }, leadingIcon = if (tagId == tag.id) {
                            { LineIcon(Glyph.CHECK, MaterialTheme.colorScheme.primary, size = 16) }
                        } else null)
                    }
                    ElevatedAssistChip(onClick = {
                        focus.clearFocus()
                        keyboard?.hide()
                        tagDialog = true
                    }, label = { Text("新建标签") }, leadingIcon = { LineIcon(Glyph.PLUS, size = 16) })
                }
            }
            GroupCard {
                SettingsRow("账户", icon = Glyph.WALLET, end = account?.name ?: "不关联") {
                    focus.clearFocus()
                    accountDialog = true
                }
                Hairline(13)
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    TextButton(onClick = { focus.clearFocus(); chooseDate() }, modifier = Modifier.weight(1.45f)) {
                        LineIcon(Glyph.CALENDAR, MaterialTheme.colorScheme.primary, size = 17)
                        Text(" ${day.year}/${day.monthValue}/${day.dayOfMonth}", maxLines = 1)
                    }
                    TextButton(onClick = { focus.clearFocus(); chooseTime() }, modifier = Modifier.weight(1f)) {
                        Text(time.format(TimeLabel))
                    }
                }
            }
            OutlinedTextField(note, { if (it.length <= MAX_NOTE) note = it }, Modifier.fillMaxWidth(),
                label = { Text("备注（选填）") }, maxLines = 3, shape = CardShape)
        }
        Surface(color = MaterialTheme.colorScheme.background) {
            Button(onClick = {
                val saveCents = cents ?: return@Button
                val selected = tagId ?: return@Button
                saving = true
                focus.clearFocus()
                keyboard?.hide()
                model.saveEntry(initial?.id, kind, saveCents, selected, accountId, note, occurredAt, day, back)
                    .invokeOnCompletion { saving = false }
            }, enabled = cents != null && tagId != null && !saving,
                modifier = Modifier.fillMaxWidth().padding(start = PagePadding, end = PagePadding, top = 8.dp, bottom = 10.dp)) {
                Text(if (saving) "正在保存" else "保存流水")
            }
        }
    }
    if (tagDialog) {
        NameDialog("新建${kind.title}标签", "标签名称", "", onDismiss = { tagDialog = false }) { name ->
            model.saveTag(kind, name, null) { tag ->
                tagId = tag.id
                tagDialog = false
            }
        }
    }
    if (accountDialog) {
        val available = data.accounts.filter { !it.archived || it.id == initial?.accountId }
        AlertDialog(onDismissRequest = { accountDialog = false }, title = { Text("关联账户") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SettingsRow("不关联", end = if (accountId == null) "当前" else null) {
                    accountId = null
                    accountDialog = false
                }
                available.forEach { item ->
                    Hairline()
                    SettingsRow(item.name, end = if (accountId == item.id) "当前" else null) {
                        accountId = item.id
                        accountDialog = false
                    }
                }
                if (available.isEmpty()) Text("暂无账户，可在资产页添加。", Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }, confirmButton = { TextButton(onClick = { accountDialog = false }) { Text("关闭") } })
    }
    if (deleteDialog && initial != null) {
        ConfirmDialog("删除这笔流水？", "删除后会更新收支统计和关联账户余额。", "删除", { deleteDialog = false }) {
            deleteDialog = false
            model.deleteEntry(initial.id, back)
        }
    }
}

@Composable
fun NameDialog(title: String, label: String, original: String, allowBlank: Boolean = false,
    onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(original) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        OutlinedTextField(name, { if (it.length <= MAX_NAME) name = it }, label = { Text(label) }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done))
    }, confirmButton = { TextButton(onClick = { onSave(name) }, enabled = validName(name.trim()) || allowBlank && name.isBlank()) {
        Text("保存")
    } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
