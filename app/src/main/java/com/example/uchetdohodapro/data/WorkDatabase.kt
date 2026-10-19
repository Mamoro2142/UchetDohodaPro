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
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tariffs")
data class TariffEntity(
    @PrimaryKey
    val id: Int = 1,
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
}

@Database(
    entities = [TariffEntity::class, WorkDayEntity::class],
    version = 1,
    exportSchema = false
)
abstract class WorkDatabase : RoomDatabase() {

    abstract fun workDao(): WorkDao

    companion object {
        @Volatile
        private var INSTANCE: WorkDatabase? = null

        fun getInstance(context: Context): WorkDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WorkDatabase::class.java,
                    "uchet_dohoda.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
