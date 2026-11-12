package com.shiftcalendar.ui

import com.shiftcalendar.data.Shift
import com.shiftcalendar.model.moneyText
import com.shiftcalendar.model.parseTimeOrNull
import com.shiftcalendar.model.roundMoney
import java.time.Duration
import java.time.LocalTime

/**
 * Форматирование процентов надбавки:
 * дробная часть рассчитана на два знака, но нули в конце не показываются.
 *
 * 20    -> "20"      20.5  -> "20,5"      20.25 -> "20,25"      22.0 -> "22"
 */
fun formatPercent(value: Double): String {
    val rounded = Math.round(value * 100.0) / 100.0
    if (rounded % 1.0 == 0.0) return rounded.toLong().toString()
    var text = String.format("%02.2f", rounded).replace('.', ',')
    if (text.contains(',')) text = text.trimEnd('0').trimEnd(',')
    return text
}

/** Обратное преобразование текста поля в проценты; мусор -> null. */
fun parsePercent(text: String): Double? =
    text.replace(',', '.').trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()

/**
 * Как задана надбавка (решение клиента 13-Б): процентом от ставки или
 * фиксированной суммой за час. Пользователь выбирает один из двух вариантов.
 */
enum class PremiumMode {
    /** Надбавка в процентах: час в окне = ставка × (1 + процент/100). */
    PERCENT,

    /** Надбавка рублями за час: час в окне = ставка + сумма. */
    AMOUNT
}

/**
 * Настройка надбавки целиком: как считается ([mode]), величина ([percent] или
 * [amount]), с какого времени начисляется ([threshold]) и учитывать ли
 * перерыв, попавший после начала надбавки ([breakCountsInPremium]).
 *
 * Название ([title]) — пользовательское: «Ночная», «Вечерняя» и т.п.
 * Окно надбавки теперь одностороннее — от [threshold] до конца смены
 * (решение 13-Б); прежняя «Надбавка до» больше не нужна.
 */
data class PremiumSettings(
    val title: String = "Ночная надбавка",
    val mode: PremiumMode = PremiumMode.PERCENT,
    val percent: Double = 20.0,
    val amount: Double = 0.0,
    val threshold: String = "22:00",
    val breakCountsInPremium: Boolean = true
) {
    /** Надбавка выключена: ни процента, ни суммы. */
    val isOff: Boolean get() = percent <= 0.0 && amount <= 0.0

    /** Подпись режима для итогов: «30%» или «67Р/ч». */
    val rateLegend: String
        get() = when {
            mode == PremiumMode.PERCENT && percent > 0.0 -> "${formatPercent(percent)}%"
            mode == PremiumMode.AMOUNT && amount > 0.0 -> "${moneyText(amount)}/ч"
            else -> "без надбавки"
        }
}

/** Надбавочная ставка: базовая ставка + процент от неё. 100 ₽/ч + 20% = 120 ₽/ч. */
fun premiumRate(rate: Double, percent: Double): Double = rate * (1.0 + percent / 100.0)

/**
 * Длительность интервала смены в минутах; переход через полночь = +24 ч.
 * Равные концы — некорректный интервал, а не сутки: 0 минут.
 */
fun intervalMinutes(start: LocalTime, end: LocalTime): Long {
    val raw = Duration.between(start, end).toMinutes()
    return when {
        raw == 0L -> 0L
        raw < 0L -> raw + 1440
        else -> raw
    }
}

/**
 * Находится ли время t в окне [winStart, winEnd). Окно может переходить через
 * полночь (22:00–06:00); при winStart == winEnd окно считается пустым.
 */
fun inWindow(t: LocalTime, winStart: LocalTime, winEnd: LocalTime): Boolean {
    if (winStart == winEnd) return false
    return if (!winEnd.isAfter(winStart)) !t.isBefore(winStart) || t.isBefore(winEnd)
    else !t.isBefore(winStart) && t.isBefore(winEnd)
}

