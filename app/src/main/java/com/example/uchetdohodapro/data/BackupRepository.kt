package com.example.uchetdohodapro.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

/** Immutable, validated candidate. Null measurements must survive a round trip. */
data class BackupSnapshot(
    val tariffs: List<TariffEntity>,
    val days: List<WorkDayEntity>,
    val shifts: List<ShiftEntity>
)

object BackupFormat {
    const val MAX_BYTES = 16 * 1024 * 1024
    private val metrics = listOf("picks", "weight", "volume", "boxes", "hours")
    private fun digest(text: String) = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun encode(snapshot: BackupSnapshot): String {
        validate(snapshot)
        val payload = JSONObject().apply {
            put("tariffs", JSONArray().apply { snapshot.tariffs.forEach { t ->
                put(JSONObject().put("id", t.id).put("savedAt", t.savedAt ?: JSONObject.NULL).apply {
                    listOf(t.picksRate, t.weightRate, t.volumeRate, t.boxesRate, t.hoursRate)
                        .forEachIndexed { i, v -> put(metrics[i] + "Rate", v) }
                })
            } })
            put("days", JSONArray().apply { snapshot.days.forEach { d ->
                put(JSONObject().put("id", d.id).put("date", d.date)
                    .put("shiftId", d.shiftId ?: JSONObject.NULL).apply {
                        listOf(d.picks, d.weight, d.volume, d.boxes, d.hours)
                            .forEachIndexed { i, v -> put(metrics[i], v ?: JSONObject.NULL) }
                        listOf(d.picksRate, d.weightRate, d.volumeRate, d.boxesRate, d.hoursRate)
                            .forEachIndexed { i, v -> put(metrics[i] + "Rate", v) }
                    })
            } })
            put("shifts", JSONArray().apply { snapshot.shifts.forEach { s ->
                put(JSONObject().put("id", s.id).put("startDate", s.startDate)
                    .put("endDate", s.endDate ?: JSONObject.NULL).put("isActive", s.isActive))
            } })
        }.toString()
        return JSONObject().put("applicationId", "com.example.uchetdohodapro")
            .put("formatVersion", 2).put("payload", payload).put("sha256", digest(payload))
            .toString().also { require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Слишком большая копия" } }
    }

    fun decode(text: String): BackupSnapshot {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Слишком большой файл" }
        val envelope = JSONObject(text)
        require(envelope.getString("applicationId") == "com.example.uchetdohodapro") { "Файл другого приложения" }
        val version = envelope.get("formatVersion")
        require(version == 1 || version == 2) { "Неподдерживаемая версия резервной копии" }
        val payload = envelope.getString("payload")
        require(digest(payload) == envelope.getString("sha256")) { "Нарушена целостность файла" }
        val json = JSONObject(payload)
        fun objects(key: String): List<JSONObject> = json.getJSONArray(key).let { array ->
            (0 until array.length()).map { array.getJSONObject(it) }
        }
        fun JSONObject.id(key: String): Long {
            val value = get(key)
            require(value is Int || value is Long) { "Неверный ID" }
            return (value as Number).toLong().also { require(it > 0) { "Неверный ID" } }
        }
        fun JSONObject.number(key: String): Double {
            val value = get(key)
            require(value is Number) { "Неверное число" }
            return (value as Number).toDouble().also { require(it.isFinite() && it >= 0) { "Неверное число" } }
        }
        fun JSONObject.nullableNumber(key: String): Double? = if (get(key) == JSONObject.NULL) null else number(key)
        val snapshot = BackupSnapshot(
            objects("tariffs").map { t ->
                require(t.id("id") == 1L)
                TariffEntity(1, t.number("picksRate"), t.number("weightRate"), t.number("volumeRate"), t.number("boxesRate"), t.number("hoursRate"),
                    if (version == 1 || t.get("savedAt") == JSONObject.NULL) null else t.id("savedAt"))
            },
            objects("days").map { d -> WorkDayEntity(
                id = d.id("id"), date = d.getString("date"),
                picks = d.nullableNumber("picks"), weight = d.nullableNumber("weight"),
                volume = d.nullableNumber("volume"), boxes = d.nullableNumber("boxes"), hours = d.nullableNumber("hours"),
                picksRate = d.number("picksRate"), weightRate = d.number("weightRate"),
                volumeRate = d.number("volumeRate"), boxesRate = d.number("boxesRate"), hoursRate = d.number("hoursRate"),
                shiftId = if (d.get("shiftId") == JSONObject.NULL) null else d.id("shiftId")
            ) },
            objects("shifts").map { s ->
                require(s.get("isActive") is Boolean)
                ShiftEntity(s.id("id"), s.getString("startDate"),
                    if (s.get("endDate") == JSONObject.NULL) null else s.getString("endDate"), s.getBoolean("isActive"))
            }
        )
        validate(snapshot)
        return snapshot
    }

    fun validate(snapshot: BackupSnapshot) {
        fun parseDate(value: String): java.util.Date? {
            val format = when {
                value.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) -> "yyyy-MM-dd"
                value.matches(Regex("\\d{2}\\.\\d{2}\\.\\d{4}")) -> "dd.MM.yyyy"
                else -> return null
            }
            val parser = SimpleDateFormat(format, Locale.ROOT).apply { isLenient = false }
            val position = ParsePosition(0)
            return parser.parse(value, position)?.takeIf { position.index == value.length }
        }
        require(snapshot.tariffs.all { it.savedAt == null || it.savedAt > 0 }) { "Неверная дата сохранения тарифов" }
        require(snapshot.tariffs.size <= 1 && snapshot.tariffs.all { it.id == 1 }) { "Неверные тарифы" }
        require(snapshot.days.all { it.id > 0 } && snapshot.days.map { it.id }.distinct().size == snapshot.days.size) { "Повторные ID дней" }
        require(snapshot.shifts.all { it.id > 0 } && snapshot.shifts.map { it.id }.distinct().size == snapshot.shifts.size) { "Повторные ID вахт" }
        val ids = snapshot.shifts.map { it.id }.toSet()
        require(snapshot.days.all { it.shiftId == null || it.shiftId in ids }) { "Рабочий день ссылается на отсутствующую вахту" }
        require(snapshot.days.all { parseDate(it.date) != null } && snapshot.shifts.all {
            parseDate(it.startDate) != null && (it.endDate == null ||
                (parseDate(it.endDate) != null && !parseDate(it.endDate)!!.before(parseDate(it.startDate))))
        }) { "Некорректная дата" }
        val numbers = snapshot.tariffs.flatMap { listOf(it.picksRate, it.weightRate, it.volumeRate, it.boxesRate, it.hoursRate) } +
            snapshot.days.flatMap { listOfNotNull(it.picks, it.weight, it.volume, it.boxes, it.hours) +
                listOf(it.picksRate, it.weightRate, it.volumeRate, it.boxesRate, it.hoursRate, it.calculateIncome()) }
        require(numbers.all { it.isFinite() && it >= 0 }) { "Некорректное число" }
    }
}

class BackupRepository(private val db: WorkDatabase) {
    suspend fun export(): String = db.withTransaction {
        val dao = db.workDao()
        BackupFormat.encode(BackupSnapshot(dao.backupTariffs(), dao.backupDays(), dao.backupShifts()))
    }

    suspend fun restore(snapshot: BackupSnapshot) {
        // Validate before touching the live database, even when called outside the UI.
        BackupFormat.validate(snapshot)
        db.withTransaction {
            val dao = db.workDao()
            dao.clearDaysForRestore()
            dao.clearShiftsForRestore()
            dao.clearTariffsForRestore()
            dao.restoreShifts(snapshot.shifts)
            snapshot.tariffs.forEach { dao.saveTariffs(it) }
            dao.restoreDays(snapshot.days)
        }
    }
}
