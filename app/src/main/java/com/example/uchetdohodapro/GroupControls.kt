package com.example.uchetdohodapro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.uchetdohodapro.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val groupOrange = Color(0xFFFF8A24)
private val groupGreen = Color(0xFF43D17D)

internal data class GroupCatalog(val tariffs: TariffEntity?, val stored: List<WorkGroupEntity>) {
    val groups get() = availableGroups(tariffs, stored)
    val configured get() = stored.isNotEmpty()
}

@Composable
internal fun observeGroupCatalog(): GroupCatalog? {
    val context = LocalContext.current
    val dao = remember(context) { WorkDatabase.getInstance(context).workDao() }
    val flow = remember(dao) { combine(dao.observeTariffs(), dao.observeGroups(), ::GroupCatalog) }
    val catalog by flow.collectAsState(initial = null)
    return catalog
}

@Composable
internal fun GroupSettingsPanel() {
    val catalog = observeGroupCatalog() ?: return
    val context = LocalContext.current
    val dao = remember(context) { WorkDatabase.getInstance(context).workDao() }
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<WorkGroupEntity?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var expandedHistory by remember { mutableStateOf<Set<Long>>(emptySet()) }
    Text("Рабочие группы", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text("У каждой группы собственные показатели и ставки.", style = MaterialTheme.typography.bodySmall)
    catalog.groups.forEach { group -> key(group.id) {
        Card(Modifier.fillMaxWidth().padding(vertical = 5.dp).seasonalCard()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(group.name, color = groupOrange, style = MaterialTheme.typography.titleLarge)
                group.definitions().forEach { metric ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(metric.name); Text(formatRubles(metric.rate), color = groupGreen)
                    }
                }
                Text(group.savedAt?.let { "Последнее сохранение: " + SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(it)) } ?: "Дата сохранения пока не указана", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { error = ""; editing = group }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Редактировать группу") }
                val history = group.history().asReversed()
                if (history.isNotEmpty()) {
                    TextButton(onClick = { expandedHistory = if (group.id in expandedHistory) expandedHistory - group.id else expandedHistory + group.id }) { Text("История группы · ${history.size}") }
                    if (group.id in expandedHistory) history.forEachIndexed { index, old ->
                        HorizontalDivider()
                        Text(old.name + " · " + (old.savedAt?.let { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(it)) } ?: "Дата неизвестна"))
                        val next = if (index == 0) group.definitions() else history[index - 1].definitions
                        old.definitions.forEach { metric ->
                            val newer = next.firstOrNull { it.id == metric.id }
                            val change = if (metric.rate > 0 && newer != null) (newer.rate / metric.rate - 1) * 100 else null
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(metric.name); Text(formatRubles(metric.rate)); Text(change?.takeIf { it.isFinite() }?.let { String.format(Locale.getDefault(), "%+.1f%%", it) } ?: if (newer == null) "Удалён" else "—", color = if ((change ?: 0.0) < 0) MaterialTheme.colorScheme.error else groupGreen)
                            }
                        }
                    }
                }
            }
        }
    } }
    OutlinedButton(onClick = { error = ""; editing = WorkGroupEntity(0, "", 1, writeRates(List(5) { 0.0 }), null) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("+ Создать группу") }
    editing?.let { group -> key(group.id) {
        GroupEditorDialog(group, busy, error, onDismiss = { if (!busy) editing = null }, onSave = { updated ->
            if (!busy) {
                busy = true; error = ""
                scope.launch {
                    try { dao.saveGroup(updated); editing = null }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = e.message ?: "Не удалось сохранить группу" }
                    finally { busy = false }
                }
            }
        })
    } }
}

