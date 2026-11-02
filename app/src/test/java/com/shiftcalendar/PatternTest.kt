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
            profileId = 0L,
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
            profileId = 0L,
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
            profileId = 0L,
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
            profileId = 0L,
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
            profileId = 0L,
            existingDates = existing
        )
        assertEquals(2, res.skippedExisting)
        assertEquals(4, res.shifts.size)
        assertTrue(res.shifts.none { it.date == "2026-11-01" })
    }

    @Test
    fun `пустой цикл ничего не проставляет`() {
        val start = LocalDate.of(2026, 11, 1)
        val res = buildPatternShifts(emptyList(), start, start, LocalDate.of(2026, 12, 31), false, 0L, emptySet())
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
        val res = buildPatternShifts(mixed, start, start, LocalDate.of(2026, 11, 2), false, 0L, emptySet())
        assertEquals(6000.0, res.shifts[0].hours * res.shifts[0].rate, 0.001)
        assertEquals(8400.0, res.shifts[1].hours * res.shifts[1].rate, 0.001)
        assertEquals(14400.0, res.totalEarned, 0.001)
    }

    @Test
    fun `ночная смена получает правильный интервал`() {
        val start = LocalDate.of(2026, 11, 1)
        val res = buildPatternShifts(
            listOf(PatternStep(kind = ShiftKind.NIGHT)),
            start, start, start, false, 0L, emptySet()
        )
        assertEquals("20:00", res.shifts[0].startTime)
        assertEquals("08:00", res.shifts[0].endTime)
        assertEquals(12.0, res.shifts[0].hours, 0.001)
        assertEquals(1, res.shifts.size)
    }

    @Test
    fun `у каждого шага свой интервал — шаг хранит начало и конец`() {
        val step = PatternStep(kind = ShiftKind.DAY, startTime = "09:30", endTime = "21:00", rate = 400.0)
        assertEquals("09:30", step.start)
        assertEquals("21:00", step.end)
        assertEquals(11.5, step.effectiveHours, 0.001)

        val res = buildPatternShifts(
            listOf(step), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1),
            LocalDate.of(2026, 11, 1), false, 0L, emptySet()
        )
        assertEquals("09:30", res.shifts[0].startTime)
        assertEquals("21:00", res.shifts[0].endTime)
        assertEquals(11.5, res.shifts[0].hours, 0.001)
        assertEquals(11.5, res.totalHours, 0.001)
        assertEquals(11.5 * 400.0, res.totalEarned, 0.001)
    }

    @Test
    fun `каждый шаг цикла сохраняет собственное время при применении`() {
        val pattern = listOf(
            PatternStep(kind = ShiftKind.DAY, startTime = "08:00", endTime = "16:00", rate = 100.0),
            PatternStep(kind = ShiftKind.DAY, startTime = "10:00", endTime = "18:30", rate = 100.0),
            PatternStep(isDayOff = true)
        )
        val start = LocalDate.of(2026, 11, 1)
        val res = buildPatternShifts(
            pattern, start, start, LocalDate.of(2026, 11, 6), false, 0L, emptySet()
        )
        assertEquals("08:00", res.shifts[0].startTime)
        assertEquals("16:00", res.shifts[0].endTime)
        assertEquals("10:00", res.shifts[1].startTime)
        assertEquals("18:30", res.shifts[1].endTime)
        // третий день — выходной без времени
        assertTrue(res.shifts[2].isDayOff)
        assertEquals(null, res.shifts[2].startTime)
        assertEquals(null, res.shifts[2].endTime)
        assertEquals(0.0, res.shifts[2].hours, 0.001)
        // цикл повторяется: 4-й день снова 08:00–16:00
        assertEquals("08:00", res.shifts[3].startTime)
        assertEquals("10:00", res.shifts[4].startTime)
        assertEquals(8.0 + 8.5 + 8.0 + 8.5, res.totalHours, 0.001)
    }

    @Test
    fun `смена через полночь в шаге считается как ночная и 12 часов`() {
        val step = PatternStep(kind = ShiftKind.DAY, startTime = "20:00", endTime = "08:00")
        assertEquals(12.0, step.effectiveHours, 0.001)
        val res = buildPatternShifts(
            listOf(step), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1),
            LocalDate.of(2026, 11, 1), false, 0L, emptySet()
        )
        assertEquals("NIGHT", res.shifts[0].kind)
        assertEquals(12.0, res.shifts[0].hours, 0.001)
        assertEquals(12.0, res.totalHours, 0.001)
    }

    @Test
    fun `до конца следующего года ставится много смен`() {
        val start = LocalDate.of(2026, 11, 1)
        val until = LocalDate.of(2027, 12, 31)
        val res = buildPatternShifts(pattern420, start, start, until, false, 0L, emptySet())
        val days = java.time.temporal.ChronoUnit.DAYS.between(start, until) + 1
        assertEquals(days.toInt(), res.shifts.size)
        // доля рабочих дней цикла 4/6
        val expectedWork = res.shifts.count { !it.isDayOff }
        assertEquals(expectedWork, res.workCount)
        assertTrue(res.workCount > 280)
    }
}
