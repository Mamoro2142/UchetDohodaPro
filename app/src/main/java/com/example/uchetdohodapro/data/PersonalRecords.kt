package com.example.uchetdohodapro.data

internal data class PersonalRecords(
    val bestDay: WorkDayEntity?, val bestShift: ShiftEntity?, val bestShiftIncome: Double?,
    val maxima: List<Double?>, val streak: Int
) {
    fun scores(): List<Double?> = listOf(bestDay?.calculateIncome(), bestShiftIncome) + maxima + listOf(streak.toDouble())
}

internal fun personalRecords(days: List<WorkDayEntity>, shifts: List<ShiftEntity>): PersonalRecords {
    val byShift = days.groupBy { it.shiftId }
    val incomes = shifts.mapNotNull { shift -> byShift[shift.id]?.takeIf { it.isNotEmpty() }?.let { shift to it.sumOf(WorkDayEntity::calculateIncome) } }
    val best = incomes.maxByOrNull { it.second }
    val fields: List<(WorkDayEntity) -> Double?> = listOf({ it.picks }, { it.weight }, { it.volume }, { it.boxes }, { it.hours })
    val streak = byShift.values.maxOfOrNull { entries ->
        val dates = entries.mapNotNull { parseStoredDate(it.date) }.distinct().sorted()
        var length = 0; var max = 0; var previous: java.time.LocalDate? = null
        dates.forEach { date ->
            length = if (previous?.plusDays(1) == date) length + 1 else 1
            max = maxOf(max, length); previous = date
        }
        max
    } ?: 0
    return PersonalRecords(days.maxByOrNull { it.calculateIncome() }, best?.first, best?.second,
        fields.map { field -> days.mapNotNull(field).maxOrNull() }, streak)
}

/** Celebrate only a newly added day, not tab switches, rate changes or initial load. */
internal fun isNewRecord(old: IncomeSnapshot, new: IncomeSnapshot): Boolean {
    if (new.days.size != old.days.size + 1 || !new.days.toHashSet().containsAll(old.days)) return false
    val before = personalRecords(old.days, old.shifts).scores()
    val after = personalRecords(new.days, new.shifts).scores()
    return after.indices.any { i -> after[i]?.let { it > (before[i] ?: 0.0) } == true }
}
