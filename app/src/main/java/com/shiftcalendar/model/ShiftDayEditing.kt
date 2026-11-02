package com.shiftcalendar.model

import com.shiftcalendar.data.Shift
import com.shiftcalendar.data.ShiftKind
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Чистое ядро редактора дня с несколькими сменами.
 *
 * Редактор — это черновик (поля формы) плюс список уже сохранённых смен дня.
 * Всё, что можно проверить без Android, собрано здесь: сборка смены из черновика,
 * проверки интервала и дубликата, правило «выходной против рабочих смен»,
 * цветовые слоты дня и итоги месяца.
 *
 * ViewModel вызывает именно эти функции, поэтому их поведение — это и есть
 * поведение приложения; тесты бьют по реальному коду, а не по его копии.
 */

/**
 * Доступ к палитре слотов для JVM-тестов без Compose: `ShiftSlotColors`
 * лежит в UI-модуле, но цвета — обычные ARGB-числа.
 */
object SlotPaletteTestAccess {
    fun light(): List<Long> = com.shiftcalendar.ui.ShiftSlotColors.LIGHT
    fun dark(): List<Long> = com.shiftcalendar.ui.ShiftSlotColors.DARK
    fun argb(index: Int, isDayOff: Boolean = false, dark: Boolean = false): Long =
        com.shiftcalendar.ui.ShiftSlotColors.argb(index, isDayOff, dark)
}

/** Черновик одной смены из полей редактора. */
data class ShiftDraft(
    /** id правленой смены, 0 — новая смена. */
    val id: Long = 0,
    val date: String,
    val isDayOff: Boolean = false,
    val kind: ShiftKind = ShiftKind.DAY,
    val startTime: String? = null,
    val endTime: String? = null,
    val breakMinutes: Int = 0,
    val rate: Double = 0.0,
    val note: String = "",
    /** Профиль, который поставил смену (0 — ручная). */
    val profileId: Long = 0,
    /** Часы, посчитанные в форме раньше: фолбэк, если интервал не читается. */
    val fallbackHours: Double = DEFAULT_SHIFT_HOURS
)

/** Результат сборки смены: либо готовая сущность, либо причина отказа. */
sealed class ShiftPlan {
    data class Ready(val shift: Shift) : ShiftPlan()
    data class Invalid(val reason: String) : ShiftPlan()
}

/** Проверка черновика: пустое время, вырожденный интервал. null — всё хорошо. */
fun draftIntervalError(draft: ShiftDraft): String? {
    if (draft.isDayOff) return null
    val start = normalizeTime(draft.startTime)
    val end = normalizeTime(draft.endTime)
    if (start == null) return "Введите начало смены, например 09:00"
    if (end == null) return "Введите конец смены, например 18:00"
    if (isDegenerateInterval(start, end)) return "Начало и конец совпадают — проверьте время"
    return null
}

/** Та же смена по интервалу (выходные не считаются). */
fun isDuplicateShift(draft: ShiftDraft, day: List<Shift>): Shift? =
    day.firstOrNull { other ->
        other.id != draft.id && other.isDayOff == draft.isDayOff &&
            other.startTime == normalizeTime(draft.startTime) &&
            other.endTime == normalizeTime(draft.endTime)
    }

/**
 * Собрать смену из черновика.
 *
 * Часы считаются по интервалу через полночь, перерыв вычитается, отрицательных
 * часов не бывает. Тип уточняется по интервалу: конец не позже начала — ночь.
 */
fun planShift(draft: ShiftDraft, day: List<Shift>): ShiftPlan {
    draftIntervalError(draft)?.let { return ShiftPlan.Invalid(it) }
    val start = if (draft.isDayOff) null else normalizeTime(draft.startTime)
    val end = if (draft.isDayOff) null else normalizeTime(draft.endTime)
    isDuplicateShift(draft, day)?.let { dup ->
        val what = if (dup.isDayOff) "Выходной на этот день уже отмечен"
        else "Такая смена уже есть: ${dup.startTime}–${dup.endTime}"
        return ShiftPlan.Invalid(what)
    }
    val hours = when {
        draft.isDayOff -> 0.0
        start != null && end != null ->
            ((intervalHours(start, end) ?: draft.fallbackHours) - draft.breakMinutes / 60.0)
        else -> draft.fallbackHours
    }.coerceAtLeast(0.0)
    val kind = when {
        draft.isDayOff -> draft.kind
        start != null && end != null -> nightAwareKind(draft.kind, start, end)
        else -> draft.kind
    }
    return ShiftPlan.Ready(
        Shift(
            id = draft.id,
            date = draft.date,
            kind = kind.name,
            hours = hours,
            startTime = start,
            rate = if (draft.isDayOff) 0.0 else draft.rate,
            isDayOff = draft.isDayOff,
            note = draft.note.trim(),
            endTime = end,
            breakMinutes = if (draft.isDayOff) 0 else draft.breakMinutes.coerceAtLeast(0),
            profileId = draft.profileId
        )
    )
}

