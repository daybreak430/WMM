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
import com.wmm.ledger.LedgerViewModel
import com.wmm.ledger.data.BILL_MIME_TYPES
import com.wmm.ledger.data.BillRow
import com.wmm.ledger.data.Kind
import com.wmm.ledger.data.MAX_NAME
import com.wmm.ledger.data.MAX_NOTE
import com.wmm.ledger.data.Provider
import com.wmm.ledger.data.currency
import com.wmm.ledger.data.parseCents

@Composable
internal fun BillImportScreen(model: LedgerViewModel, provider: Provider, hasKey: Boolean,
    back: () -> Unit, openModel: () -> Unit) {
    val state by model.billImport.collectAsStateWithLifecycle()
    var upload by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmSave by rememberSaveable { mutableStateOf(false) }
    val chooser = rememberLauncherForActivityResult(remember { OpenLocal() }) { uri -> upload = uri?.toString() }
    val selected = state.rows.filter { it.selected }
    val review = state.rows.isNotEmpty()
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
                        Text("核对流水", style = MaterialTheme.typography.titleMedium)
                        Text("已选 ${selected.size} / ${state.rows.size} 笔", Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        if (state.skippedCurrency > 0) {
                            Text("已略过 ${state.skippedCurrency} 笔外币或币种不明的记录",
                                Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant)
                        }
                    }
                    items(state.rows, key = { it.id }) { row ->
                        BillReviewRow(row, enabled = !state.saving,
                            toggle = { model.changeBillRow(row.copy(selected = it)) },
                            edit = { editing = row.id })
                    }
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = PagePadding, vertical = 8.dp)) {
                    PrimaryAction(if (state.saving) "正在导入" else "导入 ${selected.size} 笔",
                        onClick = { confirmSave = true }, enabled = !state.saving && selected.isNotEmpty() && selected.all { it.isReady() },
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
            "将所选截图、PDF 页面图像或文件文本发送至 ${provider.title} 识别。核对后可导入本机账本。",
            "发送并识别", { upload = null }) {
            upload = null
            model.scanBill(Uri.parse(uri), provider)
        }
    }
    state.rows.firstOrNull { it.id == editing }?.let { row ->
        BillRowDialog(row, dismiss = { editing = null }) { edited ->
            model.changeBillRow(edited)
            editing = null
        }
    }
    if (confirmSave) {
        ConfirmDialog("导入 ${selected.size} 笔流水？",
            "所选流水将追加到账本，账户留空。缺少时间的流水按当天 00:00 记账。",
            "确认导入", { confirmSave = false }) {
            confirmSave = false
            model.saveBill()
        }
    }
}

@Composable
private fun BillReviewRow(row: BillRow, enabled: Boolean, toggle: (Boolean) -> Unit, edit: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val status = when {
        row.alreadyImported -> "已导入"
        !row.isReady() -> "待核对"
        row.duplicate -> "可能重复"
        else -> null
    }
    GroupCard {
        Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = row.selected, onCheckedChange = toggle, enabled = enabled && !row.alreadyImported)
            Column(Modifier.weight(1f).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                val label = listOfNotNull(row.scene.takeIf { it.isNotBlank() && row.kind == Kind.EXPENSE },
                    row.type.takeIf { it.isNotBlank() }).joinToString(" · ")
                Text(label.ifBlank { "类型待填写" }, style = MaterialTheme.typography.titleSmall)
                val amount = parseCents(row.amount)?.let(::currency) ?: "金额待核对"
                val occurred = listOf(row.date, row.time).filter { it.isNotBlank() }.joinToString(" ")
                Text(listOfNotNull(row.kind?.title, amount, occurred.ifBlank { "日期待核对" })
                    .joinToString(" · "), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                if (row.note.isNotBlank()) Text(row.note, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                if (status != null) Text(status, color = if (row.isReady()) colors.onSurfaceVariant else colors.error,
                    style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = edit, enabled = enabled && !row.alreadyImported) {
                LucideIcon(Glyph.EDIT, description = "编辑待导入流水", size = 18)
            }
        }
    }
}

@Composable
private fun BillRowDialog(row: BillRow, dismiss: () -> Unit, save: (BillRow) -> Unit) {
    var kindName by rememberSaveable { mutableStateOf(row.kind?.name) }
    var amount by rememberSaveable { mutableStateOf(row.amount) }
    var date by rememberSaveable { mutableStateOf(row.date) }
    var time by rememberSaveable { mutableStateOf(row.time) }
    var type by rememberSaveable { mutableStateOf(row.type) }
    var scene by rememberSaveable { mutableStateOf(row.scene) }
    var note by rememberSaveable { mutableStateOf(row.note) }
    val kind = kindName?.let(Kind::valueOf)
    val updated = row.copy(kind = kind, amount = amount, date = date, time = time, type = type, scene = scene, note = note)
    LedgerAlertDialog(onDismissRequest = dismiss, title = { Text("核对流水") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LedgerSegments(Kind.entries, kind, { it?.title.orEmpty() }) { kindName = it?.name }
            BillField("金额", amount, 18, KeyboardType.Decimal) { amount = it }
            BillField("日期 YYYY-MM-DD", date, 10) { date = it }
            BillField("时间 HH:mm（选填）", time, 8) { time = it }
            BillField("类型", type, MAX_NAME) { type = it }
            if (kind == Kind.EXPENSE) BillField("场景（选填）", scene, MAX_NAME) { scene = it }
            BillField("备注", note, MAX_NOTE) { note = it }
        }
    }, confirmButton = {
        TextButton(onClick = { save(updated) }, enabled = updated.isReady()) { Text("保存修改") }
    }, dismissButton = { TextButton(onClick = dismiss) { Text("取消") } })
}

@Composable
private fun BillField(label: String, value: String, limit: Int, keyboard: KeyboardType = KeyboardType.Text,
    change: (String) -> Unit) {
    OutlinedTextField(value, { if (it.length <= limit) change(it) }, Modifier.fillMaxWidth(),
        label = { Text(label) }, singleLine = true, shape = FieldShape, colors = ledgerFieldColors(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard))
}
