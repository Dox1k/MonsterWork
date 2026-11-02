package com.shiftcalendar

import com.shiftcalendar.data.ShiftKind
import com.shiftcalendar.model.*
import com.shiftcalendar.ui.isEmptyInterval
import com.shiftcalendar.ui.kindForInterval
import com.shiftcalendar.ui.sanitizeTimeInput
import com.shiftcalendar.ui.timeFieldText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Пользовательские интервалы смены и шага паттерна: разбор «ЧЧ:ММ»,
 * длительность с переходом через полночь и подстановка типа.
 */
class ShiftIntervalTest {

    @Test
    fun `разбор времени принимает разные формы записи`() {
        assertEquals("08:00", normalizeTime("8:0"))
        assertEquals("21:45", normalizeTime("21:45"))
        assertEquals("07:05", normalizeTime("07 05"))
        assertEquals("09:30", normalizeTime("0930".chunked(2).joinToString(":")))
        assertNull(normalizeTime(null))
        assertNull(normalizeTime(""))
        assertNull(normalizeTime("25:00"))
        assertNull(normalizeTime("12:75"))
        assertNull(normalizeTime("abc"))
    }

    @Test
    fun `обычный интервал даёт разницу часов`() {
        assertEquals(12.0, intervalHours("08:00", "20:00")!!, 0.001)
        assertEquals(8.5, intervalHours("09:00", "17:30")!!, 0.001)
        assertEquals(0.5, intervalHours("08:00", "08:30")!!, 0.001)
    }

    @Test
    fun `ночной интервал считается через полночь`() {
        assertEquals(12.0, intervalHours("20:00", "08:00")!!, 0.001)
        assertEquals(7.5, intervalHours("22:00", "05:30")!!, 0.001)
        // равные концы — сутки
        assertEquals(24.0, intervalHours("08:00", "08:00")!!, 0.001)
    }

    @Test
    fun `некорректный интервал не даёт часов`() {
        assertNull(intervalHours("", "20:00"))
        assertNull(intervalHours("08:00", null))
        assertNull(intervalHours("29:00", "30:00"))
    }

    @Test
    fun `тип смены уточняется по интервалу`() {
        assertEquals(ShiftKind.NIGHT, kindForInterval(ShiftKind.DAY, "20:00", "08:00"))
        assertEquals(ShiftKind.DAY, kindForInterval(ShiftKind.DAY, "08:00", "20:00"))
        assertEquals(ShiftKind.CUSTOM, kindForInterval(ShiftKind.CUSTOM, "07:00", "15:00"))
    }

    @Test
    fun `поле времени фильтрует ввод`() {
        assertEquals("0930", sanitizeTimeInput("09:30".replace(":", "")))
        assertEquals("09:30", sanitizeTimeInput("09:30"))
        assertEquals("09:35", sanitizeTimeInput("09:3a5"))
        assertEquals("", sanitizeTimeInput("ab"))
        assertEquals("12:00", timeFieldText("12:00"))
        assertEquals("", timeFieldText(null))
    }

    @Test
    fun `пустой интервал распознаётся`() {
        assertTrue(isEmptyInterval("", ""))
        assertTrue(isEmptyInterval("xx", ""))
        assertFalse(isEmptyInterval("08:00", ""))
        assertFalse(isEmptyInterval("", "20:00"))
    }

    @Test
    fun `сдвиг времени перекатывается через полночь`() {
        assertEquals("07:30", shiftTime("08:00", -30, DAY_START))
        assertEquals("08:30", shiftTime("08:00", 30, DAY_START))
        assertEquals("23:30", shiftTime("00:00", -30, DAY_START))
        assertEquals("00:30", shiftTime("00:00", 30, DAY_START))
        // мусор в поле — считаем от запасного значения
        assertEquals("07:30", shiftTime("", -30, DAY_START))
    }

    @Test
    fun `интервалы по умолчанию зависят от типа`() {
        assertEquals(DAY_START, defaultInterval(ShiftKind.DAY).first)
        assertEquals(DAY_END, defaultInterval(ShiftKind.DAY).second)
        assertEquals(NIGHT_START, defaultInterval(ShiftKind.NIGHT).first)
        assertEquals(NIGHT_END, defaultInterval(ShiftKind.NIGHT).second)
        assertEquals(12.0, intervalHours(NIGHT_START, NIGHT_END)!!, 0.001)
    }

    @Test
    fun `шаг паттерна без явного времени подставляет типовой интервал`() {
        val day = PatternStep(kind = ShiftKind.DAY)
        assertEquals(DAY_START, day.start)
        assertEquals(true, day.effectiveHours > 0.0)
        assertEquals(DAY_END, day.end)
        assertEquals(12.0, day.effectiveHours, 0.001)

        val night = PatternStep(kind = ShiftKind.NIGHT)
        assertEquals(NIGHT_START, night.start)
        assertEquals(NIGHT_END, night.end)
        assertEquals(12.0, night.effectiveHours, 0.001)
    }

    @Test
    fun `выходной шаг не имеет часов и времени`() {
        val off = PatternStep(isDayOff = true, kind = ShiftKind.DAY)
        assertEquals(0.0, off.effectiveHours, 0.001)
    }

    @Test
    fun `пресеты циклов задают время явно`() {
        PRESET_PATTERNS.forEach { (_, steps) ->
            steps.forEach { step ->
                if (step.isDayOff) {
                    assertNull(step.startTime)
                } else {
                    assertEquals(true, normalizeTime(step.startTime) != null)
                    assertEquals(true, normalizeTime(step.endTime) != null)
                }
            }
        }
    }
}
