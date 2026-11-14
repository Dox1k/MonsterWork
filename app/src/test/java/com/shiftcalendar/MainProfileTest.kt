package com.shiftcalendar

import com.shiftcalendar.data.Shift
import com.shiftcalendar.model.PatternProfile
import com.shiftcalendar.model.dayBanksShift
import com.shiftcalendar.model.mainProfileShifts
import com.shiftcalendar.model.timeLabelForProfile
import com.shiftcalendar.model.withMainProfile
import com.shiftcalendar.model.mainProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Главный профиль (просьба клиента): банки монстров (1 шт за час смены) и
 * статистика одной смены идут только за рабочие смены ГЛАВНОГО профиля,
 * а смены каждого профиля в статистике считаются раздельно.
 */
class MainProfileTest {

    private fun shift(
        id: Long,
        profileId: Long = 0L,
        start: String? = "08:00",
        end: String? = "20:00",
        hours: Double = 12.0,
        dayOff: Boolean = false
    ) = Shift(
        id = id, date = "2026-11-02", kind = "DAY", hours = hours,
        startTime = start, rate = 100.0, isDayOff = dayOff, endTime = end,
        profileId = profileId
    )

    // ---------- Галочка «главный»: в календаре он ровно один ----------

    @Test
    fun `галочка главного снимается с остальных профилей`() {
        val list = listOf(
            PatternProfile(id = 1, name = "Первый", steps = emptyList()),
            PatternProfile(id = 2, name = "Второй", steps = emptyList())
        )
        val after = list.withMainProfile(2)
        assertFalse(after.first { it.id == 1L }.isMain)
        assertTrue(after.first { it.id == 2L }.isMain)
        assertEquals(2L, after.mainProfile()?.id)
        // Главный ровно один — сколько бы раз ни переключали.
        val again = after.withMainProfile(1)
        assertEquals(1, again.count { it.isMain })
        assertEquals(1L, again.mainProfile()?.id)
    }

    @Test
    fun `галочку главного можно снять совсем`() {
        val list = listOf(PatternProfile(id = 1, name = "Первый", steps = emptyList(), isMain = true))
        val after = list.withMainProfile(1, main = false)
        assertNull(after.mainProfile())
    }

    @Test
    fun `первый профиль становится главным, следующие — нет`() {
        // Пока профилей нет, новый профиль забирает галочку.
        val empty = emptyList<PatternProfile>()
        assertTrue(empty.none { it.isMain })
        val first = PatternProfile(id = 1, name = "Первый", steps = emptyList(), isMain = true)
        val afterFirst = empty + first
        assertTrue(afterFirst.mainProfile()?.id == 1L)
        // Второй профиль главным не становится: галочка уже занята.
        val second = PatternProfile(id = 2, name = "Второй", steps = emptyList(), isMain = false)
        val afterSecond = afterFirst + second
        assertEquals(1, afterSecond.count { it.isMain })
        assertEquals(1L, afterSecond.mainProfile()?.id)
    }

    // ---------- Банки идут только за смены главного профиля ----------

    @Test
    fun `банки берут смену только главного профиля`() {
        val day = listOf(
            shift(id = 1, profileId = 1, start = "08:00", end = "20:00"),
            shift(id = 2, profileId = 2, start = "20:00", end = "08:00")
        )
        assertEquals(1L, dayBanksShift(day, mainProfileId = 1L)?.id)
        assertEquals(2L, dayBanksShift(day, mainProfileId = 2L)?.id)
    }

    @Test
    fun `без главного профиля банки не идут ни за кого`() {
        val day = listOf(shift(id = 1, profileId = 1))
        assertNull(dayBanksShift(day, mainProfileId = 0L))
        assertTrue(mainProfileShifts(day, mainProfileId = 0L).isEmpty())
    }

    @Test
    fun `выходной главного профиля не даёт банок`() {
        val day = listOf(
            shift(id = 1, profileId = 1, start = null, end = null, hours = 0.0, dayOff = true),
            shift(id = 2, profileId = 2, start = "08:00", end = "20:00")
        )
        assertNull(dayBanksShift(day, mainProfileId = 1L))
        assertEquals(2L, dayBanksShift(day, mainProfileId = 2L)?.id)
    }

    @Test
    fun `статистика одной смены считает только смены главного профиля`() {
        val month = listOf(
            shift(id = 1, profileId = 1),
            shift(id = 2, profileId = 1),
            shift(id = 3, profileId = 2),
            shift(id = 4, profileId = 0)   // ручная, без профиля
        )
        val own = mainProfileShifts(month, mainProfileId = 1L)
        assertEquals(2, own.size)
        assertEquals(setOf(1L, 2L), own.map { it.id }.toSet())
    }

    // ---------- Время в половине квадрата: у каждого профиля своё ----------

    @Test
    fun `время берётся из половины своего профиля`() {
        val day = listOf(
            shift(id = 1, profileId = 1, start = "08:00", end = "20:00"),
            shift(id = 2, profileId = 2, start = "20:00", end = "08:00")
        )
        assertEquals("08:00–20:00", timeLabelForProfile(day, 1L))
        assertEquals("20:00–08:00", timeLabelForProfile(day, 2L))
    }

    @Test
    fun `у выходного профиля времени нет`() {
        val day = listOf(shift(id = 1, profileId = 1, start = null, end = null, dayOff = true))
        assertNull(timeLabelForProfile(day, 1L))
        assertNull(timeLabelForProfile(day, 99L))
    }
}
