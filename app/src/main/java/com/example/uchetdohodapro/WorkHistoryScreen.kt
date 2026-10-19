
package com.example.uchetdohodapro

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.example.uchetdohodapro.data.WorkDatabase
import com.example.uchetdohodapro.data.WorkDayEntity
import java.util.Locale

@Composable
fun WorkHistoryScreen() {
    val context = LocalContext.current
    val dao = remember(context) {
        WorkDatabase.getInstance(context).workDao()
    }

    val days by dao.observeWorkDays()
        .collectAsState(initial = emptyList())

    val total = days.sumOf { it.calculateIncome() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text(
            "ИСТОРИЯ РАБОЧИХ ДНЕЙ",
            color = Color(0xFFFF8A24),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1C2023)
            ),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    "Всего заработано",
                    color = Color(0xFF9DA5AB)
                )

                Text(
                    String.format(Locale.getDefault(), "%.2f ₽", total),
                    color = Color(0xFF43D17D),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    "Рабочих дней: ${days.size}",
                    color = Color(0xFFF4F5F6)
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        if (days.isEmpty()) {
            Text(
                "Пока нет сохранённых рабочих дней.",
                color = Color(0xFF9DA5AB)
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(days, key = { it.id }) { day ->
                    WorkHistoryCard(day)
                }
            }
        }
    }
}

@Composable
private fun WorkHistoryCard(day: WorkDayEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF272C30)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                day.date,
                color = Color(0xFFF4F5F6),
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
                color = Color(0xFF43D17D),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            if (day.picks != null) {
                Text("Пики: ${day.picks}", color = Color.White)
            }
            if (day.weight != null) {
                Text("Вес: ${day.weight}", color = Color.White)
            }
            if (day.volume != null) {
                Text("Объём: ${day.volume}", color = Color.White)
            }
            if (day.boxes != null) {
                Text("Коробки: ${day.boxes}", color = Color.White)
            }
            if (day.hours != null) {
                Text("Часы: ${day.hours}", color = Color.White)
            }
        }
    }
}
