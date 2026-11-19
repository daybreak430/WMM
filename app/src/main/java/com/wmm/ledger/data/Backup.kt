package com.wmm.ledger.data

import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

private const val FORMAT = "wmm-ledger"
private const val BACKUP_VERSION = 8
const val MAX_BACKUP_BYTES = 32 * 1024 * 1024
const val MAX_AVATAR_BYTES = 256 * 1024

internal fun InputStream.readBounded(limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val size = read(buffer)
        if (size < 0) break
        require(output.size() <= limit - size)
        output.write(buffer, 0, size)
    }
    return output.toByteArray()
}

object Backup {
    fun encode(data: LedgerSnapshot): ByteArray = JSONObject().apply {
        put("format", FORMAT)
        put("version", BACKUP_VERSION)
        put("tags", array(data.tags) {
            put("id", it.id); put("kind", it.kind); put("name", it.name); put("archived", it.archived)
            put("sortOrder", it.sortOrder ?: JSONObject.NULL)
            put("iconKey", it.iconKey ?: JSONObject.NULL); put("iconColor", it.iconColor ?: JSONObject.NULL)
        })
        put("scenes", array(data.scenes) {
            put("id", it.id); put("name", it.name)
            put("sortOrder", it.sortOrder ?: JSONObject.NULL)
            put("iconKey", it.iconKey ?: JSONObject.NULL); put("iconColor", it.iconColor ?: JSONObject.NULL)
        })
        put("accounts", array(data.accounts) {
            put("id", it.id); put("type", it.type); put("name", it.name)
            put("baselineCents", it.baselineCents); put("calibratedAt", it.calibratedAt); put("archived", it.archived)
            put("sortOrder", it.sortOrder ?: JSONObject.NULL); put("isDefault", it.isDefault)
        })
        put("entries", array(data.entries) {
            put("id", it.id); put("kind", it.kind); put("cents", it.cents); put("tagId", it.tagId)
            put("accountId", it.accountId ?: JSONObject.NULL); put("note", it.note)
            put("occurredAt", it.occurredAt); put("day", it.day)
            put("sceneId", it.sceneId ?: JSONObject.NULL)
            put("hasTime", it.hasTime)
        })
        put("scheduledBills", array(data.scheduledBills) {
            put("id", it.id); put("kind", it.kind); put("cents", it.cents); put("tagId", it.tagId)
            put("accountId", it.accountId ?: JSONObject.NULL); put("sceneId", it.sceneId ?: JSONObject.NULL)
            put("note", it.note); put("period", it.period); put("startDay", it.startDay)
            put("lastRecordedDay", it.lastRecordedDay ?: JSONObject.NULL)
        })
        put("transfers", array(data.transfers) {
            put("id", it.id); put("cents", it.cents)
            put("fromAccountId", it.fromAccountId); put("toAccountId", it.toAccountId)
            put("note", it.note); put("occurredAt", it.occurredAt); put("day", it.day); put("hasTime", it.hasTime)
        })
        put("reports", array(data.reports) {
            put("period", it.period); put("startDay", it.startDay); put("snapshotId", it.snapshotId)
            put("createdAt", it.createdAt); put("provider", it.provider); put("content", it.content)
        })
        put("profile", JSONObject().apply {
            put("nickname", data.profile.nickname)
            put("avatar", data.profile.avatar?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: JSONObject.NULL)
        })
    }.toString().toByteArray().also { require(it.size <= MAX_BACKUP_BYTES) }

