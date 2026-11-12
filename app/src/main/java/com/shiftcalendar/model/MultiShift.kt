package com.shiftcalendar.model

import com.shiftcalendar.data.Shift

/**
 * Несколько независимых смен в один день.
 *
 * Схема этому уже соответствует: первичный ключ — `id`, а не дата, поэтому
 * на одну дату можно записать сколько угодно строк (например, подработка
 * днём и ночное дежурство). Ограничение «одна смена в день» жило в DAO
 * (`byDate ... LIMIT 1`) и в редакторе, а не в базе.
 *
 * Здесь собраны чистые функции для такого режима — их проверяют тесты,
 * а UI/ViewModel используют отдельно.
 */
object MultiShift {

    /**
     * Смены дня в порядке отображения: по времени начала, смены без времени
     * (и выходные) — в конце, при равном начале — по id (стабильно).
     */
    fun ordered(shifts: List<Shift>): List<Shift> =
        shifts.sortedWith(
            compareBy<Shift> { shiftStartMinutes(it) ?: Int.MAX_VALUE }.thenBy { it.id }
        )

    /** Минуты от полуночи для начала смены или null, если времени нет. */
    fun shiftStartMinutes(shift: Shift): Int? {
        if (shift.isDayOff) return null
        val t = parseTimeOrNull(shift.startTime) ?: return null
        return t.hour * 60 + t.minute
    }

    /** [start, end) смены в минутах от полуночи дня; ночь переходит через 1440. */
    fun shiftSpan(shift: Shift): IntRange? {
        if (shift.isDayOff) return null
        val s = parseTimeOrNull(shift.startTime) ?: return null
        val e = parseTimeOrNull(shift.endTime) ?: return null
        val start = s.hour * 60 + s.minute
        val endRaw = e.hour * 60 + e.minute
        if (start == endRaw) return null           // некорректный интервал
        val end = if (endRaw < start) endRaw + 1440 else endRaw
        return start until end
    }

    /** Пересекаются ли две смены по времени (выходные и смены без времени — нет). */
    fun overlaps(a: Shift, b: Shift): Boolean {
        val sa = shiftSpan(a) ?: return false
        val sb = shiftSpan(b) ?: return false
        return sa.first < sb.last && sb.first < sa.last
    }

    /**
     * Пары пересекающихся смен дня — для подсказки «смены накладываются».
     * Возвращает пары id в порядке отображения.
     */
    fun overlappingPairs(shifts: List<Shift>): List<Pair<Long, Long>> {
        val ordered = ordered(shifts)
        val pairs = ArrayList<Pair<Long, Long>>()
        for (i in ordered.indices) {
            for (j in i + 1 until ordered.size) {
                if (overlaps(ordered[i], ordered[j])) pairs.add(ordered[i].id to ordered[j].id)
            }
        }
        return pairs
    }

    /** Суммарные часы дня: все рабочие смены, каждая со своим перерывом. */
    fun totalHours(shifts: List<Shift>): Double =
        shifts.filter { !it.isDayOff }.sumOf { it.hours }

    /** Суммарный заработок без надбавки: часы × ставка по каждой смене. */
    fun totalBaseEarned(shifts: List<Shift>): Double =
        shifts.filter { !it.isDayOff }.sumOf { it.hours * it.rate }

    /** Сколько смен, кроме выходных, в списке дня. */
    fun workCount(shifts: List<Shift>): Int = shifts.count { !it.isDayOff }

    /** Есть ли среди смен дня выходной. */
    fun hasDayOff(shifts: List<Shift>): Boolean = shifts.any { it.isDayOff }

    /**
     * Смены одного дня по датам месяца: порядок внутри дня — как в [ordered].
     * Плоский список месяцев приходит из DAO без гарантий порядка по дням.
     */
    fun byDate(shifts: List<Shift>): Map<String, List<Shift>> =
        shifts.groupBy { it.date }.mapValues { ordered(it.value) }
}

/**
 * Порядковый номер смены среди смен своего дня (0 — первая по времени).
 * Сравнение идёт по (время начала, id) — как в [MultiShift.ordered], но без
 * пересортировки всего списка на каждую ячейку сетки.
 */
fun shiftSlot(shifts: List<Shift>, shiftId: Long): Int {
    val me = shifts.firstOrNull { it.id == shiftId } ?: return 0
    val myKey = MultiShift.shiftStartMinutes(me) ?: Int.MAX_VALUE
    return shifts.count { other ->
        val key = MultiShift.shiftStartMinutes(other) ?: Int.MAX_VALUE
        key < myKey || (key == myKey && other.id < me.id)
    }
}

