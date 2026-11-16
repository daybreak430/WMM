package com.wmm.ledger.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LedgerHabitTest {
    private val lunch = Tag("meal", Kind.EXPENSE.name, "午餐")
    private val drink = Tag("drink", Kind.EXPENSE.name, "咖啡")
    private val office = Scene("office", "工作日")
    private val monday = LocalDate.of(2026, 1, 5)

    @Test fun learnsOnlyFromRepeatedUnambiguousCombinations() {
        val trail = List(3) { index -> record(index.toLong(), lunch, "街角食堂", "area") }
        val habits = LedgerHabits(LedgerSnapshot(entries = trail, tags = listOf(lunch, drink), scenes = listOf(office)), ZoneOffset.UTC)
        assertEquals(office, habits.suggest(Kind.EXPENSE, null, noon(4), "", "area")?.scene)
        assertEquals(lunch, habits.suggest(Kind.EXPENSE, 1850, noon(4), "", null)?.tag)
        assertEquals(lunch, habits.suggest(Kind.EXPENSE, null, null, "街角食堂：工作餐", null)?.tag)
        assertNull(habits.suggest(Kind.EXPENSE, null, noon(4), "", "different"))
        assertNull(habits.suggest(Kind.INCOME, 1850, noon(4), "", null))
        val ambiguous = trail + List(3) { index -> record(index.toLong(), drink, "另一家店", "area") }
        assertNull(LedgerHabits(LedgerSnapshot(entries = ambiguous, tags = listOf(lunch, drink), scenes = listOf(office)),
            ZoneOffset.UTC).suggest(Kind.EXPENSE, 1850, noon(4), "", "area"))
        val fewDays = List(3) { record(0, lunch, "街角食堂", "area").copy(id = "same-$it") }
        assertNull(LedgerHabits(LedgerSnapshot(entries = fewDays, tags = listOf(lunch), scenes = listOf(office)),
            ZoneOffset.UTC).suggest(Kind.EXPENSE, 1850, noon(4), "街角食堂", "area"))
    }

    @Test fun categorizationEditsSuggestOnlyUnreviewedSimilarRows() {
        val first = BillRow("first", Kind.EXPENSE, "18.50", "2026-01-05", "12:15", "餐饮", "", "街角食堂")
        val revised = first.copy(type = "午餐", scene = "工作日")
        val similar = first.copy(id = "similar", date = "2026-01-06", amount = "19.00")
        val different = first.copy(id = "different", note = "滨江食堂")
        val reviewed = similar.copy(id = "reviewed", reviewed = true)
        val imported = similar.copy(id = "imported", alreadyImported = true)
        assertEquals(listOf("similar"), similarBillRows(first, revised, listOf(first, similar, different, reviewed, imported)))
        assertEquals(emptyList<String>(), similarBillRows(first, first.copy(note = "备注更新"), listOf(similar)))
    }

    private fun record(offset: Long, tag: Tag, merchant: String, area: String) =
        Entry("${tag.id}-$offset", Kind.EXPENSE.name, 1850, tag.id, null, merchant, noon(offset),
            monday.plusDays(offset).toString(), office.id, area)

    private fun noon(offset: Long): Long =
        monday.plusDays(offset).atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC).toEpochMilli()
}
