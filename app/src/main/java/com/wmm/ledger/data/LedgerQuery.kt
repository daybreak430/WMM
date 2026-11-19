package com.wmm.ledger.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

internal enum class QuerySource { ENTRIES, TRANSFERS, ACCOUNTS, SCHEDULES }
internal enum class QueryMode { RECORDS, SUMMARY, GROUPS }
internal enum class QuerySort { AMOUNT, COUNT, DATE }
internal enum class QueryGroupBy { TYPE, SCENE, ACCOUNT, YEAR, MONTH, DAY, WEEKDAY, KIND }
internal data class LedgerReference(val source: QuerySource, val id: String)
internal data class QueryFilter(
    val from: String? = null, val to: String? = null, val kinds: Set<String> = emptySet(),
    val text: String = "", val type: String = "", val scene: String = "", val account: String = "", val note: String = "",
    val minCents: Long? = null, val maxCents: Long? = null, val anyText: List<String> = emptyList(),
)
internal data class LedgerQuery(
    val source: QuerySource = QuerySource.ENTRIES, val mode: QueryMode = QueryMode.SUMMARY,
    val filter: QueryFilter = QueryFilter(), val groupBy: List<QueryGroupBy> = emptyList(),
    val sort: QuerySort = QuerySort.AMOUNT, val descending: Boolean = true, val limit: Int = 20,
    val includeArchived: Boolean = false,
)
internal data class QueryRow(
    val id: String, val source: QuerySource, val cents: Long, val day: String, val kind: String,
    val type: String, val scene: String, val account: String, val note: String,
    val archived: Boolean = false, val details: Map<String, String> = emptyMap(),
) {
    fun matchesText(token: String): Boolean {
        val amount = parseCents(normalizeLedgerText(token).replace(",", "").removePrefix("¥")
            .removeSuffix("元").removePrefix("+").replace('−', '-'), signed = true)
        return amount?.let { abs(it) == abs(cents) } == true ||
            (listOf(type, scene, account, note, day, kind) + details.values).any { containsLedgerText(it, token) }
    }
}
internal data class QueryStats(
    val count: Int, val totalCents: Long, val incomeCents: Long, val expenseCents: Long,
    val minCents: Long?, val maxCents: Long?, val averageCents: Long?,
)
internal data class QueryGroup(val labels: List<String>, val stats: QueryStats)
internal data class QueryResult(
    val query: LedgerQuery, val stats: QueryStats, val records: List<QueryRow>, val groups: List<QueryGroup>,
    val groupCount: Int, val evidence: List<LedgerReference>, val evidenceCount: Int,
)
internal data class LedgerSearchResult(val query: String = "", val entries: List<Entry> = emptyList(),
    val transfers: List<Transfer> = emptyList())

internal fun normalizeLedgerText(text: String): String = Normalizer.normalize(text.trim(), Normalizer.Form.NFKC)
    .lowercase(Locale.ROOT)
internal fun ledgerSearchTokens(query: String): List<String> = normalizeLedgerText(query)
    .replace(Regex("¥\\s+"), "¥").replace(Regex("(?<=\\d)\\s+元"), "元")
    .split(Regex("\\s+")).filter { it.isNotEmpty() }
internal fun containsLedgerText(text: String, query: String): Boolean =
    normalizeLedgerText(text).contains(normalizeLedgerText(query))

// 搜索和 AI 查询共用字段映射，金额用整数分精确比较；转账始终保持独立来源。
internal class LedgerQueryEngine(private val data: LedgerSnapshot) {
    private val tags = data.tags.associateBy { it.id }
    private val scenes = data.scenes.associateBy { it.id }
    private val accounts = data.accounts.associateBy { it.id }
    private val cache = mutableMapOf<QuerySource, List<QueryRow>>()

    fun rows(source: QuerySource): List<QueryRow> = cache.getOrPut(source) {
        when (source) {
            QuerySource.ENTRIES -> data.entries.map { entry ->
                QueryRow(entry.id, source, entry.cents, entry.day, entry.kind,
                    tags[entry.tagId]?.name.orEmpty(), scenes[entry.sceneId]?.name.orEmpty(),
                    accounts[entry.accountId]?.name.orEmpty(), entry.note,
                    details = mapOf("方向" to if (entry.kind == Kind.EXPENSE.name) "支出" else "收入"))
            }
            QuerySource.TRANSFERS -> data.transfers.map { transfer ->
                val from = accounts[transfer.fromAccountId]?.name.orEmpty()
                val to = accounts[transfer.toAccountId]?.name.orEmpty()
                QueryRow(transfer.id, source, transfer.cents, transfer.day, "TRANSFER", "转账", "", "$from → $to",
                    transfer.note, details = mapOf("转出账户" to from, "转入账户" to to))
            }
            QuerySource.ACCOUNTS -> {
                val balances = data.accountBalances()
                data.accounts.map { account -> QueryRow(account.id, source, balances[account.id] ?: 0, "", "",
                    AccountType.valueOf(account.type).title, "", account.name, "", account.archived,
                    mapOf("余额含义" to if (account.isCredit) "负数为欠款，正数为溢缴款" else "资产余额")) }
            }
            QuerySource.SCHEDULES -> data.scheduledBills.map { bill ->
                QueryRow(bill.id, source, bill.cents, bill.nextDay()?.toString().orEmpty(), bill.kind,
                    tags[bill.tagId]?.name.orEmpty(), scenes[bill.sceneId]?.name.orEmpty(),
                    accounts[bill.accountId]?.name.orEmpty(), bill.note,
                    details = mapOf("周期" to bill.period, "首次日期" to bill.startDay,
                        "状态" to (bill.unavailableReason(data) ?: "可用")))
            }

        }
    }

