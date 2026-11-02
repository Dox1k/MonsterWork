package com.shiftcalendar

import com.shiftcalendar.data.Shift
import com.shiftcalendar.data.ShiftKind
import com.shiftcalendar.model.MultiShift
import com.shiftcalendar.model.ShiftDraft
import com.shiftcalendar.model.ShiftPlan
import com.shiftcalendar.model.SlotPaletteTestAccess
import com.shiftcalendar.model.activeShiftNow
import com.shiftcalendar.model.activeShiftsAt
import com.shiftcalendar.model.draftIntervalError
import com.shiftcalendar.model.isDuplicateShift
import com.shiftcalendar.model.monthTotals
import com.shiftcalendar.model.nightAwareKind
import com.shiftcalendar.model.planShift
import com.shiftcalendar.model.reconcileDayConflicts
import com.shiftcalendar.model.shiftLabel
import com.shiftcalendar.model.shiftSlot
import com.shiftcalendar.model.shiftSlots
import com.shiftcalendar.model.summarizeDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * Редактор дня с несколькими сменами: сборка смены, дубликаты, выходной,
 * слоты цвета, ярлыки, текущая смена и итоги месяца.
 *
 * Все проверки идут через те же функции, которые вызывает ViewModel при
 * сохранении, поэтому красный тест здесь — это красное поведение приложения.
 */
class ShiftDayEditingTest {

    private fun existing(
        id: Long,
        start: String = "08:00",
        end: String = "14:00",
        date: String = "2026-11-02",
        hours: Double = 6.0,
        rate: Double = 500.0,
        dayOff: Boolean = false,
        kind: String = "DAY"
    ) = Shift(
        id = id, date = date, kind = kind, hours = hours,
        startTime = if (dayOff) null else start,
        rate = if (dayOff) 0.0 else rate,
        isDayOff = dayOff,
        endTime = if (dayOff) null else end
    )

    private fun draft(
        start: String? = "17:00",
        end: String? = "21:00",
        id: Long = 0,
        kind: ShiftKind = ShiftKind.DAY,
        dayOff: Boolean = false,
        breakMinutes: Int = 0,
        rate: Double = 400.0
    ) = ShiftDraft(
        id = id,
        date = "2026-11-02",
        isDayOff = dayOff,
        kind = kind,
        startTime = start,
        endTime = end,
        breakMinutes = breakMinutes,
        rate = rate
    )

    // ---- сборка смены --------------------------------------------------

    @Test
    fun `вторая смена того же дня сохраняется рядом с первой`() {
        val day = listOf(existing(1))
        val plan = planShift(draft(start = "17:00", end = "21:00"), day)
        assertTrue(plan is ShiftPlan.Ready)
        val s = (plan as ShiftPlan.Ready).shift
        assertEquals(0L, s.id)                      // новая строка
        assertEquals("2026-11-02", s.date)          // та же дата
        assertEquals(4.0, s.hours, 0.001)
        assertEquals("17:00", s.startTime)
        // Существующая смена дня не изменилась — правки одной не задевают другую.
        assertEquals("08:00", day.first().startTime)
    }

    @Test
    fun `ночной интервал через полночь считается ночной сменой`() {
        val plan = planShift(draft(start = "22:00", end = "06:00", kind = ShiftKind.DAY), emptyList())
        val s = (plan as ShiftPlan.Ready).shift
        assertEquals("NIGHT", s.kind)
        assertEquals(8.0, s.hours, 0.001)
    }

    @Test
    fun `перерыв вычитается и не уводит часы в минус`() {
        val plan = planShift(draft(start = "09:00", end = "18:00", breakMinutes = 60), emptyList())
        assertEquals(8.0, (plan as ShiftPlan.Ready).shift.hours, 0.001)

        val tooLong = planShift(draft(start = "09:00", end = "10:00", breakMinutes = 120), emptyList())
        assertEquals(0.0, (tooLong as ShiftPlan.Ready).shift.hours, 0.001)
    }

