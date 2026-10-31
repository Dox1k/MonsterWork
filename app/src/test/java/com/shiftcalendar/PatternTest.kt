package com.shiftcalendar

import com.shiftcalendar.data.ShiftKind
import com.shiftcalendar.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Паттерн смен: цикл «раб/раб/раб/раб/вых/вых» и его наложение на календарь.
 */
class PatternTest {

    private val pattern420 = listOf(
        PatternStep(kind = ShiftKind.DAY, rate = 500.0),
        PatternStep(kind = ShiftKind.DAY, rate = 500.0),
        PatternStep(kind = ShiftKind.DAY, rate = 500.0),
        PatternStep(kind = ShiftKind.DAY, rate = 500.0),
        PatternStep(isDayOff = true),
        PatternStep(isDayOff = true)
    )

    @Test
    fun `цикл 4 через 2 чередуется по порядку`() {
        val start = LocalDate.of(2026, 11, 1)
        val res = buildPatternShifts(
            pattern = pattern420,
            start = start,
            from = start,
            to = LocalDate.of(2026, 11, 6),
            alignToMonth = false,
            keepExisting = false,
            existingDates = emptySet()
        )
        assertEquals(6, res.shifts.size)
        assertEquals(4, res.workCount)
        assertEquals(2, res.dayOffCount)
        // последние два дня цикла — выходные
        assertTrue(res.shifts[4].isDayOff)
        assertTrue(res.shifts[5].isDayOff)
    }

    @Test
    fun `часы и заработок считаются по ставке шага`() {
        val start = LocalDate.of(2026, 11, 1)
        val res = buildPatternShifts(
            pattern = pattern420,
            start = start,
            from = start,
            to = LocalDate.of(2026, 11, 30),
            alignToMonth = false,
            keepExisting = false,
            existingDates = emptySet()
        )
        // 30 дней = 5 полных циклов по 4 смены
        assertEquals(20, res.workCount)
        assertEquals(10, res.dayOffCount)
        assertEquals(20 * 12.0, res.totalHours, 0.001)
        assertEquals(20 * 12.0 * 500.0, res.totalEarned, 0.001)
    }

    @Test
    fun `режим выравнивания по месяцу начинает каждый месяц с первого шага`() {
        val start = LocalDate.of(2026, 11, 1)
        val res = buildPatternShifts(
            pattern = listOf(
                PatternStep(kind = ShiftKind.DAY),
                PatternStep(isDayOff = true)
            ),
            start = start,
            from = LocalDate.of(2026, 11, 1),
            to = LocalDate.of(2026, 12, 3),
            alignToMonth = true,
            keepExisting = false,
            existingDates = emptySet()
        )
        // 1 декабря должен снова быть рабочим днём (начало цикла)
        val dec1 = res.shifts.first { it.date == "2026-12-01" }
        assertTrue(!dec1.isDayOff)
        val dec2 = res.shifts.first { it.date == "2026-12-02" }
        assertTrue(dec2.isDayOff)
    }

    @Test
    fun `без выравнивания цикл продолжается непрерывно через границу месяца`() {
        val res = buildPatternShifts(
            pattern = listOf(
                PatternStep(kind = ShiftKind.DAY),
                PatternStep(isDayOff = true)
            ),
            start = LocalDate.of(2026, 11, 1),
            from = LocalDate.of(2026, 11, 1),
            to = LocalDate.of(2026, 12, 3),
            alignToMonth = false,
            keepExisting = false,
            existingDates = emptySet()
        )
        // непрерывный цикл 1/1: 30 ноября (день 30, чётный индекс) -> выходной,
        // 1 декабря (день 31, нечётный) -> рабочий
        assertEquals("2026-11-30", res.shifts[29].date)
        assertTrue(res.shifts[29].isDayOff)
        assertTrue(!res.shifts[30].isDayOff)
    }

    @Test
    fun `занятые дни не перетираются при включённой защите`() {
        val existing = setOf("2026-11-01", "2026-11-02")
        val res = buildPatternShifts(
            pattern = pattern420,
            start = LocalDate.of(2026, 11, 1),
            from = LocalDate.of(2026, 11, 1),
            to = LocalDate.of(2026, 11, 6),
            alignToMonth = false,
            keepExisting = true,
            existingDates = existing
        )
        assertEquals(2, res.skippedExisting)
        assertEquals(4, res.shifts.size)
        assertTrue(res.shifts.none { it.date == "2026-11-01" })
    }

    @Test
    fun `пустой цикл ничего не проставляет`() {
        val start = LocalDate.of(2026, 11, 1)
        val res = buildPatternShifts(emptyList(), start, start, LocalDate.of(2026, 12, 31), false, false, emptySet())
        assertEquals(0, res.shifts.size)
        assertEquals(0, res.workCount)
    }

    @Test
    fun `разные ставки внутри цикла учитываются отдельно`() {
        val mixed = listOf(
            PatternStep(kind = ShiftKind.DAY, hours = 12.0, rate = 500.0),
            PatternStep(kind = ShiftKind.NIGHT, hours = 12.0, rate = 700.0)
        )
        val start = LocalDate.of(2026, 11, 1)
        val res = buildPatternShifts(mixed, start, start, LocalDate.of(2026, 11, 2), false, false, emptySet())
        assertEquals(6000.0, res.shifts[0].hours * res.shifts[0].rate, 0.001)
        assertEquals(8400.0, res.shifts[1].hours * res.shifts[1].rate, 0.001)
        assertEquals(14400.0, res.totalEarned, 0.001)
    }

    @Test
    fun `ночная смена получает правильный интервал`() {
        val start = LocalDate.of(2026, 11, 1)
        val res = buildPatternShifts(
            listOf(PatternStep(kind = ShiftKind.NIGHT)),
            start, start, start, false, false, emptySet()
        )
        assertEquals("20:00", res.shifts[0].startTime)
        assertEquals(1, res.shifts.size)
    }

    @Test
    fun `до конца следующего года ставится много смен`() {
        val start = LocalDate.of(2026, 11, 1)
        val until = LocalDate.of(2027, 12, 31)
        val res = buildPatternShifts(pattern420, start, start, until, false, false, emptySet())
        val days = java.time.temporal.ChronoUnit.DAYS.between(start, until) + 1
        assertEquals(days.toInt(), res.shifts.size)
        // доля рабочих дней цикла 4/6
        val expectedWork = res.shifts.count { !it.isDayOff }
        assertEquals(expectedWork, res.workCount)
        assertTrue(res.workCount > 280)
    }
}
