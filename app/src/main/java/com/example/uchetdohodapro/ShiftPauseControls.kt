package com.example.uchetdohodapro

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.example.uchetdohodapro.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
fun ShiftPauseControls(shift: ShiftEntity, enabled: Boolean = true) {
    val context = LocalContext.current
    val dao = remember(context) { WorkDatabase.getInstance(context).workDao() }
    val all by remember(dao) { dao.observePauses() }.collectAsState(emptyList())
    val paused = all.any { it.shiftId == shift.id && it.resumeDate == null }
    var dialog by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    if (shift.isActive) {
        val actionColor = if (paused) Color(0xFF55BD83) else Color(0xFFFF8A24)
        OutlinedButton(
            onClick = { date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.uuuu")); error = null; dialog = true },
            enabled = enabled && !busy,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, if (enabled && !busy) actionColor else MaterialTheme.colorScheme.outline),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = actionColor)
        ) {
            Text(if (paused) "▶  Продолжить вахту" else "Ⅱ  Пауза вахты")
        }
    }
    if (dialog) AlertDialog(onDismissRequest = { if (!busy) dialog = false }, title = { Text(if (paused) "Дата продолжения" else "Начало паузы") }, text = {
        Column { if (!paused) Text("Если после выбранной даты уже есть смена, историческая пауза закончится перед ней."); OutlinedTextField(date, { date = it; error = null }, label = { Text("дд.ММ.гггг") }, singleLine = true, enabled = !busy); error?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
    }, confirmButton = { TextButton(enabled = !busy, onClick = {
        val parsed = parseStoredDate(date)
        if (parsed == null) error = "Неверная дата" else {
            busy = true
            scope.launch { try { dao.togglePause(shift.id, parsed); dialog = false } catch (e: Exception) { error = e.message ?: "Ошибка сохранения" } finally { busy = false } }
        }
    }) { Text("Сохранить") } }, dismissButton = { TextButton(enabled = !busy, onClick = { dialog = false }) { Text("Отмена") } })
}

@Composable
fun ShiftCalendarSummary(shift: ShiftEntity, days: List<WorkDayEntity>) {
    val context = LocalContext.current
    val dao = remember(context) { WorkDatabase.getInstance(context).workDao() }
    val all by remember(dao) { dao.observePauses() }.collectAsState(emptyList())
    val pauses = all.filter { it.shiftId == shift.id }
    val work = days.mapNotNull { parseStoredDate(it.date) }.distinct().sorted()
    val fmt = DateTimeFormatter.ofPattern("dd.MM.uuuu")
    val weekends = remember(work, pauses) { work.zipWithNext().flatMap { (a,b) -> generateSequence(a.plusDays(1)) { it.plusDays(1) }.takeWhile { it < b }.filter { d -> pauses.none { it.contains(d) } }.toList() } }
    val pauseLabels = pauses.map { p -> LocalDate.parse(p.startDate).format(fmt) + " — " + (p.resumeDate?.let { LocalDate.parse(it).minusDays(1).format(fmt) } ?: "по настоящее время") }
    val today = LocalDate.now()
    val pauseDays = pauses.sumOf { p ->
        val start = LocalDate.parse(p.startDate)
        val end = minOf(p.resumeDate?.let { LocalDate.parse(it) } ?: today.plusDays(1), today.plusDays(1))
        java.time.temporal.ChronoUnit.DAYS.between(start, end).coerceAtLeast(0)
    }
    val green = Color(0xFF68C98B)
    val yellow = Color(0xFFFACC15)
    val orange = Color(0xFFF28C28)
    val entries = (work.map { Triple(it, it.format(fmt), "Рабочая смена") } +
        weekends.map { Triple(it, it.format(fmt), "Выходной") } +
        pauses.zip(pauseLabels).map { (p, label) -> Triple(LocalDate.parse(p.startDate), label, "Пауза вахты") }).sortedBy { it.first }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Хронология вахты", style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()
            entries.forEach { (_, label, status) ->
                if (status == "Пауза вахты") HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(status, color = when(status) { "Рабочая смена" -> green; "Выходной" -> yellow; else -> orange })
                }
                if (status == "Пауза вахты") HorizontalDivider()
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(Triple(work.size.toString(), "Смены", green), Triple(weekends.size.toString(), "Выходные", yellow), Triple(pauseDays.toString(), "Дни паузы", orange)).forEach { (count, label, color) ->
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Text(count, color = color, style = MaterialTheme.typography.headlineSmall)
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettlementRequestButton(
    shift: ShiftEntity,
    days: List<WorkDayEntity>,
    pauses: List<ShiftPauseEntity>,
    advances: Double,
    enabled: Boolean
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    OutlinedButton(
        onClick = {
            clipboard.setText(AnnotatedString(settlementRequestText(shift, days, pauses, advances)))
            Toast.makeText(context, "Заявление на расчёт скопировано", Toast.LENGTH_SHORT).show()
        },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    ) { Text("Прошу поставить на расчёт") }
}
