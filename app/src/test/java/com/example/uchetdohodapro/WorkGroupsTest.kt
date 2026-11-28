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
class WorkGroupsTest {
    private val section = DayGroup(1, "Раздел", 31, listOf(1200.0, 8000.0, 25.0, 300.0, 12.0), listOf(1.5, .15, 20.0, 2.0, 150.0))
    private val adapto = DayGroup(2, "Адапто", 1, listOf(1500.0, null, null, null, null), listOf(2.5, 0.0, 0.0, 0.0, 0.0))
    private val shift = ShiftEntity(9, "2026-11-01", "2026-11-30", false, 15)

    @Test fun multipleGroupsCountOnceAndUseSeparateRates() {
        val day = withDayGroups(WorkDayEntity(id = 7, date = "2026-11-20", shiftId = 9), listOf(section, adapto))
        assertEquals(9650.0, day.calculateIncome(), 0.00001)
        assertEquals(2700.0, day.picks!!, 0.0)
        assertEquals(5550.0, day.metricIncome(0), 0.00001)
        assertEquals(1, uniqueWorkDayCount(listOf(day)))
        assertEquals(listOf(section, adapto), day.groups())
        assertNull(day.groups()[1].weightValue())
        assertEquals(9650.0, personalRecords(listOf(day), listOf(shift)).bestDay!!.calculateIncome(), 0.00001)
    }

    private fun DayGroup.weightValue() = values[1]

    @Test fun legacyDayPreservesExactTariffsAndNulls() {
        val old = WorkDayEntity(id = 3, date = "2026-11-18", picks = 1.234, boxes = 0.0, picksRate = 2.5)
        val grouped = withDayGroups(old, old.groups())
        assertEquals(old.calculateIncome(), grouped.calculateIncome(), 0.00000001)
        assertNull(grouped.weight)
        assertEquals(0.0, grouped.boxes!!, 0.0)
        assertEquals(old.picksRate, grouped.groups().single().rates[0], 0.0)
    }

    @Test fun groupMaskAndDuplicateGroupIdsAreValidated() {
        val base = WorkDayEntity(date = "2026-11-20")
        assertTrue(runCatching { withDayGroups(base, listOf(adapto.copy(values = listOf(1.0, 2.0, null, null, null)))) }.isFailure)
        assertTrue(runCatching { withDayGroups(base, listOf(adapto, adapto)) }.isFailure)
        assertTrue(runCatching { withDayGroups(base, listOf(adapto.copy(values = listOf(Double.MAX_VALUE, null, null, null, null)))) }.isFailure)
    }

    @Test fun saveEditAndBackupPreserveGroupHistory() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WorkDatabase::class.java).build()
        try {
            val dao = db.workDao()
            dao.saveTariffs(TariffEntity(picksRate = 1.5))
            dao.saveGroup(WorkGroupEntity(0, "Адапто", 1, writeRates(adapto.rates), null))
            val group = dao.backupGroups().single()
            assertEquals(2L, group.id)
            val id = dao.insertWorkDay(withDayGroups(WorkDayEntity(date = "2026-11-20"), listOf(section, adapto)))
            dao.saveGroup(group.copy(ratesJson = writeRates(listOf(3.0,0.0,0.0,0.0,0.0))))
            val changed = dao.backupGroups().single()
            assertEquals(2.5, changed.history().single().rates[0], 0.0)
            assertEquals(9650.0, dao.getWorkDay(id)!!.calculateIncome(), 0.00001)
            val old = dao.getWorkDay(id)!!
            dao.updateWorkDay(withDayGroups(old, listOf(section, adapto.copy(values = listOf(2000.0, null, null, null, null)))))
            assertEquals(10900.0, dao.getWorkDay(id)!!.calculateIncome(), 0.00001)
            assertEquals(1, dao.backupDays().size)
            val repo = BackupRepository(db)
            val backup = BackupFormat.decode(repo.export())
            assertEquals(2.5, backup.days.single().groups()[1].rates[0], 0.0)
            repo.restore(backup); repo.restore(backup)
            assertEquals(1, dao.backupDays().size)
            assertEquals(10900.0, dao.getWorkDay(id)!!.calculateIncome(), 0.00001)
            assertEquals(2.5, dao.backupGroups().single().history().single().rates[0], 0.0)
            assertTrue(runCatching { dao.insertWorkDay(WorkDayEntity(date = "2026-11-20")) }.isFailure)
        } finally { db.close() }
    }

    @Test fun backupRejectsInconsistentAggregateAndMissingGroup() {
        val group = WorkGroupEntity(2,"Адапто",1,writeRates(adapto.rates),null)
        val day = withDayGroups(WorkDayEntity(id = 7, date = "2026-11-20", shiftId = 9), listOf(section, adapto))
        val snapshot = BackupSnapshot(emptyList(), listOf(day), listOf(shift), groups = listOf(group))
        val restored = BackupFormat.decode(BackupFormat.encode(snapshot))
        assertEquals(day.groups(), restored.days.single().groups())
        assertTrue(runCatching { BackupFormat.encode(snapshot.copy(groups = emptyList())) }.isFailure)
        assertTrue(runCatching { BackupFormat.encode(snapshot.copy(days = listOf(day.copy(picks = 1.0)))) }.isFailure)
    }
}
