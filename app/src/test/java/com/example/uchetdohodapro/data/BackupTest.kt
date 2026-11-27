package com.example.uchetdohodapro.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
                .addMigrations(WorkDatabase.MIGRATION_1_2, WorkDatabase.MIGRATION_2_3, WorkDatabase.MIGRATION_3_4, WorkDatabase.MIGRATION_4_5, WorkDatabase.MIGRATION_5_6, WorkDatabase.MIGRATION_6_7, WorkDatabase.MIGRATION_7_8).build()
            try {
                val dao = db.workDao()
                assertNull(dao.backupTariffs().single().savedAt)
                val day = dao.backupDays().single()
                assertEquals(3.085, day.calculateIncome(), 0.00000001)
                assertNull(day.weight)
                assertEquals(if (version >= 3) 9L else null, day.shiftId)
                assertTrue(dao.backupShifts().all { it.plannedWorkDays == null })
                val timestamp = 1792832400000L
                dao.saveTariffs(TariffEntity(picksRate = 1000.0, savedAt = timestamp))
                dao.updateWorkDay(day.copy(picks = 2.0))
                assertEquals(5.0, dao.backupDays().single().calculateIncome(), 0.0)
                assertEquals(timestamp, dao.backupTariffs().single().savedAt)
            } finally { db.close(); context.deleteDatabase(name) }
        }
    }

    @Test fun decimalCommaAndPointHaveOneStrictInterpretation() {
        mapOf("1.5" to 1.5, "1,5" to 1.5, "0,25" to 0.25, "1000.75" to 1000.75, " 1,5 " to 1.5, ".5" to 0.5).forEach { (input, expected) ->
            assertEquals(expected, parseNonNegativeNumber(input)!!, 0.0)
        }
        listOf("", " ", "NaN", "Infinity", "-1", "+1", "1e3", "1.", "1,2.3", "9".repeat(400), "0." + "0".repeat(400) + "1").forEach {
            assertNull(parseNonNegativeNumber(it))
        }
        assertEquals("0.0000001", editableNumber(0.0000001))
        assertEquals(1.23456789, parseNonNegativeNumber(editableNumber(1.23456789))!!, 0.0)
    }

    @Test fun commaPreviewEqualsStoredIncomeAndBlankValuesStayNull() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WorkDatabase::class.java).build()
        try {
            val tariff = TariffEntity(picksRate = 2.5, weightRate = 4.0, volumeRate = 1.0, boxesRate = 0.5, hoursRate = 10.0)
            val draft = dayFromInput("2026-11-14", listOf("1,5", "0,25", "", "0", "2"), tariff)
            assertEquals(24.75, draft.calculateIncome(), 0.0)
            val dao = db.workDao()
            val id = dao.insertWorkDay(draft)
            assertEquals(draft.copy(id = id), dao.getWorkDay(id))
            assertNull(dao.getWorkDay(id)!!.volume)
            assertEquals(0.0, dao.getWorkDay(id)!!.boxes!!, 0.0)
            dao.saveTariffs(TariffEntity(picksRate = 999.0))
            assertEquals(draft.calculateIncome(), dao.getWorkDay(id)!!.calculateIncome(), 0.0)
            val restored = BackupFormat.decode(BackupRepository(db).export())
            assertNull(restored.days.single().volume)
            assertEquals(24.75, restored.days.single().calculateIncome(), 0.0)
        } finally { db.close() }
    }

    @Test fun daoRejectsInvalidNumbersWithoutChangingSavedData() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WorkDatabase::class.java).build()
        try {
            val repo = BackupRepository(db)
            repo.restore(snapshot)
            val before = repo.export()
            val dao = db.workDao()
            assertTrue(runCatching { dao.saveTariffs(TariffEntity(picksRate = Double.NaN)) }.isFailure)
            assertTrue(runCatching { dao.insertWorkDay(WorkDayEntity(date = "2026-11-01", picks = Double.POSITIVE_INFINITY)) }.isFailure)
            assertTrue(runCatching { dao.updateWorkDay(snapshot.days.single().copy(picks = Double.MAX_VALUE, picksRate = Double.MAX_VALUE)) }.isFailure)
            assertEquals(before, repo.export())
        } finally { db.close() }
    }

    @Test fun strictDatesAndUniqueDayCountDoNotRewriteHistory() {
        assertNull(parseStoredDate("31.02.2026"))
        assertNull(parseStoredDate("2026-10-24junk"))
        assertEquals(parseStoredDate("24.10.2026"), parseStoredDate("2026-10-24"))
        val day = snapshot.days.single()
        assertEquals(1, uniqueWorkDayCount(listOf(day, day.copy(id = 8, date = "24.10.2026"))))
        assertTrue(dayBelongsToShift("2026-10-01", snapshot.shifts.single()))
        assertTrue(dayBelongsToShift("2026-10-31", snapshot.shifts.single()))
        assertFalse(dayBelongsToShift("2026-11-01", snapshot.shifts.single()))
    }

    @Test fun concurrentShiftCreationAllowsOnlyOneActiveShift() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WorkDatabase::class.java).build()
        try {
            val dao = db.workDao()
            val results = coroutineScope {
                (1..20).map { async { runCatching { dao.insertShift(ShiftEntity(startDate = "01.11.2026")) } } }.awaitAll()
            }
            assertEquals(1, results.count { it.isSuccess })
            assertEquals(1, dao.backupShifts().size)
            val active = dao.backupShifts().single()
            dao.updateShift(active.copy(endDate = "02.11.2026", isActive = false))
            assertTrue(dao.insertShift(ShiftEntity(startDate = "03.11.2026")) > active.id)
        } finally { db.close() }
    }

    @Test fun bindingChecksBoundsAndDeletionPreservesDaysAndRates() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WorkDatabase::class.java).build()
        try {
            BackupRepository(db).restore(snapshot)
            val dao = db.workDao()
            val original = dao.backupDays().single()
            assertTrue(runCatching { dao.insertWorkDay(original.copy(id = 0)) }.isFailure)
            assertTrue(runCatching { dao.assignWorkDayToShift(original.id, 999) }.isFailure)
            assertTrue(runCatching { dao.insertWorkDay(original.copy(id = 0, date = "2026-11-01")) }.isFailure)
            val next = dao.insertShift(ShiftEntity(startDate = "01.11.2026", endDate = "30.11.2026", isActive = false))
            assertTrue(runCatching { dao.assignWorkDayToShift(original.id, next) }.isFailure)
            assertTrue(runCatching { dao.updateShift(snapshot.shifts.single().copy(startDate = "25.10.2026")) }.isFailure)
            assertEquals(snapshot.shifts.single(), dao.getShift(9))
            val id = dao.insertWorkDay(original.copy(id = 0, date = "2026-11-05", shiftId = null))
            dao.assignWorkDayToShift(id, next)
            val assigned = dao.getWorkDay(id)!!
            assertEquals(original.calculateIncome(), assigned.calculateIncome(), 0.0)
            dao.deleteShiftSafely(next)
            assertEquals(assigned.copy(shiftId = null), dao.getWorkDay(id))
            assertEquals(original, dao.getWorkDay(original.id))
        } finally { db.close() }
    }

    @Test fun historicalDateMismatchCanBeEditedWithoutRepricingOrMoving() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WorkDatabase::class.java).build()
        try {
            val legacy = snapshot.days.single().copy(date = "2026-09-30")
            BackupRepository(db).restore(snapshot.copy(days = listOf(legacy)))
            db.workDao().updateWorkDay(legacy.copy(picks = 2.0))
            assertEquals(legacy.copy(picks = 2.0), db.workDao().getWorkDay(legacy.id))
        } finally { db.close() }
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

    @Test fun operationOrphanedByShiftDeletionRemainsEditableWithoutAutomaticRebinding() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WorkDatabase::class.java).build()
        try {
            BackupRepository(db).restore(snapshot)
            val dao = db.workDao()
            dao.saveMoneyOperation(MoneyOperationEntity(shiftId = 9, date = "2026-10-24", type = ADVANCE, amountMinor = 100))
            val bound = dao.backupMoneyOperations().single()
            assertTrue(runCatching { dao.saveMoneyOperation(bound.copy(shiftId = null)) }.isFailure)
            dao.deleteShiftSafely(9)
            val orphan = dao.backupMoneyOperations().single()
            dao.saveMoneyOperation(orphan.copy(amountMinor = 250, comment = "Исправлено"))
            assertEquals(orphan.copy(amountMinor = 250, comment = "Исправлено"), dao.backupMoneyOperations().single())
            assertTrue(runCatching { dao.saveMoneyOperation(orphan.copy(id = 0)) }.isFailure)
            assertEquals(3.085, dao.backupDays().single().calculateIncome(), 0.00000001)
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
    @Test fun pausesSurviveBackupAndClosingShift() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WorkDatabase::class.java).build()
        try {
            val dao = db.workDao()
            val start = java.time.LocalDate.now().minusDays(12)
            val id = dao.insertShift(ShiftEntity(startDate = start.toString(), isActive = true))
            dao.togglePause(id, start.plusDays(2))
            assertTrue(runCatching { dao.insertWorkDay(WorkDayEntity(date = start.plusDays(3).toString(), shiftId = id)) }.isFailure)
            dao.togglePause(id, start.plusDays(5))
            dao.insertWorkDay(WorkDayEntity(date = start.plusDays(5).toString(), shiftId = id))
            dao.togglePause(id, start.plusDays(7))
            dao.updateShift(dao.getShift(id)!!.copy(isActive = false, endDate = start.plusDays(9).toString()))
            val pauses = dao.backupPauses().sortedBy { it.startDate }
            assertEquals(2, pauses.size)
            assertEquals(start.plusDays(10).toString(), pauses.last().resumeDate)
            val repo = BackupRepository(db)
            val saved = BackupFormat.decode(repo.export())
            repo.restore(saved)
            assertEquals(saved, BackupFormat.decode(repo.export()))
        } finally { db.close() }
    }
    @Test fun historicalPauseAndTomorrowResume() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WorkDatabase::class.java).build()
        try {
            val dao = db.workDao()
            val today = java.time.LocalDate.now()
            val id = dao.insertShift(ShiftEntity(startDate = today.minusDays(5).toString(), isActive = true))
            dao.insertWorkDay(WorkDayEntity(date = today.minusDays(2).toString(), shiftId = id))
            dao.togglePause(id, today.minusDays(3))
            assertEquals(today.minusDays(2).toString(), dao.backupPauses().single().resumeDate)
            dao.togglePause(id, today)
            dao.togglePause(id, today.plusDays(1))
            assertEquals(today.plusDays(1).toString(), dao.backupPauses().maxByOrNull { it.startDate }!!.resumeDate)
        } finally { db.close() }
    }
    @Test fun activeShiftPlanCanBeSetChangedReducedAndCleared() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WorkDatabase::class.java).build()
        try {
            val dao = db.workDao()
            val today = java.time.LocalDate.now()
            val id = dao.insertShift(ShiftEntity(startDate = today.minusDays(3).toString()))
            dao.insertWorkDay(WorkDayEntity(date = today.minusDays(3).toString(), shiftId = id, picks = 2.0, picksRate = 5.0))
            dao.insertWorkDay(WorkDayEntity(date = today.minusDays(2).toString(), shiftId = id, picks = 3.0, picksRate = 5.0))
            val original = dao.getShift(id)!!
            val days = dao.backupDays()
            assertNull(original.plannedWorkDays)
            for (plan in listOf(15, 20, 1, null)) {
                dao.setShiftPlan(id, plan)
                assertEquals(original.copy(plannedWorkDays = plan), dao.getShift(id))
                assertEquals(days, dao.backupDays())
            }
            assertTrue(runCatching { dao.setShiftPlan(id, -1) }.isFailure)
            assertNull(dao.getShift(id)!!.plannedWorkDays)
        } finally { db.close() }
    }

    @Test fun planAndPausesSurviveOldAndNewBackupFormats() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WorkDatabase::class.java).build()
        try {
            val dao = db.workDao()
            val start = java.time.LocalDate.now().minusDays(10)
            val id = dao.insertShift(ShiftEntity(startDate = start.toString(), plannedWorkDays = 15))
            dao.insertWorkDay(WorkDayEntity(date = start.toString(), shiftId = id, picks = 2.0, picksRate = 5.0))
            dao.togglePause(id, start.plusDays(1))
            dao.togglePause(id, start.plusDays(3))
            dao.setShiftPlan(id, 20)
            assertEquals(20, dao.getShift(id)!!.plannedWorkDays)
            assertTrue(runCatching { dao.setShiftPlan(id, 0) }.isFailure)
            val repo = BackupRepository(db)
            val encoded = repo.export()
            val restored = BackupFormat.decode(encoded)
            assertEquals(20, restored.shifts.single().plannedWorkDays)
            assertEquals(1, restored.pauses.size)
            dao.setShiftPlan(id, null)
            assertNull(dao.getShift(id)!!.plannedWorkDays)
            repo.restore(restored)
            assertEquals(restored, BackupFormat.decode(repo.export()))
            val old = JSONObject(encoded)
            val payload = JSONObject(old.getString("payload"))
            payload.getJSONArray("shifts").getJSONObject(0).remove("plannedWorkDays")
            val body = payload.toString()
            val hash = java.security.MessageDigest.getInstance("SHA-256").digest(body.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
            old.put("payload", body).put("sha256", hash).put("formatVersion", 4)
            val legacy = BackupFormat.decode(old.toString())
            assertNull(legacy.shifts.single().plannedWorkDays)
            assertEquals(restored.pauses, legacy.pauses)
            assertEquals(restored.days, legacy.days)
        } finally { db.close() }
    }

    @Test fun migrateVersion6PreservesPausesOperationsAndDays() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "plan-migration-6.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, 0, null).use { old ->
            old.execSQL("CREATE TABLE tariffs (id INTEGER NOT NULL PRIMARY KEY, picksRate REAL NOT NULL, weightRate REAL NOT NULL, volumeRate REAL NOT NULL, boxesRate REAL NOT NULL, hoursRate REAL NOT NULL, savedAt INTEGER)")
            old.execSQL("CREATE TABLE work_days (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, date TEXT NOT NULL, picks REAL, weight REAL, volume REAL, boxes REAL, hours REAL, picksRate REAL NOT NULL, weightRate REAL NOT NULL, volumeRate REAL NOT NULL, boxesRate REAL NOT NULL, hoursRate REAL NOT NULL, shiftId INTEGER)")
            old.execSQL("CREATE TABLE shifts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, startDate TEXT NOT NULL, endDate TEXT, isActive INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE money_operations (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, shiftId INTEGER, date TEXT NOT NULL, type TEXT NOT NULL, amountMinor INTEGER NOT NULL, comment TEXT NOT NULL, FOREIGN KEY(shiftId) REFERENCES shifts(id) ON UPDATE NO ACTION ON DELETE SET NULL)")
            old.execSQL("CREATE INDEX index_money_operations_shiftId ON money_operations(shiftId)")
            old.execSQL("CREATE TABLE shift_pauses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, shiftId INTEGER NOT NULL, startDate TEXT NOT NULL, resumeDate TEXT, FOREIGN KEY(shiftId) REFERENCES shifts(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            old.execSQL("CREATE INDEX index_shift_pauses_shiftId ON shift_pauses(shiftId)")
            old.execSQL("INSERT INTO shifts VALUES (9, '2026-10-01', NULL, 1)")
            old.execSQL("INSERT INTO work_days (id,date,picks,picksRate,weightRate,volumeRate,boxesRate,hoursRate,shiftId) VALUES (7,'2026-10-24',2,5,0,0,0,0,9)")
            old.execSQL("INSERT INTO money_operations VALUES (1,9,'2026-10-24','advance',1000,'')")
            old.execSQL("INSERT INTO shift_pauses VALUES (1,9,'2026-10-25','2026-10-27')")
            old.version = 6
        }
        val db = Room.databaseBuilder(context, WorkDatabase::class.java, name).addMigrations(WorkDatabase.MIGRATION_6_7, WorkDatabase.MIGRATION_7_8).build()
        try {
            val dao = db.workDao()
            assertNull(dao.getShift(9)!!.plannedWorkDays)
            assertEquals(10.0, dao.getWorkDay(7)!!.calculateIncome(), 0.0)
            assertEquals(1000L, dao.backupMoneyOperations().single().amountMinor)
            assertEquals("2026-10-27", dao.backupPauses().single().resumeDate)
            dao.setShiftPlan(9, 15)
            assertEquals(15, dao.getShift(9)!!.plannedWorkDays)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun tariffHistoryIsAtomicAndRestoresWithoutDuplicates() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WorkDatabase::class.java).build()
        try {
            val dao = db.workDao()
            dao.saveTariffs(TariffEntity(picksRate = 1.25))
            dao.saveTariffs(TariffEntity(picksRate = 2.0, savedAt = 100L))
            dao.saveTariffs(TariffEntity(picksRate = 2.0, savedAt = 101L))
            dao.saveTariffs(TariffEntity(picksRate = 3.0, savedAt = 200L))
            val history = dao.backupTariffHistory()
            assertEquals(listOf(1.25, 2.0), history.map { it.picksRate })
            assertNull(history.first().savedAt)
            assertEquals(101L, history.last().savedAt)
            val repo = BackupRepository(db)
            val encoded = repo.export()
            val data = BackupFormat.decode(encoded)
            assertEquals(history, data.tariffHistory)
            repo.restore(data); repo.restore(data)
            assertEquals(data, BackupFormat.decode(repo.export()))
            val legacy = JSONObject(encoded)
            val body = JSONObject(legacy.getString("payload")).apply { remove("tariffHistory") }.toString()
            val hash = java.security.MessageDigest.getInstance("SHA-256").digest(body.toByteArray()).joinToString("") { "%02x".format(it) }
            legacy.put("payload", body).put("sha256", hash).put("formatVersion", 5)
            assertEquals(data.copy(tariffHistory = emptyList()), BackupFormat.decode(legacy.toString()))
        } finally { db.close() }
    }

}
