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
import androidx.compose.ui.graphics.Color
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

/**
 * Ввод времени квадратами «[ЧЧ]:[ММ]» (решение клиента 11-Б) — общий компонент
 * для редактора дня и конструктора паттерна, чтобы правила ввода были ровно
 * одни и те же.
 *
 * Время пишется только руками, цифрами с клавиатуры: кнопок «−/+» и «0» рядом
 * с полем нет (решение клиента 11-В) — они мешали и путали, кому что двигают.
 *
 * Курсор — это выбранный разряд: 0 — часы, 1 — минуты. Нажатие на квадрат
 * меняет выбранный разряд, двоеточие статично и некликабельно. Цифры вводит
 * невидимая клавиатура ([BasicTextField]), логику переполнения и стирания
 * считает чистое ядро [TimeInput]: 16 + «8» → 18, 22 + «0» → 02, стирание
 * возвращает разряд к 00.
 *
 * @param invalid подсветка ошибки: «ЧЧ:ММ» введено, но интервал некорректен.
 */
@Composable
fun TimeField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    invalid: Boolean = false,
    focusKey: Any? = null
) {
    var active by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Значение приходит извне уже нормализованным; показываем его как есть,
    // а курсор ставим в конец, чтобы IME не сбивал наш разряд.
    var field by remember(focusKey) { mutableStateOf(TextFieldValue(TimeInput.parse(value), TextRange(5))) }
    LaunchedEffect(value, focusKey) {
        if (value != field.text) {
            field = TextFieldValue(TimeInput.parse(value), TextRange(5))
        }
    }

    fun emit(next: String, index: Int) {
        active = index
        onChange(next)
    }

    fun type(digit: Char) {
        val (next, index) = TimeInput.digit(field.text, active, digit)
        field = TextFieldValue(next, TextRange(5))
        emit(next, index)
    }

    fun erase() {
        val (next, index) = TimeInput.backspace(field.text, active)
        field = TextFieldValue(next, TextRange(5))
        emit(next, index)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TimeFieldLabel(label)
        Box(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        1.dp,
                        if (invalid) Palette.ShiftRed else Palette.Outline,
                        RoundedCornerShape(10.dp)
                    )
                    .background(Palette.CardBg)
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DigitSquare(TimeInput.hourText(field.text), active == 0) {
                    active = 0
                    focus.requestFocus()
                    keyboard?.show()
                }
                Text(
                    ":",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Palette.Ink,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
                DigitSquare(TimeInput.minuteText(field.text), active == 1) {
                    active = 1
                    focus.requestFocus()
                    keyboard?.show()
                }
            }
            // Невидимое поле ввода: держит клавиатуру и перехватывает цифры.
            BasicTextField(
                value = field,
                onValueChange = { newValue ->
                    val clean = newValue.text.filter { it.isDigit() }.take(4)
                    when {
                        clean.isEmpty() -> erase()
                        clean.length >= field.text.filter { it.isDigit() }.length -> type(clean.last())
                        else -> erase()
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done
                ),
                cursorBrush = SolidColor(Color.Transparent),
                textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
                modifier = Modifier
                    .matchParentSize()
                    .focusRequester(focus)
            )
        }
    }
}

/** Квадрат одного разряда: выбранный — залит, невыбранный — только обводка. */
@Composable
private fun DigitSquare(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 34.dp, height = 38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) Palette.Ink else Palette.WeekendTint)
            .border(1.dp, Palette.Outline, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) Palette.OnInk else Palette.Ink,
            textAlign = TextAlign.Center
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