    @Test
    fun `пустое время и совпадающие концы не сохраняются`() {
        assertNotNull(draftIntervalError(draft(start = "", end = "")))
        assertNotNull(draftIntervalError(draft(start = "09:00", end = "09:00")))
        assertNull(draftIntervalError(draft(start = "09:00", end = "18:00")))
        // Выходному время не нужно вовсе.
        assertNull(draftIntervalError(draft(start = "", end = "", dayOff = true)))

        val bad = planShift(draft(start = "", end = ""), emptyList())
        assertTrue(bad is ShiftPlan.Invalid)
        assertTrue((bad as ShiftPlan.Invalid).reason.contains("начало", ignoreCase = true))
    }

    @Test
    fun `одинаковая по времени смена дня не дублируется`() {
        val day = listOf(existing(1, start = "08:00", end = "14:00"))
        val dup = isDuplicateShift(draft(start = "08:00", end = "14:00"), day)
        assertNotNull(dup)
        val plan = planShift(draft(start = "08:00", end = "14:00"), day)
        assertTrue(plan is ShiftPlan.Invalid)
        assertTrue((plan as ShiftPlan.Invalid).reason.contains("уже есть"))
    }

    @Test
    fun `правка своей же смены не считается дубликатом`() {
        val day = listOf(existing(7, start = "08:00", end = "14:00"))
        val plan = planShift(draft(id = 7, start = "08:00", end = "14:00"), day)
        assertTrue(plan is ShiftPlan.Ready)
        assertEquals(7L, (plan as ShiftPlan.Ready).shift.id)
    }

    @Test
    fun `одно начало но с другим концом — разные смены`() {
        val day = listOf(existing(1, start = "08:00", end = "12:00"))
        assertNull(isDuplicateShift(draft(start = "08:00", end = "14:00"), day))
    }

    @Test
    fun `время с клавиатуры приводится к ЧЧ мм`() {
        val plan = planShift(draft(start = "8:5", end = " 17.30 "), emptyList())
        val s = (plan as ShiftPlan.Ready).shift
        assertEquals("08:05", s.startTime)
        assertEquals("17:30", s.endTime)
        assertEquals("DAY", s.kind)
    }

    // ---- выходной ------------------------------------------------------

    @Test
    fun `выходной сразу чистит рабочие смены дня`() {
        val day = listOf(existing(1), existing(2, start = "17:00", end = "21:00"))
        val off = planShift(draft(dayOff = true), day)
        val offShift = (off as ShiftPlan.Ready).shift.copy(id = 99)   // как после вставки в базу
        assertTrue(offShift.isDayOff)
        assertNull(offShift.startTime)
        assertEquals(0.0, offShift.hours, 0.001)

        // Сохранён выходной (99) — обе рабочие смены дня убираются.
        val doomed = reconcileDayConflicts(listOf(offShift) + day, fresh = 99L)
        assertEquals(setOf(1L, 2L), doomed.map { it.id }.toSet())
    }

    @Test
    fun `рабочая смена в день выходного снимает выходной`() {
        val off = existing(9, dayOff = true)
        val plan = planShift(draft(start = "08:00", end = "14:00"), listOf(off))
        val saved = (plan as ShiftPlan.Ready).shift.copy(id = 10)     // как после вставки в базу

        // Сохранена рабочая смена (10) — выходной этого дня убирается.
        val doomed = reconcileDayConflicts(listOf(off, saved), fresh = 10L)
        assertEquals(listOf(9L), doomed.map { it.id })
    }

    @Test
    fun `без подсказки о свежей смене выходной перевешивает`() {
        // Унаследованные данные без контекста: выходной + рабочая смена.
        // Выходных не меньше, чем рабочих, поэтому снимаются рабочие смены.
        val off = existing(9, dayOff = true)
        val work = existing(10)
        assertEquals(listOf(10L), reconcileDayConflicts(listOf(off, work)).map { it.id })
    }

    @Test
    fun `день без смеси выходных и рабочих конфликтов не даёт`() {
        assertTrue(reconcileDayConflicts(listOf(existing(1), existing(2))).isEmpty())
        assertTrue(reconcileDayConflicts(listOf(existing(3, dayOff = true))).isEmpty())
        assertTrue(reconcileDayConflicts(emptyList()).isEmpty())
    }

