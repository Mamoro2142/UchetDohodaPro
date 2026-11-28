package com.example.uchetdohodapro

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.uchetdohodapro.data.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val CalendarGreen = Color(0xFF55C58A)
private val CalendarYellow = Color(0xFFEAC54C)
private val CalendarOrange = Color(0xFFFF922B)
private val CalendarGray = Color(0xFF9DA5AB)
private val monthNames = listOf("Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь")
private fun statusColor(status: ShiftDayStatus) = when (status) {
    ShiftDayStatus.WORK -> CalendarGreen
    ShiftDayStatus.REST -> CalendarYellow
    ShiftDayStatus.PAUSE -> CalendarOrange
    ShiftDayStatus.NONE -> CalendarGray
}
private fun statusLabel(status: ShiftDayStatus) = when (status) {
    ShiftDayStatus.WORK -> "Рабочая смена"
    ShiftDayStatus.REST -> "Выходной"
    ShiftDayStatus.PAUSE -> "Пауза вахты"
    ShiftDayStatus.NONE -> "Нет данных"
}

@Composable
fun ShiftMonthCalendar(shift: ShiftEntity, days: List<WorkDayEntity>, selected: LocalDate, onSelect: (LocalDate) -> Unit, onEdit: (WorkDayEntity) -> Unit, onAdd: (LocalDate) -> Unit) {
    val pauses = LocalIncomeSnapshot.current.pauses
    val today = LocalDate.now()
    val model = remember(shift, days, pauses, today) { ShiftCalendarModel(shift, days, pauses, today) }
    val month = YearMonth.from(selected)
    val dates = model.monthDates(month)
    val firstOffset = dates.first().dayOfWeek.value - 1
    val rows = (firstOffset + dates.size + 6) / 7
    val shape = RoundedCornerShape(8.dp)
    val fmt = DateTimeFormatter.ofPattern("dd.MM.uuuu")
    fun money(value: Double) = formatRubles(value)
    fun changeMonth(delta: Long) {
        val candidate = month.plusMonths(delta)
        if (candidate.year in 1..9999) onSelect(candidate.atDay(1))
    }
    Card(Modifier.fillMaxWidth().seasonalCard(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2023)), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Календарь вахты", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { changeMonth(-1) }, modifier = Modifier.width(48.dp)) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
                Text("${monthNames[month.monthValue - 1]} ${month.year}", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                TextButton(onClick = { changeMonth(1) }, modifier = Modifier.width(48.dp)) { Text("›", style = MaterialTheme.typography.headlineSmall) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, color = CalendarGray, style = MaterialTheme.typography.labelSmall) }
            }
            repeat(rows) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(7) { col ->
                        val i = row * 7 + col - firstOffset
                        val date = dates.getOrNull(i)
                        if (date == null) Spacer(Modifier.weight(1f).aspectRatio(1f)) else {
                            val status = model.status(date)
                            val color = statusColor(status)
                            val background = when (status) {
                                ShiftDayStatus.WORK -> Color(0xFF233E33)
                                ShiftDayStatus.REST -> Color(0xFF403A24)
                                ShiftDayStatus.PAUSE -> Color(0xFF493321)
                                ShiftDayStatus.NONE -> Color(0xFF28292D)
                            }
                            Box(Modifier.weight(1f).aspectRatio(1f).background(background, shape)
                                .then(if (selected == date) Modifier.border(2.dp, CalendarOrange, shape) else Modifier)
                                .clickable { onSelect(date) }, contentAlignment = Alignment.Center) {
                                Text(date.dayOfMonth.toString(), color = color, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("● Смена", color = CalendarGreen, style = MaterialTheme.typography.labelSmall)
                Text("● Выходной", color = CalendarYellow, style = MaterialTheme.typography.labelSmall)
                Text("● Пауза", color = CalendarOrange, style = MaterialTheme.typography.labelSmall)
            }
            Text("● Нет данных", color = CalendarGray, style = MaterialTheme.typography.labelSmall)
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(Triple(ShiftDayStatus.WORK, "Смены", CalendarGreen), Triple(ShiftDayStatus.REST, "Выходные", CalendarYellow), Triple(ShiftDayStatus.PAUSE, "Дни паузы", CalendarOrange)).forEach { (status, label, color) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(dates.count { model.status(it) == status && (status != ShiftDayStatus.PAUSE || it <= today) }.toString(), color = color, style = MaterialTheme.typography.headlineSmall)
                        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                    }
                }
            }
            val status = model.status(selected)
            Column(Modifier.fillMaxWidth().background(Color(0xFF303136), RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(selected.format(fmt), fontWeight = FontWeight.Bold)
                Text(statusLabel(status), color = statusColor(status))
                when (status) {
                    ShiftDayStatus.WORK -> {
                        Text("Заработано: ${money(model.income(selected))}", color = CalendarGreen, style = MaterialTheme.typography.titleMedium)
                        model.records(selected).forEach { day ->
                            if (day.groupsJson != null) { GroupedDayBreakdown(day) } else {
                            Text("Раздел", color = CalendarOrange)
                            listOf("Пики" to day.picks, "Вес" to day.weight, "Объём" to day.volume, "Боксы" to day.boxes, "Часы" to day.hours).forEach { (label, value) ->
                                if (value != null) Text("$label: ${editableNumber(value)}", style = MaterialTheme.typography.bodySmall)
                            }
                            }
                            OutlinedButton(onClick = { onEdit(day) }, modifier = Modifier.fillMaxWidth()) { Text("Редактировать смену") }
                        }
                    }
                    ShiftDayStatus.PAUSE -> model.pause(selected)?.let { p ->
                        Text("${LocalDate.parse(p.startDate).format(fmt)} — ${p.resumeDate?.let { LocalDate.parse(it).minusDays(1).format(fmt) } ?: "по настоящее время"}")
                        if (selected > today) Text("Запланированный день паузы", style = MaterialTheme.typography.bodySmall)
                    }
                    ShiftDayStatus.REST -> {
                        Text("Выходной между рабочими сменами", style = MaterialTheme.typography.bodySmall)
                        if (model.canAdd(selected)) OutlinedButton(onClick = { onAdd(selected) }, modifier = Modifier.fillMaxWidth()) { Text("Добавить рабочий день") }
                    }
                    ShiftDayStatus.NONE -> Text("Записи отсутствуют", color = CalendarGray)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Заработано за месяц", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text(money(dates.sumOf { model.income(it) }), color = CalendarGreen, fontWeight = FontWeight.Bold)
            }
        }
    }
}
