package com.wmm.ledger.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlin.math.roundToInt

private const val WORD_DOCUMENT = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
internal val BILL_MIME_TYPES = arrayOf("image/png", "image/jpeg", "image/webp", "application/pdf", WORD_DOCUMENT,
    "text/plain", "text/csv", "text/comma-separated-values", "application/csv", "application/vnd.ms-excel")

internal const val BILL_FILE_LIMIT = 12 * 1024 * 1024
private const val IMAGE_LIMIT = 7 * 1024 * 1024
private const val PDF_PAGE_LIMIT = 8
private const val BILL_TEXT_LIMIT = 100_000

internal data class BillImage(val mime: String, val bytes: ByteArray)

internal data class BillSource(
    val fingerprint: String,
    val text: String? = null,
    val images: List<BillImage> = emptyList(),
)

internal fun readBillSource(context: Context, uri: Uri): BillSource {
    val bytes = try {
        context.contentResolver.openInputStream(uri)?.use { it.readBounded(BILL_FILE_LIMIT) }
            ?: throw LedgerError("无法读取这个文件，请重新选择。")
    } catch (_: IllegalArgumentException) {
        throw LedgerError("文件超过 12 MB，请拆分后再导入。")
    }
    if (bytes.isEmpty()) throw LedgerError("所选文件为空。")
    val fingerprint = sha256(bytes)
    val mime = context.contentResolver.getType(uri)
    if (bytes.take(5).toByteArray().contentEquals("%PDF-".encodeToByteArray()) || mime == "application/pdf") {
        return BillSource(fingerprint, images = renderBillPdf(context, bytes))
    }
    if (mime == WORD_DOCUMENT || uri.lastPathSegment?.endsWith(".docx", ignoreCase = true) == true) {
        return BillSource(fingerprint, text = readBillWord(context, bytes))
    }
    val imageMime = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, this)
    }.outMimeType
    if (imageMime != null || mime?.startsWith("image/") == true) {
        if (imageMime !in listOf("image/png", "image/jpeg", "image/webp")) {
            throw LedgerError("请选用 PNG、JPG 或 WebP 图片。")
        }
        if (bytes.size > IMAGE_LIMIT) throw LedgerError("图片超过 7 MB，请缩小或分段截图。")
        return BillSource(fingerprint, images = listOf(BillImage(checkNotNull(imageMime), bytes)))
    }
    return BillSource(fingerprint, text = readBillText(bytes))
}

private fun readBillText(bytes: ByteArray): String {
    fun decode(charset: String) = charset(charset).newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
    val prefix = bytes.take(2).map { it.toInt() and 0xff }
    val bom = prefix == listOf(0xfe, 0xff) || prefix == listOf(0xff, 0xfe)
    val text = try {
        if (bom) decode("UTF-16") else bytes.decodeToString(throwOnInvalidSequence = true)
    } catch (_: CharacterCodingException) {
        try {
            decode("GB18030")
        } catch (_: CharacterCodingException) {
            throw LedgerError("无法读取文件。可用格式为图片、PDF、CSV 和 TXT。")
        }
    }.removePrefix("\uFEFF")
    if (text.any { it.isISOControl() && it !in "\r\n\t" }) {
        throw LedgerError("无法读取文件。请将表格导出为 CSV，或将文档转换为 PDF 或 TXT。")
    }
    return checkBillText(text)
}

private fun checkBillText(text: String): String {
    if (text.isBlank()) throw LedgerError("所选文件没有可读取的文字。")
    if (text.length > BILL_TEXT_LIMIT) throw LedgerError("账单文本过长，请拆成几个文件导入。")
    return text
}

private fun readBillWord(context: Context, bytes: ByteArray): String = try {
    withBillFile(context, bytes, ".docx") { file ->
        ZipFile(file).use { zip ->
            val document = zip.getEntry("word/document.xml") ?: throw LedgerError("这个 Word 文档缺少正文。")
            val xml = zip.getInputStream(document).use { it.readBounded(2 * 1024 * 1024) }
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(xml.inputStream(), null)
            checkBillText(buildString {
                while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                    if (parser.namespace == "http://schemas.openxmlformats.org/wordprocessingml/2006/main") {
                        if (parser.eventType == XmlPullParser.START_TAG) {
                            when (parser.name) {
                                "t" -> append(parser.nextText())
                                "tab" -> append('\t')
                                "br" -> append('\n')
                            }
                        } else if (parser.eventType == XmlPullParser.END_TAG && parser.name == "p") {
                            append('\n')
                        }
                    }
                    if (length > BILL_TEXT_LIMIT) throw LedgerError("账单文本过长，请拆成几个文件导入。")
                    parser.next()
                }
            })
        }
    }
} catch (error: LedgerError) {
    throw error
} catch (_: Exception) {
    throw LedgerError("无法读取 Word 文档，请确认文件完整，或转换为 PDF。")
}

private inline fun <T> withBillFile(context: Context, bytes: ByteArray, suffix: String, use: (File) -> T): T {
    val file = File.createTempFile("bill-", suffix, context.cacheDir)
    try {
        file.writeBytes(bytes)
        return use(file)
    } finally {
        file.delete()
    }
}

private fun renderBillPdf(context: Context, bytes: ByteArray): List<BillImage> =
    withBillFile(context, bytes, ".pdf") { file ->
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            PdfRenderer(descriptor)
        } catch (_: Exception) {
            descriptor.close()
            throw LedgerError("无法打开 PDF，请确认文件完整且已解除密码保护。")
        }
        renderer.use { pdf ->
            if (pdf.pageCount !in 1..PDF_PAGE_LIMIT) throw LedgerError("请一次导入 1–8 页 PDF，长账单可以分段导入。")
            var totalBytes = 0
            List(pdf.pageCount) { index ->
                pdf.openPage(index).use { page ->
                    val scale = 2200.0 / maxOf(page.width, page.height)
                    val bitmap = Bitmap.createBitmap((page.width * scale).roundToInt().coerceAtLeast(1),
                        (page.height * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val out = ByteArrayOutputStream()
                        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)) {
                            throw LedgerError("无法读取 PDF 页面。")
                        }
                        totalBytes += out.size()
                        if (totalBytes > BILL_FILE_LIMIT) throw LedgerError("PDF 页面内容太多，请分段导入。")
                        BillImage("image/jpeg", out.toByteArray())
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        }
    }

internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
    .joinToString("") { "%02x".format(it) }
