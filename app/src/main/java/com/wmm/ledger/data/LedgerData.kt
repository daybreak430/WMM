package com.wmm.ledger.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
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
)

@Entity(tableName = "scenes", indices = [Index(value = ["name"], unique = true)])
data class Scene(@PrimaryKey val id: String, val name: String, val sortOrder: Int? = null)

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey val id: String,
    val type: String,
    val name: String,
    val baselineCents: Long,
    val calibratedAt: Long,
    val archived: Boolean = false,
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
    @Query("SELECT * FROM accounts ORDER BY calibratedAt DESC, id") suspend fun accounts(): List<Account>
    @Query("SELECT * FROM entries ORDER BY day DESC, occurredAt DESC, id") suspend fun entries(): List<Entry>
    @Query("SELECT * FROM reports") suspend fun reports(): List<Report>
    @Query("SELECT * FROM profile WHERE id = 0") suspend fun profile(): Profile?
    @Query("SELECT * FROM entries WHERE id = :id") suspend fun entry(id: String): Entry?
    @Query("SELECT * FROM tags WHERE id = :id") suspend fun tag(id: String): Tag?
    @Query("SELECT * FROM accounts WHERE id = :id") suspend fun account(id: String): Account?
    @Upsert suspend fun save(tag: Tag)
    @Update suspend fun updateTags(tags: List<Tag>)
    @Update suspend fun updateScenes(scenes: List<Scene>)
    @Insert suspend fun addScene(scene: Scene)
    @Upsert suspend fun save(account: Account)
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
    @Query("DELETE FROM tags") suspend fun clearTags()
    @Query("DELETE FROM scenes") suspend fun clearScenes()
    @Query("DELETE FROM accounts") suspend fun clearAccounts()
    @Query("DELETE FROM reports") suspend fun clearReports()
}

@Database(entities = [Tag::class, Account::class, Entry::class, Report::class, Profile::class, Scene::class], version = 3)
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
