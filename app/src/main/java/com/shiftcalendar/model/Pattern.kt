package com.shiftcalendar.model

import com.shiftcalendar.data.Shift
import com.shiftcalendar.data.ShiftKind
import org.json.JSONArray
import org.json.JSONObject

/**
 * Один шаг паттерна: смена или выходной, со своей ставкой и своим интервалом.
 * Например «раб/раб/раб/раб/вых/вых».
 *
 * Время шага хранится явно ([startTime]/[endTime], «HH:mm»), чтобы каждый шаг
 * мог быть своим: 8:00–20:00, 9:30–21:30, 20:00–08:00 и т.д.
 * Поле [hours] — устаревшая подсказка для шагов без явных концов: если оно
 * задано и хотя бы одно из времён не заполнено, часы берутся из него.
 */
data class PatternStep(
    val isDayOff: Boolean = false,
    val kind: ShiftKind = ShiftKind.DAY,
    val hours: Double = DEFAULT_SHIFT_HOURS,
    val rate: Double = 0.0,
    val startTime: String? = null,
    val endTime: String? = null,
    /** Перерыв шага в минутах — вычитается из времени смены. */
    val breakMinutes: Int = 0
) {
    /** Начало смены: заданное в шаге или по типу. */
    val start: String get() = startTime ?: defaultInterval(kind).first

    /** Конец смены: заданный в шаге или рассчитанный из начала и часов. */
    val end: String
        get() = endTime ?: run {
            val base = parseTimeOrNull(start) ?: java.time.LocalTime.of(8, 0)
            val normalized = "%02d:%02d".format(base.hour, base.minute)
            if (hours > 0.0) shiftTime(normalized, minutesOf(hours), normalized) else defaultInterval(kind).second
        }

    /** Часы смены: из интервала, если он валиден, иначе из [hours]. */
    val effectiveHours: Double
        get() = if (isDayOff) 0.0 else (intervalHours(start, end) ?: hours)

    /** Оплачиваемые часы: интервал минус перерыв, не меньше нуля. */
    val paidHours: Double
        get() = (effectiveHours - breakMinutes / 60.0).coerceAtLeast(0.0)
}

private fun minutesOf(hours: Double): Int = Math.round(hours * 60.0).toInt()

/** Ночная смена — та, что пересекает полночь. */
private fun PatternStep.isNightSpan(): Boolean {
    val s = parseTimeOrNull(start) ?: return false
    val e = parseTimeOrNull(end) ?: return false
    return !e.isAfter(s)
}

/** Готовые шаблоны циклов — чтобы не собирать с нуля. */
val PRESET_PATTERNS: List<Pair<String, List<PatternStep>>> = listOf(
    "4/2 (раб×4, вых×2)" to listOf(
        PatternStep(kind = ShiftKind.DAY, startTime = DAY_START, endTime = DAY_END),
        PatternStep(kind = ShiftKind.DAY, startTime = DAY_START, endTime = DAY_END),
        PatternStep(kind = ShiftKind.DAY, startTime = DAY_START, endTime = DAY_END),
        PatternStep(kind = ShiftKind.DAY, startTime = DAY_START, endTime = DAY_END),
        PatternStep(isDayOff = true),
        PatternStep(isDayOff = true)
    ),
    "2/2 (раб×2, вых×2)" to listOf(
        PatternStep(kind = ShiftKind.DAY, startTime = DAY_START, endTime = DAY_END),
        PatternStep(kind = ShiftKind.DAY, startTime = DAY_START, endTime = DAY_END),
        PatternStep(isDayOff = true),
        PatternStep(isDayOff = true)
    ),
    "3/1 (вахта)" to listOf(
        PatternStep(kind = ShiftKind.DAY, startTime = DAY_START, endTime = DAY_END),
        PatternStep(kind = ShiftKind.DAY, startTime = DAY_START, endTime = DAY_END),
        PatternStep(kind = ShiftKind.DAY, startTime = DAY_START, endTime = DAY_END),
        PatternStep(isDayOff = true)
    ),
    "5/2 (будни)" to listOf(
        PatternStep(kind = ShiftKind.DAY, startTime = DAY_START, endTime = DAY_END),
        PatternStep(kind = ShiftKind.DAY, startTime = DAY_START, endTime = DAY_END),
        PatternStep(kind = ShiftKind.DAY, startTime = DAY_START, endTime = DAY_END),
        PatternStep(kind = ShiftKind.DAY, startTime = DAY_START, endTime = DAY_END),
        PatternStep(kind = ShiftKind.DAY, startTime = DAY_START, endTime = DAY_END),
        PatternStep(isDayOff = true),
        PatternStep(isDayOff = true)
    ),
    "2 дня / 2 ночи / 2 вых" to listOf(
        PatternStep(kind = ShiftKind.DAY, startTime = DAY_START, endTime = DAY_END),
        PatternStep(kind = ShiftKind.DAY, startTime = DAY_START, endTime = DAY_END),
        PatternStep(kind = ShiftKind.NIGHT, startTime = NIGHT_START, endTime = NIGHT_END),
        PatternStep(kind = ShiftKind.NIGHT, startTime = NIGHT_START, endTime = NIGHT_END),
        PatternStep(isDayOff = true),
        PatternStep(isDayOff = true)
    ),
    "1/1 (сутки через сутки)" to listOf(
        PatternStep(kind = ShiftKind.DAY, startTime = DAY_START, endTime = DAY_END),
        PatternStep(isDayOff = true)
    )
)

