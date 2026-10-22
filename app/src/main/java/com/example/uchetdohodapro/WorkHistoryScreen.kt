
package com.example.uchetdohodapro

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.uchetdohodapro.data.ShiftEntity
import com.example.uchetdohodapro.data.WorkDatabase
import com.example.uchetdohodapro.data.WorkDayEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

private val HistoryBackground = Color(0xFF111315)
private val HistoryPanel = Color(0xFF1C2023)
private val HistoryPanelLight = Color(0xFF272C30)
private val HistoryOrange = Color(0xFFFF8A24)
private val HistoryGreen = Color(0xFF43D17D)
private val HistoryRed = Color(0xFFFF5C5C)
private val HistoryText = Color(0xFFF4F5F6)
private val HistoryMuted = Color(0xFF9DA5AB)

@Composable
fun WorkHistoryScreen() {
    val context = LocalContext.current
    val dao = remember(context) {
        WorkDatabase.getInstance(context).workDao()
    }
    val scope = rememberCoroutineScope()

    val days by remember(dao) {
        dao.observeWorkDays()
    }.collectAsState(initial = emptyList())

    val shifts by remember(dao) {
        dao.observeShifts()
    }.collectAsState(initial = emptyList())

    // Единая нумерация существующих вахт: 1, 2, 3...
    // ID в базе данных не изменяем.
    val shiftNumbers = remember(shifts) {
        shifts.sortedBy { it.id }
            .mapIndexed { index, shift -> shift.id to (index + 1) }
            .toMap()
    }

    var editingDay by remember {
        mutableStateOf<WorkDayEntity?>(null)
    }

    var deletingDay by remember {
        mutableStateOf<WorkDayEntity?>(null)
    }

    var saving by remember {
        mutableStateOf(false)
    }

    var operationError by remember {
        mutableStateOf("")
    }

    if (editingDay != null) {
        val day = editingDay!!

        WorkDayEditScreen(
            day = day,
            shifts = shifts,
            shiftNumbers = shiftNumbers,
            saving = saving,
            onBack = {
                if (!saving) {
                    editingDay = null
                    operationError = ""
                }
            },
            onSave = { updated ->
                if (!saving) {
                    saving = true
                    scope.launch {
                        try {
                            dao.updateWorkDay(updated)
                            editingDay = null
                            operationError = ""
                        } catch (e: Exception) {
                            operationError =
                                "Не удалось сохранить изменения."
                        } finally {
                            saving = false
                        }
                    }
                }
            },
            onDelete = {
                deletingDay = day
            },
            error = operationError
        )
    } else {
        val total = days.sumOf {
            it.calculateIncome()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Text(
                "ИСТОРИЯ РАБОЧИХ ДНЕЙ",
                color = HistoryOrange,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = HistoryPanel
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    Modifier.padding(18.dp)
                ) {
                    Text(
                        "Всего заработано",
                        color = HistoryMuted
                    )

                    Text(
                        String.format(
                            Locale.getDefault(),
                            "%.2f ₽",
                            total
                        ),
                        color = HistoryGreen,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "Рабочих дней: ${days.size}",
                        color = HistoryText
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "Нажми на рабочий день, чтобы изменить его или выбрать вахту.",
                color = HistoryMuted,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(14.dp))

            if (days.isEmpty()) {
                Text(
                    "Пока нет сохранённых рабочих дней.",
                    color = HistoryMuted
                )
            } else {
                LazyColumn(
                    verticalArrangement =
                        Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        days,
                        key = { it.id }
                    ) { day ->
                        WorkHistoryCard(
                            day = day,
                            shiftNumbers = shiftNumbers,
                            onClick = {
                                operationError = ""
                                editingDay = day
                            }
                        )
                    }
                }
            }
        }
    }

    if (deletingDay != null) {
        val dayToDelete = deletingDay!!

        AlertDialog(
            onDismissRequest = {
                if (!saving) {
                    deletingDay = null
                }
            },
            containerColor = HistoryPanel,
            title = {
                Text(
                    "Удалить рабочий день?",
                    color = HistoryText
                )
            },
            text = {
                Text(
                    "Запись за ${dayToDelete.date} будет удалена без возможности восстановления.",
                    color = HistoryMuted
                )
            },
            confirmButton = {
                Button(
                    enabled = !saving,
                    onClick = {
                        if (!saving) {
                            saving = true
                            scope.launch {
                                try {
                                    dao.deleteWorkDay(
                                        dayToDelete.id
                                    )
                                    deletingDay = null
                                    editingDay = null
                                    operationError = ""
                                } catch (e: Exception) {
                                    deletingDay = null
                                    operationError =
                                        "Не удалось удалить запись."
                                } finally {
                                    saving = false
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HistoryRed,
                        contentColor = Color.Black
                    )
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !saving,
                    onClick = {
                        deletingDay = null
                    }
                ) {
                    Text(
                        "Отмена",
                        color = HistoryText
                    )
                }
            }
        )
    }
}

@Composable
private fun WorkHistoryCard(
    day: WorkDayEntity,
    shiftNumbers: Map<Long, Int>,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = HistoryPanelLight
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            Modifier.padding(16.dp)
        ) {
            Text(
                day.date,
                color = HistoryText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(6.dp))

            Text(
                String.format(
                    Locale.getDefault(),
                    "%.2f ₽",
                    day.calculateIncome()
                ),
                color = HistoryGreen,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            Text(
                if (day.shiftId != null) {
                    shiftNumbers[day.shiftId]?.let { "Вахта №$it" }
                        ?: "Вахта недоступна"
                } else {
                    "Без привязки к вахте"
                },
                color = if (day.shiftId != null) {
                    HistoryOrange
                } else {
                    HistoryMuted
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(8.dp))

            if (day.picks != null) {
                Text(
                    "Пики: ${day.picks}",
                    color = HistoryText
                )
            }

            if (day.weight != null) {
                Text(
                    "Вес: ${day.weight}",
                    color = HistoryText
                )
            }

            if (day.volume != null) {
                Text(
                    "Объём: ${day.volume}",
                    color = HistoryText
                )
            }

            if (day.boxes != null) {
                Text(
                    "Коробки: ${day.boxes}",
                    color = HistoryText
                )
            }

            if (day.hours != null) {
                Text(
                    "Часы: ${day.hours}",
                    color = HistoryText
                )
            }
        }
    }
}

@Composable
private fun WorkDayEditScreen(
    day: WorkDayEntity,
    shifts: List<ShiftEntity>,
    shiftNumbers: Map<Long, Int>,
    saving: Boolean,
    onBack: () -> Unit,
    onSave: (WorkDayEntity) -> Unit,
    onDelete: () -> Unit,
    error: String
) {
    var date by remember(day.id) {
        mutableStateOf(day.date)
    }

    var picks by remember(day.id) {
        mutableStateOf(day.picks.toInputText())
    }

    var weight by remember(day.id) {
        mutableStateOf(day.weight.toInputText())
    }

    var volume by remember(day.id) {
        mutableStateOf(day.volume.toInputText())
    }

    var boxes by remember(day.id) {
        mutableStateOf(day.boxes.toInputText())
    }

    var hours by remember(day.id) {
        mutableStateOf(day.hours.toInputText())
    }

    var selectedShiftId by remember(day.id) {
        mutableStateOf(day.shiftId)
    }

    var showShiftPicker by remember {
        mutableStateOf(false)
    }

    var validationError by remember(day.id) {
        mutableStateOf("")
    }

    val income =
        (picks.toDoubleOrNull() ?: 0.0) * day.picksRate +
        (weight.toDoubleOrNull() ?: 0.0) * day.weightRate +
        (volume.toDoubleOrNull() ?: 0.0) * day.volumeRate +
        (boxes.toDoubleOrNull() ?: 0.0) * day.boxesRate +
        (hours.toDoubleOrNull() ?: 0.0) * day.hoursRate

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onBack,
            enabled = !saving,
            colors = ButtonDefaults.buttonColors(
                containerColor = HistoryPanelLight,
                contentColor = HistoryText
            )
        ) {
            Text("← Назад")
        }

        Text(
            "РЕДАКТИРОВАНИЕ ДНЯ",
            color = HistoryOrange,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = date,
            onValueChange = {
                date = it
            },
            label = {
                Text("Дата (ГГГГ-ММ-ДД)")
            },
            singleLine = true,
            enabled = !saving,
            modifier = Modifier.fillMaxWidth()
        )

        HistoryNumberField(
            "Пики",
            picks,
            { picks = it },
            !saving
        )

        HistoryNumberField(
            "Вес",
            weight,
            { weight = it },
            !saving
        )

        HistoryNumberField(
            "Объём",
            volume,
            { volume = it },
            !saving
        )

        HistoryNumberField(
            "Коробки",
            boxes,
            { boxes = it },
            !saving
        )

        HistoryNumberField(
            "Часы",
            hours,
            { hours = it },
            !saving
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = HistoryPanel
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                Modifier.padding(16.dp)
            ) {
                Text(
                    "ПРИВЯЗКА К ВАХТЕ",
                    color = HistoryMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    if (selectedShiftId != null) {
                        shiftNumbers[selectedShiftId]?.let { "Вахта №$it" }
                            ?: "Вахта недоступна"
                    } else {
                        "Без привязки"
                    },
                    color = HistoryOrange,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        showShiftPicker = true
                    },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Выбрать вахту")
                }
            }
        }

        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = HistoryPanel
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                Modifier.padding(16.dp)
            ) {
                Text(
                    "Заработок за день",
                    color = HistoryMuted
                )

                Text(
                    String.format(
                        Locale.getDefault(),
                        "%.2f ₽",
                        income
                    ),
                    color = HistoryGreen,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Text(
            "Расчёт использует тарифы, сохранённые для этого дня.",
            color = HistoryMuted,
            fontSize = 12.sp
        )

        if (validationError.isNotBlank()) {
            Text(
                validationError,
                color = HistoryRed
            )
        }

        if (error.isNotBlank()) {
            Text(
                error,
                color = HistoryRed
            )
        }

        Button(
            onClick = {
                val parser = SimpleDateFormat(
                    "yyyy-MM-dd",
                    Locale.getDefault()
                ).apply {
                    isLenient = false
                }

                val parsedDate = runCatching {
                    parser.parse(date)?.let {
                        SimpleDateFormat(
                            "yyyy-MM-dd",
                            Locale.getDefault()
                        ).format(it)
                    }
                }.getOrNull()

                val inputs = listOf(
                    picks,
                    weight,
                    volume,
                    boxes,
                    hours
                )

                val invalid = inputs.any { value ->
                    val number = value.toDoubleOrNull()

                    value.isNotBlank() &&
                        (
                            number == null ||
                            !number.isFinite() ||
                            number < 0.0
                        )
                }

                when {
                    parsedDate == null ||
                        parsedDate != date -> {
                        validationError =
                            "Укажи правильную дату в формате ГГГГ-ММ-ДД."
                    }

                    invalid -> {
                        validationError =
                            "Показатели должны быть числами не меньше нуля."
                    }

                    selectedShiftId != null &&
                        shifts.none {
                            it.id == selectedShiftId
                        } -> {
                        validationError =
                            "Выбранная вахта больше не существует."
                    }

                    else -> {
                        validationError = ""

                        onSave(
                            day.copy(
                                date = date,
                                picks = picks.toOptionalNumber(),
                                weight = weight.toOptionalNumber(),
                                volume = volume.toOptionalNumber(),
                                boxes = boxes.toOptionalNumber(),
                                hours = hours.toOptionalNumber(),
                                shiftId = selectedShiftId
                            )
                        )
                    }
                }
            },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = HistoryOrange,
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                if (saving) {
                    "СОХРАНЯЕМ..."
                } else {
                    "СОХРАНИТЬ ИЗМЕНЕНИЯ"
                },
                fontWeight = FontWeight.Bold
            )
        }

        OutlinedButton(
            onClick = onDelete,
            enabled = !saving,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = HistoryRed
            )
        ) {
            Text("Удалить рабочий день")
        }
    }

    if (showShiftPicker) {
        AlertDialog(
            onDismissRequest = {
                showShiftPicker = false
            },
            containerColor = HistoryPanel,
            title = {
                Text(
                    "Выбрать вахту",
                    color = HistoryText
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 350.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            selectedShiftId = null
                            showShiftPicker = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Без привязки")
                    }

                    shifts.sortedBy { it.id }.forEach { shift ->
                        OutlinedButton(
                            onClick = {
                                selectedShiftId = shift.id
                                showShiftPicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Вахта №${shiftNumbers[shift.id] ?: "—"}" +
                                    if (shift.isActive) {
                                        " (активна)"
                                    } else {
                                        " (завершена)"
                                    }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showShiftPicker = false
                    }
                ) {
                    Text("Закрыть")
                }
            }
        )
    }
}

@Composable
private fun HistoryNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(label)
        },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

private fun Double?.toInputText(): String {
    return this?.let {
        if (it % 1.0 == 0.0) {
            it.toLong().toString()
        } else {
            it.toString()
        }
    } ?: ""
}

private fun String.toOptionalNumber(): Double? {
    return if (isBlank()) {
        null
    } else {
        toDoubleOrNull()
    }
}
