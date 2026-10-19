package com.example.uchetdohodapro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Background = Color(0xFF111315)
private val Panel = Color(0xFF1C2023)
private val PanelLight = Color(0xFF272C30)
private val Orange = Color(0xFFFF8A24)
private val Green = Color(0xFF43D17D)
private val Red = Color(0xFFFF5C5C)
private val MainText = Color(0xFFF4F5F6)
private val SecondaryText = Color(0xFF9DA5AB)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Orange,
                    secondary = Green,
                    background = Background,
                    surface = Panel,
                    onPrimary = Color.Black,
                    onBackground = MainText,
                    onSurface = MainText
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Background
                ) {
                    IncomeApp()
                }
            }
        }
    }
}

@Composable
private fun IncomeApp() {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showTariffs by remember { mutableStateOf(false) }
    var showWorkDay by remember { mutableStateOf(false) }

    if (showTariffs) {
        TariffsScreen(
            onBack = { showTariffs = false }
        )
        return
    }
    
    if (showWorkDay) {
    WorkDayScreen(
        onBack = { showWorkDay = false }
    )
    return
    }

    Scaffold(
        containerColor = Background,
        bottomBar = {
            NavigationBar(
                containerColor = Panel,
                contentColor = MainText
            ) {
                val tabs = listOf(
                    "Главная",
                    "Вахта",
                    "Статистика",
                    "Профиль"
                )
                val symbols = listOf("⌂", "▣", "▥", "⚙")

                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Text(
                                text = symbols[index],
                                fontSize = 20.sp,
                                color = if (selectedTab == index) Orange
                                else SecondaryText
                            )
                        },
                        label = {
                            Text(text = title, fontSize = 10.sp)
                        }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Background)
        ) {
            when (selectedTab) {
                0 -> HomeScreen(
                    onAddClick = { showAddDialog = true }
                )

                1 -> SimpleScreen(
                    title = "Вахта",
                    subtitle = "Управление вахтами",
                    symbol = "▣"
                )

                2 -> SimpleScreen(
                    title = "Статистика",
                    subtitle = "Доходы и показатели",
                    symbol = "▥"
                )

                else -> ProfileScreen(
                    onTariffsClick = { showTariffs = true }
                )
            }
        }
    }

    if (showAddDialog) {
    AlertDialog(
        onDismissRequest = { showAddDialog = false },
        containerColor = Panel,
        title = {
            Text("Новый рабочий день", color = MainText)
        },
        text = {
            Text(
                "Открыть форму для ввода показателей рабочего дня?",
                color = SecondaryText
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    showAddDialog = false
                    showWorkDay = true
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Orange,
                    contentColor = Color.Black
                )
            ) {
                Text("Открыть форму")
            }
        },
        dismissButton = {
            TextButton(
                onClick = { showAddDialog = false }
            ) {
                Text("Отмена", color = MainText)
            }
        }
    )
}
}

@Composable
private fun HomeScreen(onAddClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Text(
            "УЧЁТ ДОХОДА",
            color = Orange,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )

        Spacer(Modifier.height(6.dp))

        Text(
            "Главная",
            color = MainText,
            fontSize = 29.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Твой заработок под контролем",
            color = SecondaryText,
            fontSize = 14.sp
        )

        Spacer(Modifier.height(22.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Panel)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "ЗАРАБОТАНО ЗА ВАХТУ",
                    color = SecondaryText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    "0 ₽",
                    color = Green,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(16.dp))

                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MiniValue("Начислено", "0 ₽", Green)
                    MiniValue("Авансы", "0 ₽", Orange)
                    MiniValue("Штрафы", "0 ₽", Red)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SmallCard(
                modifier = Modifier.weight(1f),
                title = "Рабочие дни",
                value = "0",
                subtitle = "за вахту"
            )

            SmallCard(
                modifier = Modifier.weight(1f),
                title = "Отработано",
                value = "0 ч",
                subtitle = "всего часов"
            )
        }

        Spacer(Modifier.height(22.dp))

        Button(
            onClick = onAddClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Orange,
                contentColor = Color.Black
            )
        ) {
            Text(
                "+  Добавить рабочий день",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            "ПОСЛЕДНИЕ ЗАПИСИ",
            color = SecondaryText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Panel)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Пока нет записей",
                    color = MainText,
                    fontSize = 16.sp
                )

                Spacer(Modifier.height(5.dp))

                Text(
                    "Добавленные рабочие дни появятся здесь",
                    color = SecondaryText,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun MiniValue(
    title: String,
    value: String,
    color: Color
) {
    Column {
        Text(title, color = SecondaryText, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            color = color,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SmallCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PanelLight)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = SecondaryText, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                value,
                color = MainText,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = SecondaryText, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SimpleScreen(
    title: String,
    subtitle: String,
    symbol: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(22.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            "УЧЁТ ДОХОДА",
            color = Orange,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )

        Spacer(Modifier.height(8.dp))

        Text(
            title,
            color = MainText,
            fontSize = 29.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Panel)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(symbol, color = Orange, fontSize = 38.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    subtitle,
                    color = MainText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Этот раздел подключим на следующем этапе.",
                    color = SecondaryText,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun ProfileScreen(onTariffsClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(22.dp)
    ) {
        Text(
            "УЧЁТ ДОХОДА",
            color = Orange,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Профиль",
            color = MainText,
            fontSize = 29.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(22.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Panel)
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    "Расчёт заработка",
                    color = MainText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    "Настрой стоимость пиков, веса, объёма, боксов и часов.",
                    color = SecondaryText,
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = onTariffsClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Orange,
                        contentColor = Color.Black
                    )
                ) {
                    Text(
                        "Настроить тарифы",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Panel)
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    "Резервная копия",
                    color = MainText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(5.dp))

                Text(
                    "Добавим сохранение и восстановление данных позднее.",
                    color = SecondaryText,
                    fontSize = 13.sp
                )
            }
        }
    }
}
