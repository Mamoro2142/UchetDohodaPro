
package com.example.uchetdohodapro

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.uchetdohodapro.data.ShiftEntity
import com.example.uchetdohodapro.data.WorkDatabase
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ShiftScreen(
    onDetailsVisibilityChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current

    val dao = remember(context) {
        WorkDatabase.getInstance(context).workDao()
    }

    val scope = rememberCoroutineScope()

    val shifts by remember(dao) {
        dao.observeShifts()
    }.collectAsState(initial = emptyList())

    val workDays by remember(dao) {
        dao.observeWorkDays()
    }.collectAsState(initial = emptyList())

    val activeShift = shifts.firstOrNull {
        it.isActive
    }

    val formatter = remember {
        DateTimeFormatter.ofPattern("dd.MM.yyyy")
    }

    fun today(): String {
        return LocalDate.now().format(formatter)
    }

    fun shiftDaysCount(shiftId: Long): Int {
        return workDays.count {
            it.shiftId == shiftId
        }
    }

    fun shiftIncome(shiftId: Long): Double {
        return workDays
            .filter { it.shiftId == shiftId }
            .sumOf { it.calculateIncome() }
    }

    fun money(value: Double): String {
        return String.format(
            Locale.getDefault(),
            "%.2f ₽",
            value
        )
    }

    // Порядковые номера вместо внутренних ID.
    // Сортируем по ID создания, а не по положению
    // в списке, возвращённом базой данных.
    val shiftNumbers = remember(shifts) {
        shifts
            .sortedBy { it.id }
            .mapIndexed { index, shift ->
                shift.id to (index + 1)
            }
            .toMap()
    }

    fun shiftNumber(shiftId: Long): Int {
        return shiftNumbers[shiftId] ?: 1
    }

    var editingShift by remember {
        mutableStateOf<ShiftEntity?>(null)
    }

    var deletingShift by remember {
        mutableStateOf<ShiftEntity?>(null)
    }

    var selectedShift by remember {
        mutableStateOf<ShiftEntity?>(null)
    }
    DisposableEffect(selectedShift != null) {
    onDetailsVisibilityChanged(selectedShift != null)

    onDispose {
        onDetailsVisibilityChanged(false)
    }
    }

    var startDate by remember {
        mutableStateOf("")
    }

    var endDate by remember {
        mutableStateOf("")
    }

    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    var operationError by remember {
        mutableStateOf<String?>(null)
    }

    var busy by remember {
        mutableStateOf(false)
    }

    // Системный жест "Назад" закрывает
    // подробную статистику выбранной вахты.
    BackHandler(
        enabled = selectedShift != null
    ) {
        selectedShift = null
    }

    val detailsShift = selectedShift

    if (detailsShift != null) {
        ShiftDetailsScreen(
            shift = detailsShift,
            shiftNumber = shiftNumber(detailsShift.id),
            onBack = {
                selectedShift = null
            }
        )
    } else {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Управление вахтами",
                style = MaterialTheme.typography.headlineSmall
            )

            if (operationError != null) {
                Text(
                    text = operationError!!,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (activeShift == null) {
                Button(
                    onClick = {
                        if (!busy) {
                            busy = true
                            operationError = null

                            scope.launch {
                                try {
                                    if (dao.getActiveShift() == null) {
                                        dao.insertShift(
                                            ShiftEntity(
                                                startDate = today(),
                                                isActive = true
                                            )
                                        )
                                    }
                                } catch (e: Exception) {
                                    operationError =
                                        "Не удалось начать вахту."
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Начать вахту")
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Активная вахта №${
                                shiftNumber(activeShift.id)
                            }",
                            color = Color(0xFF43D17D)
                        )

                        Text(
                            "Начало: ${activeShift.startDate}"
                        )

                        Text(
                            "Рабочих дней: ${
                                shiftDaysCount(activeShift.id)
                            }"
                        )

                        Text(
                            "Заработано: ${
                                money(shiftIncome(activeShift.id))
                            }",
                            color = Color(0xFF43D17D),
                            style = MaterialTheme.typography.titleLarge
                        )

                        Button(
                            onClick = {
                                if (!busy) {
                                    busy = true
                                    operationError = null

                                    scope.launch {
                                        try {
                                            dao.updateShift(
                                                activeShift.copy(
                                                    endDate = today(),
                                                    isActive = false
                                                )
                                            )
                                        } catch (e: Exception) {
                                            operationError =
                                                "Не удалось завершить вахту."
                                        } finally {
                                            busy = false
                                        }
                                    }
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Завершить вахту")
                        }
                    }
                }
            }

            HorizontalDivider()

            Text(
                text = "История вахт",
                style = MaterialTheme.typography.titleLarge
            )

            if (shifts.isEmpty()) {
                Text("Вахт пока нет")
            }
            shifts.forEach { shift ->
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (shift.isActive) {
                                "Вахта №${shiftNumber(shift.id)} — активна"
                            } else {
                                "Вахта №${shiftNumber(shift.id)} — завершена"
                            },
                            color = if (shift.isActive) {
                                Color(0xFF43D17D)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(
                            "Начало: ${shift.startDate}"
                        )

                        Text(
                            "Окончание: ${shift.endDate ?: "—"}"
                        )

                        HorizontalDivider()

                        Text(
                            "Рабочих дней: ${
                                shiftDaysCount(shift.id)
                            }"
                        )

                        Text(
                            "Заработано: ${
                                money(shiftIncome(shift.id))
                            }",
                            color = Color(0xFF43D17D),
                            style = MaterialTheme.typography.titleLarge
                        )

                        Button(
                            onClick = {
                                selectedShift = shift
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF8A24),
                                contentColor = Color(0xFF111315)
                            )
                        ) {
                            Text("Подробная статистика")
                        }

                        OutlinedButton(
                            onClick = {
                                editingShift = shift
                                startDate = shift.startDate
                                endDate = shift.endDate ?: ""
                                errorMessage = null
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Изменить даты")
                        }

                        OutlinedButton(
                            onClick = {
                                deletingShift = shift
                                operationError = null
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFFF5C5C)
                            )
                        ) {
                            Text("Удалить вахту")
                        }
                    }
                }
            }

            val unassignedDays = workDays.filter {
                it.shiftId == null
            }

            if (unassignedDays.isNotEmpty()) {
                HorizontalDivider()

                Text(
                    "Рабочие дни без вахты",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    "Количество: ${unassignedDays.size}"
                )

                Text(
                    "Заработано: ${
                        money(
                            unassignedDays.sumOf {
                                it.calculateIncome()
                            }
                        )
                    }",
                    color = Color(0xFFFF9800),
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    "Эти дни можно привязать к вахте в истории рабочих дней.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
    
    if (deletingShift != null) {
        val shift = deletingShift!!

        AlertDialog(
            onDismissRequest = {
                if (!busy) {
                    deletingShift = null
                }
            },
            title = {
                Text(
                    "Удалить вахту №${shiftNumber(shift.id)}?"
                )
            },
            text = {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Вы действительно хотите удалить эту вахту?"
                    )

                    Text(
                        "Рабочих дней: ${
                            shiftDaysCount(shift.id)
                        }"
                    )

                    Text(
                        "Заработано: ${
                            money(shiftIncome(shift.id))
                        }",
                        color = Color(0xFF43D17D)
                    )

                    Text(
                        "Все рабочие дни сохранятся и перейдут в категорию «Без привязки». Заработок и тарифы не изменятся."
                    )

                    if (shift.isActive) {
                        Text(
                            "Внимание: это активная вахта. После удаления новые рабочие дни будут сохраняться без привязки, пока вы не начнёте новую вахту.",
                            color = Color(0xFFFF9800)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        if (!busy) {
                            busy = true
                            operationError = null

                            scope.launch {
                                try {
                                    dao.deleteShiftSafely(
                                        shift.id
                                    )

                                    deletingShift = null
                                } catch (e: Exception) {
                                    operationError =
                                        "Не удалось удалить вахту."
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    }
                ) {
                    Text(
                        "Удалить",
                        color = Color(0xFFFF5C5C)
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        deletingShift = null
                    }
                ) {
                    Text("Отмена")
                }
            }
        )
    }

    if (editingShift != null) {
        AlertDialog(
            onDismissRequest = {
                if (!busy) {
                    editingShift = null
                }
            },
            title = {
                Text("Редактирование вахты")
            },
            text = {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = startDate,
                        onValueChange = {
                            startDate = it
                            errorMessage = null
                        },
                        label = {
                            Text("Дата начала (дд.ММ.гггг)")
                        },
                        singleLine = true,
                        enabled = !busy
                    )

                    OutlinedTextField(
                        value = endDate,
                        onValueChange = {
                            endDate = it
                            errorMessage = null
                        },
                        label = {
                            Text("Дата окончания (необязательно)")
                        },
                        singleLine = true,
                        enabled = !busy
                    )

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        val shift = editingShift
                            ?: return@TextButton

                        val start = runCatching {
                            LocalDate.parse(
                                startDate,
                                formatter
                            )
                        }.getOrNull()

                        val end = if (endDate.isBlank()) {
                            null
                        } else {
                            runCatching {
                                LocalDate.parse(
                                    endDate,
                                    formatter
                                )
                            }.getOrNull()
                        }

                        when {
                            start == null -> {
                                errorMessage =
                                    "Неверная дата начала"
                            }

                            endDate.isNotBlank() &&
                                end == null -> {
                                errorMessage =
                                    "Неверная дата окончания"
                            }

                            end != null &&
                                end.isBefore(start) -> {
                                errorMessage =
                                    "Окончание раньше начала"
                            }

                            shift.isActive &&
                                endDate.isBlank() &&
                                shifts.any {
                                    it.id != shift.id &&
                                    it.isActive
                                } -> {
                                errorMessage =
                                    "Уже существует другая активная вахта"
                            }

                            else -> {
                                busy = true
                                errorMessage = null

                                scope.launch {
                                    try {
                                        dao.updateShift(
                                            shift.copy(
                                                startDate = startDate,
                                                endDate = if (
                                                    shift.isActive
                                                ) {
                                                    endDate.ifBlank {
                                                        null
                                                    }
                                                } else {
                                                    endDate.ifBlank {
                                                        shift.endDate
                                                    }
                                                },
                                                isActive =
                                                    shift.isActive &&
                                                    endDate.isBlank()
                                            )
                                        )

                                        editingShift = null
                                        operationError = null
                                    } catch (e: Exception) {
                                        errorMessage =
                                            "Не удалось сохранить даты вахты."
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        editingShift = null
                    }
                ) {
                    Text("Отмена")
                }
            }
        )
    }
}
