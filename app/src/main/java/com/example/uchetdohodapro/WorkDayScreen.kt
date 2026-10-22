
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

    val income =
        (picks.toDoubleOrNull() ?: 0.0) *
            (tariffs?.picksRate ?: 0.0) +
        (weight.toDoubleOrNull() ?: 0.0) *
            (tariffs?.weightRate ?: 0.0) +
        (volume.toDoubleOrNull() ?: 0.0) *
            (tariffs?.volumeRate ?: 0.0) +
        (boxes.toDoubleOrNull() ?: 0.0) *
            (tariffs?.boxesRate ?: 0.0) +
        (hours.toDoubleOrNull() ?: 0.0) *
            (tariffs?.hoursRate ?: 0.0)

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
                    if (activeShiftId != null) {
                        "Активная вахта №${
                            shiftNumber(activeShiftId) ?: "—"
                        }"
                    } else {
                        "Нет активной вахты"
                    },
                    color = if (activeShiftId != null) {
                        Color(0xFF43D17D)
                    } else {
                        Color(0xFFFF9800)
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    if (activeShiftId != null) {
                        "Рабочий день будет автоматически привязан к этой вахте."
                    } else {
                        "Рабочий день сохранится без привязки к вахте."
                    },
                    color = Color(0xFFB8BEC3),
                    fontSize = 12.sp
                )
            }
        }

        OutlinedTextField(
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
            }
        )

        WorkDayNumberField(
            "Вес",
            weight,
            {
                weight = it
                saved = false
            }
        )

        WorkDayNumberField(
            "Объём",
            volume,
            {
                volume = it
                saved = false
            }
        )

        WorkDayNumberField(
            "Боксы",
            boxes,
            {
                boxes = it
                saved = false
            }
        )

        WorkDayNumberField(
            "Часы",
            hours,
            {
                hours = it
                saved = false
            }
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
                    String.format(
                        Locale.getDefault(),
                        "%.2f ₽",
                        income
                    ),
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
                    val number = value.toDoubleOrNull()

                    value.isNotBlank() &&
                        (
                            number == null ||
                            !number.isFinite() ||
                            number < 0
                        )
                }

                val currentTariffs = tariffs

                when {
                    parsedDate == null -> {
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
                        error = ""
                        saving = true

                        // Сохранение продолжится в Части 3.
                        
        
                        scope.launch {
                            try {
                                // Получаем внутренний ID
                                // активной вахты из базы данных.
                                val shiftId =
                                    dao.getActiveShift()?.id

                                dao.insertWorkDay(
                                    WorkDayEntity(
                                        date = parsedDate,
                                        picks = picks.takeIf {
                                            it.isNotBlank()
                                        }?.toDouble(),
                                        weight = weight.takeIf {
                                            it.isNotBlank()
                                        }?.toDouble(),
                                        volume = volume.takeIf {
                                            it.isNotBlank()
                                        }?.toDouble(),
                                        boxes = boxes.takeIf {
                                            it.isNotBlank()
                                        }?.toDouble(),
                                        hours = hours.takeIf {
                                            it.isNotBlank()
                                        }?.toDouble(),
                                        picksRate =
                                            currentTariffs.picksRate,
                                        weightRate =
                                            currentTariffs.weightRate,
                                        volumeRate =
                                            currentTariffs.volumeRate,
                                        boxesRate =
                                            currentTariffs.boxesRate,
                                        hoursRate =
                                            currentTariffs.hoursRate,
                                        shiftId = shiftId
                                    )
                                )

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
}

@Composable
private fun WorkDayNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(label)
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

