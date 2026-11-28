package com.example.uchetdohodapro.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CustomMetricsTest {
    private val defs = listOf(MetricDefinition("base:0", "Пики 1", 1.5),
        MetricDefinition("custom:p2", "Пики 2", 2.1), MetricDefinition("custom:p3", "Пики 3", 3.2))
    private val original = WorkGroupEntity(1, "Раздел", 31, writeRates(listOf(1.5, .15, 20.0, 2.0, 150.0)), 1)
    @Test fun independentRatesAndStableSnapshot() {
        val configured = original.withDefinitions(defs)
        assertEquals(defs, configured.definitions())
        val group = configured.newDayGroup().withMeasurements(defs.zip(listOf(1200.0, 850.0, 400.0)).map { DayMetric(it.first, it.second) })
        val day = withDayGroups(WorkDayEntity(date = "2026-11-28"), listOf(group))
        assertEquals(4865.0, day.calculateIncome(), 0.00001)
        assertEquals(1, uniqueWorkDayCount(listOf(day)))
        assertEquals(group, day.groups().single())
        val updated = configured.withDefinitions(listOf(defs[2].copy(rate = 9.0), defs[0].copy(name = "Пики новые")))
        assertEquals(2, updated.definitions().size)
        assertEquals(4865.0, day.calculateIncome(), 0.00001)
        assertEquals("Пики 2", day.groups().single().entries()[1].definition.name)
    }
    @Test fun customOnlyAndLegacySnapshots() {
        val custom = original.withDefinitions(defs.drop(1))
        assertEquals(0, custom.mask)
        validateGroup(custom)
        val entry = DayMetric(defs[1], 0.0)
        val group = custom.newDayGroup().withMeasurements(listOf(entry, DayMetric(defs[2], null)))
        val day = withDayGroups(WorkDayEntity(date = "2026-11-28"), listOf(group))
        assertNull(day.picks)
        assertEquals(0.0, day.groups().single().entries().first().value!!, 0.0)
        assertNull(day.groups().single().entries().last().value)
        val legacy = WorkDayEntity(date = "2026-11-27", picks = 1.234, picksRate = 2.5)
        assertEquals(legacy.calculateIncome(), withDayGroups(legacy, legacy.groups()).calculateIncome(), 0.0)
        assertTrue(runCatching { original.withDefinitions(listOf(defs[0], defs[1].copy(name = defs[0].name))) }.isFailure)
    }
    @Test fun groupHistoryAndBackupRoundTrip() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WorkDatabase::class.java).build()
        try {
            val dao = db.workDao()
            dao.saveTariffs(TariffEntity(picksRate = 1.5))
            dao.saveGroup(original.withDefinitions(defs))
            val group = dao.backupGroups().single()
            val entries = group.definitions().map { DayMetric(it, 100.0) }
            val id = dao.insertWorkDay(withDayGroups(WorkDayEntity(date = "2026-11-28"), listOf(group.newDayGroup().withMeasurements(entries))))
            dao.saveGroup(group.withDefinitions(listOf(defs[2].copy(rate = 8.0), defs[0])))
            assertEquals(defs, dao.backupGroups().single().history().last().definitions)
            dao.saveTariffs(TariffEntity(picksRate = 2.0, savedAt = 42L))
            assertEquals(8.0, dao.backupGroups().single().definitions().first().rate, 0.0)
            val repo = BackupRepository(db)
            val backup = BackupFormat.decode(repo.export())
            repo.restore(backup)
            assertEquals(680.0, dao.getWorkDay(id)!!.calculateIncome(), 0.00001)
            assertEquals(entries, dao.getWorkDay(id)!!.groups().single().entries())
            assertEquals(2, dao.backupGroups().single().definitions().size)
        } finally { db.close() }
    }
}
