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
    val dao = remember { WorkDatabase.getInstance(context).workDao() }
    val scope = rememberCoroutineScope()

    var date by remember {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
    }

    var picks by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var volume by remember { mutableStateOf("") }
    var boxes by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("") }

    var tariffs by remember { mutableStateOf<TariffEntity?>(null) }
    var error by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
    dao.observeTariffs().collect { value ->
        tariffs = value
    }
}

    val income =
        (picks.toDoubleOrNull() ?: 0.0) * (tariffs?.picksRate ?: 0.0) +
        (weight.toDoubleOrNull() ?: 0.0) * (tariffs?.weightRate ?: 0.0) +
        (volume.toDoubleOrNull() ?: 0.0) * (tariffs?.volumeRate ?: 0.0) +
        (boxes.toDoubleOrNull() ?: 0.0) * (tariffs?.boxesRate ?: 0.0) +
        (hours.toDoubleOrNull() ?: 0.0) * (tariffs?.hoursRate ?: 0.0)

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

        OutlinedTextField(
            value = date,
            onValueChange = { date = it; saved = false },
            label = { Text("Дата (ГГГГ-ММ-ДД)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        WorkDayNumberField("Подборы", picks, { picks = it; saved = false })
        WorkDayNumberField("Вес", weight, { weight = it; saved = false })
        WorkDayNumberField("Объём", volume, { volume = it; saved = false })
        WorkDayNumberField("Коробки", boxes, { boxes = it; saved = false })
        WorkDayNumberField("Часы", hours, { hours = it; saved = false })

        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF202428)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Предварительный заработок", color = Color(0xFFB8BEC3))
                Text(
                    String.format(Locale.getDefault(), "%.2f", income),
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
            Text(error, color = Color(0xFFFF5252))
        }

        if (saved) {
            Text("Рабочий день сохранён!", color = Color(0xFF4CAF50))
        }

        Button(
            onClick = {
                val parsedDate = runCatching {
                    val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    parser.isLenient = false
                    parser.parse(date)?.let {
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(it)
                    }
                }.getOrNull()

                val values = listOf(picks, weight, volume, boxes, hours)
                val invalid = values.any {
                    it.isNotBlank() && (it.toDoubleOrNull() == null || it.toDoubleOrNull()!! < 0)
                }

                if (parsedDate == null) {
                    error = "Укажи дату в формате ГГГГ-ММ-ДД."
                } else if (invalid) {
                    error = "Показатели должны быть числами не меньше нуля."
                } else if (tariffs == null) {
                    error = "Сначала настрой и сохрани тарифы."
                } else {
                    error = ""
                    scope.launch {
                        dao.insertWorkDay(
                            WorkDayEntity(
                                date = parsedDate,
                                picks = picks.takeIf { it.isNotBlank() }?.toDouble(),
                                weight = weight.takeIf { it.isNotBlank() }?.toDouble(),
                                volume = volume.takeIf { it.isNotBlank() }?.toDouble(),
                                boxes = boxes.takeIf { it.isNotBlank() }?.toDouble(),
                                hours = hours.takeIf { it.isNotBlank() }?.toDouble(),
                                picksRate = tariffs!!.picksRate,
                                weightRate = tariffs!!.weightRate,
                                volumeRate = tariffs!!.volumeRate,
                                boxesRate = tariffs!!.boxesRate,
                                hoursRate = tariffs!!.hoursRate
                            )
                        )
                        saved = true
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFF9800),
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("СОХРАНИТЬ РАБОЧИЙ ДЕНЬ", fontWeight = FontWeight.Bold)
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
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
}
