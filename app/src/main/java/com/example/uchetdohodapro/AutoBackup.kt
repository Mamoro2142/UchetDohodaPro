package com.example.uchetdohodapro

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.work.*
import com.example.uchetdohodapro.data.BackupRepository
import com.example.uchetdohodapro.data.WorkDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

internal data class AutoBackupSettings(val enabled: Boolean, val mode: String, val keep: Int, val uri: String?, val last: Long, val error: String?)

internal object AutoBackup {
    private const val PERIODIC = "income-auto-backup-periodic"
    private const val IMMEDIATE = "income-auto-backup-shift"
    internal val lock = Mutex()
    fun prefs(context: Context) = context.getSharedPreferences("automatic_backups", Context.MODE_PRIVATE)
    fun read(context: Context): AutoBackupSettings = prefs(context).let {
        AutoBackupSettings(it.getBoolean("enabled", false), it.getString("mode", "daily") ?: "daily",
            it.getInt("keep", 7), it.getString("uri", null), it.getLong("last", 0), it.getString("error", null))
    }
    fun configure(context: Context, settings: AutoBackupSettings) {
        require(settings.mode in listOf("daily", "weekly", "shift") && settings.keep in listOf(3, 7, 15))
        require(!settings.enabled || settings.uri != null)
        prefs(context).edit().putBoolean("enabled", settings.enabled).putString("mode", settings.mode)
            .putInt("keep", settings.keep).putString("uri", settings.uri).apply()
        val manager = WorkManager.getInstance(context)
        if (settings.enabled && settings.mode != "shift") {
            val interval = if (settings.mode == "weekly") 7L else 1L
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(interval, TimeUnit.DAYS)
                .setInitialDelay(interval, TimeUnit.DAYS).setInputData(workDataOf("mode" to settings.mode)).build()
            manager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
        } else manager.cancelUniqueWork(PERIODIC)
        if (!settings.enabled || settings.mode != "shift") manager.cancelUniqueWork(IMMEDIATE)
    }
    fun afterShift(context: Context) {
        val s = read(context)
        if (s.enabled && s.mode == "shift") try {
            WorkManager.getInstance(context).enqueueUniqueWork(IMMEDIATE, ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<AutoBackupWorker>().setInputData(workDataOf("mode" to "shift")).build())
        } catch (_: Exception) {
            prefs(context).edit().putString("error", "Не удалось запланировать копию после завершения вахты").apply()
        }
    }
    fun fileName(now: Long, uuid: String) = "UchetDohodaPro-auto-${now.toString().padStart(13, '0')}-$uuid.json"
    private val pattern = Regex("UchetDohodaPro-auto-[0-9]{13,}-[0-9a-f-]{36}\\.json")
    fun obsolete(names: List<String>, keep: Int): List<String> = names.filter { pattern.matches(it) }.sortedDescending().drop(keep)
}

class AutoBackupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        AutoBackup.lock.withLock {
            val s = AutoBackup.read(applicationContext)
            if (!s.enabled || s.mode != inputData.getString("mode")) return@withLock Result.success()
            val prefs = AutoBackup.prefs(applicationContext)
            var created: DocumentFile? = null
            var written = false
            try {
                val uri = Uri.parse(requireNotNull(s.uri))
                val folder = DocumentFile.fromTreeUri(applicationContext, uri) ?: error("Папка недоступна")
                require(folder.isDirectory && folder.canWrite()) { "Нет доступа к папке" }
                val text = BackupRepository(WorkDatabase.getInstance(applicationContext)).export()
                val now = System.currentTimeMillis()
                val name = AutoBackup.fileName(now, UUID.randomUUID().toString())
                val file = folder.createFile("application/json", name) ?: error("Не удалось создать файл")
                created = file
                val resolver = applicationContext.contentResolver
                val bytes = text.toByteArray(Charsets.UTF_8)
                (resolver.openOutputStream(file.uri, "wt") ?: error("Не удалось открыть файл")).use { it.write(bytes); it.flush() }
                // Verify every byte, including EOF, before deleting any previous copy.
                (resolver.openInputStream(file.uri) ?: error("Не удалось проверить копию")).use { input ->
                    val buffer = ByteArray(8192); var offset = 0
                    while (offset < bytes.size) {
                        val count = input.read(buffer, 0, minOf(buffer.size, bytes.size - offset))
                        check(count > 0 && (0 until count).all { buffer[it] == bytes[offset + it] }) { "Ошибка проверки копии" }
                        offset += count
                    }
                    check(input.read() == -1) { "Ошибка размера копии" }
                }
                written = true
                val current = AutoBackup.read(applicationContext)
                if (current.enabled && current.uri == s.uri && current.mode == s.mode) {
                    val files = folder.listFiles().filter { it.isFile }
                    val old = AutoBackup.obsolete(files.filter { it.uri != file.uri }.mapNotNull { it.name }, current.keep - 1).toSet()
                    val cleanupFailed = files.filter { it.name in old }.map { !it.delete() }.any { it }
                    prefs.edit().putLong("last", now).putString("error", if (cleanupFailed) "Копия создана, но не все старые файлы удалены" else null).apply()
                }
                Result.success()
            } catch (e: CancellationException) {
                if (!written) runCatching { created?.delete() }
                throw e
            } catch (e: Exception) {
                if (!written) runCatching { created?.delete() }
                prefs.edit().putString("error", "Автокопирование не выполнено. Проверьте доступ к папке и свободное место.").apply()
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        }
    }
}

@Composable
internal fun AutoBackupControls() {
    val context = LocalContext.current
    val prefs = remember(context) { AutoBackup.prefs(context) }
    var settings by remember { mutableStateOf(AutoBackup.read(context)) }
    var message by remember { mutableStateOf("") }
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> settings = AutoBackup.read(context) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    fun save(s: AutoBackupSettings) {
        try { AutoBackup.configure(context, s); settings = AutoBackup.read(context); message = "" }
        catch (_: Exception) { message = "Не удалось настроить автоматическое копирование" }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            save(settings.copy(uri = uri.toString()))
        } catch (_: Exception) { message = "Не удалось получить постоянный доступ к папке" }
    }
    Spacer(Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(Modifier.height(12.dp))
    Text("Автоматические резервные копии", style = MaterialTheme.typography.titleMedium)
    Text(if (settings.last > 0) "Последняя автокопия: " + SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(settings.last)) else "Автоматических копий пока нет", style = MaterialTheme.typography.bodySmall)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text("Автокопирование", modifier = Modifier.weight(1f))
        Switch(checked = settings.enabled, onCheckedChange = { if (it && settings.uri == null) message = "Сначала выберите папку" else save(settings.copy(enabled = it)) })
    }
    OutlinedButton(onClick = { folderPicker.launch(null) }, modifier = Modifier.fillMaxWidth()) { Text(if (settings.uri == null) "Выбрать папку" else "Изменить папку") }
    settings.uri?.let { Text(Uri.parse(it).lastPathSegment ?: "Папка выбрана", style = MaterialTheme.typography.bodySmall) }
    listOf("daily" to "Каждый день", "weekly" to "Раз в неделю", "shift" to "После завершения вахты").forEach { (mode, label) ->
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            RadioButton(selected = settings.mode == mode, onClick = { save(settings.copy(mode = mode)) }); Text(label)
        }
    }
    Text("Хранить последних копий")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(3, 7, 15).forEach { count -> FilterChip(selected = settings.keep == count, onClick = { save(settings.copy(keep = count)) }, label = { Text(count.toString()) }) }
    }
    Text("Android может отложить фоновое копирование. Ручные копии не удаляются.", style = MaterialTheme.typography.bodySmall)
    settings.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
}
