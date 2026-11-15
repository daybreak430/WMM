package com.wmm.ledger.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

class LedgerRepository(context: Context) {
    private val app = context.applicationContext
    val secrets = Secrets(app)
    private val modelClient = ModelClient()
    private val restoreCount = AtomicLong()
    private val database: LedgerDatabase by lazy {
        System.loadLibrary("sqlcipher")
        val key = secrets.databaseKey(!app.getDatabasePath(DATABASE_NAME).exists())
        Room.databaseBuilder(app, LedgerDatabase::class.java, DATABASE_NAME)
            .openHelperFactory(SupportOpenHelperFactory(key))
            .build()
    }
    private val dao get() = database.ledger()

    suspend fun observe() = withContext(Dispatchers.IO) {
        database.invalidationTracker.createFlow("entries", "tags", "accounts", "reports", "profile")
            .conflate().map { snapshot() }.flowOn(Dispatchers.IO)
    }

    suspend fun snapshot(): LedgerSnapshot = withContext(Dispatchers.IO) {
        database.withTransaction {
            LedgerSnapshot(dao.entries(), dao.tags(), dao.accounts(), dao.reports(), dao.profile() ?: Profile())
        }
    }

    suspend fun saveTag(kind: Kind, name: String, id: String? = null): Tag = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (!validName(trimmed)) throw LedgerError("标签名称需为 1–$MAX_NAME 个字。")
        database.withTransaction {
            val old = id?.let { dao.tag(it) ?: throw LedgerError("标签已变更，请重新打开。") }
            val duplicate = dao.tags().firstOrNull { it.kind == kind.name && it.name.lowercase(java.util.Locale.ROOT) == trimmed.lowercase(java.util.Locale.ROOT) }
            if (duplicate != null && duplicate.id != id) {
                throw LedgerError(if (duplicate.archived) "已有同名归档标签，可在标签管理中恢复。" else "已有同名标签。")
            }
            Tag(id ?: newId(), old?.kind ?: kind.name, trimmed, old?.archived ?: false).also { dao.save(it) }
        }
    }

    suspend fun archiveTag(id: String, archive: Boolean) = withContext(Dispatchers.IO) {
        database.withTransaction { dao.tag(id)?.let { dao.save(it.copy(archived = archive)) } }
    }

    suspend fun saveEntry(
        id: String?, kind: Kind, cents: Long, tagId: String, accountId: String?,
        note: String, occurredAt: Long, day: LocalDate,
    ) = withContext(Dispatchers.IO) {
        if (cents !in 1..MAX_CENTS) throw LedgerError("请输入有效金额，最多两位小数。")
        if (day !in FIRST_DAY..LAST_DAY || occurredAt > System.currentTimeMillis()) throw LedgerError("请选择有效的发生日期和时间。")
        database.withTransaction {
            val old = id?.let { dao.entry(it) ?: throw LedgerError("流水已变更，请重新打开。") }
            val tag = dao.tag(tagId)
            if (tag?.kind != kind.name || tag.archived && old?.tagId != tagId) throw LedgerError("请选择一个有效标签。")
            if (accountId != null) {
                val account = dao.account(accountId)
                if (account == null || account.archived && old?.accountId != accountId) throw LedgerError("请选择可用账户。")
            }
            dao.save(Entry(id ?: newId(), kind.name, cents, tagId, accountId, note.trim().take(MAX_NOTE), occurredAt, day.toString()))
        }
    }

    suspend fun deleteEntry(id: String) = withContext(Dispatchers.IO) { dao.deleteEntry(id) }

    suspend fun saveAccount(id: String?, type: AccountType, name: String, initial: Long?) = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (!validName(trimmed)) throw LedgerError("账户名称需为 1–$MAX_NAME 个字。")
        database.withTransaction {
            val old = id?.let { dao.account(it) ?: throw LedgerError("账户已变更，请重新打开。") }
            if (old == null && (initial == null || initial !in -MAX_CENTS..MAX_CENTS)) throw LedgerError("请输入有效的初始余额。")
            dao.save(old?.copy(type = type.name, name = trimmed)
                ?: Account(newId(), type.name, trimmed, checkNotNull(initial), System.currentTimeMillis()))
        }
    }

    suspend fun calibrate(id: String, amount: Long) = withContext(Dispatchers.IO) {
        if (amount !in -MAX_CENTS..MAX_CENTS) throw LedgerError("请输入有效的余额。")
        database.withTransaction {
            val old = dao.account(id) ?: throw LedgerError("账户已变更，请重新打开。")
            dao.save(old.copy(baselineCents = amount, calibratedAt = System.currentTimeMillis()))
        }
    }

    suspend fun archiveAccount(id: String, archive: Boolean) = withContext(Dispatchers.IO) {
        database.withTransaction { dao.account(id)?.let { dao.save(it.copy(archived = archive)) } }
    }

    suspend fun saveNickname(name: String) = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty() && !validName(trimmed)) throw LedgerError("昵称最多 $MAX_NAME 个字。")
        database.withTransaction { dao.save((dao.profile() ?: Profile()).copy(nickname = trimmed)) }
    }

    suspend fun saveAvatar(avatar: ByteArray?) = withContext(Dispatchers.IO) {
        database.withTransaction { dao.save((dao.profile() ?: Profile()).copy(avatar = avatar)) }
    }

    suspend fun replace(snapshot: LedgerSnapshot) = withContext(Dispatchers.IO) {
        database.withTransaction {
            dao.clearEntries()
            dao.clearTags()
            dao.clearAccounts()
            dao.clearReports()
            dao.restoreTags(snapshot.tags)
            dao.restoreAccounts(snapshot.accounts)
            dao.restoreEntries(snapshot.entries)
            dao.restoreReports(snapshot.reports)
            dao.save(snapshot.profile)
            restoreCount.incrementAndGet()
        }
    }

    suspend fun testModel(provider: Provider, key: String) = withContext(Dispatchers.IO) {
        modelClient.complete(provider, key, null)
    }

    suspend fun generate(period: Period, day: LocalDate) = withContext(Dispatchers.IO) {
        require(period != Period.YEAR)
        val restoreVersion = restoreCount.get()
        val summary = snapshot().stats(period, day, LocalDate.now())
        if (summary.entries.isEmpty()) throw LedgerError("这个期间没有流水。")
        val provider = secrets.provider
        val key = secrets.modelKey(provider) ?: throw LedgerError("请先在设置中保存 ${provider.title} 的 API Key。")
        val reply = modelClient.complete(provider, key, summary)
        database.withTransaction {
            if (restoreVersion != restoreCount.get()) throw LedgerError("账本已恢复，请重新生成报告。")
            dao.save(Report(period.name, summary.start.toString(), summary.signature(), System.currentTimeMillis(), provider.name, reply))
        }
    }

    private fun newId() = UUID.randomUUID().toString()

    companion object {
        const val DATABASE_NAME = "wmm-ledger.db"
        fun bookingDay(occurredAt: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
            Instant.ofEpochMilli(occurredAt).atZone(zone).toLocalDate()
    }
}
