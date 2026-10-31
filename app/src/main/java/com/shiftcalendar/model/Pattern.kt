package com.shiftcalendar.model

import com.shiftcalendar.data.ShiftKind
import com.shiftcalendar.data.Shift

/**
 * Один шаг паттерна: смена или выходной, со своей ставкой.
 * Например «раб/раб/раб/раб/вых/вых».
 */
data class PatternStep(
    val isDayOff: Boolean = false,
    val kind: ShiftKind = ShiftKind.DAY,
    val hours: Double = DEFAULT_SHIFT_HOURS,
    val rate: Double = 0.0
)

/** Готовые шаблоны циклов — чтобы не собирать с нуля. */
val PRESET_PATTERNS: List<Pair<String, List<PatternStep>>> = listOf(
    "4/2 (раб×4, вых×2)" to listOf(
        PatternStep(kind = ShiftKind.DAY),
        PatternStep(kind = ShiftKind.DAY),
        PatternStep(kind = ShiftKind.DAY),
        PatternStep(kind = ShiftKind.DAY),
        PatternStep(isDayOff = true),
        PatternStep(isDayOff = true)
    ),
    "2/2 (раб×2, вых×2)" to listOf(
        PatternStep(kind = ShiftKind.DAY),
        PatternStep(kind = ShiftKind.DAY),
        PatternStep(isDayOff = true),
        PatternStep(isDayOff = true)
    ),
    "3/1 (вахта)" to listOf(
        PatternStep(kind = ShiftKind.DAY),
        PatternStep(kind = ShiftKind.DAY),
        PatternStep(kind = ShiftKind.DAY),
        PatternStep(isDayOff = true)
    ),
    "5/2 (будни)" to listOf(
        PatternStep(kind = ShiftKind.DAY),
        PatternStep(kind = ShiftKind.DAY),
        PatternStep(kind = ShiftKind.DAY),
        PatternStep(kind = ShiftKind.DAY),
        PatternStep(kind = ShiftKind.DAY),
        PatternStep(isDayOff = true),
        PatternStep(isDayOff = true)
    ),
    "2 дня / 2 ночи / 2 вых" to listOf(
        PatternStep(kind = ShiftKind.DAY),
        PatternStep(kind = ShiftKind.DAY),
        PatternStep(kind = ShiftKind.NIGHT),
        PatternStep(kind = ShiftKind.NIGHT),
        PatternStep(isDayOff = true),
        PatternStep(isDayOff = true)
    ),
    "1/1 (сутки через сутки)" to listOf(
        PatternStep(kind = ShiftKind.DAY),
        PatternStep(isDayOff = true)
    )
)

/**
 * Результат наложения паттерна на календарь: список смен, которые нужно
 * записать, и статистика для предпросмотра.
 */
data class PatternApplyResult(
    val shifts: List<Shift>,
    val firstDate: java.time.LocalDate,
    val lastDate: java.time.LocalDate,
    val workCount: Int,
    val dayOffCount: Int,
    val totalHours: Double,
    val totalEarned: Double,
    val skippedExisting: Int
)

/**
 * Наложить паттерн на диапазон дат.
 *
 * @param start первый день цикла. При [alignToMonth] паттерн «плывёт» так,
 *   чтобы каждый месяц начинался с начала цикла (режим «по календарю»);
 *   иначе цикл идёт непрерывно от [start] (режим «подряд»).
 * @param keepExisting не трогать дни, где уже есть запись.
 */
fun buildPatternShifts(
    pattern: List<PatternStep>,
    start: java.time.LocalDate,
    from: java.time.LocalDate,
    to: java.time.LocalDate,
    alignToMonth: Boolean,
    keepExisting: Boolean,
    existingDates: Set<String>
): PatternApplyResult {
    val out = ArrayList<Shift>()
    var workCount = 0
    var dayOffCount = 0
    var totalHours = 0.0
    var totalEarned = 0.0
    var skipped = 0
    var first: java.time.LocalDate? = null
    var last: java.time.LocalDate? = null

    if (pattern.isEmpty()) {
        return PatternApplyResult(emptyList(), from, from, 0, 0, 0.0, 0.0, 0)
    }

    var day = from
    while (!day.isAfter(to)) {
        // Индекс шага: либо непрерывно от старта цикла, либо от начала месяца
        val cycleBase = if (alignToMonth) day.withDayOfMonth(1) else start
        if (day.isBefore(start) && !alignToMonth) {
            day = day.plusDays(1)
            continue
        }
        val offset = java.time.temporal.ChronoUnit.DAYS.between(cycleBase, day)
        val idx = ((offset % pattern.size) + pattern.size).toInt() % pattern.size
        val step = pattern[idx]

        if (keepExisting && existingDates.contains(day.iso())) {
            skipped++
        } else {
            val (st, en) = when {
                step.isDayOff -> null to null
                step.kind == ShiftKind.DAY -> DAY_START to DAY_END
                step.kind == ShiftKind.NIGHT -> NIGHT_START to NIGHT_END
                else -> null to null
            }
            out.add(
                Shift(
                    date = day.iso(),
                    kind = step.kind.name,
                    hours = if (step.isDayOff) 0.0 else step.hours,
                    startTime = st,
                    rate = if (step.isDayOff) 0.0 else step.rate,
                    isDayOff = step.isDayOff,
                    note = "паттерн"
                )
            )
            if (step.isDayOff) dayOffCount++ else {
                workCount++
                totalHours += step.hours
                totalEarned += step.hours * step.rate
            }
            if (first == null) first = day
            last = day
        }
        day = day.plusDays(1)
    }

    return PatternApplyResult(
        shifts = out,
        firstDate = first ?: from,
        lastDate = last ?: from,
        workCount = workCount,
        dayOffCount = dayOffCount,
        totalHours = totalHours,
        totalEarned = totalEarned,
        skippedExisting = skipped
    )
}
