
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

@Entity(tableName = "tariffs")
data class TariffEntity(
    @PrimaryKey val id: Int = 1,
    val picksRate: Double = 0.0,
    val weightRate: Double = 0.0,
    val volumeRate: Double = 0.0,
    val boxesRate: Double = 0.0,
    val hoursRate: Double = 0.0
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
    val hoursRate: Double = 0.0
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

@Dao
interface WorkDao {
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

    @Query("SELECT * FROM shifts ORDER BY startDate DESC, id DESC")
    fun observeShifts(): Flow<List<ShiftEntity>>

    @Query("SELECT * FROM shifts WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveShift(): ShiftEntity?

    @Insert
    suspend fun insertShift(shift: ShiftEntity): Long

    @Update
    suspend fun updateShift(shift: ShiftEntity)
}

@Database(
    entities = [
        TariffEntity::class,
        WorkDayEntity::class,
        ShiftEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class WorkDatabase : RoomDatabase() {

    abstract fun workDao(): WorkDao

    companion object {
        @Volatile
        private var INSTANCE: WorkDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
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

        fun getInstance(context: Context): WorkDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WorkDatabase::class.java,
                    "uchet_dohoda.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
