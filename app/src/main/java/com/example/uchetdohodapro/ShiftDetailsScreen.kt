
package com.example.uchetdohodapro

import androidx.compose.foundation.layout.*
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
import com.example.uchetdohodapro.data.WorkDatabase
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

    val days by remember(dao, shift.id) {
        dao.observeWorkDaysForShift(shift.id)
    }.collectAsState(initial = emptyList())

    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("dd.MM.yyyy")
    }

    val sortedDays = days.sortedWith(
        compareByDescending { day ->
            runCatching {
                LocalDate.parse(day.date, dateFormatter)
            }.getOrNull() ?: LocalDate.MIN
        }
    )

    fun money(value: Double): String {
        return String.format(
            Locale.getDefault(),
            "%.2f ₽",
            value
        )
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
            "Рабочих дней: ${days.size}",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            "Заработано: ${money(totalIncome)}",
            color = DetailsGreen,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

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
            "Коробки",
            number(boxes),
            money(boxesIncome)
        )

        ShiftMetricRow(
            "Доп. часы",
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
                    modifier = Modifier.fillMaxWidth()
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
                                "Коробки: ${number(day.boxes)}"
                            )
                        }

                        if (day.hours != null) {
                            Text(
                                "Доп. часы: ${number(day.hours)}",
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

        
