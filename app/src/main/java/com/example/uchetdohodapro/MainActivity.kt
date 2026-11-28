
package com.example.uchetdohodapro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.uchetdohodapro.data.*

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
                    IncomeDataHost { MoneyAnimationHost { WinterThemeHost { IncomeApp() } } }
                }
            }
        }
    }
}

@Composable
private fun IncomeApp() {
    var selectedTab by rememberSaveable {
        mutableIntStateOf(0)
    }
    var shiftDetailsOpen by remember {
        mutableStateOf(false)
    }
    var showTariffs by remember {
        mutableStateOf(false)
    }

    var showWorkDay by remember {
        mutableStateOf(false)
    }

    var showHistory by remember {
        mutableStateOf(false)
    }

    var showUpdateHistory by rememberSaveable { mutableStateOf(false) }

    // Перехватываем системное действие "Назад".
    // На главной вкладке оно ничего не делает.
    BackHandler(enabled = true) {
        when {
            showUpdateHistory -> {
                showUpdateHistory = false
            }

            showTariffs -> {
                showTariffs = false
            }

            showWorkDay -> {
                showWorkDay = false
            }

            showHistory -> {
                showHistory = false
            }

            selectedTab != 0 -> {
                selectedTab = 0
            }

            else -> {
                // Главная: остаёмся в приложении.
            }
        }
    }

    if (showUpdateHistory) {
        UpdateHistoryScreen(onBack = { showUpdateHistory = false })
        return
    }

    if (showTariffs) {
        TariffsScreen(
            onBack = {
                showTariffs = false
            }
        )
        return
    }

    if (showWorkDay) {
        WorkDayScreen(
            onBack = {
                showWorkDay = false
            }
        )
        return
    }

    if (showHistory) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Background)
        ) {
            TextButton(
                onClick = {
                    showHistory = false
                },
                modifier = Modifier.padding(
                    start = 12.dp,
                    top = 8.dp
                )
            ) {
                Text(
                    "← Назад",
                    color = Orange
                )
            }

            Box(
                modifier = Modifier.weight(1f)
            ) {
                WorkHistoryScreen()
            }
        }
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

                val symbols = listOf(
                    "⌂",
                    "▣",
                    "▥",
                    "⚙"
                )

                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                        },
                        icon = {
                            Text(
                                text = symbols[index],
                                fontSize = 20.sp,
                                color = if (
                                    selectedTab == index
                                ) {
                                    Orange
                                } else {
                                    SecondaryText
                                }
                            )
                        },
                        label = {
                            Text(
                                text = title,
                                fontSize = 10.sp
                            )
                        }
                    )
                }
            }
        }
    ) { padding ->

        var horizontalDrag by remember {
            mutableFloatStateOf(0f)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Background)
           .pointerInput(selectedTab, shiftDetailsOpen) {
    if (!shiftDetailsOpen) {
        detectHorizontalDragGestures(
                        onDragStart = {
                            horizontalDrag = 0f
                        },
                        onHorizontalDrag = {
                                change, dragAmount ->
                            horizontalDrag += dragAmount
                            change.consume()
                        },
                        onDragEnd = {
                            val threshold = 90.dp.toPx()

                            when {
                                horizontalDrag < -threshold &&
                                    selectedTab < 3 -> {
                                    selectedTab += 1
                                }

                                horizontalDrag > threshold &&
                                    selectedTab > 0 -> {
                                    selectedTab -= 1
                                }
                            }

                            horizontalDrag = 0f
                        },
                     onDragCancel = { horizontalDrag = 0f }
                    )
                 }
           }
        ) {
            when (selectedTab) {
                0 -> HomeScreen(
                    onAddClick = {
                        if (!showWorkDay) showWorkDay = true
                    },
                    onHistoryClick = {
                        showHistory = true
                    }
                )

                1 -> ShiftScreen(
    onDetailsVisibilityChanged = { isOpen ->
        shiftDetailsOpen = isOpen
    }
)

                2 -> StatisticsScreen()

                else -> ProfileScreen(
                    onTariffsClick = {
                        showTariffs = true
                    },
                    onUpdateHistoryClick = { showUpdateHistory = true }
                )
            }
        }
    }

}

