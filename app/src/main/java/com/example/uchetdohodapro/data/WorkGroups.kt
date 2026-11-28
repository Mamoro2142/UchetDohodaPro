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
    fun definitions(): List<MetricDefinition> = readDefinitions(mask, JSONArray(ratesJson))
    fun enabled(index: Int) = mask and (1 shl index) != 0
    fun history(): List<GroupRateVersion> = JSONArray(historyJson).let { a ->
        (0 until a.length()).map { i -> a.getJSONObject(i).let { o ->
            GroupRateVersion(o.getString("name"), o.getInt("mask"), readRates(o.getJSONArray("rates")), if (o.isNull("savedAt")) null else o.getLong("savedAt"), readDefinitions(o.getInt("mask"), o.getJSONArray("rates")))
        } }
    }
}

data class GroupRateVersion(val name: String, val mask: Int, val rates: List<Double>, val savedAt: Long?, val definitions: List<MetricDefinition>)

internal fun readRates(array: JSONArray): List<Double> {
    require(array.length() in 5..6)
    return (0..4).map { i -> (array.get(i) as Number).toDouble().also { require(it.isFinite() && it >= 0) } }
}
internal fun writeRates(rates: List<Double>) = JSONArray(rates).toString()
/** Stable IDs preserve identity across rename, reorder and rate edits. */
data class MetricDefinition(val id: String, val name: String, val rate: Double)
data class DayMetric(val definition: MetricDefinition, val value: Double?) {
    fun income() = (value ?: 0.0) * definition.rate
}
internal fun baseIndex(id: String): Int? = (0..4).firstOrNull { id == "base:$it" }
internal fun legacyDefinitions(mask: Int, rates: List<Double>) = workMetricNames.mapIndexedNotNull { i, name ->
    if (mask and (1 shl i) != 0) MetricDefinition("base:$i", name, rates[i]) else null
}
internal fun validateDefinitions(defs: List<MetricDefinition>) {
    require(defs.isNotEmpty() && defs.map { it.id }.distinct().size == defs.size)
    require(defs.map { it.name.trim().lowercase(java.util.Locale.ROOT) }.distinct().size == defs.size)
    defs.forEach { d -> require((baseIndex(d.id) != null || d.id.startsWith("custom:") && d.id.length in 8..80) &&
        d.name.isNotBlank() && d.name.length <= 80 && d.rate.isFinite() && d.rate >= 0) }
}
private fun definitionJson(d: MetricDefinition) = JSONObject().put("id", d.id).put("name", d.name).put("rate", d.rate)
private fun readDefinition(o: JSONObject) = MetricDefinition(o.getString("id"), o.getString("name"), (o.get("rate") as Number).toDouble())
internal fun definitionMask(defs: List<MetricDefinition>) = defs.fold(0) { m, d -> baseIndex(d.id)?.let { m or (1 shl it) } ?: m }
internal fun definitionRates(defs: List<MetricDefinition>, previous: List<Double>) = previous.mapIndexed { i, r -> defs.firstOrNull { baseIndex(it.id) == i }?.rate ?: r }
internal fun writeDefinitions(defs: List<MetricDefinition>, previous: List<Double>): String {
    validateDefinitions(defs)
    return JSONArray(definitionRates(defs, previous)).put(JSONArray().apply { defs.forEach { put(definitionJson(it)) } }).toString()
}
internal fun readDefinitions(mask: Int, array: JSONArray): List<MetricDefinition> {
    val rates = readRates(array)
    val defs = if (array.length() == 5) legacyDefinitions(mask, rates) else array.getJSONArray(5).let { a -> (0 until a.length()).map { readDefinition(a.getJSONObject(it)) } }
    validateDefinitions(defs)
    require(definitionMask(defs) == mask && definitionRates(defs, rates) == rates)
    return defs
}
internal fun WorkGroupEntity.withBaseRates(rates: List<Double>): String = if (JSONArray(ratesJson).length() == 5) writeRates(rates) else
    writeDefinitions(definitions().map { d -> baseIndex(d.id)?.let { d.copy(rate = rates[it]) } ?: d }, rates)
internal fun WorkGroupEntity.withDefinitions(defs: List<MetricDefinition>) = copy(mask = definitionMask(defs), ratesJson = writeDefinitions(defs, rates()))
internal fun defaultGroup(tariffs: TariffEntity?, stored: WorkGroupEntity? = null): WorkGroupEntity =
    (stored ?: WorkGroupEntity(1, "Раздел", 31, writeRates(List(5) { 0.0 }), null)).copy(
        ratesJson = stored?.withBaseRates(tariffs?.rates() ?: stored.rates()) ?: writeRates(tariffs?.rates() ?: List(5) { 0.0 }), savedAt = tariffs?.savedAt)