    // ---- слоты, ярлыки, итоги ------------------------------------------

    @Test
    fun `слоты цвета нумеруются по времени начала а не по порядку создания`() {
        val early = existing(5, start = "07:00", end = "10:00")
        val late = existing(2, start = "18:00", end = "22:00")
        val slots = shiftSlots(listOf(late, early))
        assertEquals(0, slots[5L])   // 07:00 — первая, хотя создана позже
        assertEquals(1, slots[2L])
        assertEquals(1, shiftSlot(listOf(late, early), 2L))
    }

    @Test
    fun `две смены дня подписываются номерами`() {
        val early = existing(1, start = "08:00", end = "12:00")
        val night = existing(2, start = "20:00", end = "08:00", kind = "NIGHT")
        val day = listOf(early, night)
        assertEquals("смена 1 · день", shiftLabel(day, early))
        assertEquals("смена 2 · ночь", shiftLabel(day, night))
        assertEquals("ночь", shiftLabel(listOf(night), night))
    }

    @Test
    fun `итог дня складывает смены и не считает выходной`() {
        val summary = summarizeDay(
            listOf(
                existing(1, hours = 4.0),
                existing(2, start = "17:00", end = "21:00", hours = 4.0),
                existing(3, dayOff = true, hours = 0.0)
            )
        )
        assertEquals(2, summary.workCount)
        assertEquals(8.0, summary.hours, 0.001)
        assertTrue(summary.dayOff)
        assertTrue(summary.isMulti)
        assertFalse(summary.isEmpty)
        assertEquals(listOf(1L, 2L, 3L), summary.shifts.map { it.id })  // без времени — в конце
    }

    @Test
    fun `смены раскладываются по датам с порядком времени`() {
        val map = MultiShift.byDate(
            listOf(
                existing(1, date = "2026-11-02", start = "18:00", end = "22:00"),
                existing(2, date = "2026-11-02", start = "08:00", end = "12:00"),
                existing(3, date = "2026-11-03", start = "09:00", end = "18:00")
            )
        )
        assertEquals(2, map.getValue("2026-11-02").size)
        assertEquals(listOf(2L, 1L), map.getValue("2026-11-02").map { it.id })
        assertEquals(1, map.getValue("2026-11-03").size)
    }

    @Test
    fun `итоги месяца считают смены а не только дни`() {
        val totals = monthTotals(
            listOf(
                existing(1, date = "2026-11-02", hours = 6.0),
                existing(2, date = "2026-11-02", hours = 4.0, start = "17:00", end = "21:00"),
                existing(3, date = "2026-11-03", hours = 8.0, start = "09:00", end = "17:00"),
                existing(4, date = "2026-11-04", hours = 0.0, dayOff = true)
            )
        )
        assertEquals(3, totals.days)
        assertEquals(3, totals.shifts)
        assertEquals(18.0, totals.hours, 0.001)
        assertEquals(1, totals.dayOffDays)
    }

    @Test
    fun `смены дня друг за другом дают ровно одну текущую`() {
        val a = existing(1, start = "08:00", end = "12:00", hours = 4.0)
        val b = existing(2, start = "17:00", end = "21:00", hours = 4.0, kind = "NIGHT")
        // 18:00 — только вторая: первая уже закончилась в 12:00.
        assertEquals(1, activeShiftsAt(listOf(a, b), LocalDateTime.of(2026, 11, 2, 18, 0)))
        assertEquals(2L, activeShiftNow(listOf(listOf(a, b)), LocalDateTime.of(2026, 11, 2, 18, 0))?.id)
        // 10:00 — только первая, 22:00 — уже никакая.
        assertEquals(1, activeShiftsAt(listOf(a, b), LocalDateTime.of(2026, 11, 2, 10, 0)))
        assertEquals(1L, activeShiftNow(listOf(listOf(a, b)), LocalDateTime.of(2026, 11, 2, 10, 0))?.id)
        assertEquals(0, activeShiftsAt(listOf(a, b), LocalDateTime.of(2026, 11, 2, 22, 0)))
        assertNull(activeShiftNow(listOf(listOf(a, b)), LocalDateTime.of(2026, 11, 2, 22, 0)))
    }