/**
 * С какого времени смещается перерыв внутри смены.
 * Пользователь задаёт только длительность, поэтому место перерыва — договорённость.
 */
enum class BreakAnchor {
    /** Перерыв в начале смены: съедает первые минуты интервала. */
    START,

    /** Перерыв в конце смены: съедает последние минуты интервала. */
    END
}

/** Итог по смене: сколько минут оплачивается базово и сколько — с надбавкой. */
data class PremiumSplit(
    /** Минуты вне окна надбавки (оплачиваются по базовой ставке). */
    val baseMinutes: Long,
    /** Минуты внутри окна надбавки (оплачиваются по надбавочной ставке). */
    val premiumMinutes: Long
) {
    val totalMinutes: Long get() = baseMinutes + premiumMinutes
}

/**
 * Раскладка смены на базовые и надбавочные минуты с учётом перерыва.
 *
 * Чистое ядро расчёта надбавки: без зависимости от [Shift] и без Android,
 * поэтому проверяется обычными юнит-тестами.
 *
 * Правила:
 *  - интервал смены — полуинтервал [start, end) с переходом через полночь;
 *  - без перерыва надбавка — ровно пересечение смены с окном (точная арифметика
 *    по минутам, без масштабирования «долей»);
 *  - перерыв длиной [breakMinutes] вычитается из того участка смены, где он
 *    фактически проходит, и «съедает» сначала минуты своего участка, затем —
 *    остатка. Так перерыв, попавший в ночное окно, не оставляет надбавку за
 *    нерабочие минуты, а дневной перерыв не отнимает ночную надбавку;
 *  - место перерыва задаёт [anchor]: с начала смены (по умолчанию) или с конца.
 *
 * Пример: 100 ₽/ч, надбавка 20%, окно 22:00–06:00, смена 20:00–08:00:
 * без перерыва — 10 базовых ч + 8 надбавочных = 1960 ₽;
 * перерыв 60 мин (в начале, т.е. в дневной части 20:00–21:00) —
 * 11 базовых ч (1100 ₽) + 7 надбавочных (840 ₽) = 1940 ₽.
 */
fun premiumSplit(
    start: LocalTime,
    end: LocalTime,
    thresholdStart: LocalTime,
    thresholdEnd: LocalTime,
    breakMinutes: Int = 0,
    anchor: BreakAnchor = BreakAnchor.START
): PremiumSplit {
    val gross = intervalMinutes(start, end)
    if (gross <= 0L) return PremiumSplit(0L, 0L)

    // Минута i смены — это (start + i) на шкале от полуночи ДНЯ НАЧАЛА смены.
    // LocalTime сам перекатывается через сутки, поэтому смена 20:00–08:00
    // правильно видит 22:00–23:59 и 00:00–06:00 следующего дня.
    fun minuteAt(i: Long): LocalTime = start.plusMinutes(i)

    var grossPremium = 0L
    for (i in 0L until gross) {
        if (inWindow(minuteAt(i), thresholdStart, thresholdEnd)) grossPremium++
    }
    val grossBase = gross - grossPremium


    val breakLen = breakMinutes.toLong().coerceIn(0L, gross)
    if (breakLen == 0L) return PremiumSplit(grossBase, grossPremium)

    // Пробегаем перерыв от выбранного края и записываем его в ту категорию,
    // к которой относится каждая вычтенная минута.
    var cutBase = 0L
    var cutPremium = 0L
    for (k in 0L until breakLen) {
        val minuteIndex = if (anchor == BreakAnchor.START) k else gross - 1L - k
        if (inWindow(minuteAt(minuteIndex), thresholdStart, thresholdEnd)) cutPremium++
        else cutBase++
    }
    return PremiumSplit(
        baseMinutes = (grossBase - cutBase).coerceAtLeast(0L),
        premiumMinutes = (grossPremium - cutPremium).coerceAtLeast(0L)
    )
}

