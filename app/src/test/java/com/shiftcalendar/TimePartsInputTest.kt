package com.shiftcalendar

import com.shiftcalendar.model.*
import com.shiftcalendar.ui.isEmptyInterval
import com.shiftcalendar.ui.sanitizeTimeInput
import org.junit.Assert.*
import org.junit.Test

/**
 * Ввод времени вручную: часы и минуты набирает пользователь, предела 24 и 60,
 * стирание очищает разряд целиком, пустое поле не сохраняется.
 *
 * Тесты держат именно те правила, которые задал клиент, — чтобы следующая
 * правка поля снова не подставила нули «для удобства».
 */
class TimePartsInputTest {

    /** Набрать время посимвольно, как это делает пользователь. */
    private fun type(sequence: String): String {
        val colon = sequence.indexOf(':')
        var h = ""
        var m = ""
        sequence.forEachIndexed { i, c ->
            if (!c.isDigit()) return@forEachIndexed
            if (colon < 0 || i < colon) h = TimeInput.typeDigit(h, c, TimeInput.MAX_HOUR)
            else m = TimeInput.typeDigit(m, c, TimeInput.MAX_MINUTE)
        }
        return "$h:$m"
    }

    @Test
    fun `часы и минуты набираются цифрами`() {
        assertEquals("8:0", type("8:0"))
        // Ведущий ноль срезается: «08» — это то же время, что «8».
        assertEquals("8:30", type("08:30"))
        assertEquals("23:59", type("23:59"))
        // Одиночная цифра остаётся одиночной: «0» — это ноль, а не «00».
        assertEquals("0:5", type("0:5"))
    }

    @Test
    fun `предел часов 24 соблюдается`() {
        assertEquals("24", TimeInput.typeDigit("2", '4', TimeInput.MAX_HOUR))
        // 2 -> 25 перебор: цифра не принимается, поле остаётся «2».
        assertEquals("2", TimeInput.typeDigit("2", '5', TimeInput.MAX_HOUR))
        // из «24» дальше нельзя ничего
        assertEquals("24", TimeInput.typeDigit("24", '1', TimeInput.MAX_HOUR))
        assertEquals("24", TimeInput.typeDigit("24", '9', TimeInput.MAX_HOUR))
    }

    @Test
    fun `предел минут 60 соблюдается`() {
        assertEquals("60", TimeInput.typeDigit("6", '0', TimeInput.MAX_MINUTE))
        assertEquals("6", TimeInput.typeDigit("6", '1', TimeInput.MAX_MINUTE))
        assertEquals("59", TimeInput.typeDigit("5", '9', TimeInput.MAX_MINUTE))
    }

    @Test
    fun `стирание очищает разряд целиком`() {
        assertEquals("", TimeInput.backspace("16"))
        assertEquals("", TimeInput.backspace("0"))
        assertEquals("", TimeInput.backspace("08"))
    }

    @Test
    fun `пустое поле отличается от нуля`() {
        val empty = TimeParts(hour = "", minute = "30")
        assertTrue(empty.hourEmpty)
        assertFalse(empty.complete)
        assertNull(empty.toTimeOrNull())
        assertEquals("Впишите часы", empty.validationError())

        val bothEmpty = TimeParts("", "")
        assertEquals("Впишите часы", bothEmpty.validationError())

        val ok = TimeParts("08", "30")
        assertTrue(ok.complete)
        assertEquals("08:30", ok.toTimeOrNull())
        assertNull(ok.validationError())
    }

    @Test
    fun `значения выше предела не сохраняются и объясняются`() {
        assertEquals("Часов не может быть больше 24", TimeParts("25", "00").validationError())
        assertEquals("Минут не может быть больше 60", TimeParts("08", "61").validationError())
        assertNull(TimeParts("25", "00").toTimeOrNull())
        assertNull(TimeParts("08", "61").toTimeOrNull())
        // Верхняя граница разрешена: 24:00 и 60 минут клиент разрешил явно.
        assertEquals("24:00", TimeParts("24", "0").toTimeOrNull())
        assertEquals("08:60", TimeParts("8", "60").toTimeOrNull())
    }

    @Test
    fun `разбор принимает время в разных формах`() {
        assertEquals(TimeParts("23", "59"), parseTimeParts("2359"))
        // «08:30», «830» — одно и то же время; минуты при хранении дополняются нулём.
        assertEquals("08:30", parseTimeParts("830").toTimeOrNull())
        assertEquals("08:03", parseTimeParts("8:3").toTimeOrNull())
        assertEquals(parseTimeParts("08:30").hourValue(), parseTimeParts("830").hourValue())
        // Одна цифра без двоеточия — это часы без минут, поле не заполнено.
        assertFalse(parseTimeParts("8").complete)
        assertTrue(parseTimeParts(null).hourEmpty)
        assertTrue(parseTimeParts("").hourEmpty)
        // Мусор не превращается в нули — поле просто пустое.
        assertTrue(parseTimeParts("abc").hourEmpty)
    }

    @Test
    fun `пустой интервал распознаётся`() {
        assertTrue(isEmptyInterval("", ""))
        assertTrue(isEmptyInterval(":", ""))
        assertFalse(isEmptyInterval("08:00", ""))
    }

    @Test
    fun `поле фильтрует нецифровые символы`() {
        assertEquals("09:30", sanitizeTimeInput("09:30"))
        assertEquals("", sanitizeTimeInput("ab"))
    }
}
