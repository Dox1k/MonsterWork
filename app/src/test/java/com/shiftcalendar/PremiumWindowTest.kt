package com.shiftcalendar

import com.shiftcalendar.data.Shift
import com.shiftcalendar.ui.BreakAnchor
import com.shiftcalendar.ui.inWindow
import com.shiftcalendar.ui.intervalMinutes
import com.shiftcalendar.ui.premiumSplit
import com.shiftcalendar.ui.shiftEarnings
import com.shiftcalendar.ui.shiftEarningsBreakdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * Окно надбавки и перерыв: ночные часы должны оплачиваться точно, а перерыв —
 * уменьшать именно те минуты, в которые он проходит.
 *
 * База для примеров: ставка 100 ₽/ч, надбавка 20% (ночной час = 120 ₽),
 * окно 22:00–06:00.
 */
class PremiumWindowTest {

    private val nightStart = LocalTime.of(22, 0)
    private val nightEnd = LocalTime.of(6, 0)

    private fun shift(
        start: String = "20:00",
        end: String = "08:00",
        rate: Double = 100.0,
        breakMinutes: Int = 0
    ) = Shift(
        date = "2026-11-01", kind = "NIGHT", hours = 12.0, startTime = start,
        rate = rate, endTime = end, breakMinutes = breakMinutes
    )

    @Test
    fun `смена 20-08 задевает окно 4 дневных и 8 ночных часов`() {
        // 20:00–22:00 и 06:00–08:00 — вне окна (2 + 2 = 4 ч), 22:00–06:00 — окно (8 ч).
        val split = premiumSplit(LocalTime.of(20, 0), LocalTime.of(8, 0), nightStart, nightEnd)
        assertEquals(240L, split.baseMinutes)
        assertEquals(480L, split.premiumMinutes)
        assertEquals(720L, split.totalMinutes)
        // 4 ч · 100 ₽ + 8 ч · 120 ₽ = 400 + 960 = 1360 ₽
        assertEquals(1360.0, shiftEarnings(shift(), nightStart, nightEnd, 20.0), 0.001)
    }

    @Test
    fun `перерыв в дневной части не отнимает надбавку`() {
        // Перерыв с начала смены (20:00–21:00) целиком в базовой части.
        val split = premiumSplit(LocalTime.of(20, 0), LocalTime.of(8, 0), nightStart, nightEnd, 60)
        assertEquals(180L, split.baseMinutes)
        assertEquals(480L, split.premiumMinutes)
        // 3 ч · 100 ₽ + 8 ч · 120 ₽ = 300 + 960 = 1260 ₽
        assertEquals(1260.0, shiftEarnings(shift(breakMinutes = 60), nightStart, nightEnd, 20.0), 0.001)
    }

    @Test
    fun `перерыв в ночном окне уменьшает именно надбавочные минуты`() {
        // Смена целиком внутри окна: любой перерыв срезает только надбавку.
        val fullNight = premiumSplit(LocalTime.of(22, 0), LocalTime.of(6, 0), nightStart, nightEnd, 60)
        assertEquals(0L, fullNight.baseMinutes)
        assertEquals(420L, fullNight.premiumMinutes)
        // 7 ч · 120 ₽ = 840 ₽ (без перерыва было бы 8 · 120 = 960 ₽)
        assertEquals(
            840.0,
            shiftEarnings(shift(start = "22:00", end = "06:00", breakMinutes = 60), nightStart, nightEnd, 20.0),
            0.001
        )
    }

    @Test
    fun `место перерыва учитывается`() {
        // Смена 00:00–12:00: 00:00–06:00 в окне, 06:00–12:00 вне окна.
        // Перерыв с начала смены (00:00–02:00) — внутри окна: срезает надбавку.
        val fromStart = premiumSplit(LocalTime.of(0, 0), LocalTime.of(12, 0), nightStart, nightEnd, 120, BreakAnchor.START)
        assertEquals(360L, fromStart.baseMinutes)
        assertEquals(240L, fromStart.premiumMinutes)
        // Перерыв с конца смены (10:00–12:00) — целиком из базовой части:
        // надбавка за 00:00–06:00 не страдает.
        val fromEnd = premiumSplit(LocalTime.of(0, 0), LocalTime.of(12, 0), nightStart, nightEnd, 120, BreakAnchor.END)
        assertEquals(240L, fromEnd.baseMinutes)
        assertEquals(360L, fromEnd.premiumMinutes)
    }

    @Test
    fun `перерыв не может съесть больше смены`() {
        val split = premiumSplit(LocalTime.of(20, 0), LocalTime.of(8, 0), nightStart, nightEnd, 5000)
        assertEquals(0L, split.baseMinutes)
        assertEquals(0L, split.premiumMinutes)
        assertEquals(0.0, shiftEarnings(shift(breakMinutes = 5000), nightStart, nightEnd, 20.0), 0.001)
    }

    @Test
    fun `без надбавки перерыв просто уменьшает оплаченные часы`() {
        // Процент 0: 11 отработанных часов по 100 ₽.
        assertEquals(1100.0, shiftEarnings(shift(breakMinutes = 60), nightStart, nightEnd, 0.0), 0.001)
    }

    @Test
    fun `окно не может быть пустым`() {
        assertFalse(inWindow(LocalTime.of(23, 0), LocalTime.of(22, 0), LocalTime.of(22, 0)))
        assertTrue(inWindow(LocalTime.of(23, 0), nightStart, nightEnd))
        assertTrue(inWindow(LocalTime.of(5, 59), nightStart, nightEnd))
        assertFalse(inWindow(LocalTime.of(6, 0), nightStart, nightEnd))
        assertFalse(inWindow(LocalTime.of(21, 59), nightStart, nightEnd))
        // Дневное окно без перехода через полночь.
        assertTrue(inWindow(LocalTime.of(12, 0), LocalTime.of(9, 0), LocalTime.of(18, 0)))
        assertFalse(inWindow(LocalTime.of(19, 0), LocalTime.of(9, 0), LocalTime.of(18, 0)))
    }

    @Test
    fun `равные концы смены не считаются сутками`() {
        assertEquals(0L, intervalMinutes(LocalTime.of(8, 0), LocalTime.of(8, 0)))
        assertEquals(12 * 60L, intervalMinutes(LocalTime.of(20, 0), LocalTime.of(8, 0)))
        // Равные концы — нулевой заработок, а не 24 часа по ставке.
        assertEquals(
            0.0,
            shiftEarnings(shift(start = "08:00", end = "08:00"), nightStart, nightEnd, 20.0),
            0.001
        )
    }

    @Test
    fun `разложение на базу и надбавку сходится с итогом`() {
        val (base, premium, total) = shiftEarningsBreakdown(
            100.0, LocalTime.of(20, 0), LocalTime.of(8, 0), nightStart, nightEnd, 20.0, 60
        )
        assertEquals(300.0, base, 0.001)
        assertEquals(960.0, premium, 0.001)
        assertEquals(1260.0, total, 0.001)
        assertEquals(shiftEarnings(shift(breakMinutes = 60), nightStart, nightEnd, 20.0), total, 0.001)
    }

    @Test
    fun `дневная смена без ночных часов надбавки не получает`() {
        val day = shift(start = "08:00", end = "20:00")
        assertEquals(1200.0, shiftEarnings(day, nightStart, nightEnd, 20.0), 0.001)
    }
}
