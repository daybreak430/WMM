package com.wmm.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wmm.ledger.LedgerViewModel
import com.wmm.ledger.data.Kind
import com.wmm.ledger.data.LAST_DAY
import com.wmm.ledger.data.LedgerSnapshot
import com.wmm.ledger.data.MAX_NOTE
import com.wmm.ledger.data.Period
import com.wmm.ledger.data.ScheduledBill
import com.wmm.ledger.data.currency
import com.wmm.ledger.data.editableAmount
import com.wmm.ledger.data.orderedTags
import com.wmm.ledger.data.parseCents
import com.wmm.ledger.data.unavailableReason
import java.time.LocalDate

private val Period.repeatLabel: String get() = if (this == Period.DAY) "每天" else "每$title"

@Composable
internal fun ScheduledBillsScreen(data: LedgerSnapshot, back: () -> Unit, edit: (String?) -> Unit) {
    val tagsById = remember(data.tags) { data.tags.associateBy { it.id } }
    Column(Modifier.fillMaxSize()) {
        PageHeader("定时账单", back) {
            IconButton(onClick = { edit(null) }) { LucideIcon(Glyph.PLUS, description = "新建定时账单") }
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = PagePadding, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)) {
            if (data.scheduledBills.isEmpty()) {
                item { GroupCard { EmptyPanel("还没有定时账单", glyph = Glyph.CALENDAR) } }
            }
            items(data.scheduledBills, key = ScheduledBill::id) { bill ->
                val next = bill.nextDay()
                val status = bill.unavailableReason(data) ?: (next?.let { "下次 $it" } ?: "已结束")
                val frequency = Period.valueOf(bill.period).repeatLabel
                val amount = (if (bill.kind == Kind.INCOME.name) "+" else "−") + currency(bill.cents)
                GroupCard {
                    SettingsRow(bill.note.ifBlank { tagsById[bill.tagId]?.name ?: "定时账单" },
                        description = "$frequency · $status", icon = Glyph.CALENDAR, end = amount) { edit(bill.id) }
                }
            }
        }
    }
}

private enum class BillField(val title: String) { TAG("类型标签"), SCENE("场景"), ACCOUNT("账户") }

