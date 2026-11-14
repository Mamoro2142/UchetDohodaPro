package com.example.uchetdohodapro.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BackupTest {
    private val snapshot = BackupSnapshot(
        listOf(TariffEntity(picksRate = 999.0)),
        listOf(WorkDayEntity(id = 7, date = "2026-10-24", picks = 1.234, weight = null,
            boxes = 0.0, picksRate = 2.5, shiftId = 9)),
        listOf(ShiftEntity(9, "01.10.2026", "31.10.2026", false))
    )

    @Test fun roundTripKeepsHistoricalRatesAndMissingMeasurements() {
        val result = BackupFormat.decode(BackupFormat.encode(snapshot))
        assertEquals(snapshot, result)
        assertNull(result.days.single().weight)
        assertEquals(0.0, result.days.single().boxes!!, 0.0)
        assertEquals(3.085, result.days.single().calculateIncome(), 0.00000001)
    }

    @Test fun lastSavedTimestampSurvivesBackup() {
        val data = snapshot.copy(tariffs = listOf(TariffEntity(savedAt = 1792832400000)))
        assertEquals(data, BackupFormat.decode(BackupFormat.encode(data)))
        assertNull(BackupFormat.decode(BackupFormat.encode(snapshot)).tariffs.single().savedAt)
    }

    @Test fun legacyBackupKeepsUnknownSaveDate() {
        val envelope = JSONObject(BackupFormat.encode(snapshot))
        val payload = JSONObject(envelope.getString("payload"))
        payload.getJSONArray("tariffs").getJSONObject(0).remove("savedAt")
        val text = payload.toString()
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
        envelope.put("payload", text).put("sha256", hash).put("formatVersion", 1)
        assertEquals(snapshot, BackupFormat.decode(envelope.toString()))
    }

    @Test fun damagedAndNewerFilesAreRejected() {
        val valid = BackupFormat.encode(snapshot)
        listOf(
            "not json",
            JSONObject(valid).put("formatVersion", 99).toString(),
            JSONObject(valid).put("sha256", "broken").toString(),
            JSONObject(valid).put("applicationId", "other").toString()
        ).forEach { text -> assertTrue(runCatching { BackupFormat.decode(text) }.isFailure) }
    }

    @Test fun invalidLinksDuplicateIdsAndNonFiniteNumbersAreRejected() {
        listOf(
            snapshot.copy(shifts = emptyList()),
            snapshot.copy(days = snapshot.days + snapshot.days),
            snapshot.copy(days = listOf(snapshot.days.single().copy(picks = Double.NaN))),
            snapshot.copy(days = listOf(snapshot.days.single().copy(date = "2026-02-30")))
        ).forEach { data -> assertTrue(runCatching { BackupFormat.encode(data) }.isFailure) }
    }

    @Test fun restoreReplacesExistingDatabaseWithoutDuplicatesAndKeepsIds() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WorkDatabase::class.java).build()
        try {
            val dao = db.workDao()
            dao.insertWorkDay(WorkDayEntity(date = "2026-01-01"))
            val repository = BackupRepository(db)
            repository.restore(snapshot)
            repository.restore(snapshot)
            assertEquals(snapshot, BackupFormat.decode(repository.export()))
            assertTrue(dao.insertWorkDay(WorkDayEntity(date = "2026-11-01")) > 7)
        } finally { db.close() }
    }

    @Test fun tariffMigrationsPreserveAllOldVersionsAndUnknownSaveDate() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        for (version in 1..4) {
            val name = "tariff-migration-$version.db"
            context.deleteDatabase(name)
            context.openOrCreateDatabase(name, 0, null).use { old ->
                val savedColumn = if (version >= 4) ", savedAt INTEGER" else ""
                old.execSQL("CREATE TABLE tariffs (id INTEGER NOT NULL PRIMARY KEY, picksRate REAL NOT NULL, weightRate REAL NOT NULL, volumeRate REAL NOT NULL, boxesRate REAL NOT NULL, hoursRate REAL NOT NULL$savedColumn)")
                val shiftColumn = if (version >= 3) ", shiftId INTEGER" else ""
                old.execSQL("CREATE TABLE work_days (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, date TEXT NOT NULL, picks REAL, weight REAL, volume REAL, boxes REAL, hours REAL, picksRate REAL NOT NULL, weightRate REAL NOT NULL, volumeRate REAL NOT NULL, boxesRate REAL NOT NULL, hoursRate REAL NOT NULL$shiftColumn)")
                old.execSQL("INSERT INTO tariffs (id, picksRate, weightRate, volumeRate, boxesRate, hoursRate) VALUES (1, 99, 0, 0, 0, 0)")
                old.execSQL("INSERT INTO work_days (id, date, picks, picksRate, weightRate, volumeRate, boxesRate, hoursRate) VALUES (7, '2026-10-24', 1.234, 2.5, 0, 0, 0, 0)")
                if (version >= 2) {
                    old.execSQL("CREATE TABLE shifts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, startDate TEXT NOT NULL, endDate TEXT, isActive INTEGER NOT NULL)")
                    old.execSQL("INSERT INTO shifts VALUES (9, '01.10.2026', NULL, 1)")
                    if (version >= 3) old.execSQL("UPDATE work_days SET shiftId = 9")
                }
                old.version = version
            }
            val db = Room.databaseBuilder(context, WorkDatabase::class.java, name)
                .addMigrations(WorkDatabase.MIGRATION_1_2, WorkDatabase.MIGRATION_2_3, WorkDatabase.MIGRATION_3_4, WorkDatabase.MIGRATION_4_5).build()
            try {
                val dao = db.workDao()
                assertNull(dao.backupTariffs().single().savedAt)
                val day = dao.backupDays().single()
                assertEquals(3.085, day.calculateIncome(), 0.00000001)
                assertNull(day.weight)
                assertEquals(if (version >= 3) 9L else null, day.shiftId)
                val timestamp = 1792832400000L
                dao.saveTariffs(TariffEntity(picksRate = 1000.0, savedAt = timestamp))
                dao.updateWorkDay(day.copy(picks = 2.0))
                assertEquals(5.0, dao.backupDays().single().calculateIncome(), 0.0)
                assertEquals(timestamp, dao.backupTariffs().single().savedAt)
            } finally { db.close(); context.deleteDatabase(name) }
        }
    }

    @Test fun operationAmountsUseExactCentsAndRejectInvalidInputs() {
        assertEquals(150L, parseOperationMinor("1.5"))
        assertEquals(150L, parseOperationMinor("1,5"))
        assertEquals(25L, parseOperationMinor("0,25"))
        assertEquals(100075L, parseOperationMinor("1000.75"))
        listOf("0", "-1", "NaN", "Infinity", "1.234", "1,2,3", "922337203685477581").forEach {
            assertNull(parseOperationMinor(it))
        }
    }

    @Test fun financialCrudAndShiftDeletionNeverChangeDayIncome() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WorkDatabase::class.java).build()
        try {
            val repository = BackupRepository(db)
            repository.restore(snapshot)
            val dao = db.workDao()
            val originalDay = dao.backupDays().single()
            dao.saveMoneyOperation(MoneyOperationEntity(shiftId = 9, date = "2026-10-24", type = ADVANCE, amountMinor = 12550))
            dao.saveMoneyOperation(MoneyOperationEntity(shiftId = 9, date = "2026-10-24", type = FINE, amountMinor = 525))
            val operations = dao.backupMoneyOperations()
            assertEquals(125.50, financeTotal(operations, ADVANCE), 0.0)
            assertEquals(5.25, financeTotal(operations, FINE), 0.0)
            assertEquals(originalDay, dao.backupDays().single())
            val copy = BackupFormat.decode(repository.export())
            repository.restore(copy)
            assertEquals(operations, dao.backupMoneyOperations())
            val advance = operations.first { it.type == ADVANCE }
            dao.saveMoneyOperation(advance.copy(amountMinor = 5025))
            dao.deleteMoneyOperation(operations.first { it.type == FINE }.id)
            assertEquals(50.25, financeTotal(dao.backupMoneyOperations(), ADVANCE), 0.0)
            assertEquals(0.0, financeTotal(dao.backupMoneyOperations(), FINE), 0.0)
            assertTrue(runCatching { dao.saveMoneyOperation(advance.copy(amountMinor = -1)) }.isFailure)
            assertTrue(runCatching { dao.saveMoneyOperation(advance.copy(shiftId = 888)) }.isFailure)
            dao.deleteShiftSafely(9)
            assertEquals(originalDay.copy(shiftId = null), dao.backupDays().single())
            assertNull(dao.backupMoneyOperations().single().shiftId)
            assertEquals(50.25, financeTotal(dao.backupMoneyOperations(), ADVANCE), 0.0)
            assertEquals(1, BackupFormat.decode(repository.export()).operations.size)
        } finally { db.close() }
    }

    @Test fun versionTwoBackupRestoresWithoutInventedOperations() {
        val envelope = JSONObject(BackupFormat.encode(snapshot))
        val payload = JSONObject(envelope.getString("payload"))
        payload.remove("operations")
        val text = payload.toString()
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
        envelope.put("payload", text).put("sha256", hash).put("formatVersion", 2)
        assertEquals(snapshot, BackupFormat.decode(envelope.toString()))
    }

    @Test fun insertionFailureRollsBackAllTables() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WorkDatabase::class.java).build()
        try {
            val repository = BackupRepository(db)
            repository.restore(snapshot)
            db.workDao().saveMoneyOperation(MoneyOperationEntity(shiftId = 9, date = "2026-10-24", type = ADVANCE, amountMinor = 1000))
            val before = repository.export()
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_restore BEFORE INSERT ON work_days BEGIN SELECT RAISE(ABORT, 'test failure'); END")
            assertTrue(runCatching { repository.restore(snapshot.copy(tariffs = emptyList())) }.isFailure)
            assertEquals(before, repository.export())
        } finally { db.close() }
    }
}
