package com.example.uchetdohodapro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.uchetdohodapro.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

private val PlanOrange = Color(0xFFFF8A24)
private val PlanGreen = Color(0xFF55C58A)
private val PlanYellow = Color(0xFFEAC54C)
private val PlanTrack = Color(0xFF414247)

@Composable
fun ShiftPlanDialog(title: String, initial: Int?, onDismiss: () -> Unit, onSave: suspend (Int?) -> Unit) {
    var input by remember { mutableStateOf(initial?.toString().orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(title) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(input, { input = it; error = null }, label = { Text("Ожидаемое количество смен") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                enabled = !busy, modifier = Modifier.fillMaxWidth())
            Text("Укажите общее количество рабочих смен за вахту. Выходные и паузы не учитываются. Пустое поле — без плана.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = !busy, onClick = {
        val parsed = input.trim().toIntOrNull()
        if (input.isNotBlank() && (parsed == null || parsed <= 0)) {
            error = "Введите целое положительное число"
        } else {
            busy = true
            scope.launch {
                try { onSave(parsed); onDismiss() }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = e.message ?: "Не удалось сохранить план" }
                finally { busy = false }
            }
        }
    }) { Text("Сохранить") } }, dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Отмена") } })
}

@Composable
fun CompactShiftPlan(shift: ShiftEntity, worked: Int, enabled: Boolean = true) {
    val plan = shift.plannedWorkDays
    if (plan != null) {
        Text("Смены: $worked / $plan · Осталось ${(plan - worked).coerceAtLeast(0)}", color = PlanOrange)
        LinearProgressIndicator(progress = { (worked.toDouble() / plan).coerceIn(0.0, 1.0).toFloat() }, modifier = Modifier.fillMaxWidth(), color = PlanOrange, trackColor = PlanTrack)
    }
    ShiftPlanEditButton(shift, enabled)
}

@Composable
fun ShiftPlanEditButton(shift: ShiftEntity, enabled: Boolean = true) {
    if (!shift.isActive) return
    val context = LocalContext.current
    val dao = remember(context) { WorkDatabase.getInstance(context).workDao() }
    var edit by remember(shift.id) { mutableStateOf(false) }
    val plan = shift.plannedWorkDays
    OutlinedButton(onClick = { edit = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(if (plan == null) "Задать количество смен" else "Изменить количество смен")
    }
    if (edit) ShiftPlanDialog("План рабочих дней", plan, { edit = false }) { dao.setShiftPlan(shift.id, it) }
}

@Composable
fun ShiftProgressCard(shift: ShiftEntity, days: List<WorkDayEntity>) {
    val plan = shift.plannedWorkDays ?: return
    if (plan <= 0) return
    val context = LocalContext.current
    val dao = remember(context) { WorkDatabase.getInstance(context).workDao() }
    val pauses by remember(dao) { dao.observePauses() }.collectAsState(emptyList())
    val today = java.time.LocalDate.now()
    val model = remember(shift, days, pauses, today) { ShiftCalendarModel(shift, days, pauses, today) }
    val count = model.workCount
    val fraction = (count.toDouble() / plan).coerceIn(0.0, 1.0).toFloat()
    var edit by remember(shift.id) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2023))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Прогресс вахты", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { edit = true }) { Text("Изменить план", color = PlanOrange) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Осталось отработать", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text((plan - count).coerceAtLeast(0).toString(), color = PlanOrange, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text("рабочих дней", style = MaterialTheme.typography.bodySmall)
                }
                Box(Modifier.size(122.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = 9.dp.toPx(); val margin = stroke / 2
                        val arcSize = Size(size.width - stroke, size.height - stroke)
                        drawArc(PlanTrack, -90f, 360f, false, Offset(margin, margin), arcSize, style = Stroke(stroke))
                        if (fraction > 0) drawArc(PlanOrange, -90f, fraction * 360f, false, Offset(margin, margin), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(10.dp)) {
                        Text("$count/$plan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
                        Text(String.format(Locale.getDefault(), "%.1f%%", fraction * 100), color = PlanOrange, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            // Keep the row readable even for a very long plan; each segment is one shift for plans up to 30.
            val segments = plan.coerceAtMost(30)
            val filled = if (plan <= 30) count.coerceAtMost(plan) else (fraction * segments).toInt()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(segments) { i -> Box(Modifier.weight(1f).height(14.dp).background(if (i < filled) PlanOrange else PlanTrack, RoundedCornerShape(3.dp))) }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(Triple(count.toString(), "Смены", PlanGreen), Triple(model.restCount().toString(), "Выходные", PlanYellow), Triple(model.elapsedPauseCount().toString(), "Дни паузы", PlanOrange)).forEach { (value, label, color) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(value, color = color, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                    }
                }
            }
            if (count >= plan) Text(if (count == plan) "План выполнен" else "Сверх плана: ${count - plan} смен", color = PlanGreen)
        }
    }
    if (edit) ShiftPlanDialog("План рабочих дней", plan, { edit = false }) { dao.setShiftPlan(shift.id, it) }
}