/** Тип по интервалу: конец не позже начала (переход через полночь) — ночь. */
fun nightAwareKind(kind: ShiftKind, start: String, end: String): ShiftKind {
    val s = parseTimeOrNull(start) ?: return kind
    val e = parseTimeOrNull(end) ?: return kind
    return if (!e.isAfter(s)) ShiftKind.NIGHT else kind
}

/**
 * Выходной — состояние всего дня, а не отдельной смены. Если в дне случайно
 * оказались и выходной, и рабочие смены, остаётся «сильнейшая» сторона:
 * выходной перевешивает при off >= work, иначе снимается.
 * [fresh] — только что сохранённая смена: правка выходного в рабочую убирает
 * выходной, правка же рабочей смены выходным убирает остальные рабочие смены.
 * Возвращает смены, которые надо удалить.
 */
fun reconcileDayConflicts(day: List<Shift>, fresh: Long = 0L): List<Shift> {
    val work = day.filter { !it.isDayOff }
    val off = day.filter { it.isDayOff }
    if (work.isEmpty() || off.isEmpty()) return emptyList()
    return when {
        fresh != 0L && off.any { it.id == fresh } -> work
        fresh != 0L && work.any { it.id == fresh } -> off
        off.size >= work.size -> work
        else -> off
    }
}

/** Итоги месяца по плоскому списку смен: смены, дни, часы, выходные. */
data class MonthTotals(
    val days: Int,
    val shifts: Int,
    val hours: Double,
    val dayOffDays: Int
)

fun monthTotals(shifts: List<Shift>): MonthTotals {
    val work = shifts.filter { !it.isDayOff }
    return MonthTotals(
        days = shifts.map { it.date }.distinct().size,
        shifts = work.size,
        hours = work.sumOf { it.hours },
        dayOffDays = shifts.filter { it.isDayOff }.map { it.date }.distinct().size
    )
}

/**
 * Цветовые слоты смен внутри дня: первая смена дня — слот 0, вторая — 1…
 * Существующие смены сохраняют свой слот при добавлении новых, потому что
 * порядок задаёт время начала, а не порядок создания.
 */
fun shiftSlots(day: List<Shift>): Map<Long, Int> {
    val ordered = MultiShift.ordered(day.filter { !it.isDayOff })
    return ordered.mapIndexed { index, shift -> shift.id to index }.toMap()
}

/**
 * Смены, которые идут одновременно прямо сейчас — для подписи «сейчас идут 2».
 *
 * Значение в строке дней: сколько смен в работе в этот момент. Вывод — дело UI.
 * День сверяем с календарной датой [now], а время — с интервалом смены:
 * показываемый месяц может не содержать соседней даты, поэтому опираться
 * только на окно нельзя — ночная смена со вчерашнего числа выпала бы.
 */
fun activeShiftsAt(shifts: List<Shift>, now: LocalDateTime): Int {
    val current = now.toLocalDate()
    return shifts.count { shift ->
        val window = shiftWindowSafe(shift) ?: return@count false
        val startDay = window.first.toLocalDate()
        when {
            // Смена с прошедшим концом — закончена в любом случае.
            now.isAfter(window.second) -> false
            // Началась сегодня после полуночи и конец ещё не наступил.
            startDay == current -> now.isAfter(window.first)
            // Началась вчера и перешла через полночь — дорабатывает.
            startDay == current.minusDays(1) -> true
            else -> false
        }
    }
}

/** Первая из идущих сейчас смен: из [days] берём тот день, что идёт сейчас. */
fun activeShiftNow(days: List<List<Shift>>, now: LocalDateTime): Shift? =
    days.firstNotNullOfOrNull { day ->
        day.filter { shift ->
            val window = shiftWindowSafe(shift) ?: return@filter false
            !now.isBefore(window.first) && now.isBefore(window.second)
        }.minWithOrNull(compareBy<Shift> { it.id })
    }

/** Окно смены, но без исключения на битую дату. */
fun shiftWindowSafe(shift: Shift): Pair<LocalDateTime, LocalDateTime>? {
    if (shift.isDayOff) return null
    val start = parseTimeOrNull(shift.startTime) ?: return null
    val end = parseTimeOrNull(shift.endTime) ?: return null
    val date = runCatching { LocalDate.parse(shift.date, ISO) }.getOrNull() ?: return null
    val s = LocalDateTime.of(date, start)
    var e = LocalDateTime.of(date, end)
    if (!e.isAfter(s)) e = e.plusDays(1)
    return s to e
}
