package com.shiftcalendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiftcalendar.model.TimeInput
import com.shiftcalendar.model.TimeParts
import com.shiftcalendar.model.parseTimeParts

/**
 * Поле времени «ЧЧ:ММ», где часы и минуты — два самостоятельных поля.
 *
 * Чего в поле нет и почему (правки клиента):
 *  - нет кнопок «−/+» и «0» — время впечатывает только сам пользователь;
 *  - нет подстановки нулей: стёрли часы — поле часов пустое, а не «00»;
 *  - нельзя ввести часы больше 24 и минуты больше 60 — такая цифра просто не
 *    принимается, значение поля остаётся прежним;
 *  - пустым поле уйти не может: под полем написано, чего не хватает, а экран
 *    не сохранит смену, пока время не введено до конца.
 *
 * @param invalid внешняя подсветка ошибки (например, конец раньше начала).
 * @param onPartsChanged отдаёт текущие части — экран по ним знает, заполнено ли.
 */
@Composable
fun TimeField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    invalid: Boolean = false,
    focusKey: Any? = null,
    onPartsChanged: ((TimeParts) -> Unit)? = null
) {
    var parts by remember(focusKey) { mutableStateOf(parseTimeParts(value)) }
    // Значение пришло извне: смена дня, копирование смены, выбор профиля.
    LaunchedEffect(value, focusKey) {
        val fresh = parseTimeParts(value)
        if (fresh != parts) parts = fresh
    }
    LaunchedEffect(parts, focusKey) { onPartsChanged?.invoke(parts) }

    val error = parts.validationError()

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TimeFieldLabel(label)
        Row(verticalAlignment = Alignment.CenterVertically) {
            PartField(
                value = parts.hour,
                max = TimeInput.MAX_HOUR,
                hint = "24",
                invalid = error != null || invalid,
                onChange = { text ->
                    val next = parts.copy(hour = text)
                    parts = next
                    onChange("${next.hour}:${next.minute}")
                },
                modifier = Modifier.width(58.dp)
            )
            Text(
                ":",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Palette.Ink,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            PartField(
                value = parts.minute,
                max = TimeInput.MAX_MINUTE,
                hint = "60",
                invalid = error != null || invalid,
                onChange = { text ->
                    val next = parts.copy(minute = text)
                    parts = next
                    onChange("${next.hour}:${next.minute}")
                },
                modifier = Modifier.width(58.dp)
            )
        }
        // Пустое поле не молчит: под ним сказано, чего не хватает.
        if (error != null) {
            Text(error, fontSize = 10.sp, color = Palette.ShiftRed)
        }
    }
}

/**
 * Одно числовое поле на две цифры с жёстким пределом [max]: цифра, от которой
 * значение перевалило бы за предел, просто не принимается.
 */
@Composable
private fun PartField(
    value: String,
    max: Int,
    hint: String,
    invalid: Boolean,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Синхронизация наружу → внутрь: часть могли стереть снаружи или подставить
    // при копировании смены, поле обязано это показать.
    LaunchedEffect(value) {
        if (value != field.text) field = TextFieldValue(value, TextRange(value.length))
    }

    Box(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.CardBg)
            .border(
                1.dp,
                if (invalid) Palette.ShiftRed else Palette.Outline,
                RoundedCornerShape(10.dp)
            )
            .clickable {
                focus.requestFocus()
                keyboard?.show()
            },
        contentAlignment = Alignment.Center
    ) {
        // Подсказка-предел вместо нулей: пока поле пустое, видно «24» и «60».
        if (field.text.isEmpty()) {
            Text(hint, fontSize = 20.sp, color = Palette.Muted.copy(alpha = 0.45f))
        }
        BasicTextField(
            value = field,
            onValueChange = { raw ->
                val digits = raw.text.filter { it.isDigit() }
                val next = when {
                    // Стирание чистит поле целиком: «16» → пусто, а не «10» и не «00».
                    digits.isEmpty() -> ""
                    digits.length >= field.text.length -> {
                        val candidate = (field.text + digits.last()).takeLast(2)
                        val asNumber = candidate.toIntOrNull()
                        if (asNumber == null || asNumber > max) field.text else candidate
                    }
                    else -> ""
                }
                field = TextFieldValue(next, TextRange(next.length))
                onChange(next)
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Next
            ),
            cursorBrush = SolidColor(Palette.ShiftRed),
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Palette.Ink,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
        )
    }
}

/** Подпись над полем времени. */
@Composable
fun TimeFieldLabel(text: String) {
    Text(text, fontSize = 11.sp, color = Palette.Muted, fontWeight = FontWeight.Medium)
}

/**
 * Поле ввода обычного числа (ставка, надбавка, перерыв): держим общий стиль,
 * чтобы редактор дня и паттерн выглядели одинаково.
 */
@Composable
fun NumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
    suffix: String = "",
    allowDecimal: Boolean = false,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            val cleaned = raw.replace(',', '.')
                .filter { it.isDigit() || (allowDecimal && it == '.') }
                .take(9)
            onChange(cleaned.replace('.', ','))
        },
        label = { Text(label, color = Palette.Muted) },
        placeholder = if (placeholder.isEmpty()) null else {
            { Text(placeholder, color = Palette.Muted) }
        },
        suffix = if (suffix.isEmpty()) null else { { Text(suffix, color = Palette.Muted) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number
        ),
        singleLine = true,
        textStyle = TextStyle(fontSize = 14.sp, color = Palette.Ink),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Palette.ShiftRed,
            unfocusedBorderColor = Palette.Outline,
            focusedTextColor = Palette.Ink,
            unfocusedTextColor = Palette.Ink
        ),
        modifier = modifier.fillMaxWidth()
    )
}
