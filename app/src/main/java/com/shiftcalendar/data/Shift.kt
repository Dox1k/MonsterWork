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
    val startTime: String?,   // "HH:mm"
    val rate: Double,         // ставка за час
    val isDayOff: Boolean = false,
    val note: String = "",
    val endTime: String? = null,
    val completed: Boolean = false,
    /** Перерыв в минутах — вычитается из длительности смены. */
    val breakMinutes: Int = 0,
    /** Профиль паттерна, проставивший смену (0 — ручная смена). */
    val profileId: Long = 0
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(shifts: List<Shift>)

    @Query("SELECT date FROM shifts WHERE date >= :from AND date <= :to")
    suspend fun datesBetween(from: String, to: String): List<String>

    /** Все смены интервала (включительно) — для замены паттерном. */
    @Query("SELECT * FROM shifts WHERE date >= :from AND date <= :to")
    suspend fun shiftsBetween(from: String, to: String): List<Shift>

    /** Смены одного профиля в интервале — для самозамены профиля. */
    @Query("SELECT * FROM shifts WHERE date >= :from AND date <= :to AND profileId = :profileId")
    suspend fun shiftsBetweenOfProfile(from: String, to: String, profileId: Long): List<Shift>

    /** Удалить смены одного профиля в интервале — самозамена профиля. */
    @Query("DELETE FROM shifts WHERE date >= :from AND date <= :to AND profileId = :profileId")
    suspend fun deleteBetweenOfProfile(from: String, to: String, profileId: Long)

    /** Массовое удаление смен в интервале — «удалить смены» на главном экране. */
    @Query("DELETE FROM shifts WHERE date >= :from AND date <= :to")
    suspend fun deleteBetween(from: String, to: String)

    /** Обнулить статус выхода/работы: день остаётся, пометка «не выбрано». */
    @Query("UPDATE shifts SET isDayOff = 0, hours = 0, startTime = NULL, endTime = NULL, rate = 0, breakMinutes = 0 WHERE date >= :from AND date <= :to")
    suspend fun resetBetween(from: String, to: String)

    /** Смены, проставленные конкретным профилем (весь календарь). */
    @Query("SELECT * FROM shifts WHERE profileId = :profileId")
    suspend fun byProfile(profileId: Long): List<Shift>

    @Query("DELETE FROM shifts WHERE profileId = :profileId")
    suspend fun deleteByProfile(profileId: Long)
}

@Database(entities = [Shift::class], version = 3, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun shifts(): ShiftDao
}
