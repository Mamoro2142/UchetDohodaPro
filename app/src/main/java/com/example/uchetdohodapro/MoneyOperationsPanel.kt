package com.example.uchetdohodapro

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.uchetdohodapro.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

@Composable
fun MoneyOperationsPanel(shiftId: Long? = null) {
    val context = LocalContext.current
    val dao = remember(context) { WorkDatabase.getInstance(context).workDao() }
    val scope = rememberCoroutineScope()
    val allOperations by remember(dao) { dao.observeMoneyOperations() }.collectAsState(initial = emptyList())
    val shifts by remember(dao) { dao.observeShifts() }.collectAsState(initial = emptyList())
    val operations = allOperations.filter { shiftId == null || it.shiftId == shiftId }
    val numbers = shifts.sortedBy { it.id }.mapIndexed { index, shift -> shift.id to index + 1 }.toMap()
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MoneyOperationEntity?>(null) }
    var deleting by remember { mutableStateOf<MoneyOperationEntity?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    fun money(value: Double) = formatRubles(value)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Авансы и штрафы", style = MaterialTheme.typography.titleMedium)
        Text("Авансы: ${money(financeTotal(operations, ADVANCE))}")
        Text("Штрафы: ${money(financeTotal(operations, FINE))}")
        Button(enabled = !busy && !showForm, modifier = Modifier.fillMaxWidth(), onClick = {
            editing = null; error = ""; showForm = true
        }) { Text("Добавить аванс или штраф") }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        if (operations.isEmpty()) Text("Финансовых операций пока нет", style = MaterialTheme.typography.bodySmall)
        operations.forEach { operation ->
            Card(Modifier.fillMaxWidth().seasonalCard()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${operation.date} · ${if (operation.type == ADVANCE) "Аванс" else "Штраф"} · ${money(operation.amountMinor / 100.0)}")
                    Text(operation.shiftId?.let { numbers[it]?.let { n -> "Вахта №$n" } } ?: "Без привязки к вахте", style = MaterialTheme.typography.bodySmall)
                    if (operation.comment.isNotBlank()) Text(operation.comment)
                    Row {
                        TextButton(enabled = !busy, onClick = { editing = operation; error = ""; showForm = true }) { Text("Изменить") }
                        TextButton(enabled = !busy, onClick = { deleting = operation; error = "" }) { Text("Удалить") }
                    }
                }
            }
        }
    }
    if (showForm) {
        val current = editing
        var selectedShift by remember(current?.id, shiftId) {
            mutableStateOf(if (current != null) current.shiftId else
                shiftId ?: shifts.firstOrNull { it.isActive }?.id ?: shifts.firstOrNull()?.id)
        }
        var type by remember(current?.id) { mutableStateOf(current?.type ?: ADVANCE) }
        var date by remember(current?.id) { mutableStateOf(current?.date ?: LocalDate.now().toString()) }
        var amount by remember(current?.id) { mutableStateOf(current?.let { BigDecimal.valueOf(it.amountMinor, 2).toPlainString() } ?: "") }
        var comment by remember(current?.id) { mutableStateOf(current?.comment ?: "") }
        AlertDialog(
            onDismissRequest = { if (!busy) showForm = false },
            title = { Text(if (current == null) "Новая операция" else "Изменить операцию") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(enabled = !busy, selected = type == ADVANCE, onClick = { type = ADVANCE }, label = { Text("Аванс") })
                        FilterChip(enabled = !busy, selected = type == FINE, onClick = { type = FINE }, label = { Text("Штраф") })
                    }
                    OutlinedTextField(value = amount, onValueChange = { amount = it }, enabled = !busy,
                        label = { Text("Сумма, ₽") }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                    OutlinedTextField(value = date, onValueChange = { date = it }, enabled = !busy,
                        label = { Text("Дата (ГГГГ-ММ-ДД)") }, singleLine = true)
                    OutlinedTextField(value = comment, onValueChange = { if (it.length <= 4000) comment = it }, enabled = !busy,
                        label = { Text("Комментарий (необязательно)") })
                    Text("Вахта")
                    if (shiftId == null && current != null && current.shiftId == null) {
                        FilterChip(enabled = !busy, selected = selectedShift == null,
                            onClick = { selectedShift = null }, label = { Text("Без привязки к вахте") })
                    }
                    if (shiftId == null) shifts.sortedBy { it.id }.forEach { shift ->
                        FilterChip(enabled = !busy, selected = selectedShift == shift.id,
                            onClick = { selectedShift = shift.id }, label = { Text("Вахта №${numbers[shift.id]}") })
                    } else Text("Вахта №${numbers[shiftId] ?: "—"}")
                    if (shifts.isEmpty() && current == null) Text("Сначала создайте вахту")
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                if (busy) return@TextButton
                val minor = parseOperationMinor(amount)
                when {
                    minor == null -> error = "Укажите сумму больше нуля, максимум два знака после запятой."
                    !validOperationDate(date) -> error = "Укажите правильную дату ГГГГ-ММ-ДД."
                    (selectedShift == null && !(current != null && current.shiftId == null)) ||
                        (selectedShift != null && shifts.none { it.id == selectedShift }) -> error = "Выберите существующую вахту."
                    else -> {
                        busy = true; error = ""
                        val operation = MoneyOperationEntity(current?.id ?: 0, selectedShift, date, type, minor, comment.trim())
                        scope.launch {
                            try { dao.saveMoneyOperation(operation); showForm = false; editing = null }
                            catch (e: CancellationException) { throw e }
                            catch (e: Exception) { error = e.message ?: "Не удалось сохранить операцию. Попробуйте ещё раз." }
                            finally { busy = false }
                        }
                    }
                }
            }) { Text(if (busy) "Сохранение…" else "Сохранить") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { showForm = false }) { Text("Отмена") } }
        )
    }
    deleting?.let { operation ->
        AlertDialog(onDismissRequest = { if (!busy) deleting = null },
            title = { Text("Удалить операцию?") },
            text = { Text("Изменится только остаток к получению. Заработок рабочих дней сохранится.") },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                if (busy) return@TextButton
                busy = true
                scope.launch {
                    try { dao.deleteMoneyOperation(operation.id); deleting = null }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { error = "Не удалось удалить операцию."; deleting = null }
                    finally { busy = false }
                }
            }) { Text("Удалить") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { deleting = null }) { Text("Отмена") } })
    }
}
