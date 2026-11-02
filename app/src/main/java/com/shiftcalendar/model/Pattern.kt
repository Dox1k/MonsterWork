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

    /** Оба конца введены, но начало совпадает с концом — такую смену нельзя записать. */
    val hasDegenerateInterval: Boolean
        get() = !isDayOff && isDegenerateInterval(start, end)

    /** Тип смены по факту интервала: переход через полночь — NIGHT. */
    val resolvedKind: ShiftKind
        get() = if (isDayOff) kind else kindForInterval(kind, start, end)

    /**
     * Шаг, приведённый к записываемому виду: времена в «HH:mm», перерыв не
     * больше длины смены, у выходного нет ни времени, ни перерыва.
     * Используется при сохранении и при применении паттерна.
     */
    fun normalized(): PatternStep {
        val nStart = normalizeTime(start) ?: (if (isDayOff) null else defaultInterval(kind).first)
        val nEnd = normalizeTime(end) ?: (if (isDayOff) null else defaultInterval(kind).second)
        if (isDayOff) return copy(startTime = null, endTime = null, breakMinutes = 0)
        val len = (intervalHours(nStart, nEnd) ?: 0.0)
        val cap = Math.round(len * 60.0).toInt()
        return copy(
            startTime = nStart,
            endTime = nEnd,
            breakMinutes = breakMinutes.coerceIn(0, cap.coerceAtLeast(0))
        )
    }
}

private fun minutesOf(hours: Double): Int = Math.round(hours * 60.0).toInt()

/**
 * Тип смены по интервалу: переход через полночь — ночная.
 * Равные концы — не «ночь» и не сутки: интервал некорректен, тип остаётся.
 */
private fun kindForInterval(kind: ShiftKind, start: String?, end: String?): ShiftKind {
    val s = parseTimeOrNull(start) ?: return kind
    val e = parseTimeOrNull(end) ?: return kind
    if (s == e) return kind
    return if (e.isBefore(s)) ShiftKind.NIGHT else kind
}

/** Два шага задают одну и ту же смену — дубль, который можно схлопнуть. */
private fun PatternStep.sameShape(other: PatternStep): Boolean {
    if (isDayOff != other.isDayOff) return false
    if (isDayOff) return true
    return normalizeTime(start) == normalizeTime(other.start) &&
        normalizeTime(end) == normalizeTime(other.end) &&
        rate == other.rate &&
        breakMinutes == other.breakMinutes
}

/**
 * Свести цикл к кратчайшему периоду: «раб/раб/раб/раб/вых/вых» остаётся,
 * а «раб/вых/раб/вых» не меняется, зато цикл, повторённый дважды подряд,
 * сокращается до одного периода. Возвращает возможно более короткий цикл,
 * который даёт ту же последовательность смен (по первой [limitDays] дням,
 * по умолчанию — два полных прохода цикла).
 *
 * Это именно нормализация формы: шаги не теряются и последовательность
 * не перекашивается — в отличие от простого схлопывания соседей.
 */
fun normalizePattern(steps: List<PatternStep>, limitDays: Int = 0): List<PatternStep> {
    if (steps.isEmpty()) return steps
    // Соседние одинаковые дни НЕ склеиваем: «раб/раб/вых/вых» — это цикл 2/2,
    // и склейка превратила бы его в 1/1, перекос��в весь календарь.
    val merged = steps
    val n = merged.size
    val span = if (limitDays > 0) limitDays else n * 2
    // Сравниваем канонические формы, а не сырые: шаг без явного времени и тот же
    // шаг с проставленным временем — один и тот же день. Проверяем, что вся
    // последовательность повторяет первые [period] дней, а не что она содержит
    // только их: иначе ци��л 4/2 схлопнулся бы до 1/1.
    for (period in 1 until n) {
        if (n % period != 0) continue
        val head = canonicalForms(merged, 0, period)
        val body = canonicalForms(merged, 0, span)
        if (body.indices.all { i -> body[i] == head[i % period] }) {
            val cut = merged.take(period)
            // Время берём явное, если оно есть хотя бы в одном повторе периода.
            return cut.mapIndexed { k, s ->
                val src = (k until n step period).map { merged[it] }
                s.copy(
                    startTime = s.startTime ?: src.firstNotNullOfOrNull { it.startTime },
                    endTime = s.endTime ?: src.firstNotNullOfOrNull { it.endTime },
                    hours = if (s.hours != DEFAULT_SHIFT_HOURS) s.hours
                    else src.firstOrNull { it.hours != DEFAULT_SHIFT_HOURS }?.hours ?: s.hours
                )
            }
        }
    }
    return merged
}

/** Сравнимое описание дня цикла: время и ставка приведены к явному виду. */
private fun PatternStep.canonical(): String =
    if (isDayOff) "OFF|${kind.name}"
    else listOf("WORK", kind.name, start, end, rate.toString(), breakMinutes.toString()).joinToString("|")