    fun execute(query: LedgerQuery): QueryResult {
        val filter = query.filter
        val tokens = ledgerSearchTokens(filter.text)
        val matched = rows(query.source).filter { row ->
            (!row.archived || query.includeArchived) &&
                (filter.from == null || row.day.isNotEmpty() && row.day >= filter.from) &&
                (filter.to == null || row.day.isNotEmpty() && row.day <= filter.to) &&
                (filter.kinds.isEmpty() || row.kind in filter.kinds) &&
                containsLedgerText(row.type, filter.type) && containsLedgerText(row.scene, filter.scene) &&
                containsLedgerText(row.account, filter.account) && containsLedgerText(row.note, filter.note) &&
                (filter.minCents == null || row.cents >= filter.minCents) &&
                (filter.maxCents == null || row.cents <= filter.maxCents) && tokens.all(row::matchesText) &&
                (filter.anyText.isEmpty() || filter.anyText.any(row::matchesText))
        }
        val grouped = if (query.mode == QueryMode.GROUPS) matched.groupBy { row ->
            query.groupBy.map { key -> when (key) {
                QueryGroupBy.TYPE -> row.type.ifEmpty { "未分类" }
                QueryGroupBy.SCENE -> row.scene.ifEmpty { "未标场景" }
                QueryGroupBy.ACCOUNT -> row.account.ifEmpty { "不关联" }
                QueryGroupBy.YEAR -> row.day.take(4).ifEmpty { "未记录日期" }
                QueryGroupBy.MONTH -> row.day.take(7).ifEmpty { "未记录日期" }
                QueryGroupBy.DAY -> row.day.ifEmpty { "未记录日期" }
                QueryGroupBy.WEEKDAY -> row.day.takeIf { it.isNotEmpty() }?.let {
                    "星期${"一二三四五六日"[LocalDate.parse(it).dayOfWeek.ordinal]}"
                } ?: "未记录日期"
                QueryGroupBy.KIND -> when (row.kind) { "EXPENSE" -> "支出"; "INCOME" -> "收入"; "TRANSFER" -> "转账"; else -> "账户" }
            } }
        }.map { (labels, values) -> QueryGroup(labels, stats(values)) } else emptyList()
        val recordOrder = if (query.sort == QuerySort.DATE) compareBy<QueryRow> { it.day }.thenBy { it.id }
            else compareBy<QueryRow> { it.cents }.thenBy { it.day }.thenBy { it.id }
        val groupOrder = when (query.sort) {
            QuerySort.COUNT -> compareBy<QueryGroup> { it.stats.count }.thenBy { it.labels.joinToString() }
            QuerySort.DATE -> compareBy { it.labels.joinToString() }
            QuerySort.AMOUNT -> compareBy<QueryGroup> { it.stats.totalCents }.thenBy { it.labels.joinToString() }
        }
        val selected = if (query.mode == QueryMode.RECORDS) matched.sortedWith(
            if (query.descending) recordOrder.reversed() else recordOrder).take(query.limit) else emptyList()
        val references = (if (query.mode == QueryMode.RECORDS) selected else matched).asSequence().filter { it.source == QuerySource.ENTRIES || it.source == QuerySource.TRANSFERS }
            .map { LedgerReference(it.source, it.id) }.toList()
        return QueryResult(query, stats(matched),
            selected,
            grouped.sortedWith(if (query.descending) groupOrder.reversed() else groupOrder).take(query.limit), grouped.size,
            references.take(500), references.size)
    }

    private fun stats(rows: List<QueryRow>): QueryStats {
        val total = rows.sumOf { it.cents }
        return QueryStats(rows.size, total, rows.filter { it.kind == Kind.INCOME.name }.sumOf { it.cents },
            rows.filter { it.kind == Kind.EXPENSE.name }.sumOf { it.cents },
            rows.minOfOrNull { it.cents }, rows.maxOfOrNull { it.cents },
            if (rows.isEmpty()) null else BigDecimal.valueOf(total).divide(BigDecimal.valueOf(rows.size.toLong()),
                0, RoundingMode.HALF_UP).longValueExact())
    }
}

internal fun LedgerSnapshot.searchLedger(query: String): LedgerSearchResult {
    if (query.isBlank()) return LedgerSearchResult(query, entries, transfers)
    val engine = LedgerQueryEngine(this)
    val tokens = ledgerSearchTokens(query)
    val entryIds = engine.rows(QuerySource.ENTRIES).filter { row -> tokens.all(row::matchesText) }.mapTo(hashSetOf()) { it.id }
    val transferIds = engine.rows(QuerySource.TRANSFERS).filter { row -> tokens.all(row::matchesText) }.mapTo(hashSetOf()) { it.id }
    return LedgerSearchResult(query, entries.filter { it.id in entryIds }, transfers.filter { it.id in transferIds })
}
