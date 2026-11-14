
package com.example.uchetdohodapro.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import androidx.room.Transaction
import androidx.room.ForeignKey
import androidx.room.Index
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

@Entity(tableName = "tariffs")
data class TariffEntity(
    @PrimaryKey val id: Int = 1,
    val picksRate: Double = 0.0,
    val weightRate: Double = 0.0,
    val volumeRate: Double = 0.0,
    val boxesRate: Double = 0.0,
    val hoursRate: Double = 0.0,
    val savedAt: Long? = null
)

@Entity(tableName = "work_days")
data class WorkDayEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String,
    val picks: Double? = null,
    val weight: Double? = null,
    val volume: Double? = null,
    val boxes: Double? = null,
    val hours: Double? = null,
    val picksRate: Double = 0.0,
    val weightRate: Double = 0.0,
    val volumeRate: Double = 0.0,
    val boxesRate: Double = 0.0,
    val hoursRate: Double = 0.0,
    val shiftId: Long? = null
) {
    fun calculateIncome(): Double {
        return (picks ?: 0.0) * picksRate +
            (weight ?: 0.0) * weightRate +
            (volume ?: 0.0) * volumeRate +
            (boxes ?: 0.0) * boxesRate +
            (hours ?: 0.0) * hoursRate
    }
}

@Entity(tableName = "shifts")
data class ShiftEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startDate: String,
    val endDate: String? = null,
    val isActive: Boolean = true
)

/** A blank measurement stays null; invalid and non-finite input never becomes a stored zero. */
fun parseNonNegativeNumber(text: String): Double? {
    val normalized = text.trim().replace(',', '.')
    if (!normalized.matches(Regex("([0-9]+([.][0-9]+)?|[.][0-9]+)"))) return null
    val number = normalized.toDoubleOrNull() ?: return null
    if (!number.isFinite() || number < 0) return null
    if (number == 0.0 && BigDecimal(normalized).signum() != 0) return null
    return number
}

fun editableNumber(value: Double?): String = value?.let {
    if (it.isFinite()) BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() else it.toString()
} ?: ""

fun dayFromInput(date: String, values: List<String>, tariffs: TariffEntity, shiftId: Long? = null): WorkDayEntity {
    require(values.size == 5)
    val numbers = values.map { parseNonNegativeNumber(it) }
    return WorkDayEntity(date = date, picks = numbers[0], weight = numbers[1], volume = numbers[2], boxes = numbers[3], hours = numbers[4],
        picksRate = tariffs.picksRate, weightRate = tariffs.weightRate, volumeRate = tariffs.volumeRate,
        boxesRate = tariffs.boxesRate, hoursRate = tariffs.hoursRate, shiftId = shiftId)
}

private fun validateDayNumbers(day: WorkDayEntity) {
    val values = listOfNotNull(day.picks, day.weight, day.volume, day.boxes, day.hours) +
        listOf(day.picksRate, day.weightRate, day.volumeRate, day.boxesRate, day.hoursRate)
    require(values.all { it.isFinite() && it >= 0 } && day.calculateIncome().isFinite()) { "Проверьте показатели и тарифы: сумма должна быть конечным числом" }
}

fun parseStoredDate(text: String): LocalDate? = runCatching {
    when {
        text.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")) -> LocalDate.parse(text)
        text.matches(Regex("[0-9]{2}[.][0-9]{2}[.][0-9]{4}")) -> LocalDate.parse(text,
            DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT))
        else -> null
    }
}.getOrNull()?.takeIf { it.year in 1..9999 }

fun dayBelongsToShift(date: String, shift: ShiftEntity): Boolean {
    val day = parseStoredDate(date) ?: return false
    val start = parseStoredDate(shift.startDate) ?: return false
    val end = shift.endDate?.let { parseStoredDate(it) ?: return false }
    return !day.isBefore(start) && (end == null || !day.isAfter(end))
}

fun uniqueWorkDayCount(days: List<WorkDayEntity>): Int = days.map { parseStoredDate(it.date)?.toString() ?: it.date }.distinct().size

private fun validateShiftDates(shift: ShiftEntity) {
    val start = parseStoredDate(shift.startDate)
    val end = shift.endDate?.let { parseStoredDate(it) }
    require(start != null && (shift.endDate == null || end != null)) { "Укажите правильные даты вахты" }
    require(end == null || !end.isBefore(start)) { "Окончание раньше начала вахты" }
    require(!shift.isActive || shift.endDate == null) { "У активной вахты не должно быть даты окончания" }
}

const val ADVANCE = "advance"
const val FINE = "fine"

