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
    val paidAt: Long? = null,
) {
    val bookingAt: Long get() = paidAt ?: observedAt
}

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
    val method: String, val orderHash: String?, val paidAt: Long? = null) {
    fun fingerprint(session: String): String {
        val key = when {
            orderHash != null -> "order:$orderHash"
            paidAt != null -> "detail:$paidAt|$cents|${paymentMerchantKey(merchant)}"
            else -> "session:$session|$cents|${paymentMerchantKey(merchant)}"
        }
        return sha256("${source.name}|$key".toByteArray())
    }
}

internal fun paymentMerchantKey(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
    .lowercase(java.util.Locale.ROOT).replace(Regex("[\\p{P}\\p{Z}\\s]"), "").take(80)

internal fun PaymentCapture.similarEntries(entries: List<Entry>, amount: Long = cents): List<Entry> {
    val merchantKey = paymentMerchantKey(merchant)
    val paidDay = paidAt?.let { LedgerRepository.bookingDay(it).toString() }
    return entries.filter { entry -> entry.kind == Kind.EXPENSE.name && entry.cents == amount && merchantKey.length >= 2 &&
        (entry.day == paidDay || abs(entry.occurredAt - bookingAt) <= 120_000) &&
        paymentMerchantKey(entry.note).startsWith(merchantKey) }
}

// 付款成功页与历史详情分别确认交易依据，避免从账单列表或非消费交易推测支出。
internal object PaymentScreenParser {
    private val excluded = Regex("转账|还款|退款|退回|收款成功|收钱成功|交易关闭|支付失败|付款失败|支付中|处理中")
    private const val NUMBER = "(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]{1,2})?"
    private val money = Regex("(?:[¥￥]\\s*($NUMBER)(?![0-9.,])|(?<![0-9.,])($NUMBER)\\s*元)")
    private val amountLabels = listOf("实付金额", "实付款", "实际支付", "付款金额", "支付金额", "实付")
    private val merchantLabels = listOf("商户全称", "收款方全称", "收款商户", "收款方", "商户名称", "商家名称", "交易对方")
    private val productLabels = listOf("商品说明", "商品名称", "商品")
    private val methodLabels = listOf("支付方式", "付款方式", "支付渠道", "付款渠道")
    private val orderLabels = listOf("微信支付交易单号", "微信支付单号", "支付宝交易号", "交易单号", "交易号", "订单号", "商户单号", "订单编号")
    private val timeLabels = listOf("支付时间", "付款时间", "交易时间", "成交时间", "付款日期")
    private val statusLabels = listOf("当前状态", "交易状态", "支付状态")
    private val kindLabels = listOf("交易类型", "收支类型", "资金流向", "收/支")
    private val detailTitles = setOf("账单详情", "交易详情", "账单明细")
    private val paidStatuses = setOf("支付成功", "付款成功", "交易成功", "交易完成")
    private const val ALIPAY_AUTO_DEBIT_SUCCESS = "自动扣款成功"
    private val wechatQrHeading = Regex("扫(?:描)?二维码付款\\s*[-–—−]\\s*给\\s*(.{2,80})")
    private val expenseKinds = setOf("支出", "消费", "支付", "付款", "商户消费", "商户支付", "商家消费", "扫码付款")
    private val nonExpense = Regex("转账|还款|退款|退回|红包|提现|充值|理财|收款成功|收钱成功|收入|交易关闭|支付失败|付款失败|支付中|处理中|待付款|已取消|交易失败|已关闭")
    private val refundStatus = Regex("(?:已|部分)?退款(?:成功|中|金额.*)?|已全额退款|已部分退款")
    private val stamp = Regex("(20\\d{2})[-/年](\\d{1,2})[-/月](\\d{1,2})日?\\s+(\\d{1,2}):(\\d{2})(?::(\\d{2}))?")
    private val allLabels = amountLabels + merchantLabels + productLabels + methodLabels + orderLabels + timeLabels + statusLabels + kindLabels +
        listOf("订单金额", "优惠金额", "优惠", "原价", "账户余额", "余额", "退款金额", "管理免密支付", "管理自动续费", "汇率")

    fun parse(source: PaymentSource, texts: List<String>, now: Long, includeDetails: Boolean = false): RecognizedPayment? {
        val lines = texts.flatMap { it.lines() }.take(160).map { Normalizer.normalize(it, Normalizer.Form.NFKC).trim() }
            .filter(String::isNotEmpty)
        val joined = lines.joinToString("\n").take(8_000)
        if (source == PaymentSource.WECHAT) {
            val qrIndex = lines.take(8).indexOfFirst(wechatQrHeading::matches)
            if (qrIndex >= 0) return if (includeDetails) parseWechatQrDetail(lines, qrIndex, now) else null
        }
        val detailIndex = lines.take(4).indexOfFirst { it in detailTitles }
        val successIndex = lines.indexOfFirst { it == "支付成功" || it == "付款成功" }
        if (detailIndex >= 0 && (successIndex < 0 || detailIndex < successIndex)) {
            return if (includeDetails) parseDetail(source, lines, detailIndex, now) else null
        }
        if (excluded.containsMatchIn(joined) || lines.none { it in listOf("支付成功", "付款成功") }) return null
        stamp.find(joined)?.let { match ->
            val parsed = transactionTime(match) ?: return null
            if (now - parsed !in -60_000..900_000) return null
        }
        val labeledAmounts = amountLabels.mapNotNull { labelValue(lines, listOf(it)) }.mapNotNull(::amount).distinct()
        val allAmounts = money.findAll(joined).mapNotNull { match -> parseCents(
            (match.groups[1]?.value ?: match.groups[2]?.value).orEmpty().replace(",", "")) }.toMutableList()
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
        val merchant = validMerchant(labelValue(lines, merchantLabels + productLabels)) ?: return null
        return recognized(source, cents, merchant, lines)
    }

    private fun parseDetail(source: PaymentSource, lines: List<String>, titleIndex: Int, now: Long): RecognizedPayment? {
        val header = lines.drop(titleIndex + 1).take(10).takeWhile { field -> allLabels.none { matchesLabel(field, it) } }
        val acceptedStatuses = if (source == PaymentSource.ALIPAY) paidStatuses + ALIPAY_AUTO_DEBIT_SUCCESS else paidStatuses
        val status = labelValue(lines, statusLabels) ?: header.firstOrNull { it in acceptedStatuses } ?: return null
        if (status !in acceptedStatuses) return null
        val kind = labelValue(lines, kindLabels)
        if (kind != null && kind !in expenseKinds) return null
        // 支付宝详情可能先暴露「联系商家」等按钮。优先取金额上方最近的商户名。
        val amountIndex = header.indexOfFirst { detailAmount(it) != null }
        val headerMerchant = if (amountIndex > 0) header.take(amountIndex).asReversed().firstNotNullOfOrNull(::validMerchant)
            else header.firstNotNullOfOrNull(::validMerchant)
        val merchant = validMerchant(labelValue(lines, merchantLabels))
            ?: headerMerchant ?: validMerchant(labelValue(lines, productLabels)) ?: return null
        val product = labelValue(lines, productLabels).orEmpty()
        if (nonExpense.containsMatchIn(merchant) || nonExpense.containsMatchIn(product) ||
            header.any(nonExpense::containsMatchIn) || lines.any(refundStatus::matches)) return null

        val time = labelValue(lines, timeLabels)?.let(stamp::matchEntire) ?: return null
        val paidAt = transactionTime(time)?.takeIf { it <= now } ?: return null
        val displayAmount = header.mapNotNull(::detailAmount).distinct().singleOrNull()
        val labeled = amountLabels.mapNotNull { labelValue(lines, listOf(it)) }.mapNotNull(::detailAmount).distinct()
        val cents = when {
            status == ALIPAY_AUTO_DEBIT_SUCCESS -> displayAmount?.takeIf { it < 0 }?.let { -it }
            labeled.isNotEmpty() -> labeled.map(::abs).distinct().singleOrNull()
            else -> displayAmount?.let(::abs)
        } ?: return null
        if (header.any { it.startsWith('+') && detailAmount(it.drop(1)) != null }) return null
        if (displayAmount?.let { it < 0 } != true && labeled.none { it < 0 } && kind !in expenseKinds &&
            (status == "交易成功" || status == "交易完成" || labelValue(lines, methodLabels) == null)) return null

        val payment = recognized(source, cents, merchant, lines, paidAt)
        // 分钟精度不足以稳定区分同店同额订单，无单号时只接受带秒的交易时间。
        return payment.takeIf { it.orderHash != null || time.groupValues[6].isNotEmpty() }
    }

    private fun parseWechatQrDetail(lines: List<String>, headingIndex: Int, now: Long): RecognizedPayment? {
        // 微信单笔扫码付款明细没有页名，借用「转账」字段。仅识别明确的扫码付款标题与已支付的负金额。
        val heading = wechatQrHeading.matchEntire(lines[headingIndex]) ?: return null
        val merchant = validMerchant(heading.groupValues[1].trim()) ?: return null
        val header = lines.drop(headingIndex + 1).take(3).takeWhile { !matchesLabel(it, "当前状态") }
        val signedAmount = header.mapNotNull(::detailAmount).distinct().singleOrNull()?.takeIf { it < 0 } ?: return null
        if (header.any { it.startsWith('+') } || labelValue(lines, statusLabels) != "支付成功" || lines.any(refundStatus::matches)) return null
        if (labelValue(lines, methodLabels).isNullOrBlank()) return null
        val time = labelValue(lines, listOf("转账时间"))?.let(stamp::matchEntire) ?: return null
        val paidAt = transactionTime(time)?.takeIf { it <= now } ?: return null
        val orderHash = readOrderHash(lines, orderLabels + "转账单号") ?: return null
        return recognized(PaymentSource.WECHAT, -signedAmount, merchant, lines, paidAt, orderHash)
    }

    private fun transactionTime(match: MatchResult): Long? = runCatching {
        val (year, month, day, hour, minute, second) = match.destructured
        LocalDateTime.of(year.toInt(), month.toInt(), day.toInt(), hour.toInt(), minute.toInt(), second.toIntOrNull() ?: 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()

    private fun validMerchant(value: String?): String? = value?.take(80)?.takeIf {
        it.length >= 2 && it.any(Char::isLetter) && !money.containsMatchIn(it) && it !in paidStatuses &&
            it !in detailTitles && it !in expenseKinds && it != ALIPAY_AUTO_DEBIT_SUCCESS &&
            it !in setOf("返回", "关闭", "更多", "完成", "联系商家")
    }

    private fun recognized(source: PaymentSource, cents: Long, merchant: String, lines: List<String>, paidAt: Long? = null,
        orderHash: String? = readOrderHash(lines, orderLabels)): RecognizedPayment {
        val method = labelValue(lines, methodLabels).orEmpty().replace(Regex("\\d{5,}"), "••••").take(48)
        return RecognizedPayment(source, cents, merchant, method, orderHash, paidAt)
    }

    private fun readOrderHash(lines: List<String>, labels: List<String>): String? = labelValue(lines, labels)
        ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{8,80}")) }?.let { sha256(it.toByteArray()) }

    private fun detailAmount(text: String): Long? {
        val normalized = text.replace('−', '-').replace(Regex("^([¥￥])\\s*-\\s*"), "-¥")
        return if (normalized.startsWith('-')) amount(normalized.drop(1))?.let { -it } else amount(normalized)
    }

    private fun amount(text: String): Long? {
        val raw = text.trim().removePrefix("¥").removePrefix("￥").removeSuffix("元").trim()
        if (!raw.matches(Regex(NUMBER))) return null
        return parseCents(raw.replace(",", ""))
    }

    private fun matchesLabel(line: String, label: String) = line == label || line.startsWith("$label:") || line.startsWith("$label ")

    private fun labelValue(lines: List<String>, labels: List<String>): String? {
        labels.forEach { label -> lines.forEachIndexed { index, line ->
            if (line == label || line == "$label:" || line == "$label：") {
                lines.getOrNull(index + 1)?.takeIf { next -> next.length <= 120 && allLabels.none { matchesLabel(next, it) } &&
                    next !in listOf("完成", "返回商家") }
                    ?.let { return if (it == "¥" || it == "￥") it + lines.getOrNull(index + 2).orEmpty() else it }
            } else if (line.startsWith("$label:") || line.startsWith("$label：") || line.startsWith("$label ")) {
                line.substring(label.length + 1).trim().takeIf(String::isNotEmpty)?.let { return it }
            }
        } }
        return null
    }
}
