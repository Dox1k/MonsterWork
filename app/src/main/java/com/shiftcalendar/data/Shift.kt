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
import androidx.room.Transaction
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
    val profileId: Long = 0,
    /**
     * Второй профиль дня (решение 12-Б): когда на один день накладываются два
     * паттерна, квадрат дня делится пополам — верх по первому профилю, низ по
     * второму. 0 — второго профиля нет.
     */
    val profileId2: Long = 0
)

@Dao
interface ShiftDao {
    @Query("SELECT * FROM shifts WHERE date = :date LIMIT 1")
    suspend fun byDate(date: String): Shift?

    /**
     * ВСЕ смены дня, в стабильном порядке: сначала более раннее начало,
     * затем по id. День может содержать несколько независимых смен
     * (например, подработка днём и дежурство ночью).
     *
     * Выходные и смены без времени идут в конце: строка '99:99' больше любой
     * реальной «ЧЧ:ММ», COALESCE подставляет её только для NULL.
     */
    @Query(
        "SELECT * FROM shifts WHERE date = :date " +
            "ORDER BY CASE WHEN isDayOff = 1 OR startTime IS NULL THEN 1 ELSE 0 END, " +
            "COALESCE(startTime, '99:99'), id"
    )
    suspend fun allOnDate(date: String): List<Shift>?

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

    /** Записать пачку смен и вернуть их id — для напоминаний. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReturningIds(shifts: List<Shift>): List<Long>

    @Query("SELECT date FROM shifts WHERE date >= :from AND date <= :to")
    suspend fun datesBetween(from: String, to: String): List<String>

    /** Все смены календаря — для полной очистки и снятия напоминаний. */
    @Query("SELECT * FROM shifts")
    suspend fun all(): List<Shift>

    /** Удалить все смены одним оператором — «очистить календарь». */
    @Query("DELETE FROM shifts")
    suspend fun deleteAll()

    /** Существует ли смена на дату — без чтения всей строки. */
    @Query("SELECT COUNT(*) FROM shifts WHERE date = :date")
    suspend fun countOn(date: String): Int

    /** Сколько дат интервала уже занято сменами — для честного «заменено N». */
    @Query("SELECT COUNT(*) FROM shifts WHERE date >= :from AND date <= :to")
    suspend fun countBetween(from: String, to: String): Int

    /**
     * Атомарная замена диапазона: в ОДНОЙ транзакции удаляются смены и
     * записывается новая пачка. Без транзакции падение между DELETE и INSERT
     * оставляло календарь с дырой вместо старых смен.
     *
     * @param profileId если > 0 — удаляются только смены этого профиля
     *   (самозамена); 0 или отрицательное — удаляются все смены интервала.
     */
    @Transaction
    suspend fun replaceBetween(
        from: String,
        to: String,
        shifts: List<Shift>,
        profileId: Long = 0L
    ): List<Long> {
        require(from <= to) { "replaceBetween: from=$from > to=$to" }
        if (profileId > 0L) deleteBetweenOfProfile(from, to, profileId) else deleteBetween(from, to)
        return if (shifts.isEmpty()) emptyList() else insertAllReturningIds(shifts)
    }

    /** Атомарно стереть все смены. */
    @Transaction
    suspend fun clearAll() {
        deleteAll()
    }

    /** Атомарно стереть все смены одного профиля. */
    @Transaction
    suspend fun clearProfile(profileId: Long) {
        deleteByProfile(profileId)
    }

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

@Database(entities = [Shift::class], version = 4, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun shifts(): ShiftDao
}
