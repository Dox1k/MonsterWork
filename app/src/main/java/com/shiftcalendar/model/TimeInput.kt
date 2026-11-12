package com.shiftcalendar.model

/**
 * Чистое ядро ввода времени «ЧЧ:ММ» (решение клиента 11-Б).
 *
 * Правила, как их задал клиент:
 *  - часов не больше двух цифр, минут не больше двух;
 *  - ввод «1» → «01», «1» затем «6» → «16»;
 *  - если новая цифра даёт часы больше 24, меняется ВТОРАЯ цифра:
 *    16 + «8» → 18, 16 + «2» → 12, 12 + «2» → 22, 22 + «0» → 02;
 *  - очистка полей работает адекватно: стирание возвращает поле к «00».
 *
 * Курсор мыслится справа: каждая цифра «дописывается» в правый разряд,
 * старая правая цифра при этом сдвигается влево и при переполнении теряется.
 * Модель без Android, поэтому проверяется обычными JUnit-тестами.
 */
object TimeInput {

    /** Часы из двух цифр: больше 24 не бывает — 25…99 прижимаем к 24. */
    private fun clampHour(h: Int): Int = h.coerceIn(0, 24)

    /** Минуты из двух цифр: больше 59 не бывает. */
    private fun clampMinute(m: Int): Int = m.coerceIn(0, 59)

    /**
     * Разбор свободного текста в «ЧЧ:ММ» по правилам цифровой прокрутки.
     * Понимает «16:05», «16:5», «1605» — что бы ни пришло из поля ввода.
     */
    fun parse(text: String): String {
        val digits = text.filter { it.isDigit() }
        val colon = text.indexOf(':')
        var hourText: String
        var minuteText: String
        if (colon >= 0) {
            hourText = text.substring(0, colon).filter { it.isDigit() }.take(2)
            minuteText = text.substring(colon + 1).filter { it.isDigit() }.take(2)
        } else {
            // Без разделителя первая цифра (до двух) — часы, остальное — минуты.
            hourText = digits.take(if (digits.length <= 2) digits.length else 2)
            minuteText = digits.drop(hourText.length).take(2)
            if (digits.length == 3) {
                hourText = digits.take(1)
                minuteText = digits.drop(1)
            }
        }
        val hour = clampHour(hourText.toIntOrNull() ?: 0)
        val minute = clampMinute(minuteText.toIntOrNull() ?: 0)
        return "%02d:%02d".format(hour, minute)
    }

    /** Одна цифра, поступившая от клавиатуры. */
    private fun digitOf(c: Char): Int? =
        (c - '0').takeIf { c.isDigit() && it in 0..9 }

    /**
     * Ввод цифры в позицию [index] (0 — часы, 1 — минуты).
     * Новая цифра встаёт в правый разряд, старая сдвигается влево.
     * Возвращает «ЧЧ:ММ» и позицию, которая остаётся редактируемой.
     */
    fun digit(current: String, index: Int, digit: Char): Pair<String, Int> {
        val visible = parse(current)
        val d = digitOf(digit) ?: return visible to index
        return if (index == 0) {
            val h = (visible.substring(0, 1).toIntOrNull() ?: 0) * 10 + d
            "%02d:%s".format(clampHour(h), visible.substring(3)) to 0
        } else {
            val m = (visible.substring(3, 4).toIntOrNull() ?: 0) * 10 + d
            "%s:%02d".format(visible.substring(0, 2), clampMinute(m)) to 1
        }
    }

    /**
     * Стирание одной цифры в позиции [index].
     * «Стирание возвращает поле к 00»: цифра сдвигается влево, свободный
     * правый разряд заполняется нулём. 16 → 10, 5 → 0 (то есть «00»).
     */
    fun backspace(current: String, index: Int): Pair<String, Int> {
        val visible = parse(current)
        return if (index == 0) {
            val h = visible.substring(1, 2).toIntOrNull() ?: 0
            "%02d:%s".format(clampHour(h), visible.substring(3)) to 0
        } else {
            val m = visible.substring(4, 5).toIntOrNull() ?: 0
            "%s:%02d".format(visible.substring(0, 2), clampMinute(m)) to 1
        }
    }

    /** Часы «ЧЧ» для отрисовки квадратами. */
    fun hourText(value: String): String = parse(value).substring(0, 2)

    /** Минуты «ММ» для отрисовки квадратами. */
    fun minuteText(value: String): String = parse(value).substring(3, 5)
}

/**
 * Округление до копеек (2 знака) — «half up», как ждёт бухгалтерия клиента:
 * 14.005 → 14.01, 14.994 → 14.99, 1067.0 → 1067.0.
 *
 * Вариант `floor(v*100)/(100.0+1e-6)` не годится: он портит целые значения
 * (14.0 → 13.99999986), поэтому здесь только прибавление половины копейки.
 */
fun roundMoney(value: Double): Double {
    if (!value.isFinite()) return 0.0
    return Math.floor(value * 100.0 + 0.5) / 100.0
}

/**
 * Денежная сумма для UI: две копейки всегда, разделитель разрядов —
 * узкий пробел, знак рубля — «Р» без пробела:
 * 15.17 → «15.17Р», 1067.0 → «1 067.00Р», 3.5 → «3.50Р».
 */
fun moneyText(value: Double): String {
    val rounded = roundMoney(value)
    val negative = rounded < 0.0
    val absolute = Math.abs(rounded)
    val kopecks = Math.round(absolute * 100.0)
    val whole = kopecks / 100
    val frac = (kopecks % 100).toInt()
    val grouped = whole.toString().reversed().chunked(3).joinToString("\u00A0").reversed()
    val sign = if (negative) "−" else ""
    return "$sign$grouped.%02dР".format(frac)
}

/**
 * Часы в формате денег: целые — без дробной части («12ч»),
 * дробные — до копеек («7.50ч»). Нужны там, где часы суммируются с деньгами.
 */
fun hoursText(value: Double): String {
    val rounded = roundMoney(value)
    val kopecks = Math.round(rounded * 100.0)
    if (kopecks % 100L == 0L) return "${kopecks / 100}ч"
    return "${kopecks / 100}.%02dч".format((kopecks % 100).toInt())
}
