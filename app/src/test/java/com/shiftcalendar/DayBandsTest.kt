package com.shiftcalendar

import com.shiftcalendar.data.Shift
import com.shiftcalendar.model.bandShowsTime
import com.shiftcalendar.model.dayBandFractions
import com.shiftcalendar.model.dayBands
import com.shiftcalendar.model.mainBandIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Полосы квадрата дня (просьба клиента, дни 28 и 29 — эталон дизайна):
 *
 *  - 2 смены — по половине ячейки (0.5 / 0.5);
 *  - 3 смены — главная занимает половину (0.5), остальные по четверти (0.25);
 *  - 4 смены — по четверти каждая (поровну).
 *
 * Время показывается только полосе, занимающей половину ячейки и больше.
 */
class DayBandsTest {

    private fun shift(
        id: Long,
        profileId: Long = 0L,
        start: String? = "08:00",
        end: String? = "20:00",
        dayOff: Boolean = false
    ) = Shift(
        id = id,
        date = "2026-11-28",
        kind = "DAY",
        profileId = profileId,
        startTime = start,
        endTime = end,
        hours = 8.0,
        rate = 0.0,
        isDayOff = dayOff
    )

    // ---------- Доли полос ----------

    @Test
    fun `одна смена занимает всю ячейку`() {
        val day = listOf(shift(1, profileId = 1))
        assertEquals(listOf(1f), dayBandFractions(day, 1L))
        assertTrue(bandShowsTime(1f))
    }

    @Test
    fun `две смены делят ячейку пополам`() {
        val day = listOf(shift(1, profileId = 1), shift(2, profileId = 2, start = "20:00", end = "08:00"))
        assertEquals(listOf(0.5f, 0.5f), dayBandFractions(day, 1L))
    }

    @Test
    fun `три смены - главная половина, остальные по четверти`() {
        val day = listOf(
            shift(1, profileId = 1, start = "06:00", end = "10:00"),
            shift(2, profileId = 2, start = "10:00", end = "18:00"),
            shift(3, profileId = 3, start = "18:00", end = "22:00")
        )
        // Главный — профиль 2 (его смена вторая по времени).
        val fractions = dayBandFractions(day, 2L)
        assertEquals(listOf(0.25f, 0.5f, 0.25f), fractions)
        assertEquals(1.0f, fractions.sum(), 0.0001f)
    }

    @Test
    fun `четыре смены делят ячейку поровну`() {
        val day = listOf(
            shift(1, profileId = 1, start = "00:00", end = "06:00"),
            shift(2, profileId = 2, start = "06:00", end = "12:00"),
            shift(3, profileId = 3, start = "12:00", end = "18:00"),
            shift(4, profileId = 4, start = "18:00", end = "23:59")
        )
        assertEquals(listOf(0.25f, 0.25f, 0.25f, 0.25f), dayBandFractions(day, 2L))
    }

    @Test
    fun `без главного профиля крупной становится первая смена`() {
        val day = listOf(
            shift(1, profileId = 1, start = "06:00", end = "10:00"),
            shift(2, profileId = 2, start = "10:00", end = "18:00"),
            shift(3, profileId = 3, start = "18:00", end = "22:00")
        )
        assertEquals(0, mainBandIndex(day, 0L))
        assertEquals(listOf(0.5f, 0.25f, 0.25f), dayBandFractions(day, 0L))
    }

    // ---------- Время показывается только крупной полосе ----------

    @Test
    fun `время есть у половинной полосы и нет у четвертных`() {
        assertTrue(bandShowsTime(0.5f))
        assertTrue(bandShowsTime(1f))
        assertFalse(bandShowsTime(0.25f))
    }

    @Test
    fun `полосы идут в порядке времени и помечают главную`() {
        val day = listOf(
            shift(3, profileId = 3, start = "18:00", end = "22:00"),
            shift(1, profileId = 1, start = "06:00", end = "10:00"),
            shift(2, profileId = 2, start = "10:00", end = "18:00")
        )
        val bands = dayBands(day, 2L)
        assertEquals(listOf(1L, 2L, 3L), bands.map { it.shift.id })
        assertEquals(listOf(0.25f, 0.5f, 0.25f), bands.map { it.fraction })
        assertEquals(listOf(false, true, false), bands.map { it.isMain })
    }

    @Test
    fun `выходной день полос не даёт`() {
        assertTrue(dayBands(emptyList(), 1L).isEmpty())
    }

    @Test
    fun `сумма долей всегда единица`() {
        for (n in 1..6) {
            val day = (1..n).map { i ->
                shift(i.toLong(), profileId = i.toLong(), start = "%02d:00".format(i * 2 % 24), end = "23:00")
            }
            val sum = dayBandFractions(day, 2L).sum()
            assertEquals("при $n сменах", 1.0f, sum, 0.0001f)
        }
    }
}