@Composable
private fun HomeScreen(
    onAddClick: () -> Unit,
    onHistoryClick: () -> Unit
) {
    val appearance = checkNotNull(LocalWinterAppearance.current)
    val winterEnabled = appearance.enabled
    val winterHintSeen = appearance.hintSeen
    val winterPhase = LocalWinterPhase.current
    // When the theme is off, only the Home toggle needs animation.
    val togglePhase = winterPhase ?: rememberWinterPhase()

    val data = LocalIncomeSnapshot.current
    val days = data.days
    val shifts = data.shifts
    val operations = data.operations
    val activeShift = shifts.firstOrNull { it.isActive }
    val currentDays = days.filter { activeShift != null && it.shiftId == activeShift.id }
    val currentOperations = operations.filter { activeShift != null && it.shiftId == activeShift.id }
    val advances = financeTotal(currentOperations, ADVANCE)
    val fines = financeTotal(currentOperations, FINE)

    val totalIncome = currentDays.sumOf {
        it.calculateIncome()
    }

    val totalShifts = shifts.size
    val totalWorkDays = uniqueWorkDayCount(days)
    val recentDays = days.take(3)

    fun money(value: Double): String {
        return formatRubles(value)
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Background)
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = 20.dp,
                    vertical = 18.dp
                )
        ) {
            winterPhase?.let { WinterHeader(it) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "УЧЁТ ДОХОДА",
                        color = Orange,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Главная", color = MainText, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                    Text("Твой заработок под контролем", color = SecondaryText, fontSize = 14.sp)
                }
                WinterToggle(winterEnabled, togglePhase, onToggle = appearance::toggle)
            }

            if (showWinterHint(winterEnabled, winterHintSeen)) {
                Spacer(Modifier.height(4.dp))
                WinterHint()
            }
            Spacer(Modifier.height(22.dp))

            Card(
                modifier = Modifier.fillMaxWidth().winterCard(winterPhase),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Panel
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        if (activeShift == null) "НЕТ АКТИВНОЙ ВАХТЫ" else "ЗАРАБОТАНО ЗА ВАХТУ",
                        color = SecondaryText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(Modifier.height(8.dp))

                    AnimatedMoney(money(totalIncome), color = Green, fontSize = 34.sp, counter = "home")

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.SpaceBetween
                    ) {
                        MiniValue(
                            "Начислено",
                            money(totalIncome),
                            Green
                        )

                        MiniValue(
                            "Авансы",
                            money(advances),
                            Orange
                        )

                        MiniValue(
                            "Штрафы",
                            money(fines),
                            Red
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Остаток к получению: ${money(totalIncome - advances - fines)}", color = MainText)
                }
            }

            Spacer(Modifier.height(14.dp))
            if (activeShift?.plannedWorkDays != null) {
                ShiftProgressCard(activeShift, currentDays, winterPhase)
                Spacer(Modifier.height(14.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                horizontalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = PanelLight
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        horizontalArrangement =
                            Arrangement.SpaceEvenly,
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment =
                                Alignment.CenterHorizontally,
                            verticalArrangement =
                                Arrangement.Center
                        ) {
                            Text(
                                "ВСЕГО ВАХТ",
                                color = SecondaryText,
                                fontSize = 10.sp,
                                lineHeight = 12.sp,
                                textAlign = TextAlign.Center
                            )

                            Spacer(Modifier.height(8.dp))

                            Text(
                                totalShifts.toString(),
                                color = Orange,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment =
                                Alignment.CenterHorizontally,
                            verticalArrangement =
                                Arrangement.Center
                        ) {
                            Text(
                                "РАБОЧИХ ДНЕЙ",
                                color = SecondaryText,
                                fontSize = 10.sp,
                                lineHeight = 12.sp,
                                textAlign = TextAlign.Center
                            )

                            Spacer(Modifier.height(8.dp))

                            Text(
                                totalWorkDays.toString(),
                                color = Green,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = PanelLight
                    )
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val textSize = (
                            maxWidth.value / 8.5f
                        ).coerceIn(16f, 30f)

                        Text(
                            text = "Кадры 24",
                            color = Orange,
                            fontSize = textSize.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
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

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onHistoryClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    "История рабочих дней",
                    fontSize = 15.sp,
                    color = Orange,
                    fontWeight = FontWeight.SemiBold
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

            if (recentDays.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Panel
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment =
                            Alignment.CenterHorizontally
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
            } else {
                recentDays.forEach { day ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Panel
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalArrangement =
                                Arrangement.SpaceBetween,
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            Text(
                                day.date,
                                color = MainText,
                                fontSize = 15.sp,
                                fontWeight =
                                    FontWeight.SemiBold
                            )

                            Text(
                                money(day.calculateIncome()),
                                color = Green,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                }
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
        Text(
            title,
            color = SecondaryText,
            fontSize = 11.sp
        )

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
private fun ProfileScreen(
    onTariffsClick: () -> Unit,
    onUpdateHistoryClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(22.dp)
    ) {
        WinterPageHeader()
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
            modifier = Modifier.fillMaxWidth().seasonalCard(18.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = Panel
            )
        ) {
            Column(
                modifier = Modifier.padding(18.dp)
            ) {
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
            modifier = Modifier.fillMaxWidth().seasonalCard(18.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = Panel
            )
        ) {
            Column(
                modifier = Modifier.padding(18.dp)
            ) {
                Text(
                    "Резервная копия",
                    color = MainText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(5.dp))

                BackupControls()
            }
        }
        Spacer(Modifier.height(14.dp))
        MoneyOperationsPanel()
        Spacer(Modifier.height(20.dp))
        UpdateHistoryEntry(onClick = onUpdateHistoryClick)
    }
}
