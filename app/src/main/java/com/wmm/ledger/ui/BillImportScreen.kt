package com.wmm.ledger.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wmm.ledger.BillBatch
import com.wmm.ledger.LedgerViewModel
import com.wmm.ledger.data.BILL_MIME_TYPES
import com.wmm.ledger.data.BillLabels
import com.wmm.ledger.data.BillRow
import com.wmm.ledger.data.Kind
import com.wmm.ledger.data.LedgerSnapshot
import com.wmm.ledger.data.MAX_NAME
import com.wmm.ledger.data.MAX_NOTE
import com.wmm.ledger.data.Provider
import com.wmm.ledger.data.currency
import com.wmm.ledger.data.parseCents
import com.wmm.ledger.data.validName

@Composable
internal fun BillImportScreen(data: LedgerSnapshot, model: LedgerViewModel, provider: Provider, hasKey: Boolean,
    back: () -> Unit, openModel: () -> Unit) {
    val state by model.billImport.collectAsStateWithLifecycle()
    var upload by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmSave by rememberSaveable { mutableStateOf(false) }
    val chooser = rememberLauncherForActivityResult(remember { OpenLocal() }) { uri -> upload = uri?.toString() }
    val selected = state.rows.filter { it.selected }
    val review = state.rows.isNotEmpty()
    val labels = remember(data.entries, data.tags, data.scenes) { BillLabels(data) }
    val colors = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().imePadding()) {
        PageHeader("AI 导入账单", back)
        when {
            state.scanning -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    CircularProgressIndicator()
                    Text("正在识别账单")
                }
            }
            review -> {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(PagePadding, 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Text("核对 AI 建议", style = MaterialTheme.typography.titleMedium)
                        Text("已选 ${selected.size} / ${state.rows.size} 笔", Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        if (state.skippedCurrency > 0) {
                            Text("已略过 ${state.skippedCurrency} 笔外币或币种不明的记录",
                                Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant)
                        }
                    }
                    items(state.rows, key = { it.id }) { row ->
                        BillReviewRow(row, archivedType = labels.isArchived(row.kind, row.type), enabled = !state.saving,
                            toggle = { model.changeBillRow(row.copy(selected = it)) },
                            edit = { editing = row.id })
                    }
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = PagePadding, vertical = 8.dp)) {
                    PrimaryAction(if (state.saving) "正在导入" else "导入 ${selected.size} 笔",
                        onClick = { confirmSave = true },
                        enabled = !state.saving && selected.isNotEmpty() &&
                            selected.all { it.isReady() && !labels.isArchived(it.kind, it.type) },
                        modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = model::discardBill, enabled = !state.saving,
                        modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("放弃预览") }
                }
            }
            else -> Box(Modifier.padding(horizontal = PagePadding, vertical = 10.dp)) {
                GroupCard {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconBadge(Glyph.UPLOAD, emphasis = true)
                        Text("选择账单截图或文件", style = MaterialTheme.typography.titleMedium)
                        Text("PNG、JPG、WebP、PDF、DOCX、CSV、TXT。PDF 每次最多 8 页。",
                            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        if (hasKey) {
                            PrimaryAction("选择账单", { chooser.launch(BILL_MIME_TYPES) })
                        } else {
                            PrimaryAction("设置模型", openModel)
                        }
                    }
                }
            }
        }
    }

    upload?.let { uri ->
        ConfirmDialog("发送所选账单？",
            "将所选文件的图像或文本，以及账本的场景和类型名称发送至 ${provider.title}，用于识别流水和推荐标签。",
            "发送并识别", { upload = null }) {
            upload = null
            model.scanBill(Uri.parse(uri), provider)
        }
    }
    state.rows.firstOrNull { it.id == editing }?.let { row ->
        BillRowDialog(row, labels, dismiss = { editing = null }) { edited ->
            model.reviewBillRow(edited)
            editing = null
        }
    }
    state.batch?.let { batch ->
        BillBatchDialog(batch, state.rows.filter { it.id in batch.ids }, model::resolveBillBatch)
    }
    if (confirmSave) {
        ConfirmDialog("导入 ${selected.size} 笔流水？",
            "所选流水将追加到账本，账户留空。",
            "确认导入", { confirmSave = false }) {
            confirmSave = false
            model.saveBill()
        }
    }
}

