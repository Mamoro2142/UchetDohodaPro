package com.example.uchetdohodapro

import com.example.uchetdohodapro.data.*
import org.junit.Assert.*
import org.junit.Test

class SettlementRequestTest {
    private val closed = ShiftEntity(1, "08.11.2026", "20.11.2026", false)
    @Test fun selectsByEndDateNotIdAndIgnoresActive() {
        val older = closed.copy(id = 8, endDate = "2026-11-10")
        val active = ShiftEntity(9, "21.11.2026")
        assertEquals(closed, latestClosedShift(listOf(active, older, closed)))
        assertNull(latestClosedShift(listOf(active)))
        assertEquals(9L, latestClosedShift(listOf(closed, active.copy(isActive = false, endDate = "2026-11-24")))?.id)
        assertEquals(8L, latestClosedShift(listOf(closed, older.copy(endDate = "2026-11-20")))?.id)
        assertEquals(older, latestClosedShift(listOf(active, older)))
    }
    @Test fun textUsesOnlySelectedShiftAndExcludesPauseFromRest() {
        val days = listOf(8, 9, 18, 20).map { WorkDayEntity(date = "2026-11-${it.toString().padStart(2, '0')}", shiftId = 1) } + WorkDayEntity(date = "2026-11-19", shiftId = 9)
        val pauses = listOf(ShiftPauseEntity(1, 1, "2026-11-10", "2026-11-18"), ShiftPauseEntity(2, 9, "2026-11-19"))
        val text = settlementRequestText(closed, days, pauses, 1234.5)
        assertTrue(text.contains("Выходные: 19.11.2026\n"))
        assertTrue(text.contains("Пауза вахты: 10.11.2026 — 17.11.2026\n"))
        assertTrue(text.contains("Авансы: 1\u00A0234,50 ₽"))
        assertTrue(text.contains("Конец вахты: 20.11.2026"))
        val empty = settlementRequestText(closed, emptyList(), emptyList(), 0.0)
        assertTrue(empty.contains("Выходные: нету"))
        assertFalse(empty.contains("Пауза вахты:"))
    }
}
