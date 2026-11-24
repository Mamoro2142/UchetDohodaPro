package com.example.uchetdohodapro

import androidx.compose.foundation.layout.*
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
fun ShiftPauseControls(shift: ShiftEntity) {
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
        OutlinedButton(onClick = { date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.uuuu")); error = null; dialog = true }, enabled = !busy) {
            Text(if (paused) "Продолжить вахту" else "Пауза вахты")
        }
    }
    if (dialog) AlertDialog(onDismissRequest = { if (!busy) dialog = false }, title = { Text(if (paused) "Дата продолжения" else "Начало паузы") }, text = {
        Column { OutlinedTextField(date, { date = it; error = null }, label = { Text("дд.ММ.гггг") }, singleLine = true, enabled = !busy); error?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
    }, confirmButton = { TextButton(enabled = !busy, onClick = {
        val parsed = parseStoredDate(date)
        if (parsed == null) error = "Неверная дата" else {
            busy = true
            scope.launch { try { dao.togglePause(shift.id, parsed); dialog = false } catch (e: Exception) { error = e.message ?: "Ошибка сохранения" } finally { busy = false } }
        }
    }) { Text("Сохранить") } }, dismissButton = { TextButton(enabled = !busy, onClick = { dialog = false }) { Text("Отмена") } })
}

@Composable
fun ShiftCalendarSummary(shift: ShiftEntity, days: List<WorkDayEntity>, advances: Double) {
    val context = LocalContext.current
    val dao = remember(context) { WorkDatabase.getInstance(context).workDao() }
    val all by remember(dao) { dao.observePauses() }.collectAsState(emptyList())
    val pauses = all.filter { it.shiftId == shift.id }
    val work = days.mapNotNull { parseStoredDate(it.date) }.distinct().sorted()
    val fmt = DateTimeFormatter.ofPattern("dd.MM.uuuu")
    val weekends = remember(work, pauses) { work.zipWithNext().flatMap { (a,b) -> generateSequence(a.plusDays(1)) { it.plusDays(1) }.takeWhile { it < b }.filter { d -> pauses.none { it.contains(d) } }.toList() } }
    val pauseLabels = pauses.map { p -> LocalDate.parse(p.startDate).format(fmt) + " — " + (p.resumeDate?.let { LocalDate.parse(it).minusDays(1).format(fmt) } ?: "по настоящее время") }
    Text("Выходных: ${weekends.size}")
    weekends.forEach { Text("${it.format(fmt)} — выходной") }
    pauseLabels.forEach { Text("Пауза: $it") }
    val clipboard = LocalClipboardManager.current
    if (!shift.isActive) OutlinedButton(onClick = {
        clipboard.setText(AnnotatedString(buildString {
            appendLine("Прошу поставить на расчёт 💰")
            appendLine("ФИО:")
            appendLine("Начало вахты: ${shift.startDate}")
            appendLine("Конец вахты: ${shift.endDate.orEmpty()}")
            appendLine("Выходные: ${weekends.joinToString { it.format(fmt) }.ifEmpty { "нету" }}")
            if (pauseLabels.isNotEmpty()) appendLine("Пауза вахты: ${pauseLabels.joinToString()}")
            appendLine("Авансы: ${String.format(java.util.Locale.getDefault(), "%.2f", advances)} ₽")
            appendLine("ИНН:")
            append("Номер:")
        }))
    }) { Text("Прошу поставить на расчёт") }
}
