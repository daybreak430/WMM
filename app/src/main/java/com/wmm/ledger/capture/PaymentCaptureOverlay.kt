package com.wmm.ledger.capture

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.wmm.ledger.MainActivity
import com.wmm.ledger.data.LedgerRepository
import com.wmm.ledger.data.PaymentCapture
import com.wmm.ledger.data.PaymentSource
import com.wmm.ledger.data.currency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val REVIEW_PAYMENT_ACTION = "com.wmm.ledger.REVIEW_PAYMENT"
internal const val PAYMENT_CAPTURE_ID = "payment_capture_id"

// 覆盖层只占卡片区域，不获取输入焦点；只操作本应用的核对入口，不点击支付应用。
internal class PaymentCaptureOverlay(private val service: PaymentCaptureService, private val scope: CoroutineScope) {
    private val manager = service.getSystemService(WindowManager::class.java)
    private var view: LinearLayout? = null
    private var timeout: Job? = null
    private fun dp(value: Int) = (value * service.resources.displayMetrics.density).toInt()

    fun show(capture: PaymentCapture) {
        hide()
        val dark = service.resources.configuration.uiMode and 0x30 == 0x20
        val foreground = Color.parseColor(if (dark) "#EEF3FF" else "#17243A")
        val primary = Color.parseColor(if (dark) "#8CB9FF" else "#2868E4")
        val card = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor(if (dark) "#1C293A" else "#F7FAFF"))
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.parseColor(if (dark) "#45566E" else "#D9E4F4"))
            }
            elevation = dp(8).toFloat()
        }
        fun text(value: String, size: Float, bold: Boolean = false) = TextView(service).apply {
            text = value; textSize = size; setTextColor(foreground)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            maxLines = 2
        }
        card.addView(text("${PaymentSource.valueOf(capture.source).title} · 待核对", 13f))
        card.addView(text("支出 ${currency(capture.cents)}", 24f, true))
        card.addView(text(capture.merchant, 15f))
        val actions = LinearLayout(service).apply { gravity = Gravity.END; orientation = LinearLayout.HORIZONTAL }
        fun action(label: String, block: () -> Unit) {
            actions.addView(TextView(service).apply {
                text = label; textSize = 15f; setTextColor(primary); gravity = Gravity.CENTER
                minHeight = dp(48); setPadding(dp(12), 0, dp(12), 0)
                isClickable = true; isFocusable = true
                contentDescription = label
                setOnClickListener { block() }
            })
        }
        action("稍后") { hide() }
        action("忽略") {
            hide()
            scope.launch {
                try { LedgerRepository.shared(service).captures.ignore(capture.id) }
                catch (error: kotlinx.coroutines.CancellationException) { throw error }
                catch (_: Exception) { Toast.makeText(service, "请在自动记账页处理这条记录。", Toast.LENGTH_SHORT).show() }
            }
        }
        action("核对") {
            hide()
            try {
                service.startActivity(Intent(service, MainActivity::class.java).setAction(REVIEW_PAYMENT_ACTION)
                    .putExtra(PAYMENT_CAPTURE_ID, capture.id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            } catch (_: Exception) {
                Toast.makeText(service, "请在记个账的自动记账页核对。", Toast.LENGTH_SHORT).show()
            }
        }
        card.addView(actions)
        val width = minOf(dp(360), service.resources.displayMetrics.widthPixels - dp(32))
        val params = WindowManager.LayoutParams(width, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(48)
            title = "记个账待核对"
        }
        try {
            manager.addView(card, params)
            view = card
            timeout = scope.launch { delay(12_000); hide() }
        } catch (_: Exception) { view = null }
    }

    fun hide() {
        timeout?.cancel(); timeout = null
        view?.let { runCatching { manager.removeView(it) } }
        view = null
    }
}
