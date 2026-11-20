package com.wmm.ledger.data

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

internal data class HabitChoice(val tag: Tag, val scene: Scene?)

internal class LedgerHabits(data: LedgerSnapshot, private val zone: ZoneId = ZoneId.systemDefault()) {
    private val tags = data.tags.filterNot(Tag::archived).associateBy(Tag::id)
    private val scenes = data.scenes.associateBy(Scene::id)
    private val entries = data.entries.asSequence().filter { it.refundOfId == null && it.tagId in tags }.take(1500)
        .map { entry ->
            HabitObservation(entry, merchantKey(entry.note), entry.occurredAt.takeIf { entry.hasTime }?.let { minute(it, zone) })
        }.groupBy { it.entry.kind }

    fun suggest(kind: Kind, cents: Long?, time: Long?, note: String, area: String?): HabitChoice? {
        val merchant = merchantKey(note)
        if (merchant.isEmpty() && (time == null || area == null && cents == null)) return null
        val clock = time?.let { minute(it, zone) }
        val votes = entries[kind.name].orEmpty().mapNotNull { observed ->
            val old = observed.entry
            val familiarMerchant = sameMerchant(merchant, observed.merchant)
            val familiarTime = clock != null && observed.minute != null && timeClose(clock, observed.minute)
            val familiarAmount = cents != null && amountClose(cents, old.cents)
            val familiarArea = area != null && area == old.habitArea
            if (!familiarMerchant && !(familiarTime && (familiarArea || familiarAmount))) return@mapNotNull null
            val weight = (if (familiarMerchant) 8 else 0) + (if (familiarArea) 5 else 0) +
                (if (familiarTime) 3 else 0) + (if (familiarAmount) 2 else 0)
            val category = old.tagId to old.sceneId.takeIf { kind == Kind.EXPENSE && it in scenes }
            HabitVote(category, old.day, weight, familiarMerchant)
        }.groupBy(HabitVote::category).map { (category, matches) ->
            // 单日大量相似消费只计一次，跨天重复且明显领先才自动选中。
            val days = matches.groupBy(HabitVote::day).values.map { it.maxBy(HabitVote::weight) }
            val support = if (days.count(HabitVote::merchant) >= 2) 2 else 3
            Triple(category, days.map(HabitVote::weight).sortedDescending().take(5).sum(), days.size >= support)
        }.sortedByDescending { it.second }
        val best = votes.firstOrNull() ?: return null
        val runnerUp = votes.getOrNull(1)?.second ?: 0
        if (!best.third || best.second - runnerUp < 5 || best.second * 4 < runnerUp * 5) return null
        return HabitChoice(tags.getValue(best.first.first), scenes[best.first.second])
    }
}

private data class HabitVote(val category: Pair<String, String?>, val day: String, val weight: Int, val merchant: Boolean)
private data class HabitObservation(val entry: Entry, val merchant: String, val minute: Int?)
private val merchantNoise = Regex("[\\p{P}\\p{Z}\\d]+")
private val noteDetail = Regex("[·,，;；/：:（(\\n]")

private fun merchantKey(note: String): String =
    note.substringBefore(noteDetail.find(note)?.value ?: "\u0000").lowercase(Locale.ROOT)
        .replace(merchantNoise, "").take(24)

private fun sameMerchant(left: String, right: String): Boolean =
    left.length >= 2 && right.length >= 2 && (left == right ||
        minOf(left.length, right.length) >= 4 && (left.startsWith(right) || right.startsWith(left)))

private fun minute(time: Long, zone: ZoneId): Int = Instant.ofEpochMilli(time).atZone(zone).toLocalTime()
    .let { it.hour * 60 + it.minute }

private fun timeClose(left: Int, right: Int): Boolean {
    val distance = abs(left - right)
    return minOf(distance, 1440 - distance) <= 90
}

private fun amountClose(first: Long, second: Long): Boolean =
    abs(first - second) <= maxOf(100L, minOf(first, second) / 4)

internal fun LedgerHabits.classify(row: BillRow): BillRow {
    val kind = row.kind ?: return row
    val time = row.stamp(System.currentTimeMillis(), ZoneId.systemDefault()).takeIf { row.time.isNotBlank() }
    val match = suggest(kind, parseCents(row.amount), time, row.note, null) ?: return row
    val scene = match.scene?.name.orEmpty()
    return row.copy(type = match.tag.name, scene = scene, suggestedType = match.tag.name, suggestedScene = scene,
        habitSuggestion = true)
}

internal fun similarBillRows(before: BillRow, revised: BillRow, rows: List<BillRow>): List<String> {
    if (before.kind == null || before.kind != revised.kind || before.type == revised.type && before.scene == revised.scene) {
        return emptyList()
    }
    val cents = parseCents(before.amount)
    val time = billMinute(before.time)
    val merchant = merchantKey(before.note)
    return rows.filter { candidate ->
        val other = parseCents(candidate.amount)
        val clock = billMinute(candidate.time)
        val sameAmount = cents != null && other != null && amountClose(cents, other)
        val sameTime = time != null && clock != null && timeClose(time, clock)
        val otherMerchant = merchantKey(candidate.note)
        val merchantMatches = sameMerchant(merchant, otherMerchant)
        candidate.id != before.id && candidate.kind == before.kind && !candidate.alreadyImported &&
            !candidate.reviewed && (candidate.type != revised.type || candidate.scene != revised.scene) &&
            (merchantMatches && (sameAmount || sameTime) ||
                (merchant.isEmpty() || otherMerchant.isEmpty()) && sameAmount && sameTime &&
                before.type.isNotBlank() && candidate.type == before.type && candidate.scene == before.scene)
    }.map(BillRow::id)
}

private fun billMinute(value: String): Int? = runCatching { java.time.LocalTime.parse(value) }.getOrNull()
    ?.let { it.hour * 60 + it.minute }
