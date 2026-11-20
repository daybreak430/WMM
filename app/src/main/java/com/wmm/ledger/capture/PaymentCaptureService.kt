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
import com.wmm.ledger.data.RecognizedPayment
import com.wmm.ledger.data.PaymentScreenParser
import com.wmm.ledger.data.PaymentSource
import kotlinx.coroutines.CancellationException
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
    private var pendingSource: PaymentSource? = null
    private var overlay: PaymentCaptureOverlay? = null
    private var session = UUID.randomUUID().toString()
    private var activeSource: PaymentSource? = null
    private var lastFingerprint: String? = null
    private var lastPromptedId: String? = null
    private var registered = false
    private var settingsJob: Job? = null
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { overlay?.hide(); pendingRead?.cancel() }
    }

    override fun onServiceConnected() {
        PaymentCaptureDiagnostics.setConnected(true)
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
        PaymentCaptureDiagnostics.report(source, PaymentReadStage.EVENT)
        if (pendingSource == source && pendingRead?.isActive == true) return
        if (!settings.snapshot().allows(source) || !canReadScreen()) {
            PaymentCaptureDiagnostics.report(source, PaymentReadStage.DISABLED)
            return
        }
        // 到期读取一次而非每次事件重新计时，避免动态内容把读取无限向后推迟。
        pendingRead?.cancel()
        pendingSource = source
        pendingRead = scope.launch {
            for (wait in READ_DELAYS) {
                delay(wait)
                val options = settings.snapshot()
                if (!options.allows(source) || !canReadScreen()) return@launch
                val root = runCatching { rootInActiveWindow }.getOrNull()
                if (root?.packageName?.toString() != source.packageName) {
                    PaymentCaptureDiagnostics.report(source, PaymentReadStage.WINDOW_UNAVAILABLE)
                    continue
                }
                val texts = runCatching { readText(requireNotNull(root)) }.getOrNull().orEmpty()
                if (texts.isEmpty()) {
                    PaymentCaptureDiagnostics.report(source, PaymentReadStage.TEXT_UNAVAILABLE)
                    continue
                }
                val now = System.currentTimeMillis()
                val payment = PaymentScreenParser.parse(source, texts, now, options.includeDetails)
                if (payment == null) {
                    if (texts.size >= 3) resetSession()
                    if (PaymentCaptureDiagnostics.running) PaymentCaptureDiagnostics.report(source,
                        PaymentReadStage.UNMATCHED, PaymentScreenParser.signals(texts))
                    continue
                }
                PaymentCaptureDiagnostics.report(source, PaymentReadStage.MATCHED)
                handlePayment(payment, now)
                return@launch
            }
        }
    }

    private fun handlePayment(payment: RecognizedPayment, now: Long) {
        val source = payment.source
        if (source != activeSource) resetSession()
        activeSource = source
        val captureSession = session
        val fingerprint = payment.fingerprint(captureSession)
        val screenKey = "$fingerprint|${payment.paidAt ?: "success"}"
        if (lastFingerprint == screenKey) {
            PaymentCaptureDiagnostics.report(source, PaymentReadStage.DUPLICATE)
            return
        }
        lastFingerprint = screenKey
        val generation = ledger.dataGeneration
        scope.launch {
            try {
                val current = settings.snapshot()
                if (!current.allows(source) || (payment.paidAt != null && !current.includeDetails) || !canReadScreen()) return@launch
                val capture = ledger.captures.enqueue(payment, captureSession, now, generation)
                if (capture == null) {
                    PaymentCaptureDiagnostics.report(source, PaymentReadStage.DUPLICATE)
                    return@launch
                }
                PaymentCaptureDiagnostics.report(source, PaymentReadStage.SAVED)
                if (lastPromptedId == capture.id) return@launch
                val display = settings.snapshot()
                if (display.allows(source) && (payment.paidAt == null || display.includeDetails) && canReadScreen() &&
                    runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull() == source.packageName) {
                    val shown = overlay?.show(capture) == true
                    if (shown) lastPromptedId = capture.id
                    PaymentCaptureDiagnostics.report(source, if (shown) PaymentReadStage.OVERLAY_SHOWN else PaymentReadStage.OVERLAY_FAILED)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (lastFingerprint == screenKey) lastFingerprint = null
                PaymentCaptureDiagnostics.report(source, PaymentReadStage.SAVE_FAILED)
                Toast.makeText(this@PaymentCaptureService, "未能保存识别记录，请打开记个账检查。", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun resetSession() {
        session = UUID.randomUUID().toString()
        lastFingerprint = null
        lastPromptedId = null
    }

    private fun canReadScreen() = !getSystemService(KeyguardManager::class.java).isKeyguardLocked &&
        getSystemService(PowerManager::class.java).isInteractive

    private companion object {
        // 详情页先进入加载骨架，晚到的内容也有机会在窗口仍活跃时读取。
        val READ_DELAYS = longArrayOf(220, 380, 650, 950, 1200)
    }

    private fun readText(root: AccessibilityNodeInfo): List<String> {
        val texts = mutableListOf<String>()
        var visited = 0
        var size = 0
        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (++visited > 500 || depth > 30 || size > 8_000 || texts.size >= 160 || node.isPassword || node.isEditable) return
            // 不缓存节点或整页内容，也不读取密码、输入框和受保护节点。
            if (Build.VERSION.SDK_INT >= 34 && node.isAccessibilityDataSensitive) return
            val text = node.text?.toString()?.trim()
            val description = node.contentDescription?.toString()?.trim()
            for (value in listOfNotNull(text, description).distinct()) {
                if (value.isNotEmpty() && value.length <= 480 && texts.lastOrNull() != value) {
                    texts += value
                    size += value.length
                }
            }
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
        PaymentCaptureDiagnostics.setConnected(false)
        overlay?.hide()
        scope.cancel()
        if (registered) unregisterReceiver(screenReceiver)
        super.onDestroy()
    }
}