@Entity(
    tableName = "money_operations",
    foreignKeys = [ForeignKey(entity = ShiftEntity::class, parentColumns = ["id"],
        childColumns = ["shiftId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("shiftId")]
)
data class MoneyOperationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shiftId: Long?,
    val date: String,
    val type: String,
    val amountMinor: Long,
    val comment: String = ""
)

fun parseOperationMinor(text: String): Long? = runCatching {
    val normalized = text.trim().replace(',', '.')
    require(normalized.matches(Regex("[0-9]+([.][0-9]{1,2})?")))
    BigDecimal(normalized).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
        .also { require(it > 0) }
}.getOrNull()

fun validOperationDate(text: String): Boolean = text.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")) &&
    parseStoredDate(text)?.toString() == text

fun financeTotal(operations: List<MoneyOperationEntity>, type: String): Double =
    operations.filter { it.type == type }.sumOf { BigDecimal.valueOf(it.amountMinor, 2) }.toDouble()

@Dao
interface WorkDao {
    @Query("SELECT * FROM money_operations ORDER BY date DESC, id DESC")
    fun observeMoneyOperations(): Flow<List<MoneyOperationEntity>>

    @Query("SELECT * FROM money_operations ORDER BY id")
    suspend fun backupMoneyOperations(): List<MoneyOperationEntity>

    @Query("SELECT * FROM shifts WHERE id = :shiftId")
    suspend fun getShift(shiftId: Long): ShiftEntity?

    @Insert
    suspend fun insertMoneyOperationRaw(operation: MoneyOperationEntity): Long

    @Update
    suspend fun updateMoneyOperationRaw(operation: MoneyOperationEntity): Int

    @Transaction
    suspend fun saveMoneyOperation(operation: MoneyOperationEntity) {
        require(operation.type == ADVANCE || operation.type == FINE) { "Неизвестная операция" }
        require(operation.amountMinor > 0) { "Сумма должна быть больше нуля" }
        require(validOperationDate(operation.date)) { "Неверная дата" }
        require(operation.shiftId != null && getShift(operation.shiftId) != null) { "Выберите существующую вахту" }
        require(operation.comment.length <= 4000) { "Слишком длинный комментарий" }
        if (operation.id == 0L) insertMoneyOperationRaw(operation)
        else check(updateMoneyOperationRaw(operation) == 1) { "Операция уже удалена" }
    }

    @Query("DELETE FROM money_operations WHERE id = :id")
    suspend fun deleteMoneyOperation(id: Long)

    @Query("DELETE FROM money_operations")
    suspend fun clearMoneyOperationsForRestore()

    @Insert
    suspend fun restoreMoneyOperations(operations: List<MoneyOperationEntity>)


    @Query("SELECT * FROM tariffs ORDER BY id")
    suspend fun backupTariffs(): List<TariffEntity>

    @Query("SELECT * FROM work_days ORDER BY id")
    suspend fun backupDays(): List<WorkDayEntity>

    @Query("SELECT * FROM shifts ORDER BY id")
    suspend fun backupShifts(): List<ShiftEntity>

    @Insert
    suspend fun restoreDays(days: List<WorkDayEntity>)

    @Insert
    suspend fun restoreShifts(shifts: List<ShiftEntity>)

    @Query("DELETE FROM work_days")
    suspend fun clearDaysForRestore()

    @Query("DELETE FROM shifts")
    suspend fun clearShiftsForRestore()

    @Query("DELETE FROM tariffs")
    suspend fun clearTariffsForRestore()

    @Query("SELECT * FROM tariffs WHERE id = 1 LIMIT 1")
    fun observeTariffs(): Flow<TariffEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTariffsRaw(tariffs: TariffEntity)

    @Transaction
    suspend fun saveTariffs(tariffs: TariffEntity) {
        require(tariffs.id == 1 && listOf(tariffs.picksRate, tariffs.weightRate, tariffs.volumeRate, tariffs.boxesRate, tariffs.hoursRate)
            .all { it.isFinite() && it >= 0 }) { "Тарифы должны быть конечными числами не меньше нуля" }
        require(tariffs.savedAt == null || tariffs.savedAt > 0) { "Неверная дата сохранения" }
        saveTariffsRaw(tariffs)
    }

    @Query("SELECT * FROM work_days ORDER BY date DESC, id DESC")
    fun observeWorkDays(): Flow<List<WorkDayEntity>>

    @Insert
    suspend fun insertWorkDayRaw(day: WorkDayEntity): Long

    @Update
    suspend fun updateWorkDayRaw(day: WorkDayEntity): Int

    @Query("SELECT COUNT(*) FROM work_days WHERE id != :excludedId AND (date = :isoDate OR date = :displayDate)")
    suspend fun countDaysForDate(isoDate: String, displayDate: String, excludedId: Long): Int

    @Transaction
    suspend fun insertWorkDay(day: WorkDayEntity): Long {
        validateDayNumbers(day)
        val date = parseStoredDate(day.date) ?: error("Неверная дата рабочего дня")
        require(countDaysForDate(date.toString(), date.format(DateTimeFormatter.ofPattern("dd.MM.uuuu")), 0) == 0) {
            "Рабочий день за эту дату уже существует. Измените его в истории."
        }
        day.shiftId?.let { id ->
            val shift = getShift(id) ?: error("Выбранная вахта больше не существует")
            require(dayBelongsToShift(day.date, shift)) { "Дата рабочего дня вне периода выбранной вахты" }
        }
        return insertWorkDayRaw(day)
    }

    @Transaction
    suspend fun updateWorkDay(day: WorkDayEntity) {
        validateDayNumbers(day)
        val old = getWorkDay(day.id) ?: error("Рабочий день уже удалён")
        val date = parseStoredDate(day.date) ?: error("Неверная дата рабочего дня")
        if (day.date != old.date) require(countDaysForDate(date.toString(), date.format(DateTimeFormatter.ofPattern("dd.MM.uuuu")), day.id) == 0) {
            "Рабочий день за эту дату уже существует"
        }
        day.shiftId?.let { id ->
            val shift = getShift(id) ?: error("Выбранная вахта больше не существует")
            // Old mismatches remain editable without silently rewriting historical dates or links.
            if (old.date != day.date || old.shiftId != day.shiftId)
                require(dayBelongsToShift(day.date, shift)) { "Дата рабочего дня вне периода выбранной вахты" }
        }
        check(updateWorkDayRaw(day) == 1) { "Рабочий день уже удалён" }
    }

    @Query("DELETE FROM work_days WHERE id = :dayId")
    suspend fun deleteWorkDay(dayId: Long)

    @Query("SELECT * FROM work_days WHERE id = :dayId LIMIT 1")
    suspend fun getWorkDay(dayId: Long): WorkDayEntity?

    @Query(
        "SELECT * FROM work_days " +
        "WHERE shiftId = :shiftId " +
        "ORDER BY date DESC, id DESC"
    )
    fun observeWorkDaysForShift(
        shiftId: Long
    ): Flow<List<WorkDayEntity>>

    @Query(
        "SELECT * FROM work_days " +
        "WHERE shiftId IS NULL " +
        "ORDER BY date DESC, id DESC"
    )
    fun observeUnassignedWorkDays(): Flow<List<WorkDayEntity>>

    @Transaction
    suspend fun assignWorkDayToShift(dayId: Long, shiftId: Long?) {
        val day = getWorkDay(dayId) ?: error("Рабочий день уже удалён")
        updateWorkDay(day.copy(shiftId = shiftId))
    }

    @Query("SELECT * FROM shifts ORDER BY id DESC")
    fun observeShifts(): Flow<List<ShiftEntity>>

    @Query("SELECT * FROM shifts WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveShift(): ShiftEntity?

    @Query("SELECT COUNT(*) FROM shifts WHERE isActive = 1 AND id != :excludedId")
    suspend fun countOtherActiveShifts(excludedId: Long): Int

    @Insert
    suspend fun insertShiftRaw(shift: ShiftEntity): Long

    @Update
    suspend fun updateShiftRaw(shift: ShiftEntity): Int

    @Transaction
    suspend fun insertShift(shift: ShiftEntity): Long {
        validateShiftDates(shift)
        require(!shift.isActive || countOtherActiveShifts(0) == 0) { "Уже существует активная вахта" }
        return insertShiftRaw(shift)
    }

    @Transaction
    suspend fun updateShift(shift: ShiftEntity) {
        val old = getShift(shift.id) ?: error("Вахта уже удалена")
        validateShiftDates(shift)
        require(!shift.isActive || countOtherActiveShifts(shift.id) == 0) { "Уже существует другая активная вахта" }
        if (old.startDate != shift.startDate || old.endDate != shift.endDate) {
            require(backupDays().filter { it.shiftId == shift.id }.all { dayBelongsToShift(it.date, shift) }) {
                "Новые даты исключают связанные рабочие дни. Сначала измените их привязку в истории."
            }
        }
        check(updateShiftRaw(shift) == 1) { "Вахта уже удалена" }
    }
    
    @Query(
        "UPDATE work_days SET shiftId = NULL " +
        "WHERE shiftId = :shiftId"
    )
    suspend fun unassignDaysFromShift(
        shiftId: Long
    )

    @Query(
        "DELETE FROM shifts WHERE id = :shiftId"
    )
    suspend fun deleteShiftById(
        shiftId: Long
    )

    @Transaction
    suspend fun deleteShiftSafely(
        shiftId: Long
    ) {
        unassignDaysFromShift(shiftId)
        deleteShiftById(shiftId)
    }
    
}

@Database(
    entities = [
        TariffEntity::class,
        WorkDayEntity::class,
        ShiftEntity::class,
        MoneyOperationEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class WorkDatabase : RoomDatabase() {

    abstract fun workDao(): WorkDao

    companion object {

        @Volatile
        private var INSTANCE: WorkDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `shifts` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `startDate` TEXT NOT NULL,
                        `endDate` TEXT,
                        `isActive` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE `work_days`
                    ADD COLUMN `shiftId` INTEGER
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tariffs` ADD COLUMN `savedAt` INTEGER")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `money_operations` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `shiftId` INTEGER, `date` TEXT NOT NULL, `type` TEXT NOT NULL,
                    `amountMinor` INTEGER NOT NULL, `comment` TEXT NOT NULL,
                    FOREIGN KEY(`shiftId`) REFERENCES `shifts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                )""".trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_money_operations_shiftId` ON `money_operations` (`shiftId`)")
            }
        }

        fun getInstance(context: Context): WorkDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WorkDatabase::class.java,
                    "uchet_dohoda.db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5
                    )
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
