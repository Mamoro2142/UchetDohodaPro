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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.uchetdohodapro.data.parseNonNegativeNumber
import com.example.uchetdohodapro.data.editableNumber
import com.example.uchetdohodapro.data.TariffEntity
import com.example.uchetdohodapro.data.WorkDatabase
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TariffBackground = Color(0xFF111315)
private val TariffPanel = Color(0xFF1C2023)
private val TariffOrange = Color(0xFFFF8A24)
private val TariffText = Color(0xFFF4F5F6)
private val TariffMuted = Color(0xFF9DA5AB)

@Composable
fun TariffsScreen(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val database = remember {
        WorkDatabase.getInstance(context)
    }

    val factory = remember(database) {
        TariffViewModelFactory(database)
    }

    val model: TariffViewModel = viewModel(factory = factory)
    val loadedTariffs by model.tariffs.collectAsState()
    val tariffs = loadedTariffs?.value
    val historyFlow = remember(database) { database.workDao().observeTariffHistory() }
    val history by historyFlow.collectAsState(initial = emptyList())

    var picks by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("") }
    var volume by rememberSaveable { mutableStateOf("") }
    var boxes by rememberSaveable { mutableStateOf("") }
    var hours by rememberSaveable { mutableStateOf("") }
    var initialized by rememberSaveable { mutableStateOf(false) }
    var validationMessage by rememberSaveable { mutableStateOf("") }
    val message = validationMessage.ifBlank { model.message }

    LaunchedEffect(loadedTariffs) {
        val saved = tariffs
        if (saved != null && !initialized) {
            picks = saved.picksRate.toEditableRate()
            weight = saved.weightRate.toEditableRate()
            volume = saved.volumeRate.toEditableRate()
            boxes = saved.boxesRate.toEditableRate()
            hours = saved.hoursRate.toEditableRate()
            initialized = true
        } else if (loadedTariffs != null && saved == null && !initialized) {
            initialized = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TariffBackground)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        WinterPageHeader()
        Button(
    onClick = onBack,
    colors = ButtonDefaults.buttonColors(
        containerColor = Color(0xFF272C30),
        contentColor = Color(0xFFF4F5F6)
    ),
    shape = RoundedCornerShape(12.dp)
) {
    Text("← Назад", fontWeight = FontWeight.SemiBold)
}

Spacer(Modifier.height(18.dp))
        Text(
            text = "НАСТРОЙКИ",
            color = TariffOrange,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Тарифы",
            color = TariffText,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Укажи стоимость каждого показателя. Тарифы сохраняются на этом телефоне.",
            color = TariffMuted,
            fontSize = 14.sp
        )

        Spacer(Modifier.height(22.dp))

        TariffInput(
            title = "Пики",
            subtitle = "Стоимость за один пик, ₽",
            value = picks,
            enabled = !model.saving,
            onValueChange = { picks = it; validationMessage = ""; model.clearMessage() }
        )

        TariffInput(
            title = "Вес",
            subtitle = "Стоимость за единицу веса, ₽",
            value = weight,
            enabled = !model.saving,
            onValueChange = { weight = it; validationMessage = ""; model.clearMessage() }
        )

        TariffInput(
            title = "Объём",
            subtitle = "Стоимость за единицу объёма, ₽",
            value = volume,
            enabled = !model.saving,
            onValueChange = { volume = it; validationMessage = ""; model.clearMessage() }
        )

        TariffInput(
            title = "Боксы",
            subtitle = "Стоимость за один бокс, ₽",
            value = boxes,
            enabled = !model.saving,
            onValueChange = { boxes = it; validationMessage = ""; model.clearMessage() }
        )

        TariffInput(
            title = "Часы",
            subtitle = "Стоимость за один час, ₽",
            value = hours,
            enabled = !model.saving,
            onValueChange = { hours = it; validationMessage = ""; model.clearMessage() }
        )

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                val parsed = listOf(picks, weight, volume, boxes, hours)
                    .map { parseNonNegativeNumber(it) }

                if (parsed.any { it == null || !it.isFinite() || it < 0.0 }) {
                    validationMessage = "Введи неотрицательные числа во все поля. Можно использовать запятую."
                } else {
                    validationMessage = ""
                    model.save(
                        TariffEntity(
                            picksRate = parsed[0]!!,
                            weightRate = parsed[1]!!,
                            volumeRate = parsed[2]!!,
                            boxesRate = parsed[3]!!,
                            hoursRate = parsed[4]!!
                        )
                    )
                }
            },
            enabled = initialized && !model.saving,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TariffOrange,
                contentColor = Color.Black
            )
        ) {
            Text(if (model.saving) "Сохранение…" else "Сохранить тарифы", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            tariffs?.savedAt?.let {
                "Последнее сохранение: " + SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(it))
            } ?: "Дата сохранения пока не указана",
            color = TariffMuted,
            fontSize = 12.sp
        )

        if (message.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                color = if (message == "Тарифы сохранены") Color(0xFF43D17D)
                else Color(0xFFFF5C5C),
                fontSize = 14.sp
            )
        }

        Spacer(Modifier.height(24.dp))
        TariffHistoryPanel(tariffs, history)
    }
}

@Composable
private fun TariffInput(
    title: String,
    subtitle: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .seasonalCard(18.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = TariffPanel)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                color = TariffText,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = subtitle,
                color = TariffMuted,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                placeholder = { Text("0", color = TariffMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TariffText,
                    unfocusedTextColor = TariffText,
                    focusedBorderColor = TariffOrange,
                    unfocusedBorderColor = TariffMuted,
                    focusedLabelColor = TariffOrange,
                    cursorColor = TariffOrange
                )
            )
        }
    }
}

private fun Double.toEditableRate(): String = editableNumber(this)

data class LoadedTariffs(val value: TariffEntity?)

class TariffViewModel(
    private val database: WorkDatabase
) : ViewModel() {
    val tariffs = database.workDao().observeTariffs()
        .map { LoadedTariffs(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var saving by mutableStateOf(false)
        private set
    var message by mutableStateOf("")
        private set

    fun clearMessage() { if (!saving) message = "" }

    fun save(tariff: TariffEntity) {
        if (saving) return
        saving = true
        message = ""
        viewModelScope.launch {
            try {
                database.workDao().saveTariffs(tariff.copy(savedAt = System.currentTimeMillis()))
                message = "Тарифы сохранены"
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                message = "Не удалось сохранить тарифы. Введённые значения сохранены в форме; попробуйте ещё раз."
            } finally {
                saving = false
            }
        }
    }
}

class TariffViewModelFactory(
    private val database: WorkDatabase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TariffViewModel::class.java)) {
            return TariffViewModel(database) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