/**
 * Профиль — сохранённый график смен (например 4/2 или 2/2 5/2 3/1), который
 * пользователь собрал сам: порядок цикла, времена, ставки, перерывы и цвета.
 * Красный — рабочие дни, зелёный — выходной; оба цвета можно поменять.
 */
data class PatternProfile(
    val id: Long,
    val name: String,
    val steps: List<PatternStep>,
    /** Цвет рабочих дней, ARGB int. */
    val workColor: Long = DEFAULT_WORK_COLOR,
    /** Цвет выходных, ARGB int. */
    val dayOffColor: Long = DEFAULT_DAYOFF_COLOR
) {
    companion object {
        const val DEFAULT_WORK_COLOR = 0xFFD8433CL   // красный
        const val DEFAULT_DAYOFF_COLOR = 0xFF2E9E5BL // зелёный
    }
}

// ---------- JSON: профили и черновик цикла живут в SharedPreferences ----------

fun profileToJson(p: PatternProfile): String {
    val steps = JSONArray()
    p.steps.forEach { s ->
        steps.put(
            JSONObject()
                .put("dayOff", s.isDayOff)
                .put("kind", s.kind.name)
                .put("hours", s.hours)
                .put("rate", s.rate)
                .put("start", s.startTime ?: "")
                .put("end", s.endTime ?: "")
                .put("break", s.breakMinutes)
        )
    }
    return JSONObject()
        .put("id", p.id)
        .put("name", p.name)
        .put("workColor", p.workColor)
        .put("dayOffColor", p.dayOffColor)
        .put("steps", steps)
        .toString()
}

fun profileFromJson(text: String): PatternProfile? = runCatching {
    val o = JSONObject(text)
    val steps = ArrayList<PatternStep>()
    val arr = o.getJSONArray("steps")
    for (i in 0 until arr.length()) {
        val s = arr.getJSONObject(i)
        steps.add(
            PatternStep(
                isDayOff = s.optBoolean("dayOff"),
                kind = runCatching { ShiftKind.valueOf(s.optString("kind", "DAY")) }.getOrDefault(ShiftKind.DAY),
                hours = s.optDouble("hours", DEFAULT_SHIFT_HOURS),
                rate = s.optDouble("rate", 0.0),
                startTime = s.optString("start", "").takeIf { it.isNotEmpty() },
                endTime = s.optString("end", "").takeIf { it.isNotEmpty() },
                breakMinutes = s.optInt("break", 0)
            )
        )
    }
    PatternProfile(
        id = o.getLong("id"),
        name = o.getString("name"),
        steps = steps,
        workColor = o.optLong("workColor", PatternProfile.DEFAULT_WORK_COLOR),
        dayOffColor = o.optLong("dayOffColor", PatternProfile.DEFAULT_DAYOFF_COLOR)
    )
}.getOrNull()

