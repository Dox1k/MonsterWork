package com.shiftcalendar

import com.shiftcalendar.data.Shift
import com.shiftcalendar.ui.AppColors
import com.shiftcalendar.ui.DarkColors
import com.shiftcalendar.ui.LightColors
import com.shiftcalendar.ui.appColorScheme
import com.shiftcalendar.ui.canCountFor
import com.shiftcalendar.ui.canFill
import com.shiftcalendar.ui.elapsedHours
import com.shiftcalendar.ui.liveEarnings
import com.shiftcalendar.ui.minuteTickDelayMs
import com.shiftcalendar.ui.nextMidnight
import com.shiftcalendar.ui.nextMinuteTick
import com.shiftcalendar.ui.workedHours
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.graphics.luminance
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Банки монстров: одна банка за час смены, заливка по прошедшим часам.
 * Всё считается чистыми функциями, поэтому тест обычный JVM-тест без Android.
 */
class MonsterBanksTest {

    private fun shift(
        date: String = "2026-11-01",
        kind: String = "DAY",
        hours: Double = 12.0,
        start: String? = "08:00",
        end: String? = "20:00",
        rate: Double = 0.0,
        dayOff: Boolean = false
    ) = Shift(
        id = 1, date = date, kind = kind, hours = hours, startTime = start,
        rate = rate, isDayOff = dayOff, endTime = end
    )

    @Test
    fun `двенадцатичасовая смена показывает двенадцать банок`() {
        assertEquals(12, canCountFor(shift()))
    }

    @Test
    fun `двухчасовая смена показывает две банки`() {
        assertEquals(2, canCountFor(shift(kind = "CUSTOM", hours = 2.0, start = "10:00", end = "12:00")))
    }

    @Test
    fun `ночная смена через полночь тоже даёт банки по часам`() {
        val night = shift(kind = "NIGHT", start = "20:00", end = "08:00")
        assertEquals(12, canCountFor(night))
        assertEquals(12.0, elapsedHours(night, LocalDateTime.of(2026, 11, 3, 0, 0)), 0.001)
    }

    @Test
    fun `число банок привязано к часам, а не к константе`() {
        // 8-часовой день и 6-часовая «своя» смена дают разное количество банок.
        assertEquals(8, canCountFor(shift(kind = "DAY", hours = 8.0, start = "09:00", end = "17:00")))
        assertEquals(6, canCountFor(shift(kind = "CUSTOM", hours = 6.0, start = "10:00", end = "16:00")))
        // Получасовой интервал — минимум одна банка, пустая смена — ноль.
        assertEquals(1, canCountFor(shift(kind = "CUSTOM", hours = 0.5, start = "10:00", end = "10:30")))
        assertEquals(0, canCountFor(shift(dayOff = true, hours = 0.0, start = null, end = null)))
    }

    @Test
    fun `банки заполняются по одной за час`() {
        val s = shift()
        val at1130 = LocalDateTime.of(2026, 11, 1, 11, 30) // 3.5 часа
        val elapsed = elapsedHours(s, at1130)
        assertEquals(3.5, elapsed, 0.001)
        assertEquals(1f, canFill(0, elapsed), 0.001f)
        assertEquals(1f, canFill(2, elapsed), 0.001f)
        assertEquals(0.5f, canFill(3, elapsed), 0.001f)
        assertEquals(0f, canFill(4, elapsed), 0.001f)
        // Пока смена не началась — все банки пустые.
        assertEquals(0f, canFill(0, elapsedHours(s, LocalDateTime.of(2026, 11, 1, 7, 0))), 0.001f)
        // После конца — полный ряд.
        assertEquals(1f, canFill(11, elapsedHours(s, LocalDateTime.of(2026, 11, 1, 21, 0))), 0.001f)
    }

    @Test
    fun `живой заработок растёт пропорционально смене`() {
        val s = shift(rate = 100.0)
        val start = LocalDateTime.of(2026, 11, 1, 8, 0)
        val half = LocalDateTime.of(2026, 11, 1, 14, 0)
        assertEquals(0.0, liveEarnings(s, start, LocalTime.of(22, 0), LocalTime.of(6, 0), 20.0), 0.01)
        // Половина смены — половина дневного заработка (1200 ₽).
        assertEquals(600.0, liveEarnings(s, half, LocalTime.of(22, 0), LocalTime.of(6, 0), 20.0), 1.0)
        assertEquals(6.0, workedHours(s, half), 0.001)
    }

    @Test
    fun `тик минут срабатывает в начале следующей минуты и не чаще раза в минуту`() {
        val now = LocalDateTime.of(2026, 11, 1, 8, 30, 42)
        val tick = nextMinuteTick(now)
        assertEquals(LocalDateTime.of(2026, 11, 1, 8, 31, 0), tick)
        val delay = minuteTickDelayMs(now)
        assertEquals(18_000L, delay)
        assertTrue(delay in 0..60_000)
        // Ровно на границе минуты ждём целую минуту, а не ноль.
        assertEquals(60_000L, minuteTickDelayMs(LocalDateTime.of(2026, 11, 1, 8, 31, 0)))
        // Полночь — ближайшая граница суток.
        assertEquals(LocalDateTime.of(2026, 11, 2, 0, 0), nextMidnight(LocalDateTime.of(2026, 11, 1, 23, 59)))
    }

    @Test
    fun `тёмная палитра действительно темная и отличается от светлой`() {
        assertNotEquals(LightColors.paper, DarkColors.paper)
        assertNotEquals(LightColors.ink, DarkColors.ink)
        assertNotEquals(LightColors.cardBg, DarkColors.cardBg)
        // Фон тёмной темы темнее фона, а текст — светлее фона; в светлой наоборот.
        assertTrue(DarkColors.paper.luminance() < LightColors.paper.luminance())
        assertTrue(DarkColors.ink.luminance() > DarkColors.paper.luminance())
        assertTrue(LightColors.ink.luminance() < LightColors.paper.luminance())
        assertTrue(DarkColors.isDark)
        assertFalse(LightColors.isDark)
        // Material-схема следует палитре, а не дефолтам lightColorScheme/darkColorScheme.
        with(appColorScheme(DarkColors)) {
            assertEquals(DarkColors.paper, background)
            assertEquals(DarkColors.ink, onBackground)
            assertEquals(DarkColors.cardBg, surface)
        }
        with(appColorScheme(LightColors)) {
            assertEquals(LightColors.paper, background)
            assertEquals(LightColors.ink, onBackground)
        }
        // Для читаемости на тёмном фоне акценты светлее.
        val dark: AppColors = DarkColors
        assertTrue(dark.shiftRed.luminance() > LightColors.shiftRed.luminance())
    }
}