@Composable
internal fun ScheduledBillEditor(
    data: LedgerSnapshot, bill: ScheduledBill?, model: LedgerViewModel, today: LocalDate, back: () -> Unit,
) {
    var kindName by rememberSaveable { mutableStateOf(bill?.kind ?: Kind.EXPENSE.name) }
    var periodName by rememberSaveable { mutableStateOf(bill?.period ?: Period.MONTH.name) }
    var startDay by rememberSaveable { mutableStateOf(bill?.startDay ?: today.toString()) }
    var amount by rememberSaveable { mutableStateOf(bill?.cents?.let(::editableAmount).orEmpty()) }
    var note by rememberSaveable { mutableStateOf(bill?.note.orEmpty()) }
    var tagId by rememberSaveable { mutableStateOf(bill?.tagId) }
    var sceneId by rememberSaveable { mutableStateOf(bill?.sceneId) }
    var accountId by rememberSaveable { mutableStateOf(bill?.accountId) }
    var fieldName by rememberSaveable { mutableStateOf<String?>(null) }
    var addTag by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val kind = Kind.valueOf(kindName)
    val period = Period.valueOf(periodName)
    val start = LocalDate.parse(startDay)
    val tags = remember(data.tags, data.entries, kind) { data.orderedTags(kind) }
    val accounts = data.accounts.filterNot { it.archived }
    val selectedTag = tags.firstOrNull { it.id == tagId }
    val selectedAccount = accounts.firstOrNull { it.id == accountId }
    val cents = parseCents(amount)
    val focus = LocalFocusManager.current
    val calendar = CalendarAction(maxOf(start, today).coerceAtMost(LAST_DAY), limit = LAST_DAY, earliest = today) {
        startDay = it.toString()
    }
    val scheduleChanged = bill == null || bill.period != periodName || bill.startDay != startDay
    val canSave = !saving && cents != null && selectedTag != null &&
        (accountId == null || selectedAccount != null) && (!scheduleChanged || start >= today)

    Column(Modifier.fillMaxSize().imePadding()) {
        PageHeader(if (bill == null) "新建定时账单" else "编辑定时账单", back) {
            if (bill != null) {
                IconButton(onClick = { confirmDelete = true }, enabled = !saving) {
                    LucideIcon(Glyph.DELETE, MaterialTheme.colorScheme.error, description = "删除定时账单")
                }
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = PagePadding, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                LedgerSegments(Kind.entries, kind, { it.title }) {
                    if (it != kind) {
                        kindName = it.name
                        tagId = null
                    }
                }
            }
            item {
                OutlinedTextField(amount, { if (it.length <= 18) amount = it }, Modifier.fillMaxWidth(),
                    label = { Text("金额") }, singleLine = true, shape = FieldShape, colors = ledgerFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = amount.isNotBlank() && cents == null)
            }
            item {
                GroupCard {
                    SettingsRow("类型标签", end = selectedTag?.name ?: if (tagId == null) "请选择" else "不可用") {
                        focus.clearFocus()
                        fieldName = BillField.TAG.name
                    }
                    if (kind == Kind.EXPENSE) {
                        Hairline(14)
                        SettingsRow("场景", end = data.scenes.firstOrNull { it.id == sceneId }?.name ?: "不关联") {
                            focus.clearFocus()
                            fieldName = BillField.SCENE.name
                        }
                    }
                    Hairline(14)
                    SettingsRow("账户", end = selectedAccount?.name ?: if (accountId == null) "不关联" else "不可用") {
                        focus.clearFocus()
                        fieldName = BillField.ACCOUNT.name
                    }
                }
            }
            item {
                LedgerSegments(Period.entries, period, { it.repeatLabel }) {
                    if (it != period) {
                        periodName = it.name
                        if (start < today) startDay = today.toString()
                    }
                }
            }
            item {
                GroupCard {
                    SettingsRow("首次日期", end = startDay) {
                        focus.clearFocus()
                        calendar()
                    }
                }
            }
            item {
                OutlinedTextField(note, { if (it.length <= MAX_NOTE) note = it }, Modifier.fillMaxWidth(),
                    label = { Text("备注（选填）") }, maxLines = 3, shape = FieldShape, colors = ledgerFieldColors())
            }
        }
        Surface(color = MaterialTheme.colorScheme.background) {
            PrimaryAction(if (saving) "正在保存" else "保存定时账单", enabled = canSave,
                modifier = Modifier.fillMaxWidth().padding(horizontal = PagePadding, vertical = 10.dp), onClick = {
                    if (canSave) {
                        focus.clearFocus()
                        saving = true
                        val draft = ScheduledBill(bill?.id.orEmpty(), kindName, cents, selectedTag.id, accountId,
                            sceneId.takeIf { kind == Kind.EXPENSE }, note, periodName, startDay)
                        model.saveScheduledBill(draft, done = back, onFailure = { saving = false })
                    }
                })
        }
    }

    val field = fieldName?.let(BillField::valueOf)
    if (field != null) {
        val options = when (field) {
            BillField.TAG -> tags.map { it.id to it.name }
            BillField.SCENE -> listOf(null to "不关联") + data.scenes.map { it.id to it.name }
            BillField.ACCOUNT -> listOf(null to "不关联") + accounts.map { it.id to it.name }
        }
        val current = when (field) {
            BillField.TAG -> tagId
            BillField.SCENE -> sceneId
            BillField.ACCOUNT -> accountId
        }
        LedgerAlertDialog(onDismissRequest = { fieldName = null }, title = { Text(field.title) }, text = {
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(options) { (id, label) ->
                    SettingsRow(label, end = if (id == current) "已选" else null) {
                        when (field) {
                            BillField.TAG -> tagId = id
                            BillField.SCENE -> sceneId = id
                            BillField.ACCOUNT -> accountId = id
                        }
                        fieldName = null
                    }
                }
                if (field == BillField.TAG) item {
                    SettingsRow("新建标签", icon = Glyph.PLUS) {
                        fieldName = null
                        addTag = true
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { fieldName = null }) { Text("关闭") } })
    }
    if (addTag) {
        NewLabelDialog(model, "新建${kind.title}标签", "标签名称", kind.title, onDismiss = { addTag = false }) { label, style ->
            model.saveTag(kind, label, null, done = { tag ->
                tagId = tag.id
                addTag = false
            }, style = style)
        }
    }
    if (confirmDelete && bill != null) {
        ConfirmDialog("删除定时账单？", "删除后将停止自动记账，已生成的流水会保留。", "删除",
            onDismiss = { confirmDelete = false }) {
            confirmDelete = false
            model.deleteScheduledBill(bill.id, back)
        }
    }
}