private fun canonicalForms(steps: List<PatternStep>, from: Int, count: Int): List<String> =
    (0 until count).map { i -> steps[(from + i) % steps.size].canonical() }

/**
 * Проверка цикла перед сохранением: какие шаги нельзя записать и что
 * стоит поправить. Возвращает список проблем на русском — для показа рядом
 * с конструктором; пустой список значит «цикл годен».
 */
fun patternProblems(steps: List<PatternStep>): List<String> {
    if (steps.isEmpty()) return listOf("Цикл пуст")
    val problems = ArrayList<String>()
    steps.forEachIndexed { i, step ->
        val name = "Шаг ${i + 1}"
        if (step.isDayOff) {
            if (step.breakMinutes < 0) problems.add("$name: перерыв не может быть отрицательным")
            return@forEachIndexed
        }
        val s = parseTimeOrNull(step.start)
        val e = parseTimeOrNull(step.end)
        if (s == null || e == null) {
            problems.add("$name: проверьте время (ЧЧ:ММ)")
            return@forEachIndexed
        }
        if (s == e) {
            problems.add("$name: начало и конец совпадают — смена не может быть нулевой")
            return@forEachIndexed
        }
        if (step.breakMinutes < 0) problems.add("$name: перерыв не может быть отрицательным")
        val len = intervalHours(normalizeTime(step.start), normalizeTime(step.end)) ?: 0.0
        if (step.breakMinutes > len * 60) {
            problems.add("$name: перерыв длиннее смены")
        }
    }
    if (problems.isEmpty() && steps.all { it.isDayOff }) {
        problems.add("В цикле нет ни одной смены — только выходные")
    }
    return problems
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
    val replacedCount: Int,
    /** Даты диапазона, которые не проставлены из-за невалидного шага цикла. */
    val skippedDates: List<String> = emptyList()
) {
    /** Есть дни, не проставленные из-за невалидного шага. */
    val hasSkipped: Boolean get() = skippedDates.isNotEmpty()
}

/**
 * Наложить паттерн (профиль) на диапазон дат.
 *
 * Правило замены: паттерн НЕ дублирует существующее, а заменяет его. Все
 * смены интервала — чужих профилей и ручные — будут перезаписаны сменами
 * этого паттерна; счётчик [PatternApplyResult.replacedCount] говорит,
 * сколько ДАТ интервала уже были заняты. Один день — одна смена: повторной
 * записи на ту же дату не бывает, поэтому дублей паттерн не создаёт.
 *
 * Перед наложением цикл нормализуется ([normalizePattern]): повторённый
 * целиком цикл сжимается до периода, одинаковые соседние дни склеиваются,
 * а шаги приводятся к записи ([PatternStep.normalized]) — время в «HH:mm»,
 * перерыв не длиннее смены. Тип смены берётся по интервалу ([PatternStep.resolvedKind]):
 * шаг через полночь становится NIGHT.
 *
 * Невалидные шаги (время не разбирается или начало совпадает с концом)
 * пропускаются и попадают в [PatternApplyResult.skippedDates], чтобы вместо
 * выдуманных 24 часов пользователь увидел, какие дни не проставлены.
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
    val skipped = ArrayList<String>()

    val cycle = normalizePattern(pattern).map { it.normalized() }
    if (cycle.isEmpty() || to.isBefore(from)) {
        return PatternApplyResult(emptyList(), from, from, 0, 0, 0.0, 0.0, 0)
    }

    var day = from
    while (!day.isAfter(to)) {
        if (day.isBefore(start) && !alignToMonth) {
            day = day.plusDays(1)
            continue
        }
        // Индекс шага: либо непрерывно от старта цикла, либо от начала месяца
        val cycleBase = if (alignToMonth) day.withDayOfMonth(1) else start
        val offset = java.time.temporal.ChronoUnit.DAYS.between(cycleBase, day)
        val idx = ((offset % cycle.size) + cycle.size).toInt() % cycle.size
        val step = cycle[idx]

        if (step.hasDegenerateInterval ||
            (!step.isDayOff && (parseTimeOrNull(step.start) == null || parseTimeOrNull(step.end) == null))
        ) {
            // День остаётся как есть: лучше пропустить, чем записать фиктивные часы.
            skipped.add(day.iso())
            day = day.plusDays(1)
            continue
        }

        if (existingDates.contains(day.iso())) replaced++

        val worked = !step.isDayOff
        val hours = if (worked) step.paidHours else 0.0
        out.add(
            Shift(
                date = day.iso(),
                kind = step.resolvedKind.name,
                hours = hours,
                startTime = if (worked) step.start else null,
                rate = if (worked) step.rate else 0.0,
                isDayOff = !worked,
                note = "паттерн",
                endTime = if (worked) step.end else null,
                breakMinutes = if (worked) step.breakMinutes else 0,
                profileId = profileId
            )
        )
        if (worked) {
            workCount++
            totalHours += hours
            totalEarned += hours * step.rate
        } else dayOffCount++
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
        replacedCount = replaced,
        skippedDates = skipped
    )
}
