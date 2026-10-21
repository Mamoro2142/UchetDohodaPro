package com.example.uchetdohodapro

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.uchetdohodapro.data.ShiftEntity
import com.example.uchetdohodapro.data.WorkDatabase
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ShiftScreen() {
    val context = LocalContext.current
    val dao = remember {
        WorkDatabase.getInstance(context).workDao()
    }
    val scope = rememberCoroutineScope()

    val shifts by dao.observeShifts()
        .collectAsState(initial = emptyList())

    val activeShift = shifts.firstOrNull { it.isActive }

    val formatter = remember {
        DateTimeFormatter.ofPattern("dd.MM.yyyy")
    }

    fun today(): String =
        LocalDate.now().format(formatter)

    var editingShift by remember {
        mutableStateOf<ShiftEntity?>(null)
    }

    var startDate by remember {
        mutableStateOf("")
    }

    var endDate by remember {
        mutableStateOf("")
    }

    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Управление вахтами",
            style = MaterialTheme.typography.headlineSmall
        )

        if (activeShift == null) {
            Button(
                onClick = {
                    scope.launch {
                        if (dao.getActiveShift() == null) {
                            dao.insertShift(
                                ShiftEntity(
                                    startDate = today(),
                                    isActive = true
                                )
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Начать вахту")
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Активная вахта",
                        color = Color(0xFF4CAF50)
                    )

                    Text(
                        "Начало: ${activeShift.startDate}"
                    )

                    Button(
                        onClick = {
                            scope.launch {
                                dao.updateShift(
                                    activeShift.copy(
                                        endDate = today(),
                                        isActive = false
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Завершить вахту")
                    }
                }
            }
        }

        HorizontalDivider()

        Text(
            text = "История вахт",
            style = MaterialTheme.typography.titleLarge
        )

        if (shifts.isEmpty()) {
            Text("Вахт пока нет")
        }

        shifts.forEach { shift ->
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        if (shift.isActive)
                            "Вахта №${shift.id} — активна"
                        else
                            "Вахта №${shift.id} — завершена"
                    )

                    Text("Начало: ${shift.startDate}")

                    Text(
                        "Окончание: ${shift.endDate ?: "—"}"
                    )

                    OutlinedButton(
                        onClick = {
                            editingShift = shift
                            startDate = shift.startDate
                            endDate = shift.endDate ?: ""
                            errorMessage = null
                        }
                    ) {
                        Text("Изменить даты")
                    }
                }
            }
        }
    }

    if (editingShift != null) {
        AlertDialog(
            onDismissRequest = {
                editingShift = null
            },
            title = {
                Text("Редактирование вахты")
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = startDate,
                        onValueChange = {
                            startDate = it
                            errorMessage = null
                        },
                        label = {
                            Text("Дата начала (дд.ММ.гггг)")
                        },
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = endDate,
                        onValueChange = {
                            endDate = it
                            errorMessage = null
                        },
                        label = {
                            Text("Дата окончания (необязательно)")
                        },
                        singleLine = true
                    )

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val shift = editingShift
                            ?: return@TextButton

                        val start = runCatching {
                            LocalDate.parse(startDate, formatter)
                        }.getOrNull()

                        val end = if (endDate.isBlank()) {
                            null
                        } else {
                            runCatching {
                                LocalDate.parse(endDate, formatter)
                            }.getOrNull()
                        }

                        when {
                            start == null -> {
                                errorMessage = "Неверная дата начала"
                            }
                            endDate.isNotBlank() && end == null -> {
                                errorMessage = "Неверная дата окончания"
                            }
                            end != null && end.isBefore(start) -> {
                                errorMessage =
                                    "Окончание раньше начала"
                            }
                            else -> {
                                scope.launch {
                                    dao.updateShift(
                                        shift.copy(
                                            startDate = startDate,
                                            endDate = endDate.ifBlank {
                                                null
                                            },
                                            isActive = endDate.isBlank()
                                        )
                                    )
                                    editingShift = null
                                }
                            }
                        }
                    }
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        editingShift = null
                    }
                ) {
                    Text("Отмена")
                }
            }
        )
    }
}
