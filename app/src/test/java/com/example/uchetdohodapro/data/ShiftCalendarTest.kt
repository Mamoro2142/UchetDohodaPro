package com.example.uchetdohodapro.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ShiftCalendarTest {
    private fun date(day: Int) = LocalDate.of(2026, 11, day)
    private val shift = ShiftEntity(1, "2026-11-08", plannedWorkDays = 15)
    private fun day(d: Int, id: Long = d.toLong(), shiftId: Long = 1) =
        WorkDayEntity(id = id, date = date(d).toString(), picks = 1.0, picksRate = 100.0, shiftId = shiftId)

    @Test fun distinctWorkDaysAndMonthStatuses() {
        val days = listOf(day(8), day(9), day(18), day(20), day(20, 99), day(19, 100, 2))
        val pause = ShiftPauseEntity(1, 1, date(10).toString(), date(18).toString())
        val model = ShiftCalendarModel(shift, days, listOf(pause), date(24))
        assertEquals(4, model.workCount)
        assertEquals(11, shift.plannedWorkDays!! - model.workCount)
        assertEquals(8L, model.elapsedPauseCount())
        assertEquals(1L, model.restCount())
        assertEquals(ShiftDayStatus.REST, model.status(date(19)))
        assertEquals(ShiftDayStatus.PAUSE, model.status(date(17)))
        assertEquals(ShiftDayStatus.WORK, model.status(date(18)))
        assertEquals(ShiftDayStatus.NONE, model.status(date(21)))
        assertEquals(500.0, model.monthDates(YearMonth.of(2026, 11)).sumOf { model.income(it) }, 0.0)
        assertTrue(model.canAdd(date(19)))
        assertFalse(model.canAdd(date(17)))
    }

    @Test fun newShiftAndRemovalUpdateProgress() {
        val days = listOf(day(8), day(9))
        assertEquals(2, ShiftCalendarModel(shift, days, emptyList(), date(24)).workCount)
        assertEquals(1, ShiftCalendarModel(shift, days.drop(1), emptyList(), date(24)).workCount)
        assertEquals(0, ShiftCalendarModel(shift.copy(id = 2), days, emptyList(), date(24)).workCount)
    }

    @Test fun monthBoundariesAndScheduledResume() {
        val pause = ShiftPauseEntity(1, 1, "2026-11-28", "2026-12-03")
        val model = ShiftCalendarModel(shift, emptyList(), listOf(pause), date(29))
        assertEquals(2L, model.elapsedPauseCount())
        assertEquals(ShiftDayStatus.PAUSE, model.status(LocalDate.of(2026, 12, 2)))
        assertEquals(ShiftDayStatus.NONE, model.status(LocalDate.of(2026, 12, 3)))
        assertEquals(29, model.monthDates(YearMonth.of(2028, 2)).size)
        assertEquals(31, model.monthDates(YearMonth.of(2026, 12)).size)
        val open = ShiftCalendarModel(shift, emptyList(), listOf(pause.copy(resumeDate = null)), date(29))
        assertEquals(ShiftDayStatus.NONE, open.status(date(30)))
    }
}
