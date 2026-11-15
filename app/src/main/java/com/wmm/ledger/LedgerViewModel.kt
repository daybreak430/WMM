package com.wmm.ledger

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wmm.ledger.data.AccountType
import com.wmm.ledger.data.Avatar
import com.wmm.ledger.data.Backup
import com.wmm.ledger.data.Kind
import com.wmm.ledger.data.LedgerError
import com.wmm.ledger.data.LedgerRepository
import com.wmm.ledger.data.LedgerSnapshot
import com.wmm.ledger.data.Period
import com.wmm.ledger.data.Provider
import com.wmm.ledger.data.Scene
import com.wmm.ledger.data.Tag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class LedgerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LedgerRepository(application)
    private val resolver = application.contentResolver
    private val ledger = MutableStateFlow<LedgerSnapshot?>(null)
    val data = ledger.asStateFlow()
    private val openError = MutableStateFlow(false)
    val unableToOpen = openError.asStateFlow()
    private val selectedProvider = MutableStateFlow(repository.secrets.provider)
    val provider = selectedProvider.asStateFlow()
    private val configuredProviders = MutableStateFlow(configured())
    val providersWithKeys = configuredProviders.asStateFlow()
    private val generatingReport = MutableStateFlow(false)
    val generating = generatingReport.asStateFlow()
    private val restoringFile = MutableStateFlow(false)
    val fileBusy = restoringFile.asStateFlow()
    private val restorePreview = MutableStateFlow<LedgerSnapshot?>(null)
    val pendingRestore = restorePreview.asStateFlow()
    private val messages = Channel<String>(Channel.BUFFERED)
    val feedback = messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            try {
                repository.observe().collect { ledger.value = it }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                openError.value = true
            }
        }
    }

    fun saveTag(kind: Kind, name: String, id: String?, done: (Tag) -> Unit) =
        perform("无法保存标签。") { done(repository.saveTag(kind, name, id)) }

    fun archiveTag(id: String, archive: Boolean) =
        perform("无法修改标签。") { repository.archiveTag(id, archive) }

    fun saveScene(name: String, done: (Scene) -> Unit) =
        perform("无法保存场景。") { done(repository.saveScene(name)) }

    fun saveEntry(id: String?, kind: Kind, amount: Long, tag: String, account: String?, note: String, at: Long,
        day: LocalDate, sceneId: String?, done: () -> Unit) = perform("无法保存流水。") {
        repository.saveEntry(id, kind, amount, tag, account, note, at, day, sceneId)
        done()
        notify("已保存")
    }

    fun deleteEntry(id: String, done: () -> Unit) = perform("无法删除流水。") {
        repository.deleteEntry(id)
        done()
        notify("已删除")
    }

    fun saveAccount(id: String?, type: AccountType, name: String, amount: Long?, done: () -> Unit) =
        perform("无法保存账户。") { repository.saveAccount(id, type, name, amount); done() }

    fun calibrate(id: String, amount: Long, done: () -> Unit) = perform("无法校准余额。") {
        repository.calibrate(id, amount)
        done()
        notify("余额已校准")
    }

    fun archiveAccount(id: String, archive: Boolean) =
        perform("无法修改账户。") { repository.archiveAccount(id, archive) }

    fun saveNickname(name: String, done: () -> Unit) =
        perform("无法保存昵称。") { repository.saveNickname(name); done() }

    fun saveAvatar(uri: Uri?) = perform("无法读取这张照片，请选择其他图片。") {
        val avatar = uri?.let { withContext(Dispatchers.IO) { Avatar.import(resolver, uri) } }
        repository.saveAvatar(avatar)
        notify(if (uri == null) "已移除头像" else "头像已更新")
    }

    fun selectProvider(provider: Provider) {
        repository.secrets.provider = provider
        selectedProvider.value = provider
    }

    fun saveKey(provider: Provider, key: String, done: () -> Unit) = perform("无法安全保存 API Key。") {
        val cleaned = key.trim()
        if (cleaned.length !in 4..512 || cleaned.any { it !in '!'..'~' }) throw LedgerError("请输入有效的 API Key。")
        withContext(Dispatchers.IO) { repository.secrets.saveModelKey(provider, cleaned) }
        configuredProviders.value = configured()
        done()
        notify("API Key 已保存")
    }

    fun removeKey(provider: Provider) = perform("无法移除 API Key。") {
        withContext(Dispatchers.IO) { repository.secrets.removeModelKey(provider) }
        configuredProviders.value = configured()
        notify("API Key 已移除")
    }

    suspend fun testModel(provider: Provider): String = try {
        val key = withContext(Dispatchers.IO) { repository.secrets.modelKey(provider) }
            ?: throw LedgerError("请先保存 API Key。")
        repository.testModel(provider, key)
        "连接成功"
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        (error as? LedgerError)?.display ?: "无法验证 API Key，请检查设置。"
    }

    fun hasReportConsent() = repository.secrets.hasReportConsent()

    fun generate(period: Period, day: LocalDate, consent: Boolean = false) {
        if (generatingReport.value) return
        if (consent) repository.secrets.setReportConsent()
        generatingReport.value = true
        perform("报告未生成，请检查模型设置后重试。") {
            try {
                repository.generate(period, day)
                notify("报告已生成")
            } finally {
                generatingReport.value = false
            }
        }
    }

    fun export(uri: Uri) = fileOperation("导出失败，请检查存储空间或选择其他位置。") {
        val snapshot = repository.snapshot()
        withContext(Dispatchers.IO) {
            val bytes = Backup.encode(snapshot)
            requireNotNull(resolver.openOutputStream(uri, "wt")).use { it.write(bytes) }
        }
        notify("备份已导出")
    }

    fun readBackup(uri: Uri) = fileOperation("无法读取备份。文件可能损坏或不是 WMM 备份。") {
        restorePreview.value = withContext(Dispatchers.IO) {
            requireNotNull(resolver.openInputStream(uri)).use(Backup::decode)
        }
    }

    fun cancelRestore() { restorePreview.value = null }

    fun restore() {
        val snapshot = restorePreview.value ?: return
        restorePreview.value = null
        fileOperation("恢复失败，原有账本未被覆盖。") {
            repository.replace(snapshot)
            notify("备份已恢复")
        }
    }

    private fun fileOperation(error: String, action: suspend () -> Unit) {
        if (restoringFile.value) return
        restoringFile.value = true
        perform(error) {
            try { action() } finally { restoringFile.value = false }
        }
    }

    private fun configured() = Provider.entries.filter { repository.secrets.hasKey(it) }.toSet()

    private fun perform(fallback: String, action: suspend () -> Unit) = viewModelScope.launch {
        try {
            action()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            notify((error as? LedgerError)?.display ?: fallback)
        }
    }

    private fun notify(message: String) { messages.trySend(message) }
}