    fun decode(input: InputStream): LedgerSnapshot {
        val tokens = JSONTokener(input.readBounded(MAX_BACKUP_BYTES).decodeToString(throwOnInvalidSequence = true))
        val root = tokens.nextValue() as JSONObject
        require(tokens.nextClean() == '\u0000')
        require(root.string("format") == FORMAT)
        val version = root.number("version")
        if (version !in 1L..BACKUP_VERSION.toLong()) {
            throw LedgerError("不支持这个备份版本。请使用兼容的「记个账」版本恢复。")
        }
        val tags = root.list("tags", 50_000) {
            val style = labelStyle(version)
            Tag(id(), enumValueOf<Kind>(string("kind")).name, name(), flag("archived"), sortOrder(version),
                style?.icon?.name, style?.color?.name)
        }
        val scenes = if (version == 1L) emptyList() else root.list("scenes", 50_000) {
            val style = labelStyle(version)
            Scene(id(), name(), sortOrder(version), style?.icon?.name, style?.color?.name)
        }
        val rankedTags = tags.filter { !it.archived && it.sortOrder != null }
        val rankedScenes = scenes.mapNotNull { it.sortOrder }
        require(rankedTags.distinctBy { it.kind to it.sortOrder }.size == rankedTags.size)
        require(rankedScenes.toSet().size == rankedScenes.size)
        val sceneIds = scenes.mapTo(mutableSetOf()) { it.id }
        require(sceneIds.size == scenes.size && scenes.distinctBy { it.name.lowercase(Locale.ROOT) }.size == scenes.size)
        val accounts = root.list("accounts", 50_000) {
            Account(id(), AccountType.fromStoredName(string("type")).name, name(),
                number("baselineCents").also { require(it in -MAX_CENTS..MAX_CENTS) }, time("calibratedAt"), flag("archived"),
                sortOrder(version, since = 4), version >= 4L && flag("isDefault"))
        }
        val rankedAccounts = accounts.filterNot { it.archived }.mapNotNull { it.sortOrder }
        require(rankedAccounts.toSet().size == rankedAccounts.size)
        val defaultAccounts = accounts.filter { it.isDefault }
        require(defaultAccounts.size <= 1 && defaultAccounts.none { it.archived })
        val tagsById = tags.associateBy { it.id }
        val accountsById = accounts.associateBy { it.id }
        require(tagsById.size == tags.size && accountsById.size == accounts.size)
        require(tags.distinctBy { it.kind to it.name.lowercase(Locale.ROOT) }.size == tags.size)
        val entries = root.list("entries", 150_000) {
            val kind = enumValueOf<Kind>(string("kind")).name
            val tagId = id("tagId").also { require(tagsById[it]?.kind == kind) }
            val accountId = nullableString("accountId").also { require(it == null || it in accountsById) }
            val sceneId = if (version == 1L) null else nullableString("sceneId")
            require(sceneId == null || kind == Kind.EXPENSE.name && sceneId in sceneIds)
            val occurredAt = time("occurredAt")
            val bookingDay = day("day")
            // 时区可能与当前手机不同；允许 UTC 日期前后一天，但拒绝不一致的日期和时间。
            val utcDay = Instant.ofEpochMilli(occurredAt).atOffset(ZoneOffset.UTC).toLocalDate()
            require(bookingDay in utcDay.minusDays(1)..utcDay.plusDays(1))
            Entry(id(), kind, number("cents").also { require(it in 1..MAX_CENTS) }, tagId, accountId,
                string("note").also { require(it.length <= MAX_NOTE) }, occurredAt, bookingDay.toString(), sceneId,
                hasTime = version < 7L || flag("hasTime"))
        }
        require(entries.distinctBy { it.id }.size == entries.size)
        val transfers = if (version < 8L) emptyList() else root.list("transfers", 150_000) {
            val from = id("fromAccountId").also { require(it in accountsById) }
            val to = id("toAccountId").also { require(it in accountsById && it != from) }
            val occurredAt = time("occurredAt")
            val bookingDay = day("day")
            val utcDay = Instant.ofEpochMilli(occurredAt).atOffset(ZoneOffset.UTC).toLocalDate()
            require(bookingDay in utcDay.minusDays(1)..utcDay.plusDays(1))
            Transfer(id(), number("cents").also { require(it in 1..MAX_CENTS) }, from, to,
                string("note").also { require(it.length <= MAX_NOTE) }, occurredAt, bookingDay.toString(), flag("hasTime"))
        }
        require(transfers.distinctBy { it.id }.size == transfers.size)
        val scheduledBills = if (version < 7) emptyList() else root.list("scheduledBills", 50_000) {
            val kind = enumValueOf<Kind>(string("kind")).name
            val tagId = id("tagId").also { require(tagsById[it]?.kind == kind) }
            val accountId = nullableString("accountId").also { require(it == null || it in accountsById) }
            val sceneId = nullableString("sceneId")
            require(sceneId == null || kind == Kind.EXPENSE.name && sceneId in sceneIds)
            val lastRecordedDay = nullableString("lastRecordedDay")?.let {
                LocalDate.parse(it).also { date -> require(date in FIRST_DAY..LAST_DAY) }.toString()
            }
            ScheduledBill(id(), kind, number("cents").also { require(it in 1..MAX_CENTS) }, tagId, accountId, sceneId,
                string("note").also { require(it.length <= MAX_NOTE) }, enumValueOf<Period>(string("period")).name,
                day("startDay").toString(), lastRecordedDay)
        }
        require(scheduledBills.distinctBy { it.id }.size == scheduledBills.size)
        val reports = root.list("reports", 50_000) {
            val period = enumValueOf<Period>(string("period"))
            require(version >= 2L || period != Period.YEAR)
            require(version >= 5L || period != Period.WEEK)
            val start = day("startDay").also { require(it == period.start(it)) }
            val signature = string("snapshotId").also { require(it.matches(Regex("[a-f0-9]{64}"))) }
            Report(period.name, start.toString(), signature, time("createdAt"),
                enumValueOf<Provider>(string("provider")).name,
                string("content").also { require(it.isNotBlank()) })
        }
        require(reports.distinctBy { it.period to it.startDay }.size == reports.size)
        val profile = root.getJSONObject("profile").run {
            val nickname = string("nickname").also { require(it.isEmpty() || validName(it)) }
            val avatar = nullableString("avatar")?.let(::decodeAvatar)
            Profile(nickname = nickname, avatar = avatar)
        }
        return LedgerSnapshot(entries, tags, accounts, reports, profile, scenes, scheduledBills, transfers)
    }