/**
 * Имя смены для интерфейса. Заметку пользователя не подменяем — она
 * показывается отдельно; здесь системная подпись по порядку и типу:
 * один рабочий день — «день»/«ночь», несколько — «смена 1 · день».
 */
fun shiftLabel(shifts: List<Shift>, shift: Shift): String {
    val kind = when (shift.kind) {
        "NIGHT" -> "ночь"
        "DAY" -> "день"
        else -> "своё время"
    }
    val total = shifts.count { !it.isDayOff }
    return if (total <= 1) kind else "смена ${shiftSlot(shifts, shift.id) + 1} · $kind"
}

/** Итоги дня с несколькими сменами — для заголовка редактора и сетки. */
data class DaySummary(
    val shifts: List<Shift>,
    val workCount: Int,
    val hours: Double,
    val dayOff: Boolean
) {
    val isEmpty: Boolean get() = shifts.isEmpty()
    val isMulti: Boolean get() = workCount > 1
}

fun summarizeDay(shifts: List<Shift>): DaySummary {
    val ordered = MultiShift.ordered(shifts)
    return DaySummary(
        shifts = ordered,
        workCount = MultiShift.workCount(ordered),
        hours = MultiShift.totalHours(ordered),
        dayOff = MultiShift.hasDayOff(ordered)
    )
}

// ---------- «Первый профиль» дня (решения 15-Б и 16-Б) ----------

/**
 * Часть дня, стоящая за первым профилем (решение клиента 15-Б).
 *
 * Порядок в [MultiShift.ordered] — сначала по времени начала, потом по id, а
 * смены паттернов одного дня записываются циклом слева направо, поэтому первая
 * смена дня и есть «профиль 1». Выходные времени не имеют и уходят в конец
 * списка — в своём дне они всё равно остаются единственной сменой.
 */
fun firstProfileShift(shifts: List<Shift>): Shift? = MultiShift.ordered(shifts).firstOrNull()

/**
 * Время смены для клетки дня: только по первому профилю, и только если он
 * рабочий. Если профиль 1 — выходной, время не показываем даже когда второй
 * профиль в этот день работает (решение 15-Б).
 */
fun dayTimeLabel(shifts: List<Shift>): String? {
    val first = firstProfileShift(shifts) ?: return null
    val span = MultiShift.shiftSpan(first) ?: return null
    if (first.isDayOff || span.first == Int.MAX_VALUE) return null
    val start = first.startTime ?: return null
    val end = first.endTime ?: return null
    return "$start–$end"
}

/**
 * Кто рисует банки монстра в этот день (решение 16-Б): первый профиль дня.
 * Если у первого профиля выходной, банки не идут — даже когда второй профиль
 * в этот день работает.
 */
fun dayBanksShift(shifts: List<Shift>): Shift? {
    val first = firstProfileShift(shifts) ?: return null
    return first.takeIf { !it.isDayOff && !it.startTime.isNullOrEmpty() }
}

/**
 * Пары «профиль дня» для раскраски квадрата (решение 12-Б): верхний цвет —
 * первый профиль, нижний — второй. Возвращает пару профилей; второй равен
 * первому (или null), когда профиль в дне один.
 */
fun dayProfilePair(shifts: List<Shift>): Pair<Long, Long> {
    val ordered = MultiShift.ordered(shifts)
    val first = ordered.firstOrNull()?.profileId ?: 0L
    val second = ordered.drop(1).firstOrNull { it.profileId != first }?.profileId ?: first
    return first to second
}

/** Рабочий ли день у смен, отмеченных профилем [profileId]. */
fun isWorkDayForProfile(shifts: List<Shift>, profileId: Long): Boolean =
    shifts.any { it.profileId == profileId && !it.isDayOff }

/**
 * Цвет половины квадрата: рабочие дни профиля рисуются его рабочим цветом,
 * выходные — цветом выходного; дней профиля в дне нет — цвета нет.
 */
fun halfColorForProfile(
    shifts: List<Shift>,
    profileId: Long,
    workColor: Long?,
    dayOffColor: Long?
): Long? {
    if (profileId <= 0L) return null
    val own = shifts.filter { it.profileId == profileId }
    if (own.isEmpty()) return null
    return if (own.any { !it.isDayOff }) workColor else dayOffColor
}
