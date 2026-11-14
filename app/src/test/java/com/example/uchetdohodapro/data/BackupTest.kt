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

    @Test fun damagedAndNewerFilesAreRejected() {
        val valid = BackupFormat.encode(snapshot)
        listOf(
            "not json",
            JSONObject(valid).put("formatVersion", 2).toString(),
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

    @Test fun insertionFailureRollsBackAllTables() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WorkDatabase::class.java).build()
        try {
            val repository = BackupRepository(db)
            repository.restore(snapshot)
            val before = repository.export()
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_restore BEFORE INSERT ON work_days BEGIN SELECT RAISE(ABORT, 'test failure'); END")
            assertTrue(runCatching { repository.restore(snapshot.copy(tariffs = emptyList())) }.isFailure)
            assertEquals(before, repository.export())
        } finally { db.close() }
    }
}
