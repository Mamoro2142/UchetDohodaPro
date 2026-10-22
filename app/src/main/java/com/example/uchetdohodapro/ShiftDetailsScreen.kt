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
import java.util.Locale

private val DetailsOrange = Color(0xFFFF8A24)
private val DetailsGreen = Color(0xFF43D17D)

@Composable
fun ShiftDetailsScreen(
    shift: ShiftEntity,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val dao = remember(context) {
        WorkDatabase.getInstance(context).workDao()
    }

    val days by remember(dao, shift.id) {
        dao.observeWorkDaysForShift(shift.id)
    }.collectAsState(initial = emptyList())

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
            "Вахта №${shift.id}",
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

        if (days.isEmpty()) {
            Text(
                "В этой вахте пока нет рабочих дней.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            days.forEach { day ->
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.SpaceBetween
                        ) {
                            Text(
                                day.date,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                money(day.calculateIncome()),
                                color = DetailsGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            "Пики: ${number(day.picks ?: 0.0)} · " +
                                "Вес: ${number(day.weight ?: 0.0)}"
                        )

                        Text(
                            "Объём: ${number(day.volume ?: 0.0)} · " +
                                "Коробки: ${number(day.boxes ?: 0.0)}"
                        )

                        Text(
                            "Доп. часы: ${number(day.hours ?: 0.0)}",
                            color = DetailsOrange
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ShiftMetricRow(
    title: String,
    quantity: String,
    income: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                title,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                "Количество: $quantity",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            income,
            color = DetailsGreen,
            fontWeight = FontWeight.SemiBold
        )
    }
}
