package com.wmm.ledger.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Upsert
import androidx.room.Update

@Entity(tableName = "tags", indices = [Index(value = ["kind", "name"], unique = true)])
data class Tag(
    @PrimaryKey val id: String,
    val kind: String,
    val name: String,
    val archived: Boolean = false,
    val sortOrder: Int? = null,
    val iconKey: String? = null,
    val iconColor: String? = null,
)

@Entity(tableName = "scenes", indices = [Index(value = ["name"], unique = true)])
data class Scene(@PrimaryKey val id: String, val name: String, val sortOrder: Int? = null,
    val iconKey: String? = null, val iconColor: String? = null)

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey val id: String,
    val type: String,
    val name: String,
    val baselineCents: Long,
    val calibratedAt: Long,
    val archived: Boolean = false,
    val sortOrder: Int? = null,
    @ColumnInfo(defaultValue = "0") val isDefault: Boolean = false,
)

@Entity(
    tableName = "entries",
    foreignKeys = [
        ForeignKey(entity = Account::class, parentColumns = ["id"], childColumns = ["accountId"]),
        ForeignKey(entity = Tag::class, parentColumns = ["id"], childColumns = ["tagId"]),
        ForeignKey(entity = Scene::class, parentColumns = ["id"], childColumns = ["sceneId"]),
    ],
    indices = [Index("tagId"), Index("accountId"), Index("day"), Index("sceneId")],
)
data class Entry(
    @PrimaryKey val id: String,
    val kind: String,
    val cents: Long,
    val tagId: String,
    val accountId: String?,
    val note: String,
    val occurredAt: Long,
    val day: String,
    val sceneId: String? = null,
    val habitArea: String? = null,
    @ColumnInfo(defaultValue = "1") val hasTime: Boolean = true,
)

@Entity(tableName = "reports", primaryKeys = ["period", "startDay"])
data class Report(
    val period: String,
    val startDay: String,
    val snapshotId: String,
    val createdAt: Long,
    val provider: String,
    val content: String,
)

@Entity(tableName = "profile")
data class Profile(@PrimaryKey val id: Int = 0, val nickname: String = "", val avatar: ByteArray? = null)

@Dao
interface LedgerDao {
    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE") suspend fun tags(): List<Tag>
    @Query("SELECT * FROM scenes ORDER BY name COLLATE NOCASE") suspend fun scenes(): List<Scene>
    @Query("SELECT * FROM scenes WHERE id = :id") suspend fun scene(id: String): Scene?
    @Query("SELECT * FROM accounts ORDER BY sortOrder IS NULL, sortOrder, calibratedAt DESC, id")
    suspend fun accounts(): List<Account>
    @Query("SELECT * FROM entries ORDER BY day DESC, occurredAt DESC, id") suspend fun entries(): List<Entry>
    @Query("SELECT * FROM reports") suspend fun reports(): List<Report>
    @Query("SELECT * FROM profile WHERE id = 0") suspend fun profile(): Profile?
    @Query("SELECT * FROM entries WHERE id = :id") suspend fun entry(id: String): Entry?
    @Query("SELECT * FROM transfers ORDER BY day DESC, occurredAt DESC, id") suspend fun transfers(): List<Transfer>
    @Query("SELECT * FROM transfers WHERE id = :id") suspend fun transfer(id: String): Transfer?
    @Upsert suspend fun save(transfer: Transfer)
    @Query("DELETE FROM transfers WHERE id = :id") suspend fun deleteTransfer(id: String)
    @Query("DELETE FROM transfers") suspend fun clearTransfers()
    @Insert suspend fun restoreTransfers(transfers: List<Transfer>)
    @Query("SELECT * FROM scheduled_bills ORDER BY startDay, id") suspend fun scheduledBills(): List<ScheduledBill>
    @Query("SELECT * FROM scheduled_bills WHERE id = :id") suspend fun scheduledBill(id: String): ScheduledBill?
    @Upsert suspend fun save(bill: ScheduledBill)
    @Query("DELETE FROM scheduled_bills WHERE id = :id") suspend fun deleteScheduledBill(id: String)
    @Query("DELETE FROM scheduled_bills") suspend fun clearScheduledBills()
    @Insert suspend fun restoreScheduledBills(bills: List<ScheduledBill>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertScheduledEntries(entries: List<Entry>)
    @Query("SELECT * FROM tags WHERE id = :id") suspend fun tag(id: String): Tag?
    @Query("SELECT * FROM accounts WHERE id = :id") suspend fun account(id: String): Account?
    @Upsert suspend fun save(tag: Tag)
    @Update suspend fun updateTags(tags: List<Tag>)
    @Update suspend fun updateScenes(scenes: List<Scene>)
    @Insert suspend fun addScene(scene: Scene)
    @Upsert suspend fun save(account: Account)
    @Update suspend fun updateAccounts(accounts: List<Account>)
    @Query("""
        UPDATE accounts SET isDefault = CASE WHEN id = :id THEN 1 ELSE 0 END
        WHERE isDefault = 1 OR id = :id
    """)
    suspend fun setDefaultAccount(id: String?)
    @Upsert suspend fun save(entry: Entry)
    @Upsert suspend fun save(report: Report)
    @Upsert suspend fun save(profile: Profile)
    @Insert suspend fun restoreTags(tags: List<Tag>)
    @Insert suspend fun restoreScenes(scenes: List<Scene>)
    @Insert suspend fun restoreAccounts(accounts: List<Account>)
    @Insert suspend fun restoreEntries(entries: List<Entry>)
    @Insert suspend fun restoreReports(reports: List<Report>)
    @Query("DELETE FROM entries WHERE id = :id") suspend fun deleteEntry(id: String)
    @Query("DELETE FROM entries") suspend fun clearEntries()
    @Query("UPDATE entries SET habitArea = NULL WHERE habitArea IS NOT NULL") suspend fun clearHabitAreas()
    @Query("DELETE FROM tags") suspend fun clearTags()
    @Query("DELETE FROM scenes") suspend fun clearScenes()
    @Query("DELETE FROM accounts") suspend fun clearAccounts()
    @Query("DELETE FROM reports") suspend fun clearReports()
}

@Database(
    entities = [Tag::class, Account::class, Entry::class, Report::class, Profile::class, Scene::class, ScheduledBill::class,
        Transfer::class],
    version = 8,
)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun ledger(): LedgerDao
}

internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE scenes (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX index_scenes_name ON scenes (name)")
        // 新列允许为空，旧支出保留原值且不会被自动归入任何场景。
        db.execSQL("ALTER TABLE entries ADD COLUMN sceneId TEXT REFERENCES scenes(id)")
        db.execSQL("CREATE INDEX index_entries_sceneId ON entries (sceneId)")
    }
}

internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tags ADD COLUMN sortOrder INTEGER")
        db.execSQL("ALTER TABLE scenes ADD COLUMN sortOrder INTEGER")
    }
}

internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE accounts ADD COLUMN sortOrder INTEGER")
        db.execSQL("ALTER TABLE accounts ADD COLUMN isDefault INTEGER NOT NULL DEFAULT 0")
    }
}

internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE entries ADD COLUMN habitArea TEXT")
        db.execSQL("ALTER TABLE tags ADD COLUMN iconKey TEXT")
        db.execSQL("ALTER TABLE tags ADD COLUMN iconColor TEXT")
        db.execSQL("ALTER TABLE scenes ADD COLUMN iconKey TEXT")
        db.execSQL("ALTER TABLE scenes ADD COLUMN iconColor TEXT")
    }
}

internal val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 旧版无法区分精确时间和占位时间，迁移时保留原有显示。
        db.execSQL("ALTER TABLE entries ADD COLUMN hasTime INTEGER NOT NULL DEFAULT 1")
        db.execSQL("""
            CREATE TABLE scheduled_bills (
                id TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, cents INTEGER NOT NULL,
                tagId TEXT NOT NULL, accountId TEXT, sceneId TEXT, note TEXT NOT NULL,
                period TEXT NOT NULL, startDay TEXT NOT NULL, lastRecordedDay TEXT
            )
        """.trimIndent())
    }
}

internal val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE transfers (
                id TEXT NOT NULL PRIMARY KEY, cents INTEGER NOT NULL,
                fromAccountId TEXT NOT NULL, toAccountId TEXT NOT NULL,
                note TEXT NOT NULL, occurredAt INTEGER NOT NULL, day TEXT NOT NULL, hasTime INTEGER NOT NULL,
                FOREIGN KEY(fromAccountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE NO ACTION,
                FOREIGN KEY(toAccountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE NO ACTION
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX index_transfers_fromAccountId ON transfers (fromAccountId)")
        db.execSQL("CREATE INDEX index_transfers_toAccountId ON transfers (toAccountId)")
        db.execSQL("CREATE INDEX index_transfers_day ON transfers (day)")
    }
}

internal val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 仅统一旧分类编码；账户标识、名称、欠款、校准点和流水关联均保留。
        db.execSQL("UPDATE accounts SET type = 'CREDIT' WHERE type IN ('CREDIT_CARD', 'MEITUAN_CREDIT', 'HUABEI')")
    }
}
