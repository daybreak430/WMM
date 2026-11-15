package com.wmm.ledger.ui

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.SolidColor
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
        PageHeader(if (initial == null) "记一笔" else "编辑流水", back, centered = true) {
            if (initial != null) IconButton(onClick = { deleteDialog = true }) {
                LucideIcon(Glyph.DELETE, MaterialTheme.colorScheme.error, "删除流水")
            }
        }
        val colors = MaterialTheme.colorScheme
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
            .padding(horizontal = PagePadding, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                val amountColor = if (amountError) colors.error else colors.onSurface
                Text("金额", color = if (amountError) colors.error else colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge)
                val inputStyle = HeroNumber.copy(fontSize = if (amountText.length > 11) 32.sp else 44.sp)
                val amountWidth = rememberTextMeasurer().measure(
                    AnnotatedString(amountText.ifEmpty { "0.00" }), inputStyle, maxLines = 1,
                ).size.width
                val preferredWidth = with(LocalDensity.current) { amountWidth.toDp() } + 6.dp
                BasicTextField(
                    value = amountText,
                    onValueChange = { if (it.length <= 17) amountText = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).focusRequester(amountFocus)
                        .testTag("entry-amount").semantics { contentDescription = "金额" },
                    textStyle = inputStyle.copy(color = amountColor),
                    singleLine = true,
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    decorationBox = { inner ->
                        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            val textWidth = preferredWidth.coerceAtMost((maxWidth - 45.dp).coerceAtLeast(36.dp))
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("¥", color = amountColor, style = MaterialTheme.typography.headlineLarge)
                                Box(Modifier.width(textWidth), contentAlignment = Alignment.CenterStart) {
                                    if (amountText.isEmpty()) Text("0.00", color = colors.outline, style = inputStyle)
                                    inner()
                                }
                            }
                        }
                    },
                )
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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    visibleTags.forEach { tag ->
                        FilterChip(
                            selected = tagId == tag.id,
                            onClick = {
                                tagId = tag.id
                                focus.clearFocus()
                                keyboard?.hide()
                            },
                            shape = FieldShape,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = colors.surface,
                                selectedContainerColor = colors.primaryContainer,
                                selectedLabelColor = colors.onPrimaryContainer,
                            ),
                            label = { Text(tag.name) },
                        )
                    }
                    AssistChip(
                        onClick = {
                            focus.clearFocus()
                            keyboard?.hide()
                            tagDialog = true
                        },
                        shape = FieldShape,
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = colors.surface, labelColor = colors.primary,
                        ),
                        label = { Text("新建标签") },
                        leadingIcon = { LucideIcon(Glyph.PLUS, colors.primary, size = 16) },
                    )
                }
            }
            GroupCard {
                SettingsRow("账户", icon = Glyph.WALLET, end = account?.name ?: "不关联") {
                    focus.clearFocus()
                    accountDialog = true
                }
                Hairline(14)
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                    TextButton(onClick = { focus.clearFocus(); chooseDate() }, modifier = Modifier.weight(1.5f)) {
                        LucideIcon(Glyph.CALENDAR, colors.primary, size = 17)
                        Text(" ${day.year}/${day.monthValue}/${day.dayOfMonth}", maxLines = 1)
                    }
                    TextButton(onClick = { focus.clearFocus(); chooseTime() }, modifier = Modifier.weight(1f)) {
                        Text(time.format(TimeLabel))
                    }
                }
            }
            OutlinedTextField(note, { if (it.length <= MAX_NOTE) note = it }, Modifier.fillMaxWidth(),
                label = { Text("备注（选填）") }, maxLines = 3, shape = FieldShape, colors = ledgerFieldColors())
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = PagePadding, vertical = 10.dp),
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
            modifier = Modifier.fillMaxWidth(), shape = FieldShape, colors = ledgerFieldColors(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done))
    }, confirmButton = { TextButton(onClick = { onSave(name) }, enabled = validName(name.trim()) || allowBlank && name.isBlank()) {
        Text("保存")
    } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