internal fun availableGroups(tariffs: TariffEntity?, stored: List<WorkGroupEntity>): List<WorkGroupEntity> =
    listOf(defaultGroup(tariffs, stored.firstOrNull { it.id == 1L })) + stored.filter { it.id != 1L }.sortedBy { it.id }
internal fun validateGroup(group: WorkGroupEntity) {
    require(group.id > 0 && group.name.isNotBlank() && group.name.length <= 80 && group.mask in 0..31)
    group.definitions()
    require(group.savedAt == null || group.savedAt > 0)
    group.history().forEach { h ->
        require(h.name.isNotBlank() && h.name.length <= 80 && h.mask in 0..31 && (h.savedAt == null || h.savedAt > 0))
    }
}
internal fun previousGroupHistory(old: WorkGroupEntity): String = JSONArray(old.historyJson).apply {
    put(JSONObject().put("name", old.name).put("mask", old.mask).put("rates", JSONArray(old.ratesJson)).put("savedAt", old.savedAt ?: JSONObject.NULL))
}.toString()

/** Each day stores its own group names, quantities and rates, independent of later configuration changes. */
data class DayGroup(val id: Long, val name: String, val mask: Int, val values: List<Double?>, val rates: List<Double>, val measurements: List<DayMetric>? = null) {
    fun entries(): List<DayMetric> = measurements ?: legacyDefinitions(mask, rates).map { DayMetric(it, values[baseIndex(it.id)!!]) }
    fun income(index: Int): Double = (values[index] ?: 0.0) * rates[index]
    fun income(): Double = if (measurements == null) (0..4).sumOf { income(it) } else measurements.sumOf { it.income() }
    fun validate() {
        require(id > 0 && name.isNotBlank() && name.length <= 80 && mask in 0..31 && values.size == 5 && rates.size == 5)
        require(rates.all { it.isFinite() && it >= 0 } && values.withIndex().all { (i,v) -> v == null || (mask and (1 shl i) != 0 && v.isFinite() && v >= 0) })
        val entries = entries()
        validateDefinitions(entries.map { it.definition })
        require(entries.all { it.value == null || it.value.isFinite() && it.value >= 0 })
        require(definitionMask(entries.map { it.definition }) == mask)
        require(definitionRates(entries.map { it.definition }, rates) == rates)
        require((0..4).all { i -> values[i] == entries.firstOrNull { baseIndex(it.definition.id) == i }?.value })
        require(income().isFinite())
    }
}
internal fun DayGroup.withMeasurements(entries: List<DayMetric>): DayGroup {
    val defs = entries.map { it.definition }
    validateDefinitions(defs)
    return copy(mask = definitionMask(defs), rates = definitionRates(defs, rates),
        values = (0..4).map { i -> entries.firstOrNull { baseIndex(it.definition.id) == i }?.value }, measurements = entries)
}
internal fun WorkGroupEntity.newDayGroup(): DayGroup = DayGroup(id, name, mask, List(5) { null }, rates())
    .withMeasurements(definitions().map { DayMetric(it, null) })
internal fun encodeDayGroups(groups: List<DayGroup>): String = JSONArray().apply {
    groups.forEach { g -> put(JSONObject().put("id", g.id).put("name", g.name).put("mask", g.mask)
        .put("values", JSONArray().apply { g.values.forEach { put(it ?: JSONObject.NULL) } }).put("rates", JSONArray(g.rates)).apply { g.measurements?.let { entries -> put("measurements", JSONArray().apply { entries.forEach { put(definitionJson(it.definition).put("value", it.value ?: JSONObject.NULL)) } }) } }) }
}.toString()
internal fun decodeDayGroups(json: String): List<DayGroup> = JSONArray(json).let { a ->
    (0 until a.length()).map { i -> a.getJSONObject(i).let { o ->
        val v = o.getJSONArray("values"); require(v.length() == 5)
        val id = o.get("id"); require(id is Int || id is Long)
        val mask = o.get("mask"); require(mask is Int && mask in 0..31)
        DayGroup((id as Number).toLong(), o.getString("name"), mask, (0..4).map { if (v.isNull(it)) null else (v.get(it) as Number).toDouble() }, readRates(o.getJSONArray("rates")), if (o.has("measurements")) o.getJSONArray("measurements").let { m -> (0 until m.length()).map { j -> m.getJSONObject(j).let { e -> DayMetric(readDefinition(e), if (e.isNull("value")) null else (e.get("value") as Number).toDouble()) } } } else null)
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
