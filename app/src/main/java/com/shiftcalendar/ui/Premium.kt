package com.shiftcalendar.ui

import com.shiftcalendar.data.Shift
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

/** Надбавочная ставка: базовая ставка + процент от неё. 100 ₽/ч + 20% = 120 ₽/ч. */
fun premiumRate(rate: Double, percent: Double): Double = rate * (1.0 + percent / 100.0)

/** Длительность интервала смены в минутах; переход через полночь = +24 ч. */
fun intervalMinutes(start: LocalTime, end: LocalTime): Long {
    val raw = Duration.between(start, end).toMinutes()
    return if (raw <= 0) raw + 1440 else raw
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
 * Заработок смены с учётом надбавки.
 *
 * Правила:
 *  - интервал смены [startTime, endTime) (ночь — через полночь) уменьшается на
 *    [breakMinutes]: перерыв оплачиваемой работы не даёт;
 *  - минуты, попадающие в окно надбавки [thresholdStart, thresholdEnd),
 *    оплачиваются как premiumRate(rate, percent), остальные — как rate;
 *  - перерыв «размазан» равномерно, поэтому надбавка уменьшается
 *    пропорционально доле отработанных минут.
 *
 * Пример: ставка 100 ₽/ч, надбавка 20%, окно 22:00–06:00, смена 20:00–08:00
 * без перерыва => 10 базовых часов (100 ₽) + 8 надбавочных (120 ₽) = 1960 ₽.
 */
fun shiftEarnings(
    shift: Shift,
    thresholdStart: LocalTime,
    thresholdEnd: LocalTime,
    percent: Double,
    breakMinutes: Int = 0
): Double {
    if (shift.isDayOff || shift.rate <= 0.0) return 0.0
    val start = runCatching { LocalTime.parse(shift.startTime ?: "00:00") }.getOrDefault(LocalTime.MIDNIGHT)
    val end = runCatching { LocalTime.parse(shift.endTime ?: "00:00") }.getOrDefault(LocalTime.MIDNIGHT)
    val gross = intervalMinutes(start, end)
    if (gross <= 0L) return 0.0
    val worked = (gross - breakMinutes.coerceIn(0, gross.toInt())).coerceAtLeast(0L)
    if (worked == 0L) return 0.0

    val premiumMinutes = (0 until gross).count { minute ->
        inWindow(start.plusMinutes(minute.toLong()), thresholdStart, thresholdEnd)
    }.toLong()

    val workShare = worked.toDouble() / gross.toDouble()
    val premiumHours = premiumMinutes * workShare / 60.0
    val baseHours = worked / 60.0 - premiumHours
    val money = shift.rate * baseHours + shift.rate * premiumHours * (1.0 + percent / 100.0)
    return money
}
