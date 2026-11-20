package com.wmm.ledger.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import java.text.Normalizer
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs

internal enum class PaymentSource(val title: String, val packageName: String) {
    WECHAT("微信", "com.tencent.mm"), ALIPAY("支付宝", "com.eg.android.AlipayGphone");
    companion object { fun forPackage(name: String?) = entries.firstOrNull { it.packageName == name } }
}

@Entity(tableName = "payment_captures", indices = [Index(value = ["fingerprint"], unique = true)])
data class PaymentCapture(
    @PrimaryKey val id: String,
    val source: String,
    val cents: Long,
    val merchant: String,
    val method: String,
    val observedAt: Long,
    val fingerprint: String,
    val status: String = "PENDING",
    val entryId: String? = null,
)

@Dao
interface PaymentCaptureDao {
    @Query("SELECT * FROM payment_captures WHERE status = 'PENDING' ORDER BY observedAt DESC, id")
    suspend fun pending(): List<PaymentCapture>
    @Query("SELECT * FROM payment_captures WHERE id = :id") suspend fun get(id: String): PaymentCapture?
    @Query("SELECT * FROM payment_captures WHERE fingerprint = :fingerprint") suspend fun byFingerprint(fingerprint: String): PaymentCapture?
    @Query("SELECT COUNT(*) FROM payment_captures") suspend fun count(): Int
    @Query("DELETE FROM payment_captures WHERE observedAt < :before") suspend fun prune(before: Long)
    @Query("DELETE FROM payment_captures") suspend fun clear()
    @Insert suspend fun insert(capture: PaymentCapture)
    @Update suspend fun update(capture: PaymentCapture)
}

internal data class RecognizedPayment(val source: PaymentSource, val cents: Long, val merchant: String,
    val method: String, val orderHash: String?) {
    fun fingerprint(session: String): String = sha256((source.name + "|" +
        (orderHash?.let { "order:$it" } ?: "session:$session|$cents|${paymentMerchantKey(merchant)}")).toByteArray())
}

internal fun paymentMerchantKey(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
    .lowercase(java.util.Locale.ROOT).replace(Regex("[\\p{P}\\p{Z}\\s]"), "").take(80)

internal fun PaymentCapture.similarEntries(entries: List<Entry>, amount: Long = cents): List<Entry> {
    val merchantKey = paymentMerchantKey(merchant)
    return entries.filter { entry -> entry.kind == Kind.EXPENSE.name && entry.cents == amount && merchantKey.length >= 2 &&
        abs(entry.occurredAt - observedAt) <= 120_000 && paymentMerchantKey(entry.note).startsWith(merchantKey) }
}

// 宁可漏识别也不从多个金额中猜测；历史详情、还款、转账和退款不按普通消费捕获。
internal object PaymentScreenParser {
    private val excluded = Regex("转账|还款|退款|退回|收款成功|收钱成功|交易关闭|支付失败|付款失败|支付中|处理中")
    private const val NUMBER = "(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]{1,2})?"
    private val money = Regex("(?:[¥￥]\\s*($NUMBER)(?![0-9.,])|(?<![0-9.,])($NUMBER)\\s*元)")
    private val amountLabels = listOf("实付金额", "实付款", "实际支付", "付款金额", "支付金额", "实付")
    private val merchantLabels = listOf("收款商户", "商户全称", "收款方", "商户名称", "商家名称", "商品说明", "商品")
    private val methodLabels = listOf("支付方式", "付款方式")
    private val orderLabels = listOf("交易单号", "交易号", "订单号", "商户单号", "订单编号")

    fun parse(source: PaymentSource, texts: List<String>, now: Long): RecognizedPayment? {
        val lines = texts.flatMap { it.lines() }.take(160).map { Normalizer.normalize(it, Normalizer.Form.NFKC).trim() }
            .filter(String::isNotEmpty)
        val joined = lines.joinToString("\n").take(8_000)
        if (lines.take(3).any { it in listOf("账单详情", "交易详情", "账单明细") }) return null
        if (excluded.containsMatchIn(joined) || lines.none { it in listOf("支付成功", "付款成功") }) return null
        val stamp = Regex("20\\d{2}[-/]\\d{1,2}[-/]\\d{1,2}\\s+\\d{1,2}:\\d{2}(?::\\d{2})?").find(joined)?.value
        if (stamp != null) {
            val parsed = runCatching {
                val values = Regex("\\d+").findAll(stamp).map { it.value.toInt() }.toList()
                LocalDateTime.of(values[0], values[1], values[2], values[3], values[4], values.getOrElse(5) { 0 })
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.getOrNull() ?: return null
            if (now - parsed !in -60_000..900_000) return null
        }
        val labeledAmounts = amountLabels.mapNotNull { labelValue(lines, listOf(it)) }.mapNotNull(::amount).distinct()
        val allAmounts = money.findAll(joined).mapNotNull { match -> parseCents(
            (match.groups[1]?.value ?: match.groups[2]?.value).orEmpty().replace(",", "")) }.toMutableList()
        val successIndex = lines.indexOfFirst { it == "支付成功" || it == "付款成功" }
        lines.drop(successIndex + 1).take(2).filter { it.matches(Regex("[0-9]+\\.[0-9]{2}")) }
            .mapNotNull(::amount).forEach(allAmounts::add)
        lines.forEachIndexed { index, line ->
            if (line in listOf("¥", "￥") && index + 1 < lines.size) amount(lines[index + 1])?.let(allAmounts::add)
        }
        // 无实付标签时只认成功标题紧邻的金额，同时要求整页金额无歧义，避免将优惠或余额当作支出。
        val header = lines.drop(successIndex + 1).take(2)
        val headerAmount = header.firstOrNull()?.let { first ->
            if (first == "¥" || first == "￥") header.getOrNull(1)?.let(::amount) else amount(first)
        }
        val cents = if (labeledAmounts.isNotEmpty()) labeledAmounts.singleOrNull()
            else headerAmount?.takeIf { allAmounts.distinct() == listOf(it) }
        if (cents == null) return null
        val merchant = labelValue(lines, merchantLabels)?.take(80)?.takeIf { it.length >= 2 && !money.containsMatchIn(it) && amount(it) == null } ?: return null
        val method = labelValue(lines, methodLabels).orEmpty().replace(Regex("\\d{5,}"), "••••").take(48)
        val order = labelValue(lines, orderLabels)?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{8,80}")) }
        return RecognizedPayment(source, cents, merchant, method, order?.let { sha256(it.toByteArray()) })
    }

    private fun amount(text: String): Long? {
        val raw = text.trim().removePrefix("¥").removePrefix("￥").removeSuffix("元").trim()
        if (!raw.matches(Regex(NUMBER))) return null
        return parseCents(raw.replace(",", ""))
    }

    private fun labelValue(lines: List<String>, labels: List<String>): String? {
        lines.forEachIndexed { index, line -> labels.forEach { label ->
            if (line == label || line == "$label:" || line == "$label：") {
                lines.getOrNull(index + 1)?.takeIf { it.length <= 120 && it !in
                    amountLabels + merchantLabels + methodLabels + orderLabels + listOf("完成", "返回商家", "支付成功", "付款成功") }
                    ?.let { return if (it == "¥" || it == "￥") it + lines.getOrNull(index + 2).orEmpty() else it }
            } else if (line.startsWith("$label:") || line.startsWith("$label：") || line.startsWith("$label ")) {
                line.substring(label.length + 1).trim().takeIf(String::isNotEmpty)?.let { return it }
            }
        } }
        return null
    }
}
