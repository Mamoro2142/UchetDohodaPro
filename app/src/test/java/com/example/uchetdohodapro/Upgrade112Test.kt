package com.example.uchetdohodapro

import com.example.uchetdohodapro.data.*
import org.junit.Assert.*
import org.junit.Test

class Upgrade112Test {
    @Test fun glyphsKeepGroupingAndDecimalPlaces() {
        val a = moneyGlyphs(formatRubles(999.99)).toMap()
        val b = moneyGlyphs(formatRubles(1000.01)).toMap()
        assertEquals('9', a["int-0"])
        assertEquals('0', b["int-0"])
        assertEquals('1', b["int-3"])
        assertEquals('0', b["frac-0"])
        assertEquals('1', b["frac-1"])
        for (v in listOf(0.0, -999.99, 999999.99, 1000000.01)) {
            val text = formatRubles(v); val glyphs = moneyGlyphs(text)
            assertEquals(text, glyphs.map { it.second }.joinToString(""))
            assertEquals(glyphs.size, glyphs.map { it.first }.distinct().size)
        }
    }
    @Test fun displayedAmountIsRememberedSeparatelyPerPage() {
        val memory = MoneyDisplayMemory()
        assertEquals("0", memory.previous("home", "0"))
        memory.shown("home", "123")
        assertEquals("123", memory.previous("home", "456"))
        assertEquals("456", memory.previous("statistics", "456"))
        memory.shown("home", "456")
        assertEquals("456", memory.previous("home", "456"))
    }
    @Test fun retentionDoesNotSelectManualFiles() {
        val auto = (1L..6L).map { AutoBackup.fileName(it, "00000000-0000-0000-0000-000000000000") }
        assertEquals(auto.take(3).reversed(), AutoBackup.obsolete(auto + listOf("UchetDohodaPro-backup.json", "photo.jpg"), 3))
        assertTrue(AutoBackup.obsolete(auto, 7).isEmpty())
    }
    @Test fun recordsUseSavedRatesAndDistinctDatesForStreak() {
        val shift = ShiftEntity(1, "2026-11-01")
        val days = listOf(WorkDayEntity(1, "2026-11-01", picks=4.0, picksRate=2.0, shiftId=1),
            WorkDayEntity(2, "02.11.2026", picks=7.0, picksRate=1.0, boxes=0.0, shiftId=1),
            WorkDayEntity(3, "2026-11-02", picks=3.0, picksRate=1.0, shiftId=1),
            WorkDayEntity(4, "2026-11-04", weight=5.0, weightRate=3.0, shiftId=1))
        val r = personalRecords(days, listOf(shift))
        assertEquals(4L, r.bestDay!!.id)
        assertEquals(33.0, r.bestShiftIncome!!, 0.0)
        assertEquals(listOf(7.0, 5.0, null, 0.0, null), r.maxima)
        assertEquals(2, r.streak)
        val first = IncomeSnapshot(days.dropLast(1), listOf(shift), emptyList(), emptyList())
        val next = first.copy(days = days)
        assertTrue(isNewRecord(first, next))
        assertFalse(isNewRecord(next, next))
        assertFalse(isNewRecord(next, first))
        assertEquals(0, personalRecords(emptyList(), emptyList()).streak)
    }
}
