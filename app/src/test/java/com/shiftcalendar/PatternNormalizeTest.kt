package com.shiftcalendar

import com.shiftcalendar.data.ShiftKind
import com.shiftcalendar.model.PatternStep
import com.shiftcalendar.model.buildPatternShifts
import com.shiftcalendar.model.normalizePattern
import com.shiftcalendar.model.patternProblems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Нормализация цикла: дубли, повторённые периоды и невалидные шаги.
 * Главное требование — нормализация не перекашивает последовательность смен:
 * любой цикл и его нормализованная форма дают одинаковый календарь.
 */
class PatternNormalizeTest {

    private val work = PatternStep(kind = ShiftKind.DAY, startTime = "08:00", endTime = "20:00", rate = 100.0)
    private val off = PatternStep(isDayOff = true)

    /** Смены цикла, приведённые к читаемой строке «раб/раб/вых/...». */
    private fun shape(res: com.shiftcalendar.model.PatternApplyResult) =
        res.shifts.joinToString("/") { if (it.isDayOff) "о" else "р" }

    @Test
    fun `цикл 4 через 2 не схлопывается до 2 через 1`() {
        // Главная ловушка: «раб/раб/раб/раб/вых/вых» — это 4/2, а не 1/1.
        val p420 = listOf(work, work, work, work, off, off)
        assertEquals(6, normalizePattern(p420).size)
        // 2/2 тоже остаётся собой.
        assertEquals(4, normalizePattern(listOf(work, work, off, off)).size)
    }

    @Test
    fun `нормализация идемпотентна`() {
        val once = normalizePattern(listOf(work, work, off, off, work, work, off, off))
        assertEquals(once, normalizePattern(once))
    }

    @Test
    fun `цикл, повторённый дважды, сжимается до периода`() {
        val doubled = listOf(work, work, off, off, work, work, off, off)
        val normalized = normalizePattern(doubled)
        assertEquals(4, normalized.size)
        assertEquals(listOf(work, work, off, off), normalized)
    }

    @Test
    fun `нормализация не меняет календарь`() {
        val start = LocalDate.of(2026, 11, 1)
        val end = LocalDate.of(2026, 12, 15)
        val variants = listOf(
            listOf(work, off),
            listOf(work, work, off, off),
            listOf(work, off, off, work),
            listOf(work, work, work, work, off, off, work, work, work, work, off, off),
            listOf(work, off, work, off, work, off)
        )
        variants.forEach { cycle ->
            val raw = buildPatternShifts(cycle, start, start, end, false, 0L, emptySet())
            val norm = normalizePattern(cycle)
            val fromNorm = buildPatternShifts(norm, start, start, end, false, 0L, emptySet())
            assertEquals("форма цикла $cycle", raw.shifts.map { it.date to it.isDayOff },
                fromNorm.shifts.map { it.date to it.isDayOff })
            assertEquals(raw.workCount, fromNorm.workCount)
            assertEquals(raw.dayOffCount, fromNorm.dayOffCount)
        }
    }

    @Test
    fun `один день — одна смена, дублей на дату нет`() {
        val start = LocalDate.of(2026, 11, 1)
        // Цикл с дублями применяется на год: дата обязана встретиться ровно один раз.
        val res = buildPatternShifts(
            listOf(work, work, off, off, work, work, off, off),
            start, start, LocalDate.of(2027, 11, 1), false, 7L, emptySet()
        )
        val dates = res.shifts.map { it.date }
        assertEquals(dates.size, dates.toSet().size)
        assertEquals(366, res.shifts.size)
    }

    @Test
    fun `невалидный шаг не превращается в сутки`() {
        val start = LocalDate.of(2026, 11, 1)
        val broken = PatternStep(kind = ShiftKind.DAY, startTime = "08:00", endTime = "08:00", rate = 100.0)
        val res = buildPatternShifts(
            listOf(worked("08:00", "20:00"), broken), start, start, LocalDate.of(2026, 11, 4),
            false, 0L, emptySet()
        )
        // Чётные дни — рабочие, нечётные (сломанный шаг) пропущены, а не записаны на 24 ч.
        assertEquals(listOf("2026-11-01", "2026-11-03"), res.shifts.map { it.date })
        assertEquals(listOf("2026-11-02", "2026-11-04"), res.skippedDates)
        assertEquals(12.0, res.shifts[0].hours, 0.001)
        assertTrue(res.hasSkipped)
    }

    @Test
    fun `проверка цикла находит проблемы`() {
        assertTrue(patternProblems(listOf(work, off)).isEmpty())
        assertEquals(1, patternProblems(emptyList()).size)
        assertTrue(patternProblems(listOf(PatternStep(kind = ShiftKind.DAY, startTime = "09:00", endTime = "09:00", isDayOff = false))).isNotEmpty())
        assertTrue(patternProblems(listOf(PatternStep(kind = ShiftKind.DAY, startTime = "abc", endTime = "20:00"))).isNotEmpty())
        assertTrue(patternProblems(listOf(off, off)).isNotEmpty())
        // перерыв длиннее смены
        assertTrue(patternProblems(listOf(worked("08:00", "09:00").copy(breakMinutes = 120))).isNotEmpty())
    }

    @Test
    fun `перерыв шага приводится к длине смены и вычитается из часов`() {
        val step = worked("08:00", "20:00").copy(breakMinutes = 60)
        assertEquals(11.0, step.paidHours, 0.001)
        // слишком длинный перерыв обрезается длиной смены
        val clamped = worked("08:00", "20:00").copy(breakMinutes = 9999).normalized()
        assertEquals(720, clamped.breakMinutes)
        assertEquals(0.0, clamped.paidHours, 0.001)
    }

    @Test
    fun `ночной шаг получает тип NIGHT по интервалу, а не по подписи`() {
        val start = LocalDate.of(2026, 11, 1)
        val res = buildPatternShifts(
            listOf(worked("20:00", "08:00")), start, start, start, false, 0L, emptySet()
        )
        assertEquals("NIGHT", res.shifts[0].kind)
    }

    @Test
    fun `равные концы не считаются ночной сменой`() {
        val step = worked("20:00", "20:00")
        assertTrue(step.hasDegenerateInterval)
        assertEquals(ShiftKind.DAY, step.resolvedKind)
    }

    private fun worked(start: String, end: String) =
        PatternStep(kind = ShiftKind.DAY, startTime = start, endTime = end, rate = 100.0)
}
