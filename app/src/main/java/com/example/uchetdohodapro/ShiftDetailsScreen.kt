
package com.example.uchetdohodapro

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.uchetdohodapro.data.ShiftEntity
import com.example.uchetdohodapro.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DetailsOrange = Color(0xFFFF8A24)
private val DetailsGreen = Color(0xFF43D17D)

@Composable
fun ShiftDetailsScreen(
    shift: ShiftEntity,
    shiftNumber: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val dao = remember(context) {
        WorkDatabase.getInstance(context).workDao()
    }

    val data = LocalIncomeSnapshot.current
    val days = remember(data.days, shift.id) { data.days.filter { it.shiftId == shift.id } }
    val operations = data.operations
    val shiftOperations = operations.filter { it.shiftId == shift.id }
    val advances = financeTotal(shiftOperations, ADVANCE)
    val fines = financeTotal(shiftOperations, FINE)

    var selectedCalendarDate by remember(shift.id) {
        mutableStateOf(if (shift.isActive) LocalDate.now() else shift.endDate?.let(::parseStoredDate)
            ?: parseStoredDate(shift.startDate) ?: LocalDate.now())
    }
    var addDate by remember(shift.id) { mutableStateOf<LocalDate?>(null) }
    var editDay by remember(shift.id) { mutableStateOf<WorkDayEntity?>(null) }
    var saving by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var editError by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val shifts = data.shifts
    val shiftNumbers = shifts.sortedBy { it.id }.mapIndexed { i, s -> s.id to i + 1 }.toMap()
    BackHandler(enabled = addDate != null || editDay != null) {
        if (!saving) { addDate = null; editDay = null; editError = "" }
    }
    addDate?.let { date ->
        WorkDayScreen(onBack = { addDate = null }, initialDate = date.toString(), initialShiftId = shift.id)
        return
    }
    editDay?.let { day ->
        WorkDayEditScreen(day, shifts, shiftNumbers, saving,
            onBack = { if (!saving) { editDay = null; editError = "" } },
            onSave = { updated ->
                if (!saving) { saving = true; scope.launch {
                    try { dao.updateWorkDay(updated); editDay = null; editError = "" }
                    catch (e: Exception) { editError = e.message ?: "Не удалось сохранить" }
                    finally { saving = false }
                } }
            }, onDelete = { deleteConfirm = true }, error = editError)
        if (deleteConfirm) AlertDialog(onDismissRequest = { deleteConfirm = false },
            title = { Text("Удалить рабочий день?") },
            text = { Text(day.date) },
            confirmButton = { TextButton(enabled = !saving, onClick = {
                deleteConfirm = false; saving = true; scope.launch {
                    try { dao.deleteWorkDay(day.id); editDay = null; editError = "" }
                    catch (e: Exception) { editError = e.message ?: "Не удалось удалить" }
                    finally { saving = false }
                }
            }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("Отмена") } })
        return
    }

    val sortedDays = days.sortedWith(compareByDescending { parseStoredDate(it.date) ?: LocalDate.MIN })

    fun money(value: Double): String {
        return formatRubles(value)
    }

    fun number(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            String.format(
                Locale.getDefault(),
                "%.2f",
                value
            )
        }
    }

    val picks = days.sumOf { it.picks ?: 0.0 }
    val weight = days.sumOf { it.weight ?: 0.0 }
    val volume = days.sumOf { it.volume ?: 0.0 }
    val boxes = days.sumOf { it.boxes ?: 0.0 }
    val hours = days.sumOf { it.hours ?: 0.0 }

    val picksIncome = days.sumOf {
        (it.picks ?: 0.0) * it.picksRate
    }

    val weightIncome = days.sumOf {
        (it.weight ?: 0.0) * it.weightRate
    }

    val volumeIncome = days.sumOf {
        (it.volume ?: 0.0) * it.volumeRate
    }

    val boxesIncome = days.sumOf {
        (it.boxes ?: 0.0) * it.boxesRate
    }

    val hoursIncome = days.sumOf {
        (it.hours ?: 0.0) * it.hoursRate
    }

    val totalIncome = days.sumOf {
        it.calculateIncome()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        WinterPageHeader()
        TextButton(onClick = onBack) {
            Text(
                "← Назад к вахтам",
                color = DetailsOrange
            )
        }

        Text(
            "Вахта №$shiftNumber",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            "${shift.startDate} — ${shift.endDate ?: "по настоящее время"}",
            style = MaterialTheme.typography.bodyMedium
        )

        Text(
            if (shift.isActive) {
                "Активная вахта"
            } else {
                "Завершённая вахта"
            },
            color = if (shift.isActive) {
                DetailsGreen
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )

        HorizontalDivider()

        Text(
            "Рабочих дней: ${uniqueWorkDayCount(days)}",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            "Заработано: ${money(totalIncome)}",
            color = DetailsGreen,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        HorizontalDivider()

        Text("Остаток к получению: ${money(totalIncome - advances - fines)}", style = MaterialTheme.typography.titleMedium)
        ShiftMonthCalendar(shift, days, selected = selectedCalendarDate, onSelect = { selectedCalendarDate = it },
            onEdit = { editDay = it; editError = "" }, onAdd = { addDate = it })
        ShiftCalendarSummary(shift, days)
        MoneyOperationsPanel(shift.id)
        HorizontalDivider()

        Text(
            "Статистика показателей",
            style = MaterialTheme.typography.titleLarge
        )

        ShiftMetricRow(
            "Пики",
            number(picks),
            money(picksIncome)
        )

        ShiftMetricRow(
            "Вес",
            number(weight),
            money(weightIncome)
        )

        ShiftMetricRow(
            "Объём",
            number(volume),
            money(volumeIncome)
        )

        ShiftMetricRow(
            "Боксы",
            number(boxes),
            money(boxesIncome)
        )

        ShiftMetricRow(
            "Часы",
            number(hours),
            money(hoursIncome)
        )

        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "ИТОГО",
                fontWeight = FontWeight.Bold
            )

            Text(
                money(totalIncome),
                color = DetailsGreen,
                fontWeight = FontWeight.Bold
            )
        }

        HorizontalDivider()

        Text(
            "История рабочих дней",
            style = MaterialTheme.typography.titleLarge
        )

        if (sortedDays.isEmpty()) {
            Text(
                "В этой вахте пока нет рабочих дней.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            sortedDays.forEach { day ->

                Card(
                    modifier = Modifier.fillMaxWidth().seasonalCard()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = day.date,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        HorizontalDivider()

                        // Показываем только заполненные поля.
                        // Если поле равно null, оно не выводится.

                        if (day.picks != null) {
                            Text(
                                "Пики: ${number(day.picks)}"
                            )
                        }

                        if (day.weight != null) {
                            Text(
                                "Вес: ${number(day.weight)}"
                            )
                        }

                        if (day.volume != null) {
                            Text(
                                "Объём: ${number(day.volume)}"
                            )
                        }

                        if (day.boxes != null) {
                            Text(
                                "Боксы: ${number(day.boxes)}"
                            )
                        }

                        if (day.hours != null) {
                            Text(
                                "Часы: ${number(day.hours)}",
                                color = DetailsOrange
                            )
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Заработано",
                                fontWeight = FontWeight.Medium
                            )

                            Text(
                                money(day.calculateIncome()),
                                color = DetailsGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(
            modifier = Modifier.height(24.dp)
        )
    }
}

@Composable
private fun ShiftMetricRow(
    title: String,
    quantity: String,
    income: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceBetween
        ) {
            Text(
                quantity,
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                income,
                color = DetailsGreen,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

        