    private fun decodeAvatar(raw: String): ByteArray {
        require(raw.length <= MAX_AVATAR_BYTES * 2)
        return Base64.decode(raw, Base64.NO_WRAP).also { bytes ->
            require(bytes.size in 1..MAX_AVATAR_BYTES)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outWidth in 1..512 && bounds.outHeight in 1..512 &&
                bounds.outMimeType in listOf("image/jpeg", "image/png"))
            requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)).recycle()
        }
    }

    private fun <T> array(items: List<T>, putItem: JSONObject.(T) -> Unit) =
        JSONArray().also { result -> items.forEach { item -> result.put(JSONObject().apply { putItem(item) }) } }

    private fun <T> JSONObject.list(key: String, max: Int, read: JSONObject.() -> T): List<T> {
        val array = getJSONArray(key)
        require(array.length() <= max)
        return List(array.length()) { index -> array.getJSONObject(index).read() }
    }

    private fun JSONObject.string(key: String): String = get(key) as String
    private fun JSONObject.number(key: String): Long = when (val value = get(key)) {
        is Int -> value.toLong()
        is Long -> value
        else -> error("非法的整数")
    }
    private fun JSONObject.flag(key: String): Boolean = get(key) as Boolean
    private fun JSONObject.labelStyle(version: Long): LabelStyle? {
        if (version < 6) return null
        val icon = nullableString("iconKey")
        val color = nullableString("iconColor")
        if (icon == null && color == null) return null
        return requireNotNull(LabelStyle.from(icon, color))
    }
    private fun JSONObject.sortOrder(version: Long, since: Long = 3): Int? =
        if (version < since || get("sortOrder") == JSONObject.NULL) null
        else number("sortOrder").also { require(it in 0..Int.MAX_VALUE.toLong()) }.toInt()
    private fun JSONObject.nullableString(key: String): String? = get(key).let { if (it == JSONObject.NULL) null else it as String }
    private fun JSONObject.id(key: String = "id") = string(key).also { require(it.length in 1..64 && it.none { c -> c.isISOControl() }) }
    private fun JSONObject.name() = string("name").also { require(validName(it) && it == it.trim()) }
    private fun JSONObject.day(key: String) = LocalDate.parse(string(key)).also { require(it in FIRST_DAY..LAST_DAY) }
    private fun JSONObject.time(key: String) = number(key).also {
        val utcDay = Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC).toLocalDate()
        require(utcDay in FIRST_DAY.minusDays(1)..LAST_DAY.plusDays(1))
    }
}
