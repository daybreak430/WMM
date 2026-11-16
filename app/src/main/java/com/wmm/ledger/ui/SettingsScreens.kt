package com.wmm.ledger.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wmm.ledger.Appearance
import com.wmm.ledger.BuildConfig
import com.wmm.ledger.LedgerViewModel
import com.wmm.ledger.data.Kind
import com.wmm.ledger.data.LedgerSnapshot
import com.wmm.ledger.data.Provider
import com.wmm.ledger.data.currency
import com.wmm.ledger.data.netAssetTotal
import com.wmm.ledger.data.orderedTags
import com.mohamedrejeb.compose.dnd.DragAndDropContainer
import com.mohamedrejeb.compose.dnd.scroll.dragAutoScroll
import kotlinx.coroutines.launch

private const val RELEASES_URL = "https://github.com/daybreak430/WMM/releases"

internal class OpenLocal : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
}

@Composable
internal fun SettingsScreen(data: LedgerSnapshot, model: LedgerViewModel, activeProvider: Provider, hasKey: Boolean,
    fileBusy: Boolean, appearance: Appearance, back: () -> Unit, assets: () -> Unit, tags: () -> Unit, openModel: () -> Unit,
    importBill: () -> Unit) {
    var editNickname by rememberSaveable { mutableStateOf(false) }
    var avatarOptions by rememberSaveable { mutableStateOf(false) }
    var exportWarning by rememberSaveable { mutableStateOf(false) }
    val habitLocation by model.habitLocation.collectAsStateWithLifecycle()
    val netAssetAmount = remember(data) { currency(data.netAssetTotal()) }
    var explainLocation by rememberSaveable { mutableStateOf(false) }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) model.setHabitLocation(true)
    }
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
            item("assets") {
                GroupCard {
                    SettingsRow("资产", description = "净资产总额", icon = Glyph.WALLET,
                        endContent = { AmountText(netAssetAmount) }, onClick = assets)
                }
            }
            item("labels") {
                GroupCard { SettingsRow("标签管理", icon = Glyph.TAG, onClick = tags) }
            }
            item("preferences") {
                GroupCard {
                    SettingsRow("AI 模型", icon = Glyph.SPARK, end = if (hasKey) activeProvider.title else "未配置", onClick = openModel)
                    Hairline(14)
                    SettingsRow("AI 导入账单", icon = Glyph.UPLOAD, onClick = importBill)
                    Hairline(14)
                    SettingsRow("按位置推荐分类", icon = Glyph.MAP_PIN, end = if (habitLocation) "开启" else "关闭") {
                        if (habitLocation) model.setHabitLocation(false) else explainLocation = true
                    }
                }
            }
            item("appearance") {
                GroupCard {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("外观", style = MaterialTheme.typography.titleMedium)
                        LedgerSegments(Appearance.entries, appearance, { it.title }, onSelect = model::selectAppearance)
                    }
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
            item("about-title") { SectionTitle("关于") }
            item("about") { AboutSettings() }
        }
    }

    if (editNickname) {
        NameDialog("编辑昵称", "昵称", data.profile.nickname, allowBlank = true, onDismiss = { editNickname = false }) {
            model.saveNickname(it) { editNickname = false }
        }
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
    if (explainLocation) {
        ConfirmDialog("按位置推荐分类？",
            "记账页面可使用大致位置学习常去区域。区域标记仅存此设备，关闭后会清除。",
            "允许大致位置", { explainLocation = false }) {
            explainLocation = false
            locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
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
private fun AboutSettings() {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    GroupCard {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("当前版本", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Hairline(14)
        SettingsRow("GitHub Releases", icon = Glyph.BOX) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL)))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, "此设备无法打开发布页面。", Toast.LENGTH_SHORT).show()
            }
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
    var showStyles by rememberSaveable { mutableStateOf(false) }
    if (showStyles) {
        LabelStylesScreen(data, model) { showStyles = false }
        return
    }
    var kindName by rememberSaveable { mutableStateOf(Kind.EXPENSE.name) }
    val kind = Kind.valueOf(kindName)
    val tags = remember(data.tags, data.entries, kind) { data.orderedTags(kind) }
    val archived = remember(data.tags, kind) { data.tags.filter { it.kind == kindName && it.archived } }
    val tagsById = remember(tags) { tags.associateBy { it.id } }
    var edit by rememberSaveable { mutableStateOf<String?>(null) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        PageHeader("标签管理", back) {
            TextButton(onClick = { showStyles = true }) { Text("配图标") }
            IconButton(onClick = { edit = "new" }) { LucideIcon(Glyph.PLUS, description = "添加标签") }
        }
        key(kind) {
            val dragState = rememberReorderDragState()
            val order = rememberItemOrder(tags.map { it.id })
            val listState = rememberLazyListState()
            DragAndDropContainer(dragState, modifier = Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().dragAutoScroll(dragState, listState),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = PagePadding, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    item {
                        LedgerSegments(Kind.entries, kind, { it.title }) { kindName = it.name }
                    }
                    if (tags.isEmpty() && archived.isEmpty()) {
                        item { GroupCard { EmptyPanel("还没有${kind.title}标签", glyph = Glyph.TAG) } }
                    }
                    items(order.ids, key = { it }) { id ->
                        ReorderableLedgerItem(
                            id, order, tagsById.keys, dragState,
                            save = { updated, failed -> model.reorderTags(kind, updated, failed) },
                            modifier = Modifier.fillMaxWidth().animateItem(),
                        ) {
                            GroupCard { SettingsRow(tagsById.getValue(id).name, icon = Glyph.TAG) { open = id } }
                        }
                    }
                    items(archived, key = { it.id }) { tag ->
                        GroupCard { SettingsRow(tag.name, icon = Glyph.TAG, end = "已归档") { open = tag.id } }
                    }
                }
            }
        }
    }
    val viewing = tagsById[open] ?: archived.firstOrNull { it.id == open }
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
    val editing = tagsById[edit] ?: archived.firstOrNull { it.id == edit }
    if (edit == "new" || editing != null) {
        NameDialog(if (edit == "new") "新建${kind.title}标签" else "标签改名", "标签名称", editing?.name.orEmpty(),
            onDismiss = { edit = null }) { name ->
            model.saveTag(kind, name, editing?.id) { edit = null }
        }
    }
}
