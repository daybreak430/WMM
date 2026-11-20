package com.wmm.ledger.capture

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.wmm.ledger.data.LedgerRepository
import com.wmm.ledger.data.PaymentCaptureSettings
import com.wmm.ledger.data.PaymentScreenParser
import com.wmm.ledger.data.PaymentSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

class PaymentCaptureService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val settings by lazy { PaymentCaptureSettings(this) }
    private val ledger by lazy { LedgerRepository.shared(this) }
    private var pendingRead: Job? = null
    private var overlay: PaymentCaptureOverlay? = null
    private var session = UUID.randomUUID().toString()
    private var activeSource: PaymentSource? = null
    private var lastFingerprint: String? = null
    private var registered = false
    private var settingsJob: Job? = null
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { overlay?.hide(); pendingRead?.cancel() }
    }

    override fun onServiceConnected() {
        overlay?.hide()
        overlay = PaymentCaptureOverlay(this, scope)
        settingsJob?.cancel()
        if (!registered) {
            val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else registerReceiver(screenReceiver, filter)
            registered = true
        }
        settingsJob = scope.launch {
            settings.observe().collect { options ->
                if (!options.enabled || !options.consent) {
                    overlay?.hide(); pendingRead?.cancel(); disableSelf()
                } else {
                    serviceInfo = serviceInfo.apply {
                        packageNames = PaymentSource.entries.filter { it.name in options.sources }.map { it.packageName }
                            .ifEmpty { listOf(packageName) }.toTypedArray()
                    }
                    overlay?.hide()
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val source = PaymentSource.forPackage(event?.packageName?.toString()) ?: return
        if (!settings.snapshot().allows(source) || (getSystemService(KeyguardManager::class.java).isKeyguardLocked || !getSystemService(PowerManager::class.java).isInteractive)) return
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        pendingRead?.cancel()
        pendingRead = scope.launch {
            delay(350)
            val root = rootInActiveWindow ?: return@launch
            val options = settings.snapshot()
            if (root.packageName?.toString() != source.packageName || !options.allows(source)) return@launch
            val texts = runCatching { readText(root) }.getOrNull() ?: return@launch
            val now = System.currentTimeMillis()
            val payment = PaymentScreenParser.parse(source, texts, now, includeDetails = options.includeDetails)
            if (payment == null) {
                if (texts.size >= 3) { session = UUID.randomUUID().toString(); lastFingerprint = null }
                return@launch
            }
            if (source != activeSource) { session = UUID.randomUUID().toString(); lastFingerprint = null }
            activeSource = source
            val fingerprint = payment.fingerprint(session)
            val screenKey = "$fingerprint|${payment.paidAt ?: "success"}"
            if (lastFingerprint == screenKey) return@launch
            lastFingerprint = screenKey
            val generation = ledger.dataGeneration
            scope.launch { try {
                val currentOptions = settings.snapshot()
                if (!currentOptions.allows(source) || (payment.paidAt != null && !currentOptions.includeDetails)) return@launch
                val capture = ledger.captures.enqueue(payment, session, now, generation) ?: return@launch
                val displayOptions = settings.snapshot()
                if (displayOptions.allows(source) && (payment.paidAt == null || displayOptions.includeDetails) &&
                    !getSystemService(KeyguardManager::class.java).isKeyguardLocked &&
                    getSystemService(PowerManager::class.java).isInteractive &&
                    rootInActiveWindow?.packageName?.toString() == source.packageName) overlay?.show(capture)
            } catch (_: kotlinx.coroutines.CancellationException) {
                // 新页面会重新读取；已经持久化的候选仍可在应用内核对。
            } catch (_: Exception) {
                Toast.makeText(this@PaymentCaptureService, "未能保存识别记录，请打开记个账检查。", Toast.LENGTH_SHORT).show()
            } }
        }
    }

    private fun readText(root: AccessibilityNodeInfo): List<String> {
        val texts = mutableListOf<String>()
        var visited = 0
        var size = 0
        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (++visited > 500 || depth > 30 || size > 8_000 || texts.size >= 160 || node.isPassword || node.isEditable) return
            // 不缓存节点或整页内容，也不读取密码、输入框和受保护节点。
            if (Build.VERSION.SDK_INT >= 34 && node.isAccessibilityDataSensitive) return
            val text = node.text?.toString()?.trim()?.takeIf(String::isNotEmpty)
                ?: node.contentDescription?.toString()?.trim().orEmpty()
            if (text.isNotEmpty() && text.length <= 200 && texts.lastOrNull() != text) { texts += text; size += text.length }
            for (index in 0 until node.childCount.coerceAtMost(100)) {
                if (visited >= 500 || size > 8_000 || texts.size >= 160) break
                node.getChild(index)?.let { visit(it, depth + 1) }
            }
        }
        visit(root, 0)
        return texts
    }

    override fun onInterrupt() { overlay?.hide(); pendingRead?.cancel() }
    override fun onDestroy() {
        overlay?.hide()
        scope.cancel()
        if (registered) unregisterReceiver(screenReceiver)
        super.onDestroy()
    }
}
