package com.wmm.ledger.data

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class StorageAndModelTest {
    @Test fun backupRestoresIntoFreshEncryptedStoreAndRejectsBrokenReferences() = runBlocking {
        val originalContext = isolated()
        val original = LedgerRepository(originalContext)
        val restored = LedgerRepository(isolated())
        val today = LocalDate.now()
        val now = System.currentTimeMillis()
        val originalData = LedgerSnapshot(
            tags = listOf(Tag("tag", "EXPENSE", "电影", archived = true),
                Tag("lunch", "EXPENSE", "餐饮", sortOrder = 0)),
            entries = listOf(Entry("entry", "EXPENSE", 5_000, "tag", "card", "不可发送的备注", now, today.toString(), "trip")),
            scenes = listOf(Scene("trip", "出差", sortOrder = 0)),
            accounts = listOf(Account("card", "SAVINGS", "我的卡", 500_000, now - 3_600_000,
                sortOrder = 0, isDefault = true)),
            profile = Profile(nickname = "仅本地"),
        )
        val analysis = originalData.reportAnalysis(Period.YEAR, today, today)
        val complete = originalData.copy(reports = listOf(
            Report(Period.YEAR.name, today.withDayOfYear(1).toString(), Reports.signature(analysis),
                now, Provider.DEEPSEEK.name, "已有年报"),
        ))
        original.replace(complete)
        original.secrets.saveModelKey(Provider.DEEPSEEK, "synthetic-test-key")
        assertEquals("synthetic-test-key", original.secrets.modelKey(Provider.DEEPSEEK))
        val encoded = Backup.encode(original.snapshot())
        assertFalse(encoded.decodeToString().contains("synthetic-test-key"))
        assertNotEquals("SQLite format 3", originalContext.getDatabasePath(LedgerRepository.DATABASE_NAME)
            .inputStream().use { ByteArray(15).also { header -> assertEquals(header.size, it.read(header)) }.decodeToString() })

        assertTrue(restored.snapshot().entries.isEmpty())
        restored.replace(Backup.decode(encoded.inputStream()))
        val actual = restored.snapshot()
        assertEquals(complete.entries, actual.entries)
        assertEquals(complete.tags, actual.tags)
        assertEquals(complete.scenes, actual.scenes)
        assertEquals(complete.accounts, actual.accounts)
        assertEquals(complete.reports, actual.reports)
        assertEquals(complete.profile.nickname, actual.profile.nickname)
        assertEquals(495_000L, actual.accountBalances().getValue("card"))
        assertFalse(restored.secrets.hasKey(Provider.DEEPSEEK))

        val broken = JSONObject(encoded.decodeToString())
        broken.getJSONArray("entries").getJSONObject(0).put("sceneId", "missing")
        assertTrue(runCatching { restored.replace(Backup.decode(broken.toString().byteInputStream())) }.isFailure)
        assertEquals(complete.entries, restored.snapshot().entries)

        val legacy = JSONObject(encoded.decodeToString()).put("version", 1).apply {
            remove("scenes")
            put("reports", org.json.JSONArray())
            getJSONArray("entries").getJSONObject(0).remove("sceneId")
        }
        val imported = Backup.decode(legacy.toString().byteInputStream())
        assertTrue(imported.scenes.isEmpty())
        assertEquals(complete.entries.map { it.copy(sceneId = null) }, imported.entries)

        val versionTwo = JSONObject(encoded.decodeToString()).put("version", 2).apply {
            getJSONArray("scenes").getJSONObject(0).remove("sortOrder")
            getJSONArray("tags").apply { repeat(length()) { index -> getJSONObject(index).remove("sortOrder") } }
        }
        val importedTwo = Backup.decode(versionTwo.toString().byteInputStream())
        assertEquals(complete.tags.map { it.copy(sortOrder = null) }, importedTwo.tags)
        assertEquals(complete.scenes.map { it.copy(sortOrder = null) }, importedTwo.scenes)
        val versionThree = JSONObject(encoded.decodeToString()).put("version", 3).apply {
            getJSONArray("accounts").getJSONObject(0).apply { remove("sortOrder"); remove("isDefault") }
        }
        val importedThree = Backup.decode(versionThree.toString().byteInputStream())
        assertEquals(complete.accounts.map { it.copy(sortOrder = null, isDefault = false) }, importedThree.accounts)

        restored.archiveAccount("card", true)
        assertFalse(restored.snapshot().accounts.single().isDefault)
        assertEquals(complete.entries, restored.snapshot().entries)
    }

    @Test fun accountRelinkAndDeletionRespectPersistedCalibration() = runBlocking {
        val store = LedgerRepository(isolated())
        val now = System.currentTimeMillis()
        val day = LedgerRepository.bookingDay(now)
        val left = Account("left", "SAVINGS", "储蓄卡", 500_000, now - 1_000)
        val right = Account("right", "PREPAID", "充值卡", 20_000, now - 2_000)
        val tag = Tag("tag", "EXPENSE", "价格")
        store.replace(LedgerSnapshot(tags = listOf(tag), accounts = listOf(left, right)))
        val previous = now - 7 * 86_400_000L
        store.saveEntry(null, Kind.EXPENSE, 10_000, tag.id, left.id, "", previous, LedgerRepository.bookingDay(previous))
        assertEquals(500_000L, store.snapshot().accountBalances().getValue(left.id))
        store.saveEntry(null, Kind.EXPENSE, 5_000, tag.id, left.id, "", now, day)
        val entry = store.snapshot().entries.single { it.occurredAt == now }
        assertEquals(495_000L, store.snapshot().accountBalances().getValue(left.id))
        store.saveEntry(entry.id, Kind.EXPENSE, 6_000, tag.id, right.id, "", now, day)
        assertEquals(mapOf(left.id to 500_000L, right.id to 14_000L), store.snapshot().accountBalances())
        store.calibrate(right.id, 17_500)
        store.deleteEntry(entry.id)
        assertEquals(17_500L, store.snapshot().accountBalances().getValue(right.id))
        assertEquals(1, store.snapshot().entries.size)
    }

    @Test fun bothProvidersOnlyReceivePeriodSummaryAndHandleFailures() {
        val today = LocalDate.now()
        val data = LedgerSnapshot(
            entries = listOf(Entry("raw-entry-id", "EXPENSE", 2123, "tag", "private-card-id",
                "private-note", 1000, today.toString(), "private-scene-id")),
            tags = listOf(Tag("tag", "EXPENSE", "晚餐")),
            scenes = listOf(Scene("private-scene-id", "出差")),
            accounts = listOf(Account("private-card-id", "SAVINGS", "private-account-name", 123_456, 0)),
            profile = Profile(nickname = "private-profile"),
        )
        val analysis = data.reportAnalysis(Period.YEAR, today, today)
        val noteEdit = data.copy(entries = data.entries.map { it.copy(note = "变动", accountId = null) })
        val sceneEdit = data.copy(entries = data.entries.map { it.copy(sceneId = null) })
        assertEquals(Reports.signature(analysis), Reports.signature(noteEdit.reportAnalysis(Period.YEAR, today, today)))
        assertNotEquals(Reports.signature(analysis), Reports.signature(sceneEdit.reportAnalysis(Period.YEAR, today, today)))
        for (provider in Provider.entries) {
            val request = Reports.request(provider, analysis)
            assertFalse(request.has("max_tokens") || request.has("max_completion_tokens"))
            assertEquals(provider.model, request.getString("model"))
            val payload = request.getJSONArray("messages").getJSONObject(1).getString("content")
            val fields = JSONObject(payload)
            assertEquals("21.23", fields.getString("支出_元"))
            val group = fields.getJSONArray("场景与类型组合排行").getJSONObject(0)
            assertEquals("晚餐", group.getString("类型标签"))
            assertEquals("出差", group.getString("场景"))
            assertEquals("100.0", group.getString("占对应总额_%"))
            assertEquals(1, fields.getJSONArray("期间分段").length())
            assertFalse(fields.has("上一完整期间"))
            listOf("raw-entry-id", "private-scene-id", "private-card-id", "private-note", "private-account-name",
                "private-profile", "1234.56", "occurredAt")
                .forEach { assertFalse(payload.contains(it)) }
            assertEquals("测试连接", Reports.request(provider, null).getJSONArray("messages")
                .getJSONObject(1).getString("content"))
            val daily = Reports.request(provider, data.reportAnalysis(Period.DAY, today, today)).getJSONArray("messages")
            val dailyPrompt = daily.getJSONObject(0).getString("content")
            assertTrue(dailyPrompt.contains("「场景与类型组合排行」"))
            assertTrue(dailyPrompt.contains("一句话也完全够用"))
            val dailyData = JSONObject(daily.getJSONObject(1).getString("content"))
            assertEquals(1, dailyData.getInt("笔数"))
            assertEquals("出差", dailyData.getJSONArray("场景与类型组合排行").getJSONObject(0).getString("场景"))
        }
        assertTrue(Reports.request(Provider.DEEPSEEK, analysis).has("thinking"))
        assertFalse(Reports.request(Provider.MINIMAX, analysis).has("thinking"))
        val prompt = Reports.request(Provider.DEEPSEEK, analysis)
            .getJSONArray("messages").getJSONObject(0).getString("content")
        assertTrue(prompt.contains("长度和结构由发现决定"))
        val longReport = "先说一件有意思的事。".repeat(900)
        val longResponse = JSONObject().put("choices", org.json.JSONArray().put(JSONObject()
            .put("finish_reason", "stop").put("message", JSONObject().put("content", longReport))))
        assertEquals(longReport, Reports.readReply(200, longResponse.toString()))
        assertEquals("观察", Reports.readReply(200,
            """{"choices":[{"message":{"content":"观察","reasoning_content":"不展示"},"finish_reason":"stop"}]}"""))
        assertTrue((runCatching { Reports.readReply(401, "") }.exceptionOrNull() as LedgerError).display.contains("Key"))
        assertTrue((runCatching { Reports.readReply(200, """{"base_resp":{"status_code":1008}}""") }
            .exceptionOrNull() as LedgerError).display.contains("余额"))
        assertTrue(runCatching { Reports.readReply(200, """{"choices":[{"message":{"content":"片段"},"finish_reason":"length"}]}""") }
            .exceptionOrNull() is LedgerError)
    }

    @Test fun billSourcesPrepareLocalTextAndPageImagesForBothProviders() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = java.nio.file.Files.createTempDirectory(context.cacheDir.toPath(), "bill-test").toFile()
        try {
            val csv = File(folder, "bill.csv").apply {
                writeBytes("日期,金额,备注\n2026-01-02,18.50,咖啡".toByteArray(charset("GB18030")))
            }
            val text = readBillSource(context, Uri.fromFile(csv))
            assertTrue(text.text.orEmpty().contains("18.50,咖啡"))
            val word = File(folder, "bill.docx")
            ZipOutputStream(word.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("word/document.xml"))
                zip.write("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>咖啡 18.50</w:t></w:r></w:p></w:body></w:document>""".toByteArray())
                zip.closeEntry()
            }
            assertEquals("咖啡 18.50", readBillSource(context, Uri.fromFile(word)).text?.trim())
            val pdf = File(folder, "bill.pdf")
            val document = PdfDocument()
            try {
                val page = document.startPage(PdfDocument.PageInfo.Builder(300, 420, 1).create())
                page.canvas.drawText("18.50", 40f, 40f, Paint())
                document.finishPage(page)
                pdf.outputStream().use(document::writeTo)
            } finally {
                document.close()
            }
            val pages = readBillSource(context, Uri.fromFile(pdf))
            assertEquals("image/jpeg", pages.images.single().mime)
            val png = File(folder, "bill.png")
            Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).also { bitmap ->
                png.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            val photo = readBillSource(context, Uri.fromFile(png))
            assertEquals("image/png", photo.images.single().mime)
            for (provider in Provider.entries) {
                val request = BillImport.request(provider, pages, LocalDate.of(2026, 1, 2))
                val messages = request.getJSONArray("messages")
                val parts = messages.getJSONObject(1).getJSONArray("content")
                assertEquals("text", parts.getJSONObject(0).getString("type"))
                assertEquals("image_url", parts.getJSONObject(1).getString("type"))
                assertTrue(parts.getJSONObject(1).getJSONObject("image_url").getString("url")
                    .startsWith("data:image/jpeg;base64,"))
                assertEquals(provider == Provider.DEEPSEEK, request.has("response_format"))
                val textParts = BillImport.request(provider, text, LocalDate.of(2026, 1, 2))
                    .getJSONArray("messages").getJSONObject(1).getJSONArray("content")
                assertEquals(text.text, textParts.getJSONObject(0).getString("text"))
            }
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test fun billImportFlagsDuplicatesAndCommitsTheReviewedBatchAtomically() = runBlocking {
        val repository = LedgerRepository(isolated())
        val day = LocalDate.of(2026, 1, 2)
        val stamp = day.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val transit = Tag("transit", Kind.EXPENSE.name, "地铁")
        val archived = Tag("archived", Kind.EXPENSE.name, "已归档", archived = true)
        val manual = Entry("manual", Kind.EXPENSE.name, 500, transit.id, null, "", stamp, day.toString())
        repository.replace(LedgerSnapshot(entries = listOf(manual), tags = listOf(transit, archived)))
        val reply = """{"more":false,"rows":[
            {"currency":"CNY","kind":"EXPENSE","amount":"5.00","date":"2026-01-02","time":"","type":"地铁"},
            {"currency":"CNY","kind":"EXPENSE","amount":"18.50","date":"2026-01-02","time":"12:40","type":"午餐","scene":"出差"},
            {"currency":"CNY","kind":"INCOME","amount":"99.00","date":"","time":"","type":"退款"},
            {"currency":"USD","kind":"EXPENSE","amount":"10.00","date":"2026-01-02","type":"食物"}
        ]}"""
        val preview = BillImport.parse(reply, "test-file", repository.snapshot().entries)
        assertEquals(1, preview.skippedCurrency)
        assertTrue(preview.rows[0].duplicate && !preview.rows[0].selected)
        assertFalse(preview.rows[2].isReady())
        val lunch = preview.rows[1]
        val refund = preview.rows[2].copy(date = day.toString())
        assertTrue(runCatching { repository.importBill(listOf(lunch, lunch.copy(id = "other", type = archived.name))) }.isFailure)
        assertEquals(listOf(manual), repository.snapshot().entries)
        assertTrue(repository.snapshot().scenes.isEmpty())
        assertEquals(2, repository.importBill(listOf(lunch, refund)))
        assertEquals(0, repository.importBill(listOf(lunch, refund)))
        val snapshot = repository.snapshot()
        assertEquals(3, snapshot.entries.size)
        assertEquals(1, snapshot.scenes.size)
        assertEquals(1_850L, snapshot.entries.single { it.id == lunch.id }.cents)
        assertTrue(snapshot.entries.all { it.accountId == null })
        assertTrue(BillImport.parse(reply, "test-file", snapshot.entries).rows[1].alreadyImported)
    }

    private fun isolated(): Context {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString()
        return object : ContextWrapper(context) {
            override fun getApplicationContext() = this
            override fun getDatabasePath(name: String): File = context.getDatabasePath("$id-$name")
            override fun getSharedPreferences(name: String, mode: Int) = context.getSharedPreferences("$id-$name", mode)
        }
    }
}
