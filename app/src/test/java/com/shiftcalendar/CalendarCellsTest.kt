package com.shiftcalendar

import com.shiftcalendar.data.Shift
import com.shiftcalendar.model.*
import com.shiftcalendar.ui.shiftProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * Проверки расчёта сетки месяца. Обычный JVM-тест без Android —
 * дополняет сборку, чтобы ловить арифметику календаря.
 */
class CalendarCellsTest {

    @Test
    fun `октябрь 2026 начинается в четверг и содержит 31 день`() {
        val ym = YearMonth.of(2026, 10)
        val cells = monthCells(ym)
        // 1 октября 2026 — четверг → 3 пустых места (Пн, Вт, Ср)
        assertEquals(3, cells.takeWhile { it == null }.size)
        assertEquals(31, cells.count { it != null })
        assertEquals(0, cells.size % 7)
        assertEquals(LocalDate.of(2026, 10, 1), cells[3])
    }

    @Test
    fun `первое число в понедельник не оставляет пустых ячеек`() {
        // 1 июня 2026 — понедельник
        val cells = monthCells(YearMonth.of(2026, 6))
        assertEquals(0, cells.takeWhile { it == null }.size)
        assertEquals(LocalDate.of(2026, 6, 1), cells.first())
    }

    @Test
    fun `все дни месяца идут подряд без пропусков`() {
        val ym = YearMonth.of(2026, 2)
        val days = monthCells(ym).filterNotNull()
        assertEquals(28, days.size)
        assertEquals((1..28).toList(), days.map { it.dayOfMonth })
    }

    @Test
    fun `денежный формат разделяет разряды и показывает копейки`() {
        assertEquals("500 ₽", formatMoney(500.0))
        assertEquals("12 000 ₽", formatMoney(12000.0))
        assertEquals("7 500 ₽", formatMoney(7500.0))
        assertEquals("1 250,50 ₽", formatMoney(1250.5))
    }

    @Test
    fun `часы показываются целыми или с запятой`() {
        assertEquals("12 ч", formatHours(12.0))
        assertEquals("8 ч", formatHours(8.0))
        assertTrue(formatHours(7.5).contains("7"))
    }

    @Test
    fun `итоги месяца считают смены, часы и заработок без выходных`() {
        val shifts = listOf(
            Shift(id = 1, date = "2026-10-01", kind = "DAY", hours = 12.0, startTime = "08:00", rate = 100.0),
            Shift(id = 2, date = "2026-10-02", kind = "NIGHT", hours = 12.0, startTime = "20:00", rate = 100.0),
            Shift(id = 3, date = "2026-10-03", kind = "DAY", hours = 0.0, startTime = null, rate = 0.0, isDayOff = true)
        )
        val m = com.shiftcalendar.ui.MonthSummary(YearMonth.of(2026, 10), shifts)
        assertEquals(2, m.shiftCount)
        assertEquals(24.0, m.totalHours, 0.001)
        assertEquals(2400.0, m.totalEarned, 0.001)
    }

    @Test
    fun `прогресс ночной смены растёт после полуночи`() {
        val shift = Shift(
            id = 1, date = "2026-10-05", kind = "NIGHT",
            hours = 12.0, startTime = "20:00", rate = 0.0
        )
        // до начала — 0
        assertEquals(0f, shiftProgress(shift, LocalDateTime.of(2026, 10, 5, 19, 0)), 0.001f)
        // ровно середина — половина: 20:00 + 6 ч = 02:00 следующего дня
        assertEquals(0.5f, shiftProgress(shift, LocalDateTime.of(2026, 10, 6, 2, 0)), 0.01f)
        // 04:00 — это уже две трети пути
        assertEquals(2f / 3f, shiftProgress(shift, LocalDateTime.of(2026, 10, 6, 4, 0)), 0.01f)
        // после конца — 1
        assertEquals(1f, shiftProgress(shift, LocalDateTime.of(2026, 10, 6, 9, 0)), 0.001f)
    }

    @Test
    fun `прогресс дневной смены половина к 14 часам`() {
        val shift = Shift(
            id = 1, date = "2026-10-05", kind = "DAY",
            hours = 12.0, startTime = "08:00", rate = 0.0
        )
        assertEquals(0.5f, shiftProgress(shift, LocalDateTime.of(2026, 10, 5, 14, 0)), 0.01f)
    }

    @Test
    fun `выходной и смена без времени не дают прогресса`() {
        val dayOff = Shift(id = 1, date = "2026-10-05", kind = "DAY", hours = 0.0, startTime = null, rate = 0.0, isDayOff = true)
        assertEquals(0f, shiftProgress(dayOff, LocalDateTime.of(2026, 10, 5, 14, 0)), 0.001f)

        val custom = Shift(id = 2, date = "2026-10-06", kind = "CUSTOM", hours = 6.0, startTime = null, rate = 0.0)
        assertEquals(0f, shiftProgress(custom, LocalDateTime.of(2026, 10, 6, 14, 0)), 0.001f)
    }
}
