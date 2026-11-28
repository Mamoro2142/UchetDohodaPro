package com.example.uchetdohodapro.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

internal val workMetricNames = listOf("Пики", "Вес", "Объём", "Боксы", "Часы")

@Entity(tableName = "work_groups")
data class WorkGroupEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val mask: Int,
    val ratesJson: String,
    val savedAt: Long?,
    val historyJson: String = "[]"
) {
    fun rates(): List<Double> = readRates(JSONArray(ratesJson))
    fun enabled(index: Int) = mask and (1 shl index) != 0
    fun history(): List<GroupRateVersion> = JSONArray(historyJson).let { a ->
        (0 until a.length()).map { i -> a.getJSONObject(i).let { o ->
            GroupRateVersion(o.getString("name"), o.getInt("mask"), readRates(o.getJSONArray("rates")), if (o.isNull("savedAt")) null else o.getLong("savedAt"))
        } }
    }
}

data class GroupRateVersion(val name: String, val mask: Int, val rates: List<Double>, val savedAt: Long?)

internal fun readRates(array: JSONArray): List<Double> {
    require(array.length() == 5)
    return (0..4).map { i -> (array.get(i) as Number).toDouble().also { require(it.isFinite() && it >= 0) } }
}
internal fun writeRates(rates: List<Double>) = JSONArray(rates).toString()
internal fun defaultGroup(tariffs: TariffEntity?, stored: WorkGroupEntity? = null): WorkGroupEntity =
    (stored ?: WorkGroupEntity(1, "Раздел", 31, writeRates(List(5) { 0.0 }), null)).copy(
        ratesJson = writeRates(tariffs?.rates() ?: List(5) { 0.0 }), savedAt = tariffs?.savedAt)
internal fun availableGroups(tariffs: TariffEntity?, stored: List<WorkGroupEntity>): List<WorkGroupEntity> =
    listOf(defaultGroup(tariffs, stored.firstOrNull { it.id == 1L })) + stored.filter { it.id != 1L }.sortedBy { it.id }
internal fun validateGroup(group: WorkGroupEntity) {
    require(group.id > 0 && group.name.isNotBlank() && group.name.length <= 80 && group.mask in 1..31)
    group.rates()
    require(group.savedAt == null || group.savedAt > 0)
    group.history().forEach { h ->
        require(h.name.isNotBlank() && h.name.length <= 80 && h.mask in 1..31 && (h.savedAt == null || h.savedAt > 0))
    }
}
internal fun previousGroupHistory(old: WorkGroupEntity): String = JSONArray(old.historyJson).apply {
    put(JSONObject().put("name", old.name).put("mask", old.mask).put("rates", JSONArray(old.rates())).put("savedAt", old.savedAt ?: JSONObject.NULL))
}.toString()

/** Each day stores its own group names, quantities and rates, independent of later configuration changes. */
data class DayGroup(val id: Long, val name: String, val mask: Int, val values: List<Double?>, val rates: List<Double>) {
    fun income(index: Int): Double = (values[index] ?: 0.0) * rates[index]
    fun income(): Double = (0..4).sumOf { income(it) }
    fun validate() {
        require(id > 0 && name.isNotBlank() && name.length <= 80 && mask in 1..31 && values.size == 5 && rates.size == 5)
        require(rates.all { it.isFinite() && it >= 0 } && values.withIndex().all { (i,v) -> v == null || (mask and (1 shl i) != 0 && v.isFinite() && v >= 0) })
        require(income().isFinite())
    }
}
internal fun encodeDayGroups(groups: List<DayGroup>): String = JSONArray().apply {
    groups.forEach { g -> put(JSONObject().put("id", g.id).put("name", g.name).put("mask", g.mask)
        .put("values", JSONArray().apply { g.values.forEach { put(it ?: JSONObject.NULL) } }).put("rates", JSONArray(g.rates))) }
}.toString()
internal fun decodeDayGroups(json: String): List<DayGroup> = JSONArray(json).let { a ->
    (0 until a.length()).map { i -> a.getJSONObject(i).let { o ->
        val v = o.getJSONArray("values"); require(v.length() == 5)
        val id = o.get("id"); require(id is Int || id is Long)
        val mask = o.get("mask"); require(mask is Int && mask in 1..31)
        DayGroup((id as Number).toLong(), o.getString("name"), mask, (0..4).map { if (v.isNull(it)) null else (v.get(it) as Number).toDouble() }, readRates(o.getJSONArray("rates")))
    } }.also { groups -> require(groups.isNotEmpty() && groups.map { it.id }.distinct().size == groups.size); groups.forEach { it.validate() } }
}
internal fun WorkDayEntity.groups(): List<DayGroup> = groupsJson?.let(::decodeDayGroups) ?: listOf(DayGroup(1, "Раздел", 31,
    listOf(picks, weight, volume, boxes, hours), listOf(picksRate, weightRate, volumeRate, boxesRate, hoursRate)))
internal fun WorkDayEntity.metricIncome(index: Int): Double = groups().sumOf { it.income(index) }
internal fun withDayGroups(day: WorkDayEntity, groups: List<DayGroup>): WorkDayEntity {
    require(groups.isNotEmpty() && groups.map { it.id }.distinct().size == groups.size)
    groups.forEach { it.validate() }
    val v = (0..4).map { i -> groups.mapNotNull { it.values[i] }.takeIf { it.isNotEmpty() }?.sum() }
    require(v.all { it == null || it.isFinite() } && groups.sumOf { it.income() }.isFinite())
    val r = groups.first().rates
    return day.copy(picks = v[0], weight = v[1], volume = v[2], boxes = v[3], hours = v[4],
        picksRate = r[0], weightRate = r[1], volumeRate = r[2], boxesRate = r[3], hoursRate = r[4], groupsJson = encodeDayGroups(groups))
}
internal fun validateGroupedDay(day: WorkDayEntity) {
    if (day.groupsJson != null) {
        val canonical = withDayGroups(day, day.groups())
        require(listOf(day.picks,day.weight,day.volume,day.boxes,day.hours) == listOf(canonical.picks,canonical.weight,canonical.volume,canonical.boxes,canonical.hours))
        require(listOf(day.picksRate,day.weightRate,day.volumeRate,day.boxesRate,day.hoursRate) == listOf(canonical.picksRate,canonical.weightRate,canonical.volumeRate,canonical.boxesRate,canonical.hoursRate))
    }
}
