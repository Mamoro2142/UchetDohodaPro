package com.example.uchetdohodapro.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

enum class ShiftDayStatus { WORK, REST, PAUSE, NONE }

/** Derive calendar and progress from persisted records; do not create synthetic work days. */
class ShiftCalendarModel(
    private val shift: ShiftEntity,
    days: List<WorkDayEntity>,
    pauses: List<ShiftPauseEntity>,
    private val today: LocalDate = LocalDate.now()
) {
    private val byDate = days.filter { it.shiftId == shift.id }
        .mapNotNull { day -> parseStoredDate(day.date)?.let { it to day } }.groupBy({ it.first }, { it.second })
    private val periods = pauses.filter { it.shiftId == shift.id }.sortedBy { it.startDate }
    private val firstWork = byDate.keys.minOrNull()
    private val lastWork = byDate.keys.maxOrNull()
    private val start = parseStoredDate(shift.startDate)
    private val end = shift.endDate?.let(::parseStoredDate)
    val workCount: Int get() = byDate.size
    fun records(date: LocalDate): List<WorkDayEntity> = byDate[date].orEmpty()
    fun pause(date: LocalDate): ShiftPauseEntity? = periods.firstOrNull { it.contains(date) && (it.resumeDate != null || date <= today) }
    fun status(date: LocalDate): ShiftDayStatus = when {
        date in byDate -> ShiftDayStatus.WORK
        pause(date) != null -> ShiftDayStatus.PAUSE
        firstWork != null && lastWork != null && date > firstWork && date < lastWork -> ShiftDayStatus.REST
        else -> ShiftDayStatus.NONE
    }
    fun canAdd(date: LocalDate): Boolean = status(date) == ShiftDayStatus.REST &&
        start != null && date >= start && (end == null || date <= end) && date <= today
    fun monthDates(month: YearMonth): List<LocalDate> = (1..month.lengthOfMonth()).map(month::atDay)
    fun income(date: LocalDate): Double = records(date).sumOf { it.calculateIncome() }
    // Only elapsed pause days enter totals, even when a future return date was selected.
    fun elapsedPauseCount(): Long = periods.sumOf { p ->
        val from = LocalDate.parse(p.startDate)
        val until = minOf(p.resumeDate?.let(LocalDate::parse) ?: today.plusDays(1), today.plusDays(1))
        ChronoUnit.DAYS.between(from, until).coerceAtLeast(0)
    }
    fun restCount(): Long {
        val first = firstWork ?: return 0
        val last = lastWork ?: return 0
        val gaps = (ChronoUnit.DAYS.between(first, last) + 1 - workCount).coerceAtLeast(0)
        val pausedInside = periods.sumOf { p ->
            val from = maxOf(first.plusDays(1), LocalDate.parse(p.startDate))
            val until = minOf(last, p.resumeDate?.let(LocalDate::parse) ?: last)
            ChronoUnit.DAYS.between(from, until).coerceAtLeast(0) - byDate.keys.count { it >= from && it < until }
        }
        return (gaps - pausedInside).coerceAtLeast(0)
    }
}