/**
 * Заработок смены с учётом надбавки.
 *
 * Правила:
 *  - интервал смены [startTime, endTime) (ночь — через полночь) уменьшается на
 *    перерыв: перерыв оплачиваемой работы не даёт;
 *  - минуты, попадающие в окно надбавки [thresholdStart, thresholdEnd),
 *    оплачиваются как premiumRate(rate, percent), остальные — как rate;
 *  - перерыв вычитается из тех минут, где он проходит, — см. [premiumSplit].
 *
 * Пример: ставка 100 ₽/ч, надбавка 20%, окно 22:00–06:00, смена 20:00–08:00
 * без перерыва => 10 базовых часов (100 ₽) + 8 надбавочных (120 ₽) = 1960 ₽.
 */
fun shiftEarnings(
    shift: Shift,
    thresholdStart: LocalTime,
    thresholdEnd: LocalTime,
    percent: Double,
    breakMinutes: Int? = null,
    anchor: BreakAnchor = BreakAnchor.START
): Double {
    if (shift.isDayOff || shift.rate <= 0.0) return 0.0
    val start = runCatching { LocalTime.parse(shift.startTime ?: "00:00") }.getOrDefault(LocalTime.MIDNIGHT)
    val end = runCatching { LocalTime.parse(shift.endTime ?: "00:00") }.getOrDefault(LocalTime.MIDNIGHT)
    // По умолчанию берём перерыв из самой смены: забытый аргумент не должен
    // молча оплачивать перерыв как рабочее время.
    val brk = breakMinutes ?: shift.breakMinutes
    return shiftEarnings(shift.rate, start, end, thresholdStart, thresholdEnd, percent, brk, anchor)
}

/**
 * Заработок по явным ставке и интервалу — без [Shift]. Нужен там, где смена
 * ещё не сохранена (редактор дня) или считается предпросмотр.
 */
fun shiftEarnings(
    rate: Double,
    start: LocalTime,
    end: LocalTime,
    thresholdStart: LocalTime,
    thresholdEnd: LocalTime,
    percent: Double,
    breakMinutes: Int = 0,
    anchor: BreakAnchor = BreakAnchor.START
): Double {
    if (rate <= 0.0) return 0.0
    val split = premiumSplit(start, end, thresholdStart, thresholdEnd, breakMinutes, anchor)
    val baseHours = split.baseMinutes / 60.0
    val premiumHours = split.premiumMinutes / 60.0
    return rate * baseHours + rate * premiumHours * (1.0 + percent / 100.0)
}

/**
 * Разложение заработка на базу и надбавку — для честных подписей в UI
 * («ставка 100 ₽ · надбавка 20% → 1940 ₽»), а не только итоговой суммы.
 */
fun shiftEarningsBreakdown(
    rate: Double,
    start: LocalTime,
    end: LocalTime,
    thresholdStart: LocalTime,
    thresholdEnd: LocalTime,
    percent: Double,
    breakMinutes: Int = 0,
    anchor: BreakAnchor = BreakAnchor.START
): Triple<Double, Double, Double> {
    if (rate <= 0.0) return Triple(0.0, 0.0, 0.0)
    val split = premiumSplit(start, end, thresholdStart, thresholdEnd, breakMinutes, anchor)
    val base = rate * split.baseMinutes / 60.0
    val premium = rate * (1.0 + percent / 100.0) * split.premiumMinutes / 60.0
    return Triple(base, premium, base + premium)
}

// ---------- Надбавка, заданная пользователем (решение 13-Б) ----------

/**
 * Надбавочная ставка за час: процент от ставки ИЛИ фиксированная сумма к ней.
 * Пример клиента: ставка 270 ₽/ч и надбавка 67 ₽ → час в окне стоит 337 ₽.
 */
fun premiumHourRate(rate: Double, settings: PremiumSettings): Double = when (settings.mode) {
    PremiumMode.PERCENT -> rate * (1.0 + settings.percent / 100.0)
    PremiumMode.AMOUNT -> rate + settings.amount
}

