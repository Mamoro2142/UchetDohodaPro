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
    val shifts: List<ShiftEntity>,
    val operations: List<MoneyOperationEntity> = emptyList(),
    val pauses: List<ShiftPauseEntity> = emptyList()
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
            put("operations", JSONArray().apply { snapshot.operations.forEach { o ->
                put(JSONObject().put("id", o.id).put("shiftId", o.shiftId ?: JSONObject.NULL)
                    .put("date", o.date).put("type", o.type).put("amountMinor", o.amountMinor).put("comment", o.comment))
            } })
            put("pauses", JSONArray().apply { snapshot.pauses.forEach { p -> put(JSONObject().put("id", p.id).put("shiftId", p.shiftId).put("startDate", p.startDate).put("resumeDate", p.resumeDate ?: JSONObject.NULL)) } })
            put("shifts", JSONArray().apply { snapshot.shifts.forEach { s ->
                put(JSONObject().put("id", s.id).put("startDate", s.startDate)
                    .put("endDate", s.endDate ?: JSONObject.NULL).put("isActive", s.isActive)
                    .put("plannedWorkDays", s.plannedWorkDays ?: JSONObject.NULL))
            } })
        }.toString()
        return JSONObject().put("applicationId", "com.example.uchetdohodapro")
            .put("formatVersion", 5).put("payload", payload).put("sha256", digest(payload))
            .toString().also { require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Слишком большая копия" } }
    }

    fun decode(text: String): BackupSnapshot {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Слишком большой файл" }
        val envelope = JSONObject(text)
        require(envelope.getString("applicationId") == "com.example.uchetdohodapro") { "Файл другого приложения" }
        val version = envelope.get("formatVersion")
        require(version == 1 || version == 2 || version == 3 || version == 4 || version == 5) { "Неподдерживаемая версия резервной копии" }
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
                    if (s.get("endDate") == JSONObject.NULL) null else s.getString("endDate"), s.getBoolean("isActive"),
                    if (version != 5 || s.get("plannedWorkDays") == JSONObject.NULL) null else {
                        val plan = s.id("plannedWorkDays")
                        require(plan <= Int.MAX_VALUE) { "Некорректный план смен" }
                        plan.toInt()
                    })
            },
            if (version == 1 || version == 2) emptyList() else objects("operations").map { o ->
                MoneyOperationEntity(o.id("id"), if (o.get("shiftId") == JSONObject.NULL) null else o.id("shiftId"),
                    o.getString("date"), o.getString("type"), o.id("amountMinor"), o.getString("comment"))
            },
            if (version == 4 || version == 5) objects("pauses").map { p -> ShiftPauseEntity(p.id("id"), p.id("shiftId"), p.getString("startDate"), if (p.isNull("resumeDate")) null else p.getString("resumeDate")) } else emptyList()
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
        require(snapshot.shifts.all { it.plannedWorkDays == null || it.plannedWorkDays > 0 }) { "Некорректный план смен" }
        val ids = snapshot.shifts.map { it.id }.toSet()
        require(snapshot.operations.all { it.id > 0 } && snapshot.operations.map { it.id }.distinct().size == snapshot.operations.size) { "Повторные ID операций" }
        require(snapshot.operations.all { (it.shiftId == null || it.shiftId in ids) &&
            (it.type == ADVANCE || it.type == FINE) && it.amountMinor > 0 && validOperationDate(it.date) && it.comment.length <= 4000
        }) { "Некорректная финансовая операция" }
        require(snapshot.days.all { it.shiftId == null || it.shiftId in ids }) { "Рабочий день ссылается на отсутствующую вахту" }
        require(snapshot.days.all { parseDate(it.date) != null } && snapshot.shifts.all {
            parseDate(it.startDate) != null && (it.endDate == null ||
                (parseDate(it.endDate) != null && !parseDate(it.endDate)!!.before(parseDate(it.startDate))))
        }) { "Некорректная дата" }
        require(snapshot.pauses.map { it.id }.distinct().size == snapshot.pauses.size)
        snapshot.pauses.groupBy { it.shiftId }.forEach { (id, pauses) ->
            val shift = snapshot.shifts.single { it.id == id }
            var previous: ShiftPauseEntity? = null
            pauses.sortedBy { it.startDate }.forEach { p ->
                val start = java.time.LocalDate.parse(p.startDate)
                val resume = p.resumeDate?.let { java.time.LocalDate.parse(it) }
                require(p.id > 0 && start >= parseStoredDate(shift.startDate)!! && (resume == null || resume > start))
                require(shift.isActive || (resume != null && resume.minusDays(1) <= parseStoredDate(shift.endDate!!)!!))
                previous?.let { require(it.resumeDate != null && start >= java.time.LocalDate.parse(it.resumeDate)) }
                require(snapshot.days.none { it.shiftId == id && p.contains(parseStoredDate(it.date)!!) })
                previous = p
            }
        }
        val numbers = snapshot.tariffs.flatMap { listOf(it.picksRate, it.weightRate, it.volumeRate, it.boxesRate, it.hoursRate) } +
            snapshot.days.flatMap { listOfNotNull(it.picks, it.weight, it.volume, it.boxes, it.hours) +
                listOf(it.picksRate, it.weightRate, it.volumeRate, it.boxesRate, it.hoursRate, it.calculateIncome()) }
        require(numbers.all { it.isFinite() && it >= 0 }) { "Некорректное число" }
    }
}

class BackupRepository(private val db: WorkDatabase) {
    suspend fun export(): String = db.withTransaction {
        val dao = db.workDao()
        BackupFormat.encode(BackupSnapshot(dao.backupTariffs(), dao.backupDays(), dao.backupShifts(), dao.backupMoneyOperations(), dao.backupPauses()))
    }

    suspend fun restore(snapshot: BackupSnapshot) {
        // Validate before touching the live database, even when called outside the UI.
        BackupFormat.validate(snapshot)
        db.withTransaction {
            val dao = db.workDao()
            dao.clearMoneyOperationsForRestore()
            dao.clearDaysForRestore()
            dao.clearShiftsForRestore()
            dao.clearTariffsForRestore()
            dao.restoreShifts(snapshot.shifts)
            snapshot.tariffs.forEach { dao.saveTariffs(it) }
            dao.restoreDays(snapshot.days)
            dao.restoreMoneyOperations(snapshot.operations)
            dao.restorePauses(snapshot.pauses)
        }
    }
}
