package com.example.uchetdohodapro

import com.example.uchetdohodapro.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Select by actual ending date, not list position or creation ID. */
internal fun latestClosedShift(shifts: List<ShiftEntity>): ShiftEntity? = shifts
    .filter { !it.isActive && it.endDate?.let(::parseStoredDate) != null }
    .maxWithOrNull(compareBy<ShiftEntity> { it.endDate?.let(::parseStoredDate) }.thenBy { it.id })

internal fun settlementRequestText(
    shift: ShiftEntity, days: List<WorkDayEntity>, pauses: List<ShiftPauseEntity>, advances: Double
): String {
    val fmt = DateTimeFormatter.ofPattern("dd.MM.uuuu")
    val periods = pauses.filter { it.shiftId == shift.id }.sortedBy { it.startDate }
    val work = days.filter { it.shiftId == shift.id }.mapNotNull { parseStoredDate(it.date) }.distinct().sorted()
    val weekends = work.zipWithNext().flatMap { (a, b) ->
        generateSequence(a.plusDays(1)) { it.plusDays(1) }.takeWhile { it < b }
            .filter { d -> periods.none { it.contains(d) } }.toList()
    }
    val pauseLabels = periods.map { p ->
        LocalDate.parse(p.startDate).format(fmt) + " — " +
            (p.resumeDate?.let { LocalDate.parse(it).minusDays(1).format(fmt) } ?: "по настоящее время")
    }
    return buildString {
        appendLine("Прошу поставить на расчёт 💰")
        appendLine("ФИО:")
        appendLine("Начало вахты: ${shift.startDate}")
        appendLine("Конец вахты: ${shift.endDate.orEmpty()}")
        appendLine("Выходные: ${weekends.joinToString { it.format(fmt) }.ifEmpty { "нету" }}")
        if (pauseLabels.isNotEmpty()) appendLine("Пауза вахты: ${pauseLabels.joinToString()}")
        appendLine("Авансы: ${formatRubles(advances)}")
        appendLine("ИНН:")
        append("Номер:")
    }
}
