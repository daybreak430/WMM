package com.wmm.ledger.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import java.time.LocalTime
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
    var noteEditor by rememberSaveable { mutableStateOf(false) }
    var dateEditor by rememberSaveable { mutableStateOf(false) }
    var deleteDialog by rememberSaveable { mutableStateOf(false) }
    var showTags by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val tags = remember(data, kind, initial?.tagId) { data.frequentTags(kind, initial?.tagId) }
    val account = data.accounts.firstOrNull { it.id == accountId }
    val currentTag = tags.firstOrNull { it.id == tagId }
    val visibleTags = if (showTags) tags else (tags.take(8) + listOfNotNull(currentTag)).distinctBy { it.id }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val pageInset = LedgerPageInset()
    val colors = MaterialTheme.colorScheme
    fun dismissKeyboard() {
        focus.clearFocus()
        keyboard?.hide()
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        PageHeader(if (initial == null) "记一笔" else "编辑流水", back, centered = true) {
            if (initial != null) IconButton(onClick = { deleteDialog = true }) {
                LucideIcon(Glyph.DELETE, MaterialTheme.colorScheme.error, "删除流水")
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
            .padding(horizontal = pageInset, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LedgerSegments(Kind.entries, kind, { it.title }) { option ->
                if (option != kind) {
                    kindName = option.name
                    tagId = null
                    showTags = false
                }
            }
            Column(Modifier.fillMaxWidth().padding(top = 7.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val amountError = amountText.isNotBlank() && cents == null
                Text("金额", color = if (amountError) colors.error else colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge)
                EntryAmount(amountText, amountError, autoFocus = initial == null) { amountText = it }
                Hairline()
                if (amountError) Text("请输入有效金额，最多两位小数。", color = colors.error,
                    style = MaterialTheme.typography.bodySmall)
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                SectionTitle("标签") {
                    if (tags.size > 8) TextButton(onClick = { showTags = !showTags }) { Text(if (showTags) "收起" else "全部") }
                    else if (tagId == null) Text("必选", color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    visibleTags.forEach { tag ->
                        EntryTagChip(tag.name, selected = tagId == tag.id) {
                            tagId = tag.id
                            dismissKeyboard()
                        }
                    }
                    EntryTagChip("新建标签") {
                        dismissKeyboard()
                        tagDialog = true
                    }
                }
            }
            GroupCard {
                EntryDetailRow("账户", account?.name ?: "不关联", Glyph.WALLET) {
                    dismissKeyboard()
                    accountDialog = true
                }
                Hairline(14)
                EntryDetailRow("日期时间", "${day.year}/${day.monthValue}/${day.dayOfMonth} ${time.format(TimeLabel)}", Glyph.CALENDAR) {
                    dismissKeyboard()
                    dateEditor = true
                }
                Hairline(14)
                EntryDetailRow("备注", note.ifBlank { "选填" }, Glyph.EDIT) {
                    dismissKeyboard()
                    noteEditor = true
                }
            }
        }
        Surface(color = colors.background) {
            PrimaryAction(
                text = if (saving) "正在保存" else "保存流水",
                onClick = save@{
                    val saveCents = cents ?: return@save
                    val selected = tagId ?: return@save
                    saving = true
                    focus.clearFocus()
                    keyboard?.hide()
                    model.saveEntry(initial?.id, kind, saveCents, selected, accountId, note, occurredAt, day, back)
                        .invokeOnCompletion { saving = false }
                },
                enabled = cents != null && tagId != null && !saving,
                modifier = Modifier.fillMaxWidth().padding(horizontal = pageInset, vertical = 10.dp),
            )
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
    if (dateEditor) {
        EntryDateDialog(day, time, onDismiss = { dateEditor = false }) { changedDay, changedTime ->
            if (changedDay != day || changedTime != time) {
                bookedDay = changedDay.toString()
                occurredAt = changedDay.atTime(changedTime).atZone(zone).toInstant().toEpochMilli()
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
        ConfirmDialog("删除这笔流水？", "删除后会更新收支统计和关联账户余额。", "删除", { deleteDialog = false }) {
            deleteDialog = false
            model.deleteEntry(initial.id, back)
        }
    }
}

@Composable
private fun EntryAmount(amount: String, error: Boolean, autoFocus: Boolean, onChange: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val amountColor = if (error) colors.error else colors.onSurface
    val currencyStyle = LedgerBalanceNumber.copy(
        fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp,
    )
    val baseStyle = LedgerBalanceNumber.copy(fontSize = 46.sp, lineHeight = 54.sp)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var requested by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val shown = AnnotatedString(amount.ifEmpty { "0.00" })
        val symbolWidth = measurer.measure(AnnotatedString("¥"), currencyStyle).size.width
        val available = with(density) { (maxWidth - 12.dp - symbolWidth.toDp()).coerceAtLeast(1.dp).toPx() }
        // 根据实际字宽适配窄屏和字号缩放，金额过长时保留输入框的横向滚动。
        val fullWidth = measurer.measure(shown, baseStyle, maxLines = 1).size.width.coerceAtLeast(1)
        val inputStyle = baseStyle.copy(fontSize = (46 * (available / fullWidth)).coerceIn(30f, 46f).sp)
        val measured = measurer.measure(shown, inputStyle, maxLines = 1).size.width
        val textWidth = with(density) { (minOf(measured.toFloat(), available).toDp() + 6.dp) }
        LaunchedEffect(Unit) {
            if (autoFocus && !requested) {
                requested = true
                focusRequester.requestFocus()
                delay(150)
                keyboard?.show()
            }
        }
        BasicTextField(
            value = amount,
            onValueChange = { if (it.length <= 17) onChange(it) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).focusRequester(focusRequester)
                .testTag("entry-amount").semantics { contentDescription = "金额" },
            textStyle = inputStyle.copy(color = amountColor),
            singleLine = true,
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            decorationBox = { inner ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                    Text("¥", color = if (amount.isEmpty()) colors.outline else amountColor, style = currencyStyle)
                    Box(Modifier.width(textWidth), contentAlignment = Alignment.CenterStart) {
                        if (amount.isEmpty()) Text("0.00", color = colors.outline, style = inputStyle)
                        inner()
                    }
                }
            },
        )
    }
}

@Composable
private fun EntryTagChip(label: String, selected: Boolean? = null, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    val interaction = if (selected != null) Modifier.selectable(selected, role = Role.RadioButton, onClick = onClick)
        else Modifier.clickable(role = Role.Button, onClick = onClick)
    val tint = if (selected != false) colors.primary else colors.onSurface
    Box(Modifier.heightIn(min = 44.dp).clip(shape).then(interaction), contentAlignment = Alignment.Center) {
        Surface(shape = shape, color = if (selected == true) colors.primaryContainer else colors.surface,
            border = BorderStroke(1.dp, colors.outlineVariant)) {
            Row(Modifier.heightIn(min = 34.dp).padding(horizontal = 11.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (selected == null) LucideIcon(Glyph.PLUS, tint, size = 16)
                Text(label, style = MaterialTheme.typography.labelLarge, color = tint)
            }
        }
    }
}

@Composable
private fun EntryDetailRow(label: String, value: String, icon: Glyph, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LucideIcon(icon, colors.onSurfaceVariant, size = 17)
        Text(label, style = MaterialTheme.typography.titleMedium)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            Text(value, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        LucideIcon(Glyph.RIGHT, colors.onSurfaceVariant, size = 16)
    }
}

@Composable
private fun EntryDateDialog(day: LocalDate, time: LocalTime, onDismiss: () -> Unit,
    onSave: (LocalDate, LocalTime) -> Unit) {
    var dateText by rememberSaveable { mutableStateOf(day.toString()) }
    var timeText by rememberSaveable { mutableStateOf(time.toString()) }
    val pendingDay = LocalDate.parse(dateText)
    val pendingTime = LocalTime.parse(timeText)
    val chooseDate = CalendarAction(pendingDay) { dateText = it.toString() }
    val chooseTime = TimeAction(pendingTime) { timeText = it.toString() }
    AlertDialog(onDismissRequest = onDismiss, shape = CardShape, title = { Text("日期时间") },
        text = {
            GroupCard {
                EntryDetailRow("日期", pendingDay.toString(), Glyph.CALENDAR, chooseDate)
                Hairline(14)
                EntryDetailRow("时间", pendingTime.format(TimeLabel), Glyph.EDIT, chooseTime)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(pendingDay, pendingTime) }) { Text("确认") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun EntryNoteDialog(note: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var draft by rememberSaveable { mutableStateOf(note) }
    AlertDialog(onDismissRequest = onDismiss, shape = CardShape, title = { Text("备注") },
        text = {
            OutlinedTextField(draft, { if (it.length <= MAX_NOTE) draft = it }, Modifier.fillMaxWidth(),
                label = { Text("备注内容") }, minLines = 2, maxLines = 4, shape = FieldShape, colors = ledgerFieldColors())
        },
        confirmButton = { TextButton(onClick = { onSave(draft) }) { Text("确认") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
fun NameDialog(title: String, label: String, original: String, allowBlank: Boolean = false,
    onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(original) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        OutlinedTextField(name, { if (it.length <= MAX_NAME) name = it }, label = { Text(label) }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = FieldShape, colors = ledgerFieldColors(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done))
    }, confirmButton = { TextButton(onClick = { onSave(name) }, enabled = validName(name.trim()) || allowBlank && name.isBlank()) {
        Text("保存")
    } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
