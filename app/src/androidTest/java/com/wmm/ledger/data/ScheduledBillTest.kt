package com.wmm.ledger.data

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduledBillTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val tag = Tag("scheduled-rent", Kind.EXPENSE.name, "房租")

    @Test fun calendarRepeatsFromTheOriginalDate() {
        val first = LocalDate.of(2024, 1, 31)
        assertEquals(first.plusDays(1), bill(first, Period.DAY).nextDay(first))
        assertEquals(first.plusWeeks(1), bill(first, Period.WEEK).nextDay(first))
        val monthly = bill(first, Period.MONTH)
        val leapDay = LocalDate.of(2024, 2, 29)
        assertEquals(leapDay, monthly.nextDay(first))
        assertEquals(LocalDate.of(2024, 3, 31), monthly.nextDay(leapDay))
        val annual = bill(leapDay, Period.YEAR)
        assertEquals(LocalDate.of(2025, 2, 28), annual.nextDay(leapDay))
        assertEquals(LocalDate.of(2028, 2, 29), annual.nextDay(LocalDate.of(2027, 2, 28)))
    }

    @Test fun systemJobBackfillsWithoutAnActivityAndKeepsProgressInBackup() = runBlocking {
        val repository = LedgerRepository.shared(context)
        val original = repository.snapshot()
        val today = LocalDate.now()
        val first = today.minusDays(2)
        val account = Account("rent-account", AccountType.SAVINGS.name, "储蓄卡", 500_000,
            System.currentTimeMillis() - 1)
        val scheduled = bill(first, Period.DAY).copy(accountId = account.id)
        val starter = LedgerSnapshot(tags = listOf(tag), accounts = listOf(account), scheduledBills = listOf(scheduled))
        try {
            repository.replace(starter)
            assertTrue(ScheduledBillJob.update(context, active = true))
            val command = "cmd jobscheduler run -f ${context.packageName} $SCHEDULED_BILL_JOB_ID"
            val result = ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand(command),
            ).bufferedReader().use { it.readText() }
            assertTrue(result, result.contains("Running job"))
            withTimeout(15_000) {
                while (repository.snapshot().entries.size != 3) delay(100)
            }

            val posted = repository.snapshot()
            assertEquals(listOf(today, today.minusDays(1), first).map(LocalDate::toString), posted.entries.map(Entry::day))
            assertTrue(posted.entries.none(Entry::hasTime))
            assertEquals(account.baselineCents - scheduled.cents, posted.accountBalances().getValue(account.id))
            assertEquals(today.toString(), posted.scheduledBills.single().lastRecordedDay)
            val restored = Backup.decode(Backup.encode(posted).inputStream())
            assertEquals(posted.entries, restored.entries)
            assertEquals(posted.scheduledBills, restored.scheduledBills)

            repository.deleteEntry(scheduled.entryId(today))
            repository.recordScheduledBills(today = today)
            assertEquals(2, repository.snapshot().entries.size)
            repository.deleteScheduledBill(scheduled.id)
            repository.recordScheduledBills(today = today.plusDays(1))
            assertEquals(2, repository.snapshot().entries.size)
        } finally {
            repository.replace(original)
            ScheduledBillJob.update(context, original.scheduledBills.isNotEmpty())
        }
    }

    @Test fun importDistinguishesBlankTimeFromExplicitMidnight() = runBlocking {
        val repository = LedgerRepository.shared(context)
        val original = repository.snapshot()
        val date = LocalDate.now(ZoneOffset.UTC).minusDays(1).toString()
        val dateOnly = BillRow("time-date-only", Kind.EXPENSE, "8.25", date, "", tag.name, "", "")
        try {
            repository.replace(LedgerSnapshot(tags = listOf(tag)))
            repository.importBill(listOf(dateOnly, dateOnly.copy(id = "time-midnight", time = "00:00")))
            val entries = repository.snapshot().entries.associateBy(Entry::id)
            assertFalse(entries.getValue("time-date-only").hasTime)
            assertTrue(entries.getValue("time-midnight").hasTime)
        } finally {
            repository.replace(original)
        }
    }

    private fun bill(day: LocalDate, period: Period) = ScheduledBill(
        "scheduled-test", Kind.EXPENSE.name, 240_000, tag.id, null, null, "房租",
        period.name, day.toString(),
    )
}
