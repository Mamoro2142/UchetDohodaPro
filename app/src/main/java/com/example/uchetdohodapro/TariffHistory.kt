package com.example.uchetdohodapro

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.example.uchetdohodapro.data.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun TariffHistoryPanel(current: TariffEntity?, entries: List<TariffHistoryEntity>) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) { Text(if (expanded) "Скрыть историю тарифов" else "История тарифов") }
    if (!expanded) return
    val versions = listOfNotNull(current?.let { Triple("Действующие тарифы", it.savedAt, it.rates()) }) +
        entries.map { Triple("Предыдущие тарифы", it.savedAt, it.rates()) }
    if (entries.isEmpty()) Text("История появится после изменения ставок. Старые даты не восстанавливаются задним числом.", style = MaterialTheme.typography.bodySmall)
    versions.forEachIndexed { i, version ->
        Card(Modifier.fillMaxWidth().padding(vertical = 6.dp).seasonalCard(16.dp), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(version.first, fontWeight = FontWeight.Bold, color = if (i == 0 && current != null) Color(0xFF43D17D) else MaterialTheme.colorScheme.onSurface)
                Text(version.second?.let { "Сохранено: " + SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(it)) } ?: "Дата сохранения неизвестна", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                listOf("Пики", "Вес", "Объём", "Боксы", "Часы").forEachIndexed { j, title ->
                    val rate = version.third[j]
                    val prior = versions.getOrNull(i + 1)?.third?.get(j)
                    val diff = prior?.let { if (it > 0) (rate - it) / it * 100 else null }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(title, modifier = Modifier.weight(1f))
                        Text(editableNumber(rate).replace('.', ',') + " ₽")
                        if (prior != null) {
                            val color = if (rate > prior) Color(0xFF43D17D) else if (rate < prior) Color(0xFFFF5C5C) else Color(0xFF9DA5AB)
                            val label = when { rate == prior -> "—"; diff == null || !diff.isFinite() -> "новая"; else -> String.format(Locale.ROOT, "%+.1f%%", diff).replace('.', ',') }
                            Text(label, color = color, modifier = Modifier.padding(start = 10.dp))
                        }
                    }
                }
            }
        }
    }
}