/**
 * Границы окна надбавки в минутах от начала смены.
 *
 * Окно одностороннее: «с 22:00 до конца смены». Для смены 16:00–01:00 начало
 * надбавки — на 360-й минуте (22:00); для смены 18:00–02:00 — тоже 360.
 * Если время начала не попадает внутрь смены, надбавки нет (окно пустое).
 */
fun premiumWindow(
    start: LocalTime,
    end: LocalTime,
    threshold: LocalTime
): Pair<Long, Long>? {
    val gross = intervalMinutes(start, end)
    if (gross <= 0L) return null
    var offset = Duration.between(start, threshold).toMinutes()
    if (offset < 0L) offset += 1440
    if (offset > gross) return null
    return offset to gross
}

/**
 * Раскладка смены на базовые и надбавочные минуты с односторонним окном
 * и правилом перерыва «считать со времени начала надбавки» (решение 13-Б).
 *
 * Правила:
 *  - интервал смены — полуинтервал [start, end) с переходом через полночь;
 *  - минуты после [threshold] (по шкале смены) — надбавочные;
 *  - при [breakCountsInPremium] перерыв вычитается С КОНЦА смены: он съедает
 *    сначала надбавочные минуты и только потом базовые. Так надбавка не
 *    начисляется за час обеда, попавший после начала надбавки;
 *  - при выключенном правиле перерыв вычитается с начала смены (базовые минуты).
 *
 * Пример клиента: ставка 270 ₽, надбавка 30%, смена 16:00–01:00 (9 ч), обед
 * 1 час в 23:00 → 6 базовых ч (1620 ₽) + 2 надбавочных ч (702 ₽) = 2322 ₽.
 */
fun premiumSplitByThreshold(
    start: LocalTime,
    end: LocalTime,
    threshold: LocalTime,
    breakMinutes: Int = 0,
    breakCountsInPremium: Boolean = true
): PremiumSplit {
    val window = premiumWindow(start, end, threshold) ?: return PremiumSplit(0L, 0L)
    val (premiumStart, gross) = window

    val cut = breakMinutes.toLong().coerceIn(0L, gross)
    // Базовые минуты идут первыми: [0, premiumStart), затем надбавочные.
    val baseGross = premiumStart
    var cutPremium = 0L
    if (breakCountsInPremium) {
        val beyondThreshold = gross - premiumStart
        cutPremium = cut.coerceAtMost(beyondThreshold)
    }
    val cutBase = cut - cutPremium
    return PremiumSplit(
        baseMinutes = (baseGross - cutBase).coerceAtLeast(0L),
        premiumMinutes = (premiumStart - cutPremium).coerceAtLeast(0L)
    )
}

/**
 * Заработок смены с надбавкой пользователя: базовая ставка за часы до начала
 * надбавки плюс надбавочная ставка за часы внутри окна.
 *
 * Формула клиента: часы до надбавки × ставка + (часы с начала надбавки −
 * перерыв после её начала) × (ставка + надбавка).
 */
fun shiftEarningsWithPremium(
    rate: Double,
    start: LocalTime,
    end: LocalTime,
    settings: PremiumSettings,
    breakMinutes: Int = 0
): Double {
    if (rate < 0.0) return 0.0
    val gross = intervalMinutes(start, end)
    if (gross <= 0L) return 0.0
    val hours = premiumHourRate(rate, settings)
    // Надбавка выключена (или ставка нулевая) — считаем просто по часам.
    if (settings.isOff) return rate * (gross - breakMinutes.coerceIn(0, gross.toInt())) / 60.0
    val split = premiumSplitByThreshold(start, end, parseTimeOrNull(settings.threshold)
        ?: LocalTime.of(22, 0), breakMinutes, settings.breakCountsInPremium)
    val base = rate * split.baseMinutes / 60.0
    val premium = hours * split.premiumMinutes / 60.0
    return roundMoney(base + premium)
}

