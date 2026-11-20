package com.wmm.ledger

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wmm.ledger.data.LedgerError
import com.wmm.ledger.data.LedgerRepository
import com.wmm.ledger.data.PaymentCaptureOptions
import com.wmm.ledger.data.PaymentCaptureSettings
import com.wmm.ledger.data.PaymentCaptureState
import com.wmm.ledger.data.PaymentSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class PaymentCaptureViewModel(application: Application) : AndroidViewModel(application) {
    private val ledger = LedgerRepository.shared(application)
    private val settings = PaymentCaptureSettings(application)
    val options = settings.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settings.snapshot())
    val captures = flow { ledger.captures.prune(); emitAll(ledger.captures.observe()) }
        .catch { error ->
            if (error is CancellationException) throw error
            failure.value = "无法读取识别记录，请检查账本状态。"
            emit(PaymentCaptureState(loaded = true))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PaymentCaptureState())
    private val bindings = MutableStateFlow<Map<PaymentSource, String?>>(emptyMap())
    val accounts = bindings.asStateFlow()
    private val failure = MutableStateFlow<String?>(null)
    val error = failure.asStateFlow()

    init { action {
        bindings.value = withContext(Dispatchers.IO) { PaymentSource.entries.associateWith { ledger.secrets.captureAccount(it) } }
    } }

    fun enable(value: Boolean, consent: Boolean = false) = settings.setEnabled(value, consent)
    fun includeDetails(value: Boolean) = settings.setIncludeDetails(value)
    fun selectSource(source: PaymentSource, value: Boolean) = settings.setSource(source, value)
    fun bind(source: PaymentSource, accountId: String?) = action {
        withContext(Dispatchers.IO) { ledger.secrets.setCaptureAccount(source, accountId) }
        bindings.value = bindings.value + (source to accountId)
    }
    fun ignore(id: String) = action { ledger.captures.ignore(id) }
    fun clear() = action { ledger.captures.clear() }
    fun dismissError() { failure.value = null }

    suspend fun review(id: String) = try {
        // 后台停止订阅后列表可能仍是旧值，核对入口必须重新读取数据库。
        ledger.captures.pending(id)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        failure.value = "无法读取识别记录，请重试。"
        null
    }
    private fun action(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { failure.value = (error as? LedgerError)?.display ?: "操作未完成，请重试。" }
    }
}
