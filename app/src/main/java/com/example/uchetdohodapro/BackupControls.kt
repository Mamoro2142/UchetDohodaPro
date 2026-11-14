package com.example.uchetdohodapro

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.uchetdohodapro.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BackupControls() {
    val context = LocalContext.current
    val repository = remember(context) { BackupRepository(WorkDatabase.getInstance(context)) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var candidate by remember { mutableStateOf<BackupSnapshot?>(null) }
    var exportText by remember { mutableStateOf<String?>(null) }

    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val content = exportText
        exportText = null
        if (uri == null || content == null) {
            busy = false
        } else scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openOutputStream(uri, "wt")
                        ?: error("Не удалось открыть файл")
                    stream.use { it.write(content.toByteArray(Charsets.UTF_8)); it.flush() }
                }
                message = "Резервная копия создана"
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) {
                message = "Не удалось записать файл. Создайте резервную копию ещё раз."
            } finally { busy = false }
        }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) busy = false
        else scope.launch {
            try {
                candidate = withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openInputStream(uri) ?: error("Не удалось открыть файл")
                    val bytes = stream.use { input ->
                        val out = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var total = 0
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= BackupFormat.MAX_BYTES) { "Слишком большой файл" }
                            out.write(buffer, 0, count)
                        }
                        out.toByteArray()
                    }
                    BackupFormat.decode(bytes.toString(Charsets.UTF_8))
                }
                message = ""
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) {
                message = "Файл повреждён или несовместим. Данные приложения не изменены."
            } finally { busy = false }
        }
    }
    Text("Все рабочие дни, их тарифы, текущие тарифы и вахты сохраняются в файл.", style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
    Button(enabled = !busy && candidate == null, modifier = Modifier.fillMaxWidth(), onClick = {
        busy = true
        message = ""
        scope.launch {
            try {
                exportText = withContext(Dispatchers.IO) { repository.export() }
                createFile.launch("UchetDohodaPro-backup.json")
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) {
                message = "Не удалось создать копию. Данные приложения не изменены."
                busy = false
            }
        }
    }) { Text("Создать резервную копию") }
    OutlinedButton(enabled = !busy && candidate == null, modifier = Modifier.fillMaxWidth(), onClick = {
        busy = true
        message = ""
        try { openFile.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }
        catch (_: Exception) { busy = false; message = "Не удалось открыть выбор файла" }
    }) { Text("Восстановить из файла") }
    if (busy) Text("Подождите…")
    if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)

    candidate?.let { snapshot ->
        AlertDialog(
            onDismissRequest = { if (!busy) candidate = null },
            title = { Text("Заменить данные приложения?") },
            text = { Text("Будут восстановлены ${snapshot.days.size} рабочих дней и ${snapshot.shifts.size} вахт. Текущие записи будут заменены целиком. Сначала рекомендуется создать резервную копию текущих данных.") },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { repository.restore(snapshot) }
                        message = "Данные восстановлены"
                        candidate = null
                    } catch (e: CancellationException) { throw e
                    } catch (_: Exception) {
                        message = "Восстановление не выполнено. Исходная база сохранена."
                        candidate = null
                    } finally { busy = false }
                }
            }) { Text("Заменить") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { candidate = null }) { Text("Отмена") } }
        )
    }
}
