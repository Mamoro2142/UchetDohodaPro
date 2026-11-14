
package com.example.uchetdohodapro

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.example.uchetdohodapro.data.dayFromInput
import com.example.uchetdohodapro.data.parseNonNegativeNumber
import com.example.uchetdohodapro.data.ShiftEntity
import com.example.uchetdohodapro.data.TariffEntity
import com.example.uchetdohodapro.data.WorkDayEntity
import com.example.uchetdohodapro.data.WorkDatabase
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WorkDayScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current

    val dao = remember(context) {
        WorkDatabase.getInstance(context).workDao()
    }

    val scope = rememberCoroutineScope()

    var date by remember {
        mutableStateOf(
            SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.getDefault()
            ).format(Date())
        )
    }

    var picks by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var volume by remember { mutableStateOf("") }
    var boxes by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("") }

    var tariffs by remember {
        mutableStateOf<TariffEntity?>(null)
    }

    var activeShiftId by remember {
        mutableStateOf<Long?>(null)
    }

    var availableShifts by remember { mutableStateOf<List<ShiftEntity>>(emptyList()) }
    var automaticShift by remember { mutableStateOf(true) }
    var selectedShiftId by remember { mutableStateOf<Long?>(null) }
    var showShiftPicker by remember { mutableStateOf(false) }

    // Порядковые номера вахт.
    // Внутренние ID базы данных не изменяются.
    var shiftNumbers by remember {
        mutableStateOf<Map<Long, Int>>(emptyMap())
    }

    fun shiftNumber(shiftId: Long?): Int? {
        return shiftId?.let { shiftNumbers[it] }
    }

    var error by remember {
        mutableStateOf("")
    }

    var saved by remember {
        mutableStateOf(false)
    }

    var saving by remember {
        mutableStateOf(false)
    }

    var savedShiftId by remember {
        mutableStateOf<Long?>(null)
    }

    LaunchedEffect(dao) {
        dao.observeTariffs().collect { value ->
            tariffs = value
        }
    }

    LaunchedEffect(dao) {
        dao.observeShifts().collect { shifts ->
            availableShifts = shifts

            shiftNumbers = shifts
                .sortedBy { it.id }
                .mapIndexed { index, shift ->
                    shift.id to (index + 1)
                }
                .toMap()

            activeShiftId = shifts.firstOrNull {
                it.isActive
            }?.id
        }
    }

    val targetShiftId = if (automaticShift) activeShiftId else selectedShiftId

    val income = dayFromInput(date, listOf(picks, weight, volume, boxes, hours), tariffs ?: TariffEntity(), targetShiftId).calculateIncome()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF272C30),
                contentColor = Color(0xFFF4F5F6)
            )
        ) {
            Text("← Назад")
        }

        Text(
            "НОВЫЙ РАБОЧИЙ ДЕНЬ",
            color = Color(0xFFFF9800),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF202428)
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "ПРИВЯЗКА К ВАХТЕ",
                    color = Color(0xFFB8BEC3),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    if (targetShiftId != null) {
                        "Вахта №${
                            shiftNumber(targetShiftId) ?: "—"
                        }"
                    } else {
                        "Нет активной вахты"
                    },
                    color = if (targetShiftId != null) {
                        Color(0xFF43D17D)
                    } else {
                        Color(0xFFFF9800)
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    if (targetShiftId != null) {
                        "Рабочий день будет привязан к этой вахте."
                    } else {
                        "Рабочий день сохранится без привязки к вахте."
                    },
                    color = Color(0xFFB8BEC3),
                    fontSize = 12.sp
                )
            }
        }

        OutlinedButton(enabled = !saving, onClick = { showShiftPicker = true }) {
            Text("Изменить привязку к вахте")
        }

        OutlinedTextField(
            enabled = !saving,
            value = date,
            onValueChange = {
                date = it
                saved = false
            },
            label = {
                Text("Дата (ГГГГ-ММ-ДД)")
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        WorkDayNumberField(
            "Пики",
            picks,
            {
                picks = it
                saved = false
            },
            enabled = !saving
        )

        WorkDayNumberField(
            "Вес",
            weight,
            {
                weight = it
                saved = false
            },
            enabled = !saving
        )

        WorkDayNumberField(
            "Объём",
            volume,
            {
                volume = it
                saved = false
            },
            enabled = !saving
        )

        WorkDayNumberField(
            "Боксы",
            boxes,
            {
                boxes = it
                saved = false
            },
            enabled = !saving
        )

        WorkDayNumberField(
            "Часы",
            hours,
            {
                hours = it
                saved = false
            },
            enabled = !saving
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF202428)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "Предварительный заработок",
                    color = Color(0xFFB8BEC3)
                )

                Text(
                    if (income.isFinite()) String.format(Locale.getDefault(), "%.2f ₽", income) else "Проверьте показатели",
                    color = Color(0xFF4CAF50),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (tariffs == null) {
            Text(
                "Сначала настрой тарифы в профиле.",
                color = Color(0xFFFF9800)
            )
        }

        if (error.isNotBlank()) {
            Text(
                error,
                color = Color(0xFFFF5252)
            )
        }

        if (saved) {
            Text(
                if (savedShiftId != null) {
                    "Рабочий день сохранён в вахту №${
                        shiftNumber(savedShiftId) ?: "—"
                    }!"
                } else {
                    "Рабочий день сохранён без привязки к вахте!"
                },
                color = Color(0xFF43D17D)
            )
        }

        Button(
            onClick = {
                if (saving || saved) return@Button
                val parser = SimpleDateFormat(
                    "yyyy-MM-dd",
                    Locale.getDefault()
                ).apply {
                    isLenient = false
                }

                val parsedDate = runCatching {
                    parser.parse(date)?.let {
                        parser.format(it)
                    }
                }.getOrNull()

                val values = listOf(
                    picks,
                    weight,
                    volume,
                    boxes,
                    hours
                )

                val invalid = values.any { value ->
                    val number = parseNonNegativeNumber(value)

                    value.isNotBlank() &&
                        (
                            number == null ||
                            !number.isFinite() ||
                            number < 0
                        )
                }

                val currentTariffs = tariffs

                when {
                    parsedDate == null || parsedDate != date -> {
                        error =
                            "Укажи дату в формате ГГГГ-ММ-ДД."
                    }

                    invalid -> {
                        error =
                            "Показатели должны быть числами не меньше нуля."
                    }

                    currentTariffs == null -> {
                        error =
                            "Сначала настрой и сохрани тарифы."
                    }

                    else -> {
                        val draft = dayFromInput(parsedDate, values, currentTariffs)
                        if (!draft.calculateIncome().isFinite()) {
                            error = "Сумма слишком велика. Проверьте показатели и тарифы."
                            return@Button
                        }
                        error = ""
                        saving = true

                        // Сохранение продолжится в Части 3.
                        
        
                        scope.launch {
                            try {
                                // Получаем внутренний ID
                                // активной вахты из базы данных.
                                val shiftId =
                                    if (automaticShift) dao.getActiveShift()?.id else selectedShiftId

                                dao.insertWorkDay(draft.copy(shiftId = shiftId))

                                savedShiftId = shiftId
                                saved = true

                            } catch (e: Exception) {
                                error =
                                    "Не удалось сохранить рабочий день: ${
                                        e.message ?: "неизвестная ошибка"
                                    }"
                            } finally {
                                saving = false
                            }
                        }
                    }
                }
            },
            enabled = !saving && !saved,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFF9800),
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                if (saving) {
                    "СОХРАНЕНИЕ..."
                } else if (saved) {
                    "РАБОЧИЙ ДЕНЬ СОХРАНЁН"
                } else {
                    "СОХРАНИТЬ РАБОЧИЙ ДЕНЬ"
                },
                fontWeight = FontWeight.Bold
            )
        }
    }
    if (showShiftPicker) {
        AlertDialog(onDismissRequest = { showShiftPicker = false },
            title = { Text("Привязка рабочего дня") },
            text = {
                Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())) {
                    TextButton(onClick = { automaticShift = true; saved = false; showShiftPicker = false }) { Text("Активная вахта автоматически") }
                    TextButton(onClick = { automaticShift = false; selectedShiftId = null; saved = false; showShiftPicker = false }) { Text("Без привязки") }
                    availableShifts.sortedBy { it.id }.forEach { shift ->
                        TextButton(onClick = { automaticShift = false; selectedShiftId = shift.id; saved = false; showShiftPicker = false }) {
                            Text("Вахта №${shiftNumber(shift.id)}: ${shift.startDate} — ${shift.endDate ?: "идёт"}")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showShiftPicker = false }) { Text("Закрыть") } })
    }
}

@Composable
private fun WorkDayNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true
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

