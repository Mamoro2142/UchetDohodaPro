package com.example.uchetdohodapro

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val UpdateBackground = Color(0xFF111315)
private val UpdatePanel = Color(0xFF1C2023)
private val UpdateOrange = Color(0xFFFF8A24)
private val UpdateGreen = Color(0xFF43D17D)
private val UpdateText = Color(0xFFF4F5F6)
private val UpdateMuted = Color(0xFF9DA5AB)

internal data class UpdateRelease(val version: String, val title: String, val changes: List<String>)

// Keep this list in sync with docs/CHANGELOG.md when publishing a release.
internal val updateReleases = listOf(
    UpdateRelease("1.1.14", "Произвольные показатели", listOf(
        "Добавление нескольких показателей одного типа с разными тарифами.",
        "Переименование, удаление и изменение порядка показателей в группе.",
        "Раздельный учёт и сохранение исторических ставок и рекордов."
    )),
    UpdateRelease("1.1.13", "Рабочие группы", listOf(
        "Создание и редактирование групп рабочих показателей.",
        "Индивидуальные тарифы и работа в нескольких группах за один день.",
        "Отдельная история тарифов, статистика и рекорды по группам.",
        "Сворачиваемый архив завершённых вахт.",
        "История обновлений в профиле."
    )),
    UpdateRelease("1.1.12", "Расширение функционала", listOf(
        "Автоматические резервные копии.", "История изменения тарифов.",
        "Личные рекорды по заработку и рабочим показателям.",
        "Механическая прокрутка изменившихся цифр заработка."
    )),
    UpdateRelease("1.1.11", "Производительность", listOf(
        "Общее состояние данных для экранов приложения.",
        "Устранение кратковременного обнуления дохода и пустых списков при переходах."
    )),
    UpdateRelease("1.1.10", "Новогоднее оформление", listOf(
        "Новогодняя тема на всех вкладках и вложенных экранах.",
        "Общий таймер праздничных анимаций."
    )),
    UpdateRelease("1.1.9", "Улучшение оформления", listOf(
        "Одноразовая подсказка о включении праздничной темы.",
        "Пульсация снежинки и золотистые искры."
    )),
    UpdateRelease("1.1.8", "Новогодняя тема", listOf(
        "Гирлянда, еловая ветка, снегопад и декор карточек на главной.",
        "Переключение оформления по снежинке."
    )),
    UpdateRelease("1.1.7", "Удобство расчёта", listOf(
        "Кнопка заявления на расчёт перенесена в последнюю завершённую вахту.",
        "Сохранены разделители тысяч и редактирование плана смен."
    )),
    UpdateRelease("1.1.6", "Исправления интерфейса", listOf(
        "Пробелы между разрядами денежных сумм.",
        "Явные кнопки изменения ожидаемого количества смен."
    )),
    UpdateRelease("1.1.5", "Календарь и прогресс", listOf(
        "Интерактивный календарь вахты.",
        "Планируемое количество смен и счётчик фактически отработанных дней."
    )),
    UpdateRelease("1.1.4", "Управление вахтой", listOf(
        "Кнопка паузы и продолжения в карточке активной вахты."
    )),
    UpdateRelease("1.1.3", "Улучшение календаря", listOf(
        "Исправлен выбор дат паузы и возобновления.",
        "Цветовое оформление рабочих дней, выходных и пауз.",
        "Уведомление о копировании заявления на расчёт."
    )),
    UpdateRelease("1.1.2", "Расширенное управление вахтой", listOf(
        "Доход текущей вахты на главной; общие итоги в статистике.",
        "Паузы вахты и автоматический подсчёт выходных.",
        "Формирование заявления на расчёт."
    )),
    UpdateRelease("1.1.1", "Терминология", listOf(
        "Единые названия показателей: Пики, Вес, Объём, Боксы, Часы."
    )),
    UpdateRelease("1.1.0", "Большое обновление", listOf(
        "Резервное копирование и восстановление данных.",
        "Учёт авансов и штрафов.", "Дата последнего сохранения тарифов.",
        "Исправления дробного ввода, дат и связей рабочих дней с вахтами.",
        "Добавление рабочего дня за одно нажатие."
    ))
)

@Composable
@Suppress("DEPRECATION")
private fun installedVersionName(): String? {
    val context = LocalContext.current
    return remember(context) {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
            null
        }
    }
}

@Composable
internal fun UpdateHistoryEntry(onClick: () -> Unit) {
    val version = installedVersionName()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("О приложении", color = UpdateMuted, fontSize = 13.sp)
        Card(
            modifier = Modifier.fillMaxWidth().seasonalCard(18.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = UpdatePanel)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("↺", color = UpdateOrange, fontSize = 30.sp)
                Column(Modifier.weight(1f)) {
                    Text("История обновлений", color = UpdateText, fontWeight = FontWeight.SemiBold)
                    Text(version?.let { "Что нового в версии $it" } ?: "Что нового", color = UpdateMuted, fontSize = 13.sp)
                }
                Text("›", color = UpdateOrange, fontSize = 26.sp)
            }
        }
        Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painter = painterResource(R.mipmap.ic_launcher), contentDescription = null, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(6.dp))
            Text("Учёт дохода Pro", color = UpdateText, fontWeight = FontWeight.Medium)
            Text(version?.let { "Версия $it" } ?: "Версия неизвестна", color = UpdateMuted, fontSize = 13.sp)
        }
    }
}

@Composable
internal fun UpdateHistoryScreen(onBack: () -> Unit) {
    val installed = installedVersionName()
    Column(Modifier.fillMaxSize().background(UpdateBackground)) {
        TextButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp, top = 8.dp)) {
            Text("← Назад", color = UpdateOrange)
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                WinterPageHeader()
                Text("История обновлений", color = UpdateText, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().seasonalCard(18.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF342C24))
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Установленная версия", color = UpdateMuted, fontSize = 13.sp)
                        Text(installed ?: "Неизвестна", color = UpdateOrange, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            items(updateReleases, key = { it.version }) { release ->
                UpdateReleaseCard(release, installed == release.version)
            }
        }
    }
}

@Composable
private fun UpdateReleaseCard(release: UpdateRelease, current: Boolean) {
    var expanded by rememberSaveable(release.version, current) { mutableStateOf(current) }
    Card(
        modifier = Modifier.fillMaxWidth().seasonalCard(18.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = UpdatePanel),
        border = if (current) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF9C652E)) else null
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .semantics { stateDescription = if (expanded) "Раскрыто" else "Свёрнуто" }
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(if (current) "✦" else "▣", color = if (current) UpdateOrange else UpdateMuted, fontSize = 23.sp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(release.version, color = UpdateOrange, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    if (current) Text("Текущая версия", color = UpdateGreen, fontSize = 12.sp)
                    Text(release.title, color = UpdateMuted, fontSize = 13.sp)
                }
                Text(if (expanded) "⌃" else "⌄", color = UpdateMuted, fontSize = 24.sp)
            }
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    HorizontalDivider(color = UpdateMuted.copy(alpha = 0.2f))
                    release.changes.forEach { change ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("✓", color = UpdateGreen, fontSize = 14.sp)
                            Text(change, color = UpdateText, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