    @Test
    fun `наложившиеся смены различаются по времени`() {
        val long = existing(1, start = "08:00", end = "20:00", hours = 12.0)
        val extra = existing(2, start = "17:00", end = "21:00", hours = 4.0, kind = "NIGHT")
        // 18:00 — обе в работе, подпись «сейчас идут 2» отработает.
        assertEquals(2, activeShiftsAt(listOf(long, extra), LocalDateTime.of(2026, 11, 2, 18, 0)))
        // 20:58 — первая уже закончилась (20:00), вторая ещё идёт.
        assertEquals(1, activeShiftsAt(listOf(long, extra), LocalDateTime.of(2026, 11, 2, 20, 58)))
        assertEquals(2L, activeShiftNow(listOf(listOf(long, extra)), LocalDateTime.of(2026, 11, 2, 20, 58))?.id)
    }

    @Test
    fun `ночная смена с прошлого дня попадает в текущие`() {
        val night = existing(4, date = "2026-11-01", start = "22:00", end = "06:00", hours = 8.0, kind = "NIGHT")
        // Ночная считается только внутри своего дня: вызывающий передаёт
        // смены вчерашней даты — иначе смена выпала бы из месяца на стыке.
        assertEquals(1, activeShiftsAt(listOf(night), LocalDateTime.of(2026, 11, 2, 2, 0)))
        assertEquals(4L, activeShiftNow(listOf(emptyList(), listOf(night)), LocalDateTime.of(2026, 11, 2, 2, 0))?.id)
        assertEquals(0, activeShiftsAt(listOf(night), LocalDateTime.of(2026, 11, 2, 7, 0)))
    }

    @Test
    fun `битая дата не роняет проверку текущей смены`() {
        val broken = existing(1).copy(date = "не дата")
        assertEquals(0, activeShiftsAt(listOf(broken), LocalDateTime.of(2026, 11, 2, 12, 0)))
    }

    // ---- цвета слотов --------------------------------------------------

    @Test
    fun `смены дня различаются цветом и цвета повторяются по кругу`() {
        val light = SlotPaletteTestAccess.light()
        val dark = SlotPaletteTestAccess.dark()
        assertTrue(light.size >= 3)
        assertEquals(light.size, dark.size)
        assertEquals("первый слот — прежний красный смены", 0xFFD8433CL, light[0])
        // Первые четыре смены дня — разные цвета.
        assertEquals(4, light.take(4).distinct().size)
        assertEquals(4, dark.take(4).distinct().size)
        // Шестая смена снова в цвете первой — по кругу.
        assertEquals(light[0], SlotPaletteTestAccess.argb(light.size, dark = false))
        // Выходной не зависит от слота и отличается от рабочих цветов.
        val off = SlotPaletteTestAccess.argb(3, isDayOff = true)
        assertFalse(off in light)
        assertFalse(off in dark)
        // Светлая и тёмная палитры различаются, но по одному образцу порядка.
        assertTrue(light != dark)
        for (i in 0 until light.size) {
            assertTrue(SlotPaletteTestAccess.argb(i, dark = true) != SlotPaletteTestAccess.argb(i))
        }
    }

    @Test
    fun `тип ночной уточняется только при переходе через полночь`() {
        assertEquals(ShiftKind.NIGHT, nightAwareKind(ShiftKind.DAY, "23:00", "07:00"))
        assertEquals(ShiftKind.DAY, nightAwareKind(ShiftKind.DAY, "07:00", "23:00"))
        assertEquals(ShiftKind.CUSTOM, nightAwareKind(ShiftKind.CUSTOM, "07:00", "15:00"))
        assertEquals(ShiftKind.CUSTOM, nightAwareKind(ShiftKind.CUSTOM, "мусор", "15:00"))
    }
}
