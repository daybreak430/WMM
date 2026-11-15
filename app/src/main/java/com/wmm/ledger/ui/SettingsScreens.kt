package com.wmm.ledger.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.wmm.ledger.Appearance
import com.wmm.ledger.LedgerViewModel
import com.wmm.ledger.data.Kind
import com.wmm.ledger.data.LedgerSnapshot
import com.wmm.ledger.data.Provider
import kotlinx.coroutines.launch

private class OpenLocal : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
}

@Composable
internal fun SettingsScreen(data: LedgerSnapshot, model: LedgerViewModel, activeProvider: Provider, hasKey: Boolean,
    fileBusy: Boolean, appearance: Appearance, back: () -> Unit, tags: () -> Unit, openModel: () -> Unit) {
    var editNickname by rememberSaveable { mutableStateOf(false) }
    var avatarOptions by rememberSaveable { mutableStateOf(false) }
    var appearanceOptions by rememberSaveable { mutableStateOf(false) }
    var exportWarning by rememberSaveable { mutableStateOf(false) }
    val avatarChooser = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) model.saveAvatar(uri)
    }
    fun choosePhoto() = avatarChooser.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    fun openAvatar() {
        if (data.profile.avatar == null) choosePhoto() else avatarOptions = true
    }
    val backupChooser = rememberLauncherForActivityResult(remember { OpenLocal() }) { uri ->
        if (uri != null) model.readBackup(uri)
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader("个人设置", back)
        LazyColumn(contentPadding = PaddingValues(horizontal = PagePadding, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item("profile") {
                GroupCard {
                    Row(Modifier.fillMaxWidth().padding(15.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(data.profile, ::openAvatar, size = 55, description = "头像")
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(data.profile.nickname.ifBlank { "未设置昵称" }, style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = ::openAvatar, contentPadding = PaddingValues(horizontal = 0.dp)) {
                                Text(if (data.profile.avatar == null) "上传头像" else "更换头像")
                            }
                        }
                        IconButton(onClick = { editNickname = true }) { LucideIcon(Glyph.EDIT, description = "编辑昵称") }
                    }
                }
            }
            item("appearance") {
                GroupCard { SettingsRow("外观", end = appearance.title) { appearanceOptions = true } }
            }
            item("preferences") {
                GroupCard {
                    SettingsRow("标签管理", icon = Glyph.TAG, onClick = tags)
                    Hairline(14)
                    SettingsRow("AI 模型", icon = Glyph.SPARK, end = if (hasKey) activeProvider.title else "未配置", onClick = openModel)
                }
            }
            item("backup-title") {
                SectionTitle("备份") { if (fileBusy) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp) }
            }
            item("backup") {
                GroupCard {
                    SettingsRow("导出备份", icon = Glyph.DOWNLOAD, enabled = !fileBusy) { exportWarning = true }
                    Hairline(14)
                    SettingsRow("从备份恢复", enabled = !fileBusy, icon = Glyph.UPLOAD) {
                        backupChooser.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
                    }
                }
            }
        }
    }

    if (editNickname) {
        NameDialog("编辑昵称", "昵称", data.profile.nickname, allowBlank = true, onDismiss = { editNickname = false }) {
            model.saveNickname(it) { editNickname = false }
        }
    }
    if (appearanceOptions) {
        LedgerAlertDialog(onDismissRequest = { appearanceOptions = false }, title = { Text("外观") }, text = {
            Column {
                Appearance.entries.forEachIndexed { index, option ->
                    if (index > 0) Hairline()
                    SettingsRow(option.title, end = if (option == appearance) "当前" else null) {
                        model.selectAppearance(option)
                        appearanceOptions = false
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { appearanceOptions = false }) { Text("取消") } })
    }
    if (avatarOptions) {
        LedgerAlertDialog(onDismissRequest = { avatarOptions = false }, title = { Text("头像") }, text = {
            Column {
                SettingsRow("选择照片") { avatarOptions = false; choosePhoto() }
                if (data.profile.avatar != null) {
                    Hairline()
                    SettingsRow("移除头像") { avatarOptions = false; model.saveAvatar(null) }
                }
            }
        }, confirmButton = { TextButton(onClick = { avatarOptions = false }) { Text("关闭") } })
    }
    if (exportWarning) {
        ConfirmDialog("导出普通备份？", "备份文件未加密，包含账本、资产、个人资料和报告。请在分享面板中选择可信的接收方。",
            "打开分享面板", { exportWarning = false }) {
            exportWarning = false
            model.exportForSharing()
        }
    }
}

@Composable
fun ModelSettings(model: LedgerViewModel, provider: Provider, providersWithKeys: Set<Provider>, back: () -> Unit) {
    val configured = provider in providersWithKeys
    var editKey by rememberSaveable { mutableStateOf(false) }
    var removeKey by rememberSaveable { mutableStateOf(false) }
    var testing by remember { mutableStateOf<Provider?>(null) }
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        PageHeader("AI 模型", back)
        LazyColumn(contentPadding = PaddingValues(horizontal = PagePadding, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item("provider") {
                GroupCard {
                    Provider.entries.forEachIndexed { index, option ->
                        if (index > 0) Hairline(14)
                        SettingsRow(option.title, description = option.model, icon = Glyph.SPARK,
                            end = if (provider == option) "当前" else null, enabled = testing == null) {
                            model.selectProvider(option)
                            result = null
                        }
                    }
                }
            }
            item("setup") {
                GroupCard {
                    SettingsRow("API Key", description = if (configured) "已保存到此设备" else "未设置",
                        enabled = testing == null) { editKey = true }
                    Hairline(14)
                    SettingsRow(if (testing != null) "正在测试连接" else "测试连接", enabled = configured && testing == null) {
                        testing = provider
                        result = null
                        scope.launch {
                            try { result = model.testModel(provider) } finally { testing = null }
                        }
                    }
                    if (configured) {
                        Hairline(14)
                        SettingsRow("移除 API Key", enabled = testing == null) { removeKey = true }
                    }
                }
            }
            if (result != null) item("result") {
                Text(result.orEmpty(), Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.bodyMedium,
                    color = if (result == "连接成功") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }
        }
    }
    if (editKey) {
        // Key 只保留在当前对话框内存中，不放进 rememberSaveable 或系统恢复状态。
        var key by remember { mutableStateOf("") }
        var saving by remember { mutableStateOf(false) }
        LedgerAlertDialog(onDismissRequest = { editKey = false }, title = { Text("${provider.title} API Key") }, text = {
            OutlinedTextField(key, { if (it.length <= 512) key = it }, Modifier.fillMaxWidth(),
                label = { Text("API Key") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                shape = FieldShape, colors = ledgerFieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        }, confirmButton = {
            TextButton(onClick = {
                saving = true
                model.saveKey(provider, key) { editKey = false; result = null }.invokeOnCompletion { saving = false }
            }, enabled = key.isNotBlank() && !saving) { Text("保存") }
        }, dismissButton = { TextButton(onClick = { editKey = false }) { Text("取消") } })
    }
    if (removeKey) {
        ConfirmDialog("移除 API Key？", "此设备将无法通过 ${provider.title} 生成报告，已保存的报告会保留。",
            "移除", { removeKey = false }) {
            removeKey = false
            result = null
            model.removeKey(provider)
        }
    }
}

@Composable
fun TagsScreen(data: LedgerSnapshot, model: LedgerViewModel, back: () -> Unit) {
    var kindName by rememberSaveable { mutableStateOf(Kind.EXPENSE.name) }
    val kind = Kind.valueOf(kindName)
    val tags = data.tags.filter { it.kind == kindName }
    var edit by rememberSaveable { mutableStateOf<String?>(null) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        PageHeader("标签管理", back) {
            IconButton(onClick = { edit = "new" }) { LucideIcon(Glyph.PLUS, description = "添加标签") }
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = PagePadding, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)) {
            item("tag-kinds") {
                LedgerSegments(Kind.entries, kind, { it.title }) { kindName = it.name }
            }
            if (tags.isEmpty()) item("empty") { GroupCard { EmptyPanel("还没有${kind.title}标签", glyph = Glyph.TAG) } }
            items(tags.sortedBy { it.archived }, key = { it.id }) { tag ->
                GroupCard { SettingsRow(tag.name, icon = Glyph.TAG, end = if (tag.archived) "已归档" else null) { open = tag.id } }
            }
        }
    }
    val viewing = tags.firstOrNull { it.id == open }
    if (viewing != null) {
        LedgerAlertDialog(onDismissRequest = { open = null }, title = { Text(viewing.name) }, text = {
            Column {
                SettingsRow("改名", icon = Glyph.EDIT) { open = null; edit = viewing.id }
                Hairline()
                SettingsRow(if (viewing.archived) "恢复标签" else "归档标签", icon = Glyph.BOX) {
                    open = null
                    model.archiveTag(viewing.id, !viewing.archived)
                }
            }
        }, confirmButton = { TextButton(onClick = { open = null }) { Text("关闭") } })
    }
    val editing = tags.firstOrNull { it.id == edit }
    if (edit == "new" || editing != null) {
        NameDialog(if (edit == "new") "新建${kind.title}标签" else "标签改名", "标签名称", editing?.name.orEmpty(),
            onDismiss = { edit = null }) { name ->
            model.saveTag(kind, name, editing?.id) { edit = null }
        }
    }
}