@Composable
private fun BillBatchDialog(batch: BillBatch, rows: List<BillRow>, resolve: (List<String>) -> Unit) {
    var chosen by rememberSaveable(batch.ids) { mutableStateOf(ArrayList(batch.ids)) }
    val colors = MaterialTheme.colorScheme
    LedgerAlertDialog(onDismissRequest = { resolve(emptyList()) }, title = { Text("同步类似流水？") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("将所选流水的分类改为「${billLabel(batch.type, batch.scene)}」。", style = MaterialTheme.typography.bodyMedium)
            LazyColumn(Modifier.heightIn(max = 260.dp)) {
                items(rows, key = BillRow::id) { row ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = row.id in chosen, onCheckedChange = { checked ->
                            chosen = ArrayList(if (checked) chosen + row.id else chosen - row.id)
                        })
                        Column(Modifier.weight(1f).padding(vertical = 5.dp)) {
                            Text(row.note.ifBlank { billLabel(row.type, row.scene).ifBlank { "待分类流水" } },
                                style = MaterialTheme.typography.titleSmall)
                            Text(listOf(row.date, parseCents(row.amount)?.let(::currency).orEmpty())
                                .filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }, confirmButton = {
        TextButton(enabled = chosen.isNotEmpty(), onClick = { resolve(chosen) }) { Text("同步 ${chosen.size} 笔") }
    }, dismissButton = { TextButton(onClick = { resolve(emptyList()) }) { Text("只修改这笔") } })
}

@Composable
private fun BillReviewRow(row: BillRow, archivedType: Boolean, enabled: Boolean, toggle: (Boolean) -> Unit, edit: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val status = when {
        row.alreadyImported -> "已导入"
        archivedType -> "类型已归档，需改选"
        !row.isReady() -> "待核对"
        row.duplicate -> "可能重复"
        row.habitSuggestion && !row.reviewed -> "按记账习惯建议"
        else -> null
    }
    GroupCard {
        Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = row.selected, onCheckedChange = toggle, enabled = enabled && !row.alreadyImported)
            Column(Modifier.weight(1f).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                val label = billLabel(row.type.ifBlank { "类型待填写" }, row.scene.takeIf { row.kind != Kind.INCOME }.orEmpty())
                Text(label, style = MaterialTheme.typography.titleSmall)
                val amount = parseCents(row.amount)?.let(::currency) ?: "金额待核对"
                val occurred = row.date
                Text(listOfNotNull(row.kind?.title, amount, occurred.ifBlank { "日期待核对" })
                    .joinToString(" · "), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                if (row.note.isNotBlank()) Text(row.note, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                if (status != null) Text(status, color = if (row.isReady() && !archivedType) colors.onSurfaceVariant else colors.error,
                    style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = edit, enabled = enabled && !row.alreadyImported) {
                LucideIcon(Glyph.EDIT, description = "编辑待导入流水", size = 18)
            }
        }
    }
}

@Composable
internal fun BillRowDialog(row: BillRow, labels: BillLabels, dismiss: () -> Unit, save: (BillRow) -> Unit) {
    var kindName by rememberSaveable { mutableStateOf(row.kind?.name) }
    var amount by rememberSaveable { mutableStateOf(row.amount) }
    var date by rememberSaveable { mutableStateOf(row.date) }
    // 时分不再开放编辑；有效来源时间仅用于内部排序，无效值在核对时降为日期精度。
    val time = row.time.takeIf { row.stamp(System.currentTimeMillis(), java.time.ZoneId.systemDefault()) != null }.orEmpty()
    var type by rememberSaveable { mutableStateOf(row.type) }
    var scene by rememberSaveable { mutableStateOf(row.scene) }
    var note by rememberSaveable { mutableStateOf(row.note) }
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    val kind = kindName?.let(Kind::valueOf)
    val updated = row.copy(kind = kind, amount = amount, date = date, time = time, type = type, scene = scene, note = note)
    val suggestion = billLabel(row.suggestedType, row.suggestedScene)
    LedgerAlertDialog(onDismissRequest = dismiss, title = { Text("编辑待导入流水") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (suggestion.isNotEmpty()) Text("${if (row.habitSuggestion) "习惯建议" else "AI 建议"} · $suggestion",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            LedgerSegments(Kind.entries, kind, { it?.title.orEmpty() }) { chosen ->
                if (chosen?.name != kindName) {
                    kindName = chosen?.name
                    type = ""
                    if (chosen != Kind.EXPENSE) scene = ""
                }
            }
            if (kind == Kind.EXPENSE) {
                SettingsRow("场景", end = scene.ifBlank { "无场景" }) { picker = "场景" }
                Hairline(14)
            }
            SettingsRow("类型", end = type.ifBlank { "待选择" }, enabled = kind != null) { picker = "类型" }
            if (labels.isArchived(kind, type)) Text("这个类型已归档，请改选", color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
            BillField("金额", amount, 18, KeyboardType.Decimal) { amount = it }
            BillField("日期 YYYY-MM-DD", date, 10) { date = it }
            BillField("备注", note, MAX_NOTE) { note = it }
        }
    }, confirmButton = {
        TextButton(onClick = { save(updated) }, enabled = updated.isReady() && !labels.isArchived(kind, type)) { Text("保存修改") }
    }, dismissButton = { TextButton(onClick = dismiss) { Text("取消") } })
    picker?.let { title ->
        val forScene = title == "场景"
        BillLabelPicker(title, if (forScene) labels.scenes else labels.types(kind),
            current = if (forScene) scene else type, optional = forScene,
            canCreate = { forScene || !labels.isArchived(kind, it) },
            dismiss = { picker = null }) { name ->
            if (forScene) scene = name else type = name
            picker = null
        }
    }
}

@Composable
private fun BillLabelPicker(title: String, names: List<String>, current: String, optional: Boolean,
    canCreate: (String) -> Boolean, dismiss: () -> Unit, choose: (String) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    val name = input.trim()
    val matches = remember(names, name) { names.filter { it.contains(name, ignoreCase = true) } }
    LedgerAlertDialog(onDismissRequest = dismiss, title = { Text("选择$title") }, text = {
        Column {
            BillField("搜索或输入新$title", input, MAX_NAME) { input = it }
            LazyColumn(Modifier.heightIn(max = 255.dp).padding(top = 8.dp)) {
                if (optional && name.isBlank()) item {
                    SettingsRow("无场景", end = if (current.isBlank()) "当前" else null) { choose("") }
                }
                items(matches, key = { it }) { option ->
                    SettingsRow(option, end = if (option == current) "当前" else null) { choose(option) }
                }
            }
        }
    }, confirmButton = {
        if (validName(name) && matches.none { it.equals(name, ignoreCase = true) }) {
            TextButton(onClick = { choose(name) }, enabled = canCreate(name)) { Text("使用新$title") }
        }
    }, dismissButton = { TextButton(onClick = dismiss) { Text("取消") } })
}

private fun billLabel(type: String, scene: String): String =
    listOf(scene, type).filter { it.isNotBlank() }.joinToString(" · ")

@Composable
private fun BillField(label: String, value: String, limit: Int, keyboard: KeyboardType = KeyboardType.Text,
    change: (String) -> Unit) {
    OutlinedTextField(value, { if (it.length <= limit) change(it) }, Modifier.fillMaxWidth(),
        label = { Text(label) }, singleLine = true, shape = FieldShape, colors = ledgerFieldColors(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard))
}
