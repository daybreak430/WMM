package com.wmm.ledger.data

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditTransferStorageTest {
    @Test fun transfersPersistEditDeleteAndRoundTripWithCreditAccounts() = runBlocking {
        val repository = LedgerRepository(isolated())
        val now = System.currentTimeMillis()
        val day = LedgerRepository.bookingDay(now)
        val bank = Account("bank", AccountType.SAVINGS.name, "银行卡", 100_000, now - 10_000)
        val credit = Account("credit", AccountType.CREDIT_CARD.name, "信用卡", -20_000, now - 10_000)
        val tag = Tag("tag", Kind.EXPENSE.name, "晚餐")
        repository.replace(LedgerSnapshot(accounts = listOf(bank, credit), tags = listOf(tag)))
        repository.saveEntry(null, Kind.EXPENSE, 3_000, tag.id, credit.id, "", now - 2_000, day)
        repository.saveTransfer(null, 5_000, bank.id, credit.id, "信用卡还款", now - 1_000, day, false)
        val saved = repository.snapshot()
        val transfer = saved.transfers.single()
        assertEquals(mapOf(bank.id to 95_000L, credit.id to -18_000L), saved.accountBalances())
        val backup = Backup.encode(saved)
        val restored = LedgerRepository(isolated())
        restored.replace(Backup.decode(backup.inputStream()))
        assertEquals(saved.transfers, restored.snapshot().transfers)
        assertEquals(saved.accountBalances(), restored.snapshot().accountBalances())

        repository.saveTransfer(transfer.id, 7_000, bank.id, credit.id, "修改还款", now - 1_000, day, true)
        assertEquals(1, repository.snapshot().transfers.size)
        assertEquals(mapOf(bank.id to 93_000L, credit.id to -16_000L), repository.snapshot().accountBalances())
        val beforeInvalid = repository.snapshot().transfers
        assertTrue(runCatching {
            repository.saveTransfer(null, 100, bank.id, bank.id, "", now, day, false)
        }.isFailure)
        assertTrue(runCatching {
            repository.saveTransfer(null, 100, bank.id, "missing", "", now, day, false)
        }.isFailure)
        assertEquals(beforeInvalid, repository.snapshot().transfers)
        repository.deleteTransfer(transfer.id)
        assertEquals(mapOf(bank.id to 100_000L, credit.id to -23_000L), repository.snapshot().accountBalances())

        val oldBackup = JSONObject(Backup.encode(repository.snapshot()).decodeToString()).put("version", 7).apply {
            remove("transfers")
        }
        assertTrue(Backup.decode(oldBackup.toString().byteInputStream()).transfers.isEmpty())
        val invalidBackup = JSONObject(backup.decodeToString()).apply {
            getJSONArray("transfers").getJSONObject(0).put("toAccountId", bank.id)
        }
        assertTrue(runCatching { Backup.decode(invalidBackup.toString().byteInputStream()) }.isFailure)
    }

    @Test fun creditCreationCalibrationAndArchivedTransferReferencesRemainConsistent() = runBlocking {
        val repository = LedgerRepository(isolated())
        repository.saveAccount(null, AccountType.HUABEI, "花呗", 12_345)
        val credit = repository.snapshot().accounts.single()
        assertEquals(-12_345L, credit.baselineCents)
        repository.calibrate(credit.id, 8_000)
        assertEquals(-8_000L, repository.snapshot().accountBalances().getValue(credit.id))
        repository.calibrate(credit.id, -2_000)
        assertEquals(2_000L, repository.snapshot().accountBalances().getValue(credit.id))

        repository.saveAccount(null, AccountType.SAVINGS, "银行卡", 100_000)
        val bank = repository.snapshot().accounts.first { it.id != credit.id }
        val now = System.currentTimeMillis()
        repository.saveTransfer(null, 1_000, bank.id, credit.id, "", now, LocalDate.now(), false)
        val transfer = repository.snapshot().transfers.single()
        repository.archiveAccount(credit.id, true)
        assertTrue(runCatching {
            repository.saveTransfer(null, 1_000, bank.id, credit.id, "", now, LocalDate.now(), false)
        }.isFailure)
        repository.saveTransfer(transfer.id, 500, bank.id, credit.id, "修改归档记录", now, LocalDate.now(), false)
        assertEquals(500L, repository.snapshot().transfers.single().cents)
        repository.deleteTransfer(transfer.id)
        assertTrue(repository.snapshot().transfers.isEmpty())
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
