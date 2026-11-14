
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
    runCatching { LocalDate.parse(text).toString() == text }.getOrDefault(false)

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
    suspend fun saveTariffs(tariffs: TariffEntity)

    @Query("SELECT * FROM work_days ORDER BY date DESC, id DESC")
    fun observeWorkDays(): Flow<List<WorkDayEntity>>

    @Insert
    suspend fun insertWorkDay(day: WorkDayEntity): Long

    @Update
    suspend fun updateWorkDay(day: WorkDayEntity)

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

    @Query(
        "UPDATE work_days SET shiftId = :shiftId " +
        "WHERE id = :dayId"
    )
    suspend fun assignWorkDayToShift(
        dayId: Long,
        shiftId: Long?
    )

    @Query("SELECT * FROM shifts ORDER BY id DESC")
    fun observeShifts(): Flow<List<ShiftEntity>>

    @Query("SELECT * FROM shifts WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveShift(): ShiftEntity?

    @Insert
    suspend fun insertShift(shift: ShiftEntity): Long

    @Update
    suspend fun updateShift(shift: ShiftEntity)
    
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
