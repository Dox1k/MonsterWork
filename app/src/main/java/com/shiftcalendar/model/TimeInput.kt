package com.shiftcalendar.model

/**
 * Состояние одного поля времени: часы и минуты, каждая часть — своими цифрами.
 *
 * Так поле устроено потому, что клиент просил именно ручной ввод: пользователь
 * сам впечатывает «08» и «30», никаких кнопок «−/+» и никакой подстановки
 * нулей. Поэтому часть хранится СТРОКОЙ, а не числом: пустая строка — это
 * «пользователь стёр и ещё не ввёл», и она отличается от честного нуля «00».
 *
 * Пределы жёсткие: часы 0..24, минуты 0..60 (границы разрешены: 24:00 читается
 * как полночь, 60 минут клиент разрешил явно). Цифра, от которой часть вышла
 * бы за предел, просто не принимается — поле остаётся как было, без «прыжка»
 * на другое значение.
 */
data class TimeParts(
    /** Часы: «8», «08», «24» — или пусто, если стёрли. */
    val hour: String = "",
    /** Минуты: «5», «05», «30», «60» — или пусто, если стёрли. */
    val minute: String = ""
) {
    val hourEmpty: Boolean get() = hour.isEmpty()
    val minuteEmpty: Boolean get() = minute.isEmpty()

    /** Обе части введены — время можно считать настоящим. */
    val complete: Boolean get() = !hourEmpty && !minuteEmpty

    /**
     * Двумя цифрами для проверки предела и хранения: «8» → «08», «24» → «24».
     * Пустая часть даёт null — это и есть «поле не заполнено».
     */
    fun hourValue(): Int? = hour.takeIf { it.isNotEmpty() }?.toIntOrNull()
    fun minuteValue(): Int? = minute.takeIf { it.isNotEmpty() }?.toIntOrNull()

    /**
     * Текст стража: что именно не так с полем. null — всё в порядке.
     * Нужен, чтобы под полем можно было написать причину, а не просто покрасить.
     */
    fun validationError(): String? = when {
        hourEmpty -> "Впишите часы"
        minuteEmpty -> "Впишите минуты"
        (hourValue() ?: 0) > 24 -> "Часов не может быть больше 24"
        (minuteValue() ?: 0) > 60 -> "Минут не может быть больше 60"
        else -> null
    }
}

/**
 * Чистое ядро ввода времени без Android — на него опирается поле «ЧЧ:ММ»
 * и в редакторе дня, и в конструкторе паттерна.
 *
 * Правила, как их задал клиент:
 *  - часы и минуты пользователь впечатывает сам, подсказок и кнопок нет;
 *  - предел часов — 24, предел минут — 60; больше ввести нельзя;
 *  - стирание очищает разряд ЦЕЛИКОМ: было «16» — стало пусто, а не «00»;
 *  - пустым поле остаться не может: при уходе из редактора оно не сохранится,
 *    а пользователю показывается, чего не хватает.
 */
object TimeInput {

    /** Предел часов: «24» — полночь, дальше нельзя. */
    const val MAX_HOUR = 24

    /** Предел минут: клиент разрешил 60. */
    const val MAX_MINUTE = 60

    /** Разбор свободного текста в части; мусор даёт пустые части. */
    fun parts(text: String?): TimeParts {
        val clean = text?.trim().orEmpty()
        if (clean.isEmpty()) return TimeParts()
        val colon = clean.indexOf(':')
        val hourText: String
        val minuteText: String
        if (colon >= 0) {
            hourText = clean.substring(0, colon).filter { it.isDigit() }.take(2)
            minuteText = clean.substring(colon + 1).filter { it.isDigit() }.take(2)
        } else {
            val digits = clean.filter { it.isDigit() }.take(4)
            // Без разделителя режем пополам: «830» → часы «8», минуты «30»,
            // «2359» → «23» и «59». Одна цифра — это часы без минут.
            hourText = if (digits.length <= 2) digits else digits.take(digits.length - 2)
            minuteText = if (digits.length <= 2) "" else digits.takeLast(2)
        }
        return TimeParts(hourText.take(2), minuteText.take(2))
    }

    /**
     * Новая часть после того, как пользователь впечатал цифру [digit].
     * Свободный разряд — правый: «8» + «3» → «83» уже перебор для часов,
     * поэтому цифра прижимается так, чтобы часть осталась в пределе.
     */
    fun typeDigit(part: String, digit: Char, max: Int): String {
        if (!digit.isDigit()) return part
        val shifted = (part + digit).takeLast(2)
        val value = shifted.toIntOrNull() ?: return part
        if (value > max) return part       // предел — эта цифра не проходит
        return shifted.trimStart('0').ifEmpty { "0" }
    }

    /**
     * Стирание одного символа в части: чистим разряд целиком.
     * Стирание из «16» даёт пусто — ровно то, чего просил клиент; из «8» — тоже
     * пусто, без превращения в ноль.
     */
    fun backspace(part: String): String = ""

    /** Часть в виде «ЧЧ» для проверки и показа; пустая остаётся пустой. */
    fun twoDigits(part: String): String {
        val value = part.toIntOrNull() ?: return ""
        return "%02d".format(value.coerceIn(0, 99))
    }

    /** Готовое «ЧЧ:ММ», когда обе части введены; иначе частичными цифрами. */
    fun compose(parts: TimeParts): String {
        val h = parts.hour.ifEmpty { "" }
        val m = parts.minute.ifEmpty { "" }
        return if (h.isEmpty() && m.isEmpty()) "" else "$h:${if (m.isEmpty()) "" else m}"
    }
}

/**
 * Полное «ЧЧ:ММ» для хранения: обе части обязаны быть введены и в пределе.
 * null — поле заполнено не до конца или значение вне предела, сохранять нельзя.
 */
fun TimeParts.toTimeOrNull(): String? {
    if (validationError() != null) return null
    val h = hourValue() ?: return null
    val m = minuteValue() ?: return null
    return "%02d:%02d".format(h, m)
}

/** Свободный текст в части времени; «08:30», «830» и «8:3» читаются одинаково. */
fun parseTimeParts(text: String?): TimeParts = TimeInput.parts(text)

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
