package com.wmm.ledger

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wmm.ledger.data.AccountType
import com.wmm.ledger.data.Avatar
import com.wmm.ledger.data.Backup
import com.wmm.ledger.data.BillRow
import com.wmm.ledger.data.similarBillRows
import com.wmm.ledger.data.LabelIconChange
import com.wmm.ledger.data.LabelIconPreview
import com.wmm.ledger.data.LabelIconSuggestion
import com.wmm.ledger.data.LabelStyle
import com.wmm.ledger.data.StyleLabel
import com.wmm.ledger.data.Kind
import com.wmm.ledger.data.LedgerError
import com.wmm.ledger.data.LedgerRepository
import com.wmm.ledger.data.LedgerSnapshot
import com.wmm.ledger.data.Period
import com.wmm.ledger.data.Provider
import com.wmm.ledger.data.Scene
import com.wmm.ledger.data.ScheduledBill
import com.wmm.ledger.data.Tag
import com.wmm.ledger.data.prepareBackupShare
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

internal enum class Appearance(val title: String) {
    SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色"),
}

internal data class BillImportState(
    val rows: List<BillRow> = emptyList(),
    val skippedCurrency: Int = 0,
    val scanning: Boolean = false,
    val saving: Boolean = false,
    val batch: BillBatch? = null,
)

internal data class BillBatch(val type: String, val scene: String, val ids: List<String>)

class LedgerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LedgerRepository.shared(application)
    private val resolver = application.contentResolver
    private val appearancePrefs = application.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    private val habitLocationPrefs = application.getSharedPreferences("habit_location", Context.MODE_PRIVATE)
    private val locateHabits = MutableStateFlow(habitLocationPrefs.getBoolean("enabled", false))
    internal val habitLocation = locateHabits.asStateFlow()
    private val pendingIcons = MutableStateFlow<LabelIconPreview?>(null)
    internal val iconPreview = pendingIcons.asStateFlow()
    private val stylingIcons = MutableStateFlow(false)
    internal val iconBusy = stylingIcons.asStateFlow()
    private val preferredAppearance = MutableStateFlow(
        Appearance.entries.firstOrNull { it.name == appearancePrefs.getString("mode", null) } ?: Appearance.SYSTEM,
    )
    internal val appearance = preferredAppearance.asStateFlow()
    private val backupShares = Channel<Uri>(Channel.BUFFERED)
    val sharedBackups = backupShares.receiveAsFlow()

    private val ledger = MutableStateFlow<LedgerSnapshot?>(null)
    val data = ledger.asStateFlow()
    private val openError = MutableStateFlow(false)
    val unableToOpen = openError.asStateFlow()
    private val selectedProvider = MutableStateFlow(repository.secrets.provider)
    val provider = selectedProvider.asStateFlow()
    private val configuredProviders = MutableStateFlow(configured())
    val providersWithKeys = configuredProviders.asStateFlow()
    private val restoringFile = MutableStateFlow(false)
    val fileBusy = restoringFile.asStateFlow()
    private val restorePreview = MutableStateFlow<LedgerSnapshot?>(null)
    val pendingRestore = restorePreview.asStateFlow()
    private val billState = MutableStateFlow(BillImportState())
    internal val billImport = billState.asStateFlow()
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

    internal fun saveTag(kind: Kind, name: String, id: String?, done: (Tag) -> Unit, style: LabelStyle? = null) =
        perform("无法保存标签。") { done(repository.saveTag(kind, name, id, style)) }

    fun archiveTag(id: String, archive: Boolean) =
        perform("无法修改标签。") { repository.archiveTag(id, archive) }

    internal fun saveScene(name: String, done: (Scene) -> Unit, style: LabelStyle? = null) =
        perform("无法保存场景。") { done(repository.saveScene(name, style)) }

    fun reorderTags(kind: Kind, order: List<String>, onFailure: () -> Unit) =
        perform("无法保存标签顺序。", onFailure) { repository.reorderTags(kind, order) }

    fun reorderScenes(order: List<String>, onFailure: () -> Unit) =
        perform("无法保存场景顺序。", onFailure) { repository.reorderScenes(order) }

    fun saveEntry(id: String?, kind: Kind, amount: Long, tag: String, account: String?, note: String, at: Long,
        day: LocalDate, sceneId: String?, habitArea: String?, hasTime: Boolean, done: () -> Unit) = perform("无法保存流水。") {
        repository.saveEntry(id, kind, amount, tag, account, note, at, day, sceneId,
            habitArea.takeIf { locateHabits.value }, hasTime)
        done()
        notify("已保存")
    }

    internal suspend fun refreshScheduledBills() {
        try {
            if (!repository.recordScheduledBills(updateJob = true)) {
                notify("后台调度暂未启用，打开应用时会继续检查定时账单。")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            notify("定时账单暂时无法检查，下次会重试。")
        }
    }

    internal fun saveScheduledBill(bill: ScheduledBill, done: () -> Unit, onFailure: () -> Unit) =
        perform("无法保存定时账单。", onFailure) {
            repository.saveScheduledBill(bill)
            refreshScheduledBills()
            done()
        }

    internal fun deleteScheduledBill(id: String, done: () -> Unit) = perform("无法删除定时账单。") {
        repository.deleteScheduledBill(id)
        refreshScheduledBills()
        done()
    }

    internal fun setHabitLocation(enabled: Boolean) {
        habitLocationPrefs.edit().putBoolean("enabled", enabled).apply()
        locateHabits.value = enabled
        if (!enabled) perform("无法清除已记录的位置范围。") { repository.clearHabitAreas() }
    }

    internal fun suggestIcons(useModel: Boolean) {
        if (stylingIcons.value) return
        stylingIcons.value = true
        perform("无法生成图标建议。") {
            try {
                pendingIcons.value = repository.suggestLabelIcons(provider.value.takeIf { useModel })
            } finally {
                stylingIcons.value = false
            }
        }
    }

    internal fun suggestNewLabelIcon(
        name: String,
        section: String,
        useModel: Boolean,
        done: (LabelIconSuggestion) -> Unit,
        onFailure: () -> Unit,
    ) = perform("无法生成图标建议。", onFailure) {
        done(repository.suggestLabelIcon(provider.value.takeIf { useModel }, name, section))
    }

    internal fun applyIconPreview() {
        val preview = pendingIcons.value ?: return
        if (stylingIcons.value) return
        stylingIcons.value = true
        perform("无法保存标签图标。") {
            try {
                repository.saveLabelIcons(preview.changes, onlyUnstyled = true)
                pendingIcons.value = null
            } finally {
                stylingIcons.value = false
            }
        }
    }

    internal fun discardIconPreview() { if (!stylingIcons.value) pendingIcons.value = null }

    internal fun saveLabelIcon(target: StyleLabel, style: LabelStyle?, done: () -> Unit) =
        perform("无法保存标签图标。") { repository.saveLabelIcons(listOf(LabelIconChange(target, style))); done() }

    fun deleteEntry(id: String, done: () -> Unit) = perform("无法删除流水。") {
        repository.deleteEntry(id)
        done()
        notify("已删除")
    }

    internal fun saveTransfer(
        id: String?, cents: Long, from: String, to: String, note: String, at: Long,
        day: LocalDate, hasTime: Boolean, done: () -> Unit,
    ) = perform("无法保存转账。") {
        repository.saveTransfer(id, cents, from, to, note, at, day, hasTime)
        done()
        notify("已保存")
    }

    internal fun deleteTransfer(id: String, done: () -> Unit) = perform("无法删除转账。") {
        repository.deleteTransfer(id)
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

    fun reorderAccounts(order: List<String>, onFailure: () -> Unit) =
        perform("无法保存账户顺序。", onFailure) { repository.reorderAccounts(order) }

    fun setDefaultAccount(id: String?, done: () -> Unit, onFailure: () -> Unit) =
        perform("无法设置默认账户。", onFailure) { repository.setDefaultAccount(id); done() }

    fun saveNickname(name: String, done: () -> Unit) =
        perform("无法保存昵称。") { repository.saveNickname(name); done() }

    fun saveAvatar(uri: Uri?) = perform("无法读取这张照片，请选择其他图片。") {
        val avatar = uri?.let { withContext(Dispatchers.IO) { Avatar.import(resolver, uri) } }
        repository.saveAvatar(avatar)
        notify(if (uri == null) "已移除头像" else "头像已更新")
    }

    internal fun selectAppearance(appearance: Appearance) {
        appearancePrefs.edit().putString("mode", appearance.name).apply()
        preferredAppearance.value = appearance
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

    internal fun scanBill(uri: Uri, provider: Provider) {
        val current = billState.value
        if (current.scanning || current.saving) return
        billState.value = current.copy(scanning = true)
        perform("无法识别账单，请检查文件或网络后重试。", onFailure = { billState.value = current }) {
            val recognized = repository.recognizeBill(uri, provider)
            billState.value = BillImportState(recognized.rows, recognized.skippedCurrency)
        }
    }

    internal fun changeBillRow(row: BillRow) {
        val current = billState.value
        if (current.scanning || current.saving) return
        billState.value = current.copy(rows = current.rows.map { if (it.id == row.id) row else it })
    }

    internal fun reviewBillRow(row: BillRow) {
        val current = billState.value
        if (current.scanning || current.saving) return
        val before = current.rows.firstOrNull { it.id == row.id } ?: return
        val similar = similarBillRows(before, row, current.rows)
        billState.value = current.copy(
            rows = current.rows.map { if (it.id == row.id) row.copy(reviewed = true) else it },
            batch = similar.takeIf { it.isNotEmpty() }?.let { BillBatch(row.type, row.scene, it) },
        )
    }

    internal fun resolveBillBatch(selectedIds: List<String>) {
        val current = billState.value
        val batch = current.batch ?: return
        val ids = batch.ids.intersect(selectedIds.toSet())
        val rows = current.rows.map {
            if (it.id in ids) it.copy(type = batch.type, scene = batch.scene, reviewed = true) else it
        }
        billState.value = current.copy(rows = rows, batch = null)
    }

    internal fun discardBill() {
        val current = billState.value
        if (!current.scanning && !current.saving) billState.value = BillImportState()
    }

    internal fun saveBill() {
        val current = billState.value
        if (current.scanning || current.saving) return
        val selected = current.rows.filter { it.selected }
        if (selected.isEmpty() || selected.any { !it.isReady() }) return
        billState.value = current.copy(saving = true)
        perform("导入失败，账本没有改动。", onFailure = { billState.value = current }) {
            val count = repository.importBill(selected)
            billState.value = BillImportState()
            notify(if (count == 0) "所选流水已导入。" else "已导入 $count 笔流水。")
        }
    }


    fun exportForSharing() = fileOperation("无法准备备份，请检查存储空间后重试。") {
        val snapshot = repository.snapshot()
        val uri = withContext(Dispatchers.IO) { prepareBackupShare(getApplication(), snapshot) }
        backupShares.send(uri)
    }

    fun readBackup(uri: Uri) = fileOperation("无法读取备份。文件可能损坏或不是「记个账」的备份。") {
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
            refreshScheduledBills()
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

    private fun perform(fallback: String, onFailure: () -> Unit = {}, action: suspend () -> Unit) = viewModelScope.launch {
        try {
            action()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            onFailure()
            notify((error as? LedgerError)?.display ?: fallback)
        }
    }

    private fun notify(message: String) { messages.trySend(message) }
}
