package com.example.uchetdohodapro

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.uchetdohodapro.data.*
import kotlin.math.*

@Composable
internal fun RecordsPanel(data: IncomeSnapshot) {
    val records = remember(data.days, data.shifts) { personalRecords(data.days, data.shifts) }
    val shiftNumbers = remember(data.shifts) { data.shifts.sortedBy { it.id }.mapIndexed { index, shift -> shift.id to (index + 1) }.toMap() }
    val gold = Color(0xFFFFBF55)
    val green = Color(0xFF43D17D)
    Text("Личные рекорды", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    Card(Modifier.fillMaxWidth().seasonalCard(18.dp), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2822))) {
        Column(Modifier.padding(18.dp)) {
            Text("ЛУЧШАЯ РАБОЧАЯ СМЕНА", color = gold, style = MaterialTheme.typography.labelMedium)
            Text(records.bestDay?.let { formatRubles(it.calculateIncome()) } ?: "Нет данных", color = gold, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            records.bestDay?.let { day -> Text(listOfNotNull(day.date, shiftNumbers[day.shiftId]?.let { "Вахта №$it" }).joinToString(" · "), style = MaterialTheme.typography.bodySmall) }
        }
    }
    val values = listOf(records.bestShiftIncome?.let(::formatRubles)) + records.maxima.map { it?.let { v -> editableNumber(v).replace('.', ',') } }
    val labels = listOf("Лучшая вахта", "Максимум пиков", "Максимальный вес", "Максимальный объём", "Максимум боксов", "Максимум часов")
    labels.indices.chunked(2).forEach { indices ->
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            indices.forEach { i ->
                Card(Modifier.weight(1f).seasonalCard(14.dp), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(labels[i], style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                        Text(values[i] ?: "—", color = green, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        if (i == 0) records.bestShift?.let { Text("Вахта №${shiftNumbers[it.id]}" + if (it.isActive) " · активна" else "", style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Card(Modifier.fillMaxWidth().seasonalCard(14.dp), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Максимум смен подряд", modifier = Modifier.weight(1f)); Text(records.streak.toString(), color = gold, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun RecordCelebration(event: Long, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(1f) }
    var lastEvent by remember { mutableLongStateOf(event) }
    LaunchedEffect(event) {
        if (event > lastEvent) { lastEvent = event; progress.snapTo(0f); progress.animateTo(1f, tween(850)) }
    }
    Canvas(modifier) {
        val p = progress.value
        if (p >= 1f) return@Canvas
        repeat(22) { i ->
            val angle = i * 2.399963
            val distance = size.minDimension * (0.10f + 0.35f * p) * (0.6f + (i % 4) * 0.13f)
            val position = Offset(size.width * .5f + cos(angle).toFloat() * distance,
                size.height * .25f + sin(angle).toFloat() * distance + p * p * 40.dp.toPx())
            drawCircle(if (i % 3 == 0) Color(0xFFFFE2A0) else Color(0xFFFF9C32), radius = (2 + i % 3).dp.toPx(), center = position, alpha = (1f - p).coerceIn(0f, 1f))
        }
    }
}
