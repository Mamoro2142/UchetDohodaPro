
package com.example.uchetdohodapro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.uchetdohodapro.data.WorkDatabase
import java.util.Locale

private val StatsBackground = Color(0xFF111315)
private val StatsPanel = Color(0xFF1C2023)
private val StatsPanelLight = Color(0xFF272C30)
private val StatsOrange = Color(0xFFFF8A24)
private val StatsGreen = Color(0xFF43D17D)
private val StatsText = Color(0xFFF4F5F6)
private val StatsSecondary = Color(0xFF9DA5AB)

private fun statsMoney(value: Double): String {
    return String.format(
        Locale.getDefault(),
        "%,.2f ₽",
        value
    )
}

@Composable
fun StatisticsScreen() {
    val context = LocalContext.current

    val dao = remember(context) {
        WorkDatabase.getInstance(context).workDao()
    }

    val days by remember(dao) {
        dao.observeWorkDays()
    }.collectAsState(initial = emptyList())

    val totalIncome = days.sumOf {
        it.calculateIncome()
    }

    val averageIncome = if (days.isNotEmpty()) {
        totalIncome / days.size
    } else {
        0.0
    }

    val bestDay = days.maxByOrNull {
        it.calculateIncome()
    }

    val monthlyIncome = days
        .filter {
            it.date.length >= 7 &&
                it.date[4] == '-'
        }
        .groupBy {
            it.date.take(7)
        }
        .mapValues { entry ->
            entry.value.sumOf {
                it.calculateIncome()
            }
        }
        .toSortedMap(
            compareByDescending { it }
        )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StatsBackground)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            "УЧЁТ ДОХОДА",
            color = StatsOrange,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Статистика",
            color = StatsText,
            fontSize = 29.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(6.dp))

        Text(
            "Анализ твоего заработка",
            color = StatsSecondary,
            fontSize = 14.sp
        )

        Spacer(Modifier.height(22.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = StatsPanel
            )
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "ЗАРАБОТАНО ВСЕГО",
                    color = StatsSecondary,
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    statsMoney(totalIncome),
                    color = StatsGreen,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatsSmallCard(
                modifier = Modifier.weight(1f),
                title = "Рабочие дни",
                value = days.size.toString()
            )

            StatsSmallCard(
                modifier = Modifier.weight(1f),
                title = "Среднее за день",
                value = statsMoney(averageIncome)
            )
        }

        Spacer(Modifier.height(14.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = StatsPanel
            )
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    "ЛУЧШИЙ ДЕНЬ",
                    color = StatsSecondary,
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    if (bestDay != null) {
                        statsMoney(bestDay.calculateIncome())
                    } else {
                        "Нет данных"
                    },
                    color = StatsGreen,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                if (bestDay != null) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        bestDay.date,
                        color = StatsSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "ДОХОД ПО МЕСЯЦАМ",
            color = StatsSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(Modifier.height(12.dp))

        if (monthlyIncome.isEmpty()) {
            Text(
                "Пока нет данных для статистики",
                color = StatsSecondary,
                fontSize = 14.sp
            )
        } else {
            val maxIncome = monthlyIncome.values
                .maxOrNull()
                ?.coerceAtLeast(1.0) ?: 1.0

            monthlyIncome.forEach { (month, income) ->
                val fraction = (
                    income / maxIncome
                ).toFloat().coerceIn(0f, 1f)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = StatsPanel
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.SpaceBetween
                        ) {
                            Text(
                                month,
                                color = StatsText,
                                fontSize = 15.sp
                            )

                            Text(
                                statsMoney(income),
                                color = StatsGreen,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .background(
                                    StatsPanelLight,
                                    RoundedCornerShape(8.dp)
                                )
                        ) {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .height(10.dp)
                                    .background(
                                        StatsOrange,
                                        RoundedCornerShape(8.dp)
                                    )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
            }
        }

        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun StatsSmallCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = StatsPanelLight
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                title,
                color = StatsSecondary,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(8.dp))

            Text(
                value,
                color = StatsText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