private data class MetricEditorRow(val id: String, val name: String, val rate: String)
@Composable
private fun GroupEditorDialog(group: WorkGroupEntity, busy: Boolean, error: String, onDismiss: () -> Unit, onSave: (WorkGroupEntity) -> Unit) {
    var name by remember { mutableStateOf(group.name) }
    var rows by remember { mutableStateOf(group.definitions().map { MetricEditorRow(it.id, it.name, editableNumber(it.rate)) }) }
    var validation by remember { mutableStateOf("") }
    fun move(index: Int, delta: Int) { rows = rows.toMutableList().apply { val target = index + delta; if (target in indices) add(target, removeAt(index)) } }
    fun addMetric(id: String, label: String, rate: String) { rows = rows + MetricEditorRow(id, label, rate) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (group.id == 0L) "Новая группа" else "Редактирование группы") }, text = {
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Название группы") }, singleLine = true, enabled = !busy)
            Text("Рабочие показатели · ${rows.size}", fontWeight = FontWeight.Bold)
            rows.forEachIndexed { index, row -> key(row.id) {
                HorizontalDivider()
                OutlinedTextField(row.name, { v -> rows = rows.map { if (it.id == row.id) it.copy(name = v) else it } }, label = { Text("Название показателя") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(row.rate, { v -> rows = rows.map { if (it.id == row.id) it.copy(rate = v) else it } }, label = { Text("Тариф, ₽") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { move(index, -1) }, enabled = !busy && index > 0) { Text("↑") }
                    TextButton(onClick = { move(index, 1) }, enabled = !busy && index < rows.lastIndex) { Text("↓") }
                    TextButton(onClick = { rows = rows.filterNot { it.id == row.id } }, enabled = !busy) { Text("Убрать", color = MaterialTheme.colorScheme.error) }
                }
            } }
            OutlinedButton(onClick = { addMetric("custom:" + java.util.UUID.randomUUID(), "", "0") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("+ Добавить показатель") }
            workMetricNames.forEachIndexed { index, label -> if (rows.none { it.id == "base:$index" }) {
                TextButton(onClick = { addMetric("base:$index", label, editableNumber(group.rates()[index])) }, enabled = !busy) { Text("+ $label") }
            } }
            if (validation.isNotBlank() || error.isNotBlank()) Text(validation.ifBlank { error }, color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(enabled = !busy, onClick = {
        val parsed = rows.mapNotNull { row -> parseNonNegativeNumber(row.rate)?.let { MetricDefinition(row.id, row.name.trim(), it) } }
        val updated = runCatching { require(name.trim().length in 1..80 && parsed.size == rows.size); group.withDefinitions(parsed).copy(name = name.trim()) }.getOrNull()
        if (updated == null) validation = "Укажите название группы, уникальные названия показателей и корректные тарифы"
        else { validation = ""; onSave(updated) }
    }) { Text(if (busy) "Сохранение…" else "Сохранить группу") } }, dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Отмена") } })
}

private data class GroupDraft(val source: DayGroup, val input: List<String>) {
    fun result(): DayGroup = source.withMeasurements(source.entries().mapIndexed { i, entry -> entry.copy(value = parseNonNegativeNumber(input[i])) })
    fun valid(): Boolean = input.all { it.isBlank() || parseNonNegativeNumber(it) != null }
}
private fun DayGroup.draft() = GroupDraft(this, entries().map { editableNumber(it.value) })
private fun WorkGroupEntity.draft() = newDayGroup().draft()

/** A single editor and a single atomic DAO write regardless of group count. */
@Composable
internal fun GroupedDayForm(
    catalog: GroupCatalog, day: WorkDayEntity?, initialDate: String?, initialShiftId: Long?,
    shifts: List<ShiftEntity>, shiftNumbers: Map<Long, Int>, busy: Boolean,
    onBack: () -> Unit, onSave: (WorkDayEntity) -> Unit, onDelete: (() -> Unit)? = null, error: String = "", saved: Boolean = false
) {
    val original = remember(day?.id) { day?.groups()?.associate { it.id to it.draft() } ?: emptyMap() }
    var drafts by remember(day?.id) { mutableStateOf(original) }
    var selected by remember(day?.id) { mutableStateOf(original.keys.toList()) }
    var choosing by remember(day?.id) { mutableStateOf(day == null) }
    var pending by remember { mutableStateOf(selected.toSet()) }
    var date by remember(day?.id) { mutableStateOf(parseStoredDate(day?.date ?: initialDate.orEmpty())?.toString() ?: LocalDate.now().toString()) }
    var shiftId by remember(day?.id) { mutableStateOf(if (day != null) day.shiftId else initialShiftId ?: shifts.firstOrNull { it.isActive }?.id) }
    var chooseShift by remember { mutableStateOf(false) }
    var validation by remember { mutableStateOf("") }
    val available = catalog.groups.filter { it.id != 1L || catalog.tariffs != null || it.id in original }
    val activeDrafts = selected.mapNotNull { drafts[it] }
    val storedDate = if (day != null && parseStoredDate(day.date)?.toString() == date) day.date else date
    val result = runCatching { withDayGroups((day ?: WorkDayEntity(date = date)).copy(date = storedDate, shiftId = shiftId), activeDrafts.map { it.result() }) }.getOrNull()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WinterPageHeader()
        TextButton(onClick = onBack, enabled = !busy) { Text("← Назад") }
        if (choosing) {
            Text("Выберите группы", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("В каких группах вы работали?")
            val candidates = (original.values.map { it.source.id to (it.source.name to it.source.entries().map { e -> e.definition.name }) } + available.map { it.id to (it.name to it.definitions().map { d -> d.name }) }).distinctBy { it.first }
            candidates.forEach { (id, info) ->
                Card(Modifier.fillMaxWidth().seasonalCard()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(id in pending, { checked -> pending = if (checked) pending + id else pending - id })
                        Column(Modifier.weight(1f)) { Text(info.first, color = groupOrange, fontWeight = FontWeight.Bold); Text(info.second.joinToString(", "), style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            if (candidates.isEmpty()) Text("Сначала настройте тарифы в профиле")
            Button(enabled = pending.isNotEmpty(), onClick = {
                drafts = drafts + pending.filterNot { it in drafts }.mapNotNull { id -> available.firstOrNull { it.id == id }?.let { id to it.draft() } }.toMap()
                selected = pending.filter { it in drafts }; choosing = false
            }, modifier = Modifier.fillMaxWidth()) { Text("Продолжить") }
            if (day != null || selected.isNotEmpty()) TextButton(onClick = { choosing = false }) { Text("Отмена") }
        } else {
            Text(if (day == null) "НОВЫЙ РАБОЧИЙ ДЕНЬ" else "РЕДАКТИРОВАНИЕ ДНЯ", color = groupOrange, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(date, { date = it }, label = { Text("Дата (ГГГГ-ММ-ДД)") }, singleLine = true, enabled = !busy && !saved, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { chooseShift = true }, enabled = !busy && !saved, modifier = Modifier.fillMaxWidth()) { Text(shiftId?.let { "Вахта №${shiftNumbers[it] ?: "—"}" } ?: "Без привязки к вахте") }
            activeDrafts.forEach { draft -> key(draft.source.id) {
                Card(Modifier.fillMaxWidth().seasonalCard()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(draft.source.name, color = groupOrange, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            TextButton(enabled = !busy && !saved && selected.size > 1, onClick = { selected = selected - draft.source.id }) { Text("Убрать") }
                        }
                        draft.source.entries().forEachIndexed { i, entry ->
                            OutlinedTextField(draft.input[i], { value -> drafts = drafts + (draft.source.id to draft.copy(input = draft.input.mapIndexed { j,v -> if (i == j) value else v })) }, label = { Text(entry.definition.name) }, supportingText = { Text("Тариф: ${formatRubles(entry.definition.rate)}") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, enabled = !busy && !saved, modifier = Modifier.fillMaxWidth())
                        }
                        Text("Заработок: ${draft.result().income().takeIf { it.isFinite() }?.let(::formatRubles) ?: "Проверьте показатели"}", color = groupGreen, fontWeight = FontWeight.Bold)
                    }
                }
            } }
            OutlinedButton(onClick = { pending = selected.toSet(); choosing = true }, enabled = !busy && !saved, modifier = Modifier.fillMaxWidth()) { Text("Изменить группы / добавить ещё") }
            Card(Modifier.fillMaxWidth().seasonalCard()) { Column(Modifier.padding(16.dp)) {
                Text("Общий заработок за день"); Text(result?.let { formatRubles(it.calculateIncome()) } ?: "Проверьте показатели", color = groupGreen, style = MaterialTheme.typography.headlineSmall)
                Text("Групп: ${selected.size} · 1 рабочий день", style = MaterialTheme.typography.bodySmall)
            } }
            if (validation.isNotBlank() || error.isNotBlank()) Text(validation.ifBlank { error }, color = MaterialTheme.colorScheme.error)
            if (saved) Text("Рабочий день сохранён", color = groupGreen)
            Button(enabled = !busy && !saved, modifier = Modifier.fillMaxWidth(), onClick = {
                when {
                    parseStoredDate(date)?.toString() != date -> validation = "Укажите корректную дату"
                    result == null || activeDrafts.any { !it.valid() } -> validation = "Проверьте показатели и тарифы"
                    else -> { validation = ""; onSave(result) }
                }
            }) { Text(if (busy) "Сохранение…" else if (saved) "Сохранено" else if (day == null) "СОХРАНИТЬ РАБОЧИЙ ДЕНЬ" else "СОХРАНИТЬ ИЗМЕНЕНИЯ") }
            onDelete?.let { action -> OutlinedButton(onClick = action, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Удалить рабочий день", color = MaterialTheme.colorScheme.error) } }
        }
    }
    if (chooseShift) AlertDialog(onDismissRequest = { chooseShift = false }, title = { Text("Выбрать вахту") }, text = {
        Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
            TextButton(onClick = { shiftId = null; chooseShift = false }) { Text("Без привязки") }
            shifts.forEach { shift -> TextButton(onClick = { shiftId = shift.id; chooseShift = false }) { Text("Вахта №${shiftNumbers[shift.id] ?: "—"}" + if (shift.isActive) " (активна)" else " (завершена)") } }
        }
    }, confirmButton = { TextButton(onClick = { chooseShift = false }) { Text("Закрыть") } })
}

@Composable
internal fun GroupedDayBreakdown(day: WorkDayEntity) {
    day.groups().forEach { group ->
        Text(group.name + " · " + formatRubles(group.income()), color = groupOrange, fontWeight = FontWeight.Medium)
        group.entries().forEach { entry -> entry.value?.let { v -> Text("${entry.definition.name}: ${editableNumber(v)} · ${formatRubles(entry.income())}", style = MaterialTheme.typography.bodySmall) } }
    }
}

@Composable
internal fun GroupIncomePanel(days: List<WorkDayEntity>, includeRecords: Boolean = false) {
    val totals = remember(days) { days.flatMap { day -> day.groups().map { day to it } }.groupBy { it.second.id } }
    if (totals.isEmpty() || (totals.size == 1 && totals.containsKey(1L) && days.none { it.groupsJson != null })) return
    val overall = days.sumOf { it.calculateIncome() }
    Card(Modifier.fillMaxWidth().seasonalCard()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Доход по группам", style = MaterialTheme.typography.titleLarge)
        val sorted = totals.values.sortedByDescending { rows -> rows.sumOf { it.second.income() } }
        val palette = listOf(groupOrange, groupGreen, Color(0xFF519BD5), Color(0xFFE7C65D), Color(0xFFA18BD8))
        if (overall > 0 && overall.isFinite()) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(168.dp).padding(10.dp)) {
                var start = -90f
                sorted.forEachIndexed { index, rows ->
                    val sweep = (rows.sumOf { it.second.income() } / overall * 360).toFloat().coerceIn(0f, 360f)
                    if (sweep > 0f) drawArc(palette[index % palette.size], start, sweep, false, style = Stroke(width = 13.dp.toPx()))
                    start += sweep
                }
            }
            Text(formatRubles(overall), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        sorted.forEachIndexed { index, rows ->
            val sum = rows.sumOf { it.second.income() }
            val name = rows.maxByOrNull { parseStoredDate(it.first.date) ?: LocalDate.MIN }!!.second.name
            HorizontalDivider(); Text(name, color = palette[index % palette.size], fontWeight = FontWeight.Bold)
            Text(formatRubles(sum), color = groupGreen)
            Text("Смен: ${uniqueWorkDayCount(rows.map { it.first })}" + if (overall > 0 && overall.isFinite()) " · ${String.format(Locale.getDefault(), "%.1f%%", sum / overall * 100)}" else "")
            val byMetric = rows.flatMap { (day, group) -> group.entries().filter { it.value != null }.map { day to it } }.groupBy { it.second.definition.id }
            byMetric.values.forEach { entries ->
                val label = entries.maxByOrNull { parseStoredDate(it.first.date) ?: LocalDate.MIN }!!.second.definition.name
                Text("$label: ${editableNumber(entries.sumOf { it.second.value ?: 0.0 })} · ${formatRubles(entries.sumOf { it.second.income() })}", style = MaterialTheme.typography.bodySmall)
                if (includeRecords) Text("Максимум · $label: ${editableNumber(entries.maxOf { it.second.value ?: 0.0 })}", style = MaterialTheme.typography.bodySmall)
            }
            if (includeRecords) {
                Text("Лучшая смена: ${formatRubles(rows.groupBy { parseStoredDate(it.first.date)?.toString() ?: it.first.date }.values.maxOf { list -> list.sumOf { it.second.income() } })}")
                rows.groupBy { it.first.shiftId }.filterKeys { it != null }.values.maxOfOrNull { list -> list.sumOf { it.second.income() } }?.let { Text("Лучшая вахта: ${formatRubles(it)}") }
                
            }
        }
    } }
}
