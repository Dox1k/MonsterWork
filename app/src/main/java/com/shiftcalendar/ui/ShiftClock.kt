package com.shiftcalendar.ui

import com.shiftcalendar.data.Shift
import com.shiftcalendar.model.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Логика «банок» и прогресса. Здесь только чистые функции без Android/Compose —
 * их покрывают обычные JVM-тесты (BankTest / ClockTest).
 *
 * Правило от пользователя: одна банка за каждый час смены.
 * 12-часовая смена — 12 банок, 2-часовая — 2 банки.
 */

/** Начало и конец смены; ночная смена переходит через полночь. */
fun shiftWindow(shift: Shift): Pair<LocalDateTime, LocalDateTime>? {
    if (shift.isDayOff) return null
    val start = shift.startTime?.let { parseTimeOrNull(it) } ?: return null
    val endText = shift.endTime ?: when (shift.kind) {
        "NIGHT" -> NIGHT_END
        "DAY" -> DAY_END
        else -> null
    }
    val end = parseTimeOrNull(endText) ?: return null
    val date = LocalDate.parse(shift.date, ISO)
    val startDt = LocalDateTime.of(date, start)
    var endDt = LocalDateTime.of(date, end)
    if (!endDt.isAfter(startDt)) endDt = endDt.plusDays(1)
    return startDt to endDt
}

/** Длительность смены в часах по интервалу (0 — если интервала нет). */
fun shiftDurationHours(shift: Shift): Double {
    val (start, end) = shiftWindow(shift) ?: return 0.0
    return java.time.Duration.between(start, end).toMinutes() / 60.0
}

/**
 * Сколько банок рисуем для смены. Одна банка = один час, поэтому число банок
 * равно длительности смены в целых часах. Получасовые смены округляются вверх,
 * чтобы за любую работу была хотя бы одна банка.
 */
fun canCountFor(shift: Shift): Int {
    if (shift.isDayOff) return 0
    val hours = shiftDurationHours(shift).takeIf { it > 0 } ?: shift.hours
    if (hours <= 0) return 0
    return kotlin.math.ceil(hours).toInt()
}

/** Сколько часов смены прошло к моменту [now], 0..duration. */
fun elapsedHours(shift: Shift, now: LocalDateTime): Double {
    val (start, end) = shiftWindow(shift) ?: return 0.0
    if (now.isBefore(start)) return 0.0
    if (!now.isBefore(end)) return java.time.Duration.between(start, end).toMinutes() / 60.0
    return java.time.Duration.between(start, now).toMinutes() / 60.0
}

/**
 * Заливка i-й банки (0..1): банка заполняется в течение своего часа.
 * Для 12-часовой смены через 3 часа залиты три банки целиком,
 * четвертая — на прошедшую долю часа.
 */
fun canFill(index: Int, elapsed: Double): Float =
    (elapsed - index).coerceIn(0.0, 1.0).toFloat()

/** Доля прошедшей смены, 0..1. */
fun shiftProgress(shift: Shift, now: LocalDateTime = LocalDateTime.now()): Float {
    if (shift.isDayOff) return 0f
    val (start, end) = shiftWindow(shift) ?: return 0f
    if (now.isBefore(start)) return 0f
    if (now.isAfter(end)) return 1f
    val total = java.time.Duration.between(start, end).seconds.toFloat()
    if (total <= 0f) return 0f
    return (java.time.Duration.between(start, now).seconds.toFloat() / total).coerceIn(0f, 1f)
}

/** Найти смену, идущую прямо сейчас (в том числе ночную, начавшуюся вчера). */
fun currentShift(months: List<MonthSummary>, now: LocalDateTime): Shift? {
    val all = months.flatMap { it.shifts }.filter { !it.isDayOff && it.startTime != null }
    return all.firstOrNull { shiftProgress(it, now) > 0f && shiftProgress(it, now) < 1f }
}

/**
 * Часы, отработанные в текущий момент: прогресс × длительность смены.
 * Из этого считаются живой заработок и проценты на карточке.
 */
fun workedHours(shift: Shift, now: LocalDateTime): Double =
    shiftDurationHours(shift).takeIf { it > 0 }?.let { it * shiftProgress(shift, now) } ?: 0.0

/**
 * Живой заработок: ставка × прогресс доли смены с ночной надбавкой
 * (та же формула, что у итогов месяца, но пропорционально прошедшему).
 */
fun liveEarnings(
    shift: Shift,
    now: LocalDateTime,
    threshold: LocalTime,
    nightEnd: LocalTime,
    percent: Double
): Double {
    if (shift.isDayOff || shift.rate <= 0.0) return 0.0
    val full = shiftEarnings(shift, threshold, nightEnd, percent, shift.breakMinutes)
    return full * shiftProgress(shift, now)
}

/** Начало следующей минуты — тик для перерисовки живых значений. */
fun nextMinuteTick(at: LocalDateTime): LocalDateTime =
    at.withSecond(0).withNano(0).plusMinutes(1)

/** Ближайшая полночь после [at] — чтобы перевернуть календарь на новый день. */
fun nextMidnight(at: LocalDateTime): LocalDateTime =
    at.toLocalDate().plusDays(1).atStartOfDay()

/** Тестовый хелпер: тик-строка до следующей минуты считается от переданного «сейчас». */
fun minuteTickDelayMs(now: LocalDateTime): Long =
    java.time.Duration.between(now, nextMinuteTick(now)).toMillis().coerceIn(0L, 60_000L)

/** «08:35» из LocalDateTime — подпись «обновлено в …». */
fun hhmm(value: LocalDateTime): String =
    "%02d:%02d".format(value.hour, value.minute)

/** Полночь текущего дня. */
fun startOfToday(now: LocalDateTime = LocalDateTime.now()): LocalDateTime =
    LocalDateTime.of(now.toLocalDate(), LocalTime.MIDNIGHT)