package com.shiftcalendar

import com.shiftcalendar.data.Shift
import com.shiftcalendar.model.MultiShift
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Несколько независимых смен в один день: порядок, пересечения, суммы.
 * Схема это допускает (PK — id), поэтому проверяем именно доменную логику.
 */
class MultiShiftTest {

    private fun shift(
        id: Long,
        start: String? = "08:00",
        end: String? = "20:00",
        hours: Double = 12.0,
        rate: Double = 100.0,
        dayOff: Boolean = false
    ) = Shift(
        id = id, date = "2026-11-02", kind = "DAY", hours = hours,
        startTime = start, rate = rate, isDayOff = dayOff, endTime = end
    )

    @Test
    fun `две смены одного дня сортируются по началу`() {
        val night = shift(id = 2, start = "20:00", end = "08:00")
        val day = shift(id = 1, start = "08:00", end = "14:00", hours = 6.0)
        val ordered = MultiShift.ordered(listOf(night, day))
        assertEquals(listOf(1L, 2L), ordered.map { it.id })
    }

    @Test
    fun `смены без времени и выходные уходят в конец`() {
        val timed = shift(id = 1)
        val untimed = shift(id = 2, start = null, end = null, hours = 0.0)
        val off = shift(id = 3, start = null, end = null, dayOff = true, hours = 0.0)
        assertEquals(listOf(1L, 2L, 3L), MultiShift.ordered(listOf(off, untimed, timed)).map { it.id })
    }

    @Test
    fun `пересечение дневной и ночной смены ловится`() {
        val day = shift(id = 1, start = "08:00", end = "20:00")
        val night = shift(id = 2, start = "20:00", end = "08:00")
        // Стык в 20:00 — не пересечение: интервалы полуоткрытые.
        assertFalse(MultiShift.overlaps(day, night))

        val overlap = shift(id = 3, start = "18:00", end = "23:00")
        assertTrue(MultiShift.overlaps(day, overlap))
        assertTrue(MultiShift.overlaps(night, overlap))
    }

    @Test
    fun `ночная смена получает интервал через полночь`() {
        val night = shift(id = 1, start = "22:00", end = "06:00")
        val span = MultiShift.shiftSpan(night)!!
        // 22:00 = 1320-я минута; конец 06:00 следующих суток = 1800, не включая.
        assertEquals(1320, span.first)
        assertEquals(1799, span.last)
    }

    @Test
    fun `некорректный интервал не даёт пересечений`() {
        val broken = shift(id = 1, start = "08:00", end = "08:00")
        assertNull(MultiShift.shiftSpan(broken))
        assertFalse(MultiShift.overlaps(broken, shift(id = 2, start = "07:00", end = "09:00")))
    }

    @Test
    fun `пары пересечений возвращаются для подсказки`() {
        val a = shift(id = 1, start = "08:00", end = "18:00")
        val b = shift(id = 2, start = "17:00", end = "23:00")
        val c = shift(id = 3, start = "20:00", end = "23:00")
        val pairs = MultiShift.overlappingPairs(listOf(a, b, c))
        assertTrue(pairs.contains(1L to 2L))
        assertTrue(pairs.contains(2L to 3L))
        assertFalse(pairs.contains(1L to 3L))
    }

    @Test
    fun `суммы дня складываются по всем сменам`() {
        val a = shift(id = 1, hours = 6.0, rate = 100.0)
        val b = shift(id = 2, hours = 8.0, rate = 200.0)
        val off = shift(id = 3, hours = 0.0, rate = 0.0, dayOff = true)
        val list = listOf(a, b, off)
        assertEquals(14.0, MultiShift.totalHours(list), 0.001)
        assertEquals(600.0 + 1600.0, MultiShift.totalBaseEarned(list), 0.001)
    }
}
