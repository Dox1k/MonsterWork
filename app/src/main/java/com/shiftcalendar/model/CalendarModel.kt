package com.shiftcalendar.model

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Русские подписи месяцев в именительном падеже — для ленты месяцев. */
val MONTH_NAMES = listOf(
    "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
    "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
)

/** Короткие подписи дней недели, неделя начинается с понедельника. */
val WEEKDAYS_SHORT = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

/** Рабочие интервалы по умолчанию. */
const val DAY_START = "08:00"
const val DAY_END = "20:00"
const val NIGHT_START = "20:00"
const val NIGHT_END = "08:00"
const val DEFAULT_SHIFT_HOURS = 12.0

/**
 * Разбор «HH:mm» в LocalTime без исключений: nullptr для мусора.
 * Пробел-разделитель и «8:5» тоже принимаются — ввод с клавиатуры.
 */
fun parseTimeOrNull(raw: String?): java.time.LocalTime? {
    val text = raw?.trim() ?: return null
    if (text.isEmpty()) return null
    val parts = text.replace(' ', ':').replace('.', ':').split(':')
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return java.time.LocalTime.of(h, m)
}

/** Привести ввод к «HH:mm» или вернуть null, если время некорректное. */
fun normalizeTime(raw: String?): String? =
    parseTimeOrNull(raw)?.let { "%02d:%02d".format(it.hour, it.minute) }

/**
 * Длительность смены в часах по интервалу. Ночной переход через полночь
 * (20:00–08:00) считается как 12 ч; равные концы (08:00–08:00) — НЕ сутки,
 * а некорректный интервал: для смены «на сутки» концы должны быть разными
 * (00:00–23:59 или 08:00–07:59). Поэтому возвращаем null.
 *
 * Прежнее поведение «равные концы = 24 ч» молча превращало опечатку
 * (начало=конец) в круглосуточную смену, что искажало часы и заработок.
 */
fun intervalHours(start: String?, end: String?): Double? {
    val s = parseTimeOrNull(start) ?: return null
    val e = parseTimeOrNull(end) ?: return null
    if (s == e) return null
    var minutes = java.time.Duration.between(s, e).toMinutes()
    if (minutes < 0) minutes += 1440
    return minutes / 60.0
}

/**
 * Невалидный интервал: оба конца введены, но начало совпадает с концом.
 * Используется для подсказки «проверьте время» рядом с полями ввода.
 */
fun isDegenerateInterval(start: String?, end: String?): Boolean {
    val s = parseTimeOrNull(start) ?: return false
    val e = parseTimeOrNull(end) ?: return false
    return s == e
}

/** Интервал, который подходит типу смены — для подстановки по умолчанию. */
fun defaultInterval(kind: com.shiftcalendar.data.ShiftKind): Pair<String, String> = when (kind) {
    com.shiftcalendar.data.ShiftKind.NIGHT -> NIGHT_START to NIGHT_END
    else -> DAY_START to DAY_END
}

/** Часы из строки «HH:mm» для сдвига «+»/«−» на один шаг. */
fun shiftTime(value: String?, deltaMinutes: Int, fallback: String): String {
    val base = parseTimeOrNull(value) ?: parseTimeOrNull(fallback) ?: java.time.LocalTime.MIDNIGHT
    val next = base.plusMinutes(deltaMinutes.toLong())
    return "%02d:%02d".format(next.hour, next.minute)
}

fun formatMoney(value: Double): String {
    val whole = value.toLong()
    val frac = Math.round((value - whole) * 100).toInt()
    val wholeStr = whole.toString().reversed().chunked(3).joinToString(" ").reversed()
    return if (frac == 0) "$wholeStr ₽" else "$wholeStr,${"%02d".format(frac)} ₽"
}

fun formatHours(value: Double): String {
    return if (value % 1.0 == 0.0) "${value.toInt()} ч" else "$value ч".replace('.', ',')
}

/** Полное имя месяца и год: «Октябрь 2026». */
fun monthTitle(ym: YearMonth): String = "${MONTH_NAMES[ym.monthValue - 1]} ${ym.year}"

/**
 * Ячейки сетки месяца: пустые места до первого числа (для выравнивания
 * по понедельникам) плюс все дни месяца.
 */
fun monthCells(ym: YearMonth): List<LocalDate?> {
    val first = ym.atDay(1)
    val leading = first.dayOfWeek.value - 1  // Пн = 0
    val cells = ArrayList<LocalDate?>(leading + ym.lengthOfMonth())
    repeat(leading) { cells.add(null) }
    for (d in 1..ym.lengthOfMonth()) cells.add(ym.atDay(d))
    while (cells.size % 7 != 0) cells.add(null)
    return cells
}

val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
fun LocalDate.iso(): String = format(ISO)
