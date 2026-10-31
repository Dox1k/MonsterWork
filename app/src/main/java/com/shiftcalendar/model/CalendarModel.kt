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
