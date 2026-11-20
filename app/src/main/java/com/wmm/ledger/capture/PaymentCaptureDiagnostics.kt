package com.wmm.ledger.capture

import android.os.SystemClock
import com.wmm.ledger.data.PaymentSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class PaymentReadStage(val title: String) {
    WAITING("未收到事件"), EVENT("已收到页面事件"), DISABLED("此来源未开启"),
    WINDOW_UNAVAILABLE("系统未提供支付页面"), TEXT_UNAVAILABLE("页面没有可读文本"),
    UNMATCHED("页面可读，未匹配账单"), MATCHED("已匹配账单"),
    DUPLICATE("该账单已处理或提示过"), SAVE_FAILED("保存待核对失败"),
    SAVED("已保存到待核对"), OVERLAY_FAILED("已保存，浮层创建失败"),
    OVERLAY_SHOWN("已保存，浮层已创建"),
}

internal data class PaymentDiagnosticResult(
    val stage: PaymentReadStage = PaymentReadStage.WAITING,
    val events: Int = 0,
    val signals: List<String> = emptyList(),
)

// 仅在用户启动的两分钟内记录固定阶段和字段名称。原始内容和值从不进入诊断状态。
internal object PaymentCaptureDiagnostics {
    private val connection = MutableStateFlow(false)
    val connected = connection.asStateFlow()
    private val results = MutableStateFlow<Map<PaymentSource, PaymentDiagnosticResult>?>(null)
    val state = results.asStateFlow()
    private var until = 0L

    fun setConnected(value: Boolean) { connection.value = value }

    fun start() {
        until = SystemClock.elapsedRealtime() + 120_000
        results.value = emptyMap()
    }

    val running: Boolean get() = SystemClock.elapsedRealtime() < until

    fun report(source: PaymentSource, stage: PaymentReadStage, signals: List<String> = emptyList()) {
        if (!running) return
        val current = results.value ?: return
        val previous = current[source] ?: PaymentDiagnosticResult()
        val best = if (stage.ordinal >= previous.stage.ordinal) stage else previous.stage
        val fields = if (signals.size >= previous.signals.size) signals else previous.signals
        results.value = current + (source to PaymentDiagnosticResult(best,
            previous.events + (if (stage == PaymentReadStage.EVENT) 1 else 0), fields))
    }
}
