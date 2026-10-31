package com.shiftcalendar.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Тип смены. Ручной — свой интервал, введённый пользователем. */
enum class ShiftKind { DAY, NIGHT, CUSTOM }

/**
 * Одна запись в календаре. Дата хранится строкой ISO "yyyy-MM-dd" —
 * так сортировка и выборка по месяцу работают без часовых поясов.
 */
@Entity(tableName = "shifts")
data class Shift(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val kind: String,
    val hours: Double,
    val startTime: String?,   // "HH:mm", null для выхода/своей без времени
    val rate: Double,         // ставка за час
    val isDayOff: Boolean = false,
    val note: String = ""
)

@Dao
interface ShiftDao {
    @Query("SELECT * FROM shifts WHERE date = :date LIMIT 1")
    suspend fun byDate(date: String): Shift?

    @Query("SELECT * FROM shifts WHERE date LIKE :monthPrefix || '%'")
    fun byMonth(monthPrefix: String): Flow<List<Shift>>

    @Query("SELECT * FROM shifts WHERE date LIKE :monthPrefix || '%'")
    suspend fun byMonthOnce(monthPrefix: String): List<Shift>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(shift: Shift): Long

    @Update
    suspend fun update(shift: Shift)

    @Delete
    suspend fun delete(shift: Shift)

    @Query("DELETE FROM shifts WHERE date = :date")
    suspend fun deleteByDate(date: String)
}

@Database(entities = [Shift::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun shifts(): ShiftDao
}
