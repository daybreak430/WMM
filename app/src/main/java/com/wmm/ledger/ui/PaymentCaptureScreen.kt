package com.wmm.ledger.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wmm.ledger.PaymentCaptureViewModel
import com.wmm.ledger.BuildConfig
import com.wmm.ledger.capture.PaymentCaptureDiagnostics
import com.wmm.ledger.capture.PaymentDiagnosticResult
import com.wmm.ledger.capture.PaymentCaptureService
import com.wmm.ledger.data.LedgerSnapshot
import com.wmm.ledger.data.PaymentSource
import com.wmm.ledger.data.currency
import java.time.Instant
import java.time.ZoneId

internal fun paymentServiceEnabled(context: Context): Boolean = context.getSystemService(AccessibilityManager::class.java)
    .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
        it.resolveInfo.serviceInfo.packageName == context.packageName &&
            it.resolveInfo.serviceInfo.name == PaymentCaptureService::class.java.name
    }

internal fun openPaymentPermission(context: Context) {
    try { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    catch (_: Exception) { Toast.makeText(context, "请在系统设置中开启记个账付款识别。", Toast.LENGTH_LONG).show() }
}

@Composable
internal fun PaymentCaptureScreen(data: LedgerSnapshot, model: PaymentCaptureViewModel, back: () -> Unit,
    setCaptureEnabled: (Boolean) -> Unit, review: (String) -> Unit) {
    val context = LocalContext.current
    val options by model.options.collectAsStateWithLifecycle()
    val captures by model.captures.collectAsStateWithLifecycle()
    val bindings by model.accounts.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    var connected by remember { mutableStateOf(paymentServiceEnabled(context)) }
    var guide by rememberSaveable { mutableStateOf(false) }
    var diagnostics by rememberSaveable { mutableStateOf(false) }
    var detailsConsent by rememberSaveable { mutableStateOf(false) }
    var clear by rememberSaveable { mutableStateOf(false) }
    var bindingSource by rememberSaveable { mutableStateOf<String?>(null) }
    var ignoreId by rememberSaveable { mutableStateOf<String?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) connected = paymentServiceEnabled(context) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader("自动记账", back) {
            IconButton(onClick = { clear = true }, enabled = captures.count > 0) { LucideIcon(Glyph.DELETE, description = "清除识别记录") }
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = PagePadding, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item("permission") {
                GroupCard { Column {
                    CaptureSwitch("自动识别付款", options.enabled, setCaptureEnabled)
                    Hairline(14)
                    SettingsRow("系统无障碍服务", end = if (connected) "已开启" else "去开启", icon = Glyph.EYE) {
                        if (options.enabled) openPaymentPermission(context) else setCaptureEnabled(true)
                    }
                    Hairline(14)
                    CaptureSwitch("识别账单详情", options.includeDetails) {
                        if (it) detailsConsent = true else model.includeDetails(false)
                    }
                    Hairline(14)
                    SettingsRow("使用指引", icon = Glyph.BOOK) { guide = true }
                    Hairline(14)
                    SettingsRow("识别诊断", icon = Glyph.SEARCH) { diagnostics = true }
                } }
            }
            item("sources") {
                GroupCard { Column {
                    PaymentSource.entries.forEachIndexed { index, source ->
                        if (index > 0) Hairline(14)
                        CaptureSwitch(source.title, source.name in options.sources) { model.selectSource(source, it) }
                        val account = data.accounts.firstOrNull { it.id == bindings[source] && !it.archived }
                        SettingsRow("核对时关联账户", icon = Glyph.WALLET, end = account?.name ?: "不关联") {
                            bindingSource = source.name
                        }
                    }
                } }
            }
            item("pending-title") { SectionTitle("待核对 · ${captures.pending.size}") }
            if (captures.pending.isEmpty()) item("empty") { GroupCard { EmptyPanel("暂无待核对账单") } }
            items(captures.pending, key = { it.id }) { capture ->
                GroupCard { Column {
                    val source = PaymentSource.valueOf(capture.source)
                    SettingsRow(capture.merchant, description = source.title + " · " + capture.type.title + " · " +
                        Instant.ofEpochMilli(capture.bookingAt).atZone(ZoneId.systemDefault()).toLocalDate(),
                        icon = Glyph.BOOK, end = currency(capture.cents)) { review(capture.id) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { ignoreId = capture.id }) { Text("忽略") }
                        TextButton(onClick = { review(capture.id) }) { Text("核对") }
                    }
                } }
            }
        }
    }
    if (guide) PaymentCaptureGuide { guide = false }
    if (diagnostics) PaymentDiagnosticsDialog { diagnostics = false }
    if (detailsConsent) ConfirmDialog("识别账单详情？",
        "打开微信或支付宝的一笔账单时，会读取可访问的交易信息，将识别到的消费、支付宝退款和还款放入待核对。必要的交易信息和去重标识仅在本机加密保存 30 天。",
        "开启", { detailsConsent = false }) { detailsConsent = false; model.includeDetails(true) }
    if (clear) ConfirmDialog("清除本机识别记录？", "待核对记录和去重记录将移除，已保存流水不受影响。", "清除",
        { clear = false }) { clear = false; model.clear() }
    ignoreId?.let { id -> ConfirmDialog("忽略这条记录？", "不会新增流水，该次识别将从待核对中移除。", "忽略",
        { ignoreId = null }) { ignoreId = null; model.ignore(id) } }
    error?.let { message -> LedgerAlertDialog(model::dismissError, title = { Text("未能完成操作") },
        text = { Text(message) }, confirmButton = { TextButton(onClick = model::dismissError) { Text("知道了") } }) }
    bindingSource?.let { sourceName ->
        val source = PaymentSource.valueOf(sourceName)
        LedgerAlertDialog({ bindingSource = null }, title = { Text("${source.title}核对账户") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SettingsRow("不关联") { model.bind(source, null); bindingSource = null }
                data.accounts.filterNot { it.archived }.forEach { account ->
                    Hairline(); SettingsRow(account.name) { model.bind(source, account.id); bindingSource = null }
                }
            }
        }, confirmButton = { TextButton(onClick = { bindingSource = null }) { Text("关闭") } })
    }
}