fun profilesToJson(list: List<PatternProfile>): String {
    val arr = JSONArray()
    list.forEach { arr.put(JSONObject(profileToJson(it))) }
    return arr.toString()
}

fun profilesFromJson(text: String): List<PatternProfile> = runCatching {
    val arr = JSONArray(text)
    (0 until arr.length()).mapNotNull { i -> profileFromJson(arr.getJSONObject(i).toString()) }
}.getOrDefault(emptyList())

/** Сериализация черновика цикла (то, что пользователь выставил в конструкторе). */
fun stepsToJson(steps: List<PatternStep>): String {
    val arr = JSONArray()
    steps.forEach { s ->
        arr.put(
            JSONObject()
                .put("dayOff", s.isDayOff)
                .put("kind", s.kind.name)
                .put("hours", s.hours)
                .put("rate", s.rate)
                .put("start", s.startTime ?: "")
                .put("end", s.endTime ?: "")
                .put("break", s.breakMinutes)
        )
    }
    return arr.toString()
}

fun stepsFromJson(text: String): List<PatternStep> = runCatching {
    val arr = JSONArray(text)
    (0 until arr.length()).map { i ->
        val s = arr.getJSONObject(i)
        PatternStep(
            isDayOff = s.optBoolean("dayOff"),
            kind = runCatching { ShiftKind.valueOf(s.optString("kind", "DAY")) }.getOrDefault(ShiftKind.DAY),
            hours = s.optDouble("hours", DEFAULT_SHIFT_HOURS),
            rate = s.optDouble("rate", 0.0),
            startTime = s.optString("start", "").takeIf { it.isNotEmpty() },
            endTime = s.optString("end", "").takeIf { it.isNotEmpty() },
            breakMinutes = s.optInt("break", 0)
        )
    }
}.getOrDefault(emptyList())

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
    val replacedCount: Int
)

/**
 * Наложить паттерн (профиль) на диапазон дат.
 *
 * Правило замены: паттерн НЕ дублирует существующее, а заменяет его. Все
 * смены интервала — чужих профилей и ручные — будут перезаписаны сменами
 * этого паттерна; счётчик [PatternApplyResult.replacedCount] говорит,
 * сколько записей затронуто. Собственные старые смены того же профиля
 * ([profileId] > 0) не считаются дублями: при записи по дате они просто
 * перезаписываются свежими значениями, дублирования не происходит.
 *
 * @param start первый день цикла. При [alignToMonth] паттерн «плывёт» так,
 *   чтобы каждый месяц начинался с начала цикла (режим «по календарю»);
 *   иначе цикл идёт непрерывно от [start] (режим «подряд»).
 */
fun buildPatternShifts(
    pattern: List<PatternStep>,
    start: java.time.LocalDate,
    from: java.time.LocalDate,
    to: java.time.LocalDate,
    alignToMonth: Boolean,
    profileId: Long,
    existingDates: Set<String>
): PatternApplyResult {
    val out = ArrayList<Shift>()
    var workCount = 0
    var dayOffCount = 0
    var totalHours = 0.0
    var totalEarned = 0.0
    var replaced = 0
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

        if (existingDates.contains(day.iso())) replaced++

        // Интервал шага — свой у каждого шага цикла; ночная смена красится в NIGHT.
        val hours = if (step.isDayOff) 0.0 else step.paidHours
        val kindName = when {
            step.isDayOff -> step.kind.name
            step.isNightSpan() -> ShiftKind.NIGHT.name
            else -> step.kind.name
        }
        out.add(
            Shift(
                date = day.iso(),
                kind = kindName,
                hours = hours,
                startTime = if (step.isDayOff) null else step.start,
                rate = if (step.isDayOff) 0.0 else step.rate,
                isDayOff = step.isDayOff,
                note = "паттерн",
                endTime = if (step.isDayOff) null else step.end,
                breakMinutes = if (step.isDayOff) 0 else step.breakMinutes,
                profileId = profileId
            )
        )
        if (step.isDayOff) dayOffCount++ else {
            workCount++
            totalHours += hours
            totalEarned += hours * step.rate
        }
        if (first == null) first = day
        last = day
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
        replacedCount = replaced
    )
}