@Composable
private fun PaymentDiagnosticsDialog(close: () -> Unit) {
    val context = LocalContext.current
    val results by PaymentCaptureDiagnostics.state.collectAsStateWithLifecycle()
    val connected by PaymentCaptureDiagnostics.connected.collectAsStateWithLifecycle()
    val summary = buildString {
        appendLine("记个账 ${BuildConfig.VERSION_NAME} · Android API ${android.os.Build.VERSION.SDK_INT}")
        appendLine(if (connected) "识别服务已连接" else "识别服务未连接")
        results?.let { snapshot ->
            PaymentSource.entries.forEach { source ->
                val result = snapshot[source] ?: PaymentDiagnosticResult()
                appendLine("${source.title} · ${result.stage.title} · ${result.events} 个事件")
                if (result.signals.isNotEmpty()) appendLine("找到字段 ${result.signals.joinToString("、")}")
            }
        }
    }.trimEnd()
    LedgerAlertDialog(close, title = { Text("识别诊断") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("开始后，在两分钟内打开一笔账单并停留几秒，再回这里查看。诊断仅记录处理阶段和字段名称。")
            Text(summary)
            if (results != null) TextButton(onClick = {
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("识别诊断", summary))
            }) { Text("复制结果") }
        }
    }, confirmButton = {
        TextButton(onClick = { PaymentCaptureDiagnostics.start(); close() }) {
            Text(if (results == null) "开始诊断" else "重新诊断")
        }
    }, dismissButton = { TextButton(onClick = close) { Text("关闭") } })
}

@Composable
internal fun PaymentCaptureConsentDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    ConfirmDialog("开启付款识别？",
        "会读取所选微信、支付宝的可访问页面文本，用于识别普通付款成功信息。识别结果在本机加密保存 30 天，核对保存后才入账。不保存整页原文，不上传，不执行支付或点击其他应用。可随时关闭。",
        "同意并去授权", onDismiss, onConfirm)
}

@Composable
private fun PaymentCaptureGuide(close: () -> Unit) {
    LedgerAlertDialog(close, title = { Text("自动记账使用指引") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("1. 开启「自动识别付款」，在系统无障碍设置中启用「记个账付款识别」。")
            Text("2. 选择微信或支付宝。补记旧账时，还需打开「识别账单详情」。")
            Text("微信 → 我 → 服务 → 钱包 → 账单。\n支付宝 → 我的 → 账单。\n进入账单后点开一笔消费。")
            Text("3. 点浮层上的「核对」，检查金额、日期和账户，再保存。点「稍后」的账单可回到这里继续核对。")
        }
    }, confirmButton = { TextButton(onClick = close) { Text("知道了") } })
}

@Composable
private fun CaptureSwitch(title: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = change)
        .padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onCheckedChange = null)
    }
}
