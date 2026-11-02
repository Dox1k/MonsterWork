package com.shiftcalendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import android.app.TimePickerDialog
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiftcalendar.data.Shift
import com.shiftcalendar.data.ShiftKind
import com.shiftcalendar.model.*
import java.time.LocalDate

/**
 * Редактор дня — открывается тапом по дню календаря.
 *
 * День может содержать несколько независимых смен. Список сверху показывает все
 * смены дня своим цветом (первая — красный, вторая — синий, третья — фиолетовый…),
 * тап по смене открывает её на правку, «+ Смена» — новую смену того же дня.
 * Внизу меню с сохранением и удалением; в полях времени есть перерыв:
 * время перерыва вычитается из времени смены (9 ч − 1 ч = 8 ч).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayEditor(
    state: UiState,
    onDayOff: (Boolean) -> Unit,
    onRate: (Double) -> Unit,
    onNote: (String) -> Unit,
    onKind: (ShiftKind) -> Unit,
    onStartText: (String) -> Unit,
    onEndText: (String) -> Unit,
    onPickStart: (String) -> Unit,
    onPickEnd: (String) -> Unit,
    onNudgeStart: (Int) -> Unit,
    onNudgeEnd: (Int) -> Unit,
    onBreak: (Int) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    onSelectShift: (Long) -> Unit = {},
    onNewShift: () -> Unit = {},
    onClearDay: () -> Unit = {},
    onClearError: () -> Unit = {}
) {
    val date = state.editingDate ?: return
    val existing = state.editingShift
    val isDayOff = state.editingDayOff
    val day = state.editingDay
    val slots = remember(state.editingDayShifts) { shiftSlots(state.editingDayShifts) }
    val dayHasWork = day.workCount > 0
    // Живой расчёт: сколько выйдет за смену при текущих вводных.
    val preview = remember(state.editingStartTime, state.editingEndTime, state.editingRate, state.editingBreakMinutes, state.nightThreshold, state.nightEnd, state.nightPremiumPercent, isDayOff) {
        val start = normalizeTime(state.editingStartTime)
        val end = normalizeTime(state.editingEndTime)
        if (isDayOff || start == null || end == null) null
        else {
            val probe = Shift(
                date = date.iso(), kind = "CUSTOM", hours = 0.0,
                startTime = start, rate = state.editingRate,
                isDayOff = false, endTime = end, breakMinutes = state.editingBreakMinutes
            )
            shiftEarnings(
                probe,
                runCatching { java.time.LocalTime.parse(state.nightThreshold) }.getOrDefault(java.time.LocalTime.of(22, 0)),
                runCatching { java.time.LocalTime.parse(state.nightEnd) }.getOrDefault(java.time.LocalTime.of(6, 0)),
                state.nightPremiumPercent,
                state.editingBreakMinutes
            )
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Palette.Paper,
        titleContentColor = Palette.Ink,
        textContentColor = Palette.Ink,
        title = {
            Column {
                Text(dayTitle(date), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Palette.Ink)
                // Итог дня: «2 смены · 14 ч» — сразу видно, что смен несколько.
                Text(
                    buildString {
                        if (day.isEmpty) append("смен пока нет")
                        else {
                            append(day.workCount).append(if (day.workCount == 1) " смена" else " смены")
                            if (day.hours > 0) append(" · ").append(formatHours(day.hours))
                            if (day.dayOff) append(if (dayHasWork) " · выходной" else " · выходной")
                        }
                    },
                    fontSize = 11.sp, color = Palette.Muted
                )
                if (existing != null && existing.profileId > 0) {
                    Text(
                        "проставлено профилем — правки сохранятся поверх",
                        fontSize = 11.sp, color = Palette.Muted
                    )
                }
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // Список смен дня: каждая своим цветом, тап — правка.
                if (state.editingDayShifts.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.editingDayShifts.forEach { shift ->
                            DayShiftRow(
                                shift = shift,
                                index = slots[shift.id] ?: 0,
                                isDayOff = shift.isDayOff,
                                selected = existing?.id == shift.id,
                                onClick = { onSelectShift(shift.id) }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (dayHasWork) {
                        SmallAction(
                            "＋ Смена",
                            selected = existing == null,
                            modifier = Modifier.weight(1f),
                            onClick = onNewShift
                        )
                    }
                    if (state.editingDayShifts.isNotEmpty()) {
                        SmallAction(
                            "Очистить день",
                            selected = false,
                            modifier = Modifier.weight(1f),
                            danger = true,
                            onClick = onClearDay
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                // День / Ночь / Выходной — «чипы» в цветах темы, не слепящие белый
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChipChoice("День", !isDayOff && state.editingKind != ShiftKind.NIGHT, Modifier.weight(1f)) { onKind(ShiftKind.DAY) }
                    ChipChoice("Ночь", !isDayOff && state.editingKind == ShiftKind.NIGHT, Modifier.weight(1f)) { onKind(ShiftKind.NIGHT) }
                    ChipChoice("Выходной", isDayOff, Modifier.weight(1f)) { onDayOff(!isDayOff) }
                }

                if (isDayOff && dayHasWork) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "выходной заменит смены этого дня — в дне остаётся что-то одно",
                        fontSize = 10.sp, color = Palette.ShiftRed
                    )
                }

                if (!isDayOff) {
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TimeField(
                            label = "Начало",
                            value = state.editingStartTime,
                            onChange = onStartText,
                            onPick = onPickStart,
                            onNudge = onNudgeStart,
                            modifier = Modifier.weight(1f)
                        )
                        TimeField(
                            label = "Конец",
                            value = state.editingEndTime,
                            onChange = onEndText,
                            onPick = onPickEnd,
                            onNudge = onNudgeEnd,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    // Перерыв: −5/+5 минут; вычитается из длины смены
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Palette.CardBg)
                            .border(1.dp, Palette.Outline, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Перерыв", fontSize = 13.sp, color = Palette.Ink, fontWeight = FontWeight.Medium)
                            Text(
                                "вычитается из времени смены",
                                fontSize = 10.sp, color = Palette.Muted
                            )
                        }
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(Palette.WeekendTint).clickable { onBreak(state.editingBreakMinutes - 5) },
                            contentAlignment = Alignment.Center
                        ) { Text("−", fontSize = 16.sp, color = Palette.Ink, fontWeight = FontWeight.Bold) }
                        Text(
                            if (state.editingBreakMinutes > 0) "${state.editingBreakMinutes} мин" else "нет",
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.Ink,
                            modifier = Modifier.widthIn(min = 62.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(Palette.WeekendTint).clickable { onBreak(state.editingBreakMinutes + 5) },
                            contentAlignment = Alignment.Center
                        ) { Text("+", fontSize = 16.sp, color = Palette.Ink, fontWeight = FontWeight.Bold) }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        buildString {
                            append("Смена: ")
                            val base = previewHoursCompat(state)
                            append(if (base != null) formatHours(base) else "проверьте ЧЧ:ММ")
                            if (state.editingBreakMinutes > 0) {
                                append(" − перерыв ${state.editingBreakMinutes} мин = ")
                                append(formatHours(((base ?: 0.0) - state.editingBreakMinutes / 60.0).coerceAtLeast(0.0)))
                            }
                        },
                        fontSize = 11.sp,
                        color = if (previewHoursCompat(state) == null) Palette.ShiftRed else Palette.Muted
                    )
                    // Предупреждение о наложении: две смены дня идут одновременно.
                    val overlaps = remember(state.editingDayShifts, state.editingStartTime, state.editingEndTime, existing) {
                        val probe = probeShift(state, date)
                        val others = state.editingDayShifts.filter { it.id != (existing?.id ?: -1L) }
                        probe != null && others.any { MultiShift.overlaps(it, probe) }
                    }
                    if (overlaps) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "накладывается на другую смену этого дня",
                            fontSize = 10.sp, color = Palette.ShiftRed
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                TimeFieldLabel("Ставка, ₽/час")
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = if (state.editingRate == 0.0) "" else if (state.editingRate % 1.0 == 0.0) state.editingRate.toLong().toString() else state.editingRate.toString().replace('.', ','),
                    onValueChange = { txt -> onRate(txt.replace(',', '.').filter { c -> c.isDigit() || c == '.' }.toDoubleOrNull() ?: 0.0) },
                    placeholder = { Text("например 500", color = Palette.Muted) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Palette.ShiftRed,
                        unfocusedBorderColor = Palette.Outline,
                        focusedTextColor = Palette.Ink,
                        unfocusedTextColor = Palette.Ink
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Живой предпросмотр денег с надбавкой
                if (preview != null && state.editingRate > 0.0) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Palette.DayOffGreen.copy(alpha = 0.08f))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text("Выйдёт за смену", fontSize = 10.sp, color = Palette.Muted, fontWeight = FontWeight.SemiBold)
                            Text(
                                formatMoney(preview) + premiumSuffix(state),
                                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Palette.Ink
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                TimeFieldLabel("Заметка")
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = state.editingNote,
                    onValueChange = onNote,
                    placeholder = { Text("необязательно", color = Palette.Muted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Palette.ShiftRed,
                        unfocusedBorderColor = Palette.Outline,
                        focusedTextColor = Palette.Ink,
                        unfocusedTextColor = Palette.Ink
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Причина отказа сохранения (пустое время, дубликат смены).
                state.editorError?.let { error ->
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Palette.ShiftRed.copy(alpha = 0.10f))
                            .clickable(onClick = onClearError)
                            .padding(10.dp)
                    ) {
                        Text(error, fontSize = 11.sp, color = Palette.ShiftRed, fontWeight = FontWeight.Medium)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                colors = accentButtonColors(),
                shape = RoundedCornerShape(12.dp)
            ) { Text(if (existing == null) "Добавить" else "Сохранить") }
        },
        dismissButton = {
            Row {
                if (existing != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = Palette.ShiftRed)
                    ) { Text("Удалить") }
                }
                TextButton(
                onClick = onClose,
                colors = ButtonDefaults.textButtonColors(contentColor = Palette.Ink)
            ) { Text("Закрыть", color = Palette.Ink) }
            }
        }
    )
}

/** Строка смены в списке дня: цвет-слот, время, часы, заметка. */
@Composable
private fun DayShiftRow(
    shift: Shift,
    index: Int,
    isDayOff: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accent = ShiftSlotColors.colorFor(index, isDayOff)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accent.copy(alpha = 0.12f) else Palette.CardBg)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) accent else Palette.Outline,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (isDayOff) "выходной"
                else "${shift.startTime ?: "—"}–${shift.endTime ?: "?"}",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Palette.Ink
            )
            Text(
                buildString {
                    if (!isDayOff) append(formatHours(shift.hours))
                    if (shift.breakMinutes > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("перерыв ${shift.breakMinutes} мин")
                    }
                    if (shift.note.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(shift.note)
                    }
                    if (isEmpty()) append(if (isDayOff) "день отдыха" else "без времени")
                },
                fontSize = 10.sp,
                color = Palette.Muted,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        if (selected) {
            Text("✎", fontSize = 13.sp, color = accent)
        }
    }
}

/** Маленькая кнопка-действие рядом со списком смен. */
@Composable
private fun SmallAction(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val accent = if (danger) Palette.ShiftRed else Palette.Ink
    Box(
        modifier
            .height(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) accent.copy(alpha = 0.14f) else Palette.CardBg)
            .border(1.dp, if (selected) accent else Palette.Outline, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 12.sp, color = accent, fontWeight = FontWeight.Medium)
    }
}

/** Смена по текущим полям редактора — для проверки наложения на другие смены дня. */
private fun probeShift(state: UiState, date: LocalDate): Shift? {
    if (state.editingDayOff) return null
    val start = normalizeTime(state.editingStartTime) ?: return null
    val end = normalizeTime(state.editingEndTime) ?: return null
    return Shift(
        id = state.editingShift?.id ?: 0L,
        date = date.iso(), kind = "CUSTOM", hours = 0.0,
        startTime = start, rate = state.editingRate,
        isDayOff = false, endTime = end, breakMinutes = state.editingBreakMinutes
    )
}

private fun previewHoursCompat(state: UiState): Double? =
    intervalHours(normalizeTime(state.editingStartTime), normalizeTime(state.editingEndTime))

/** « +20% ночь» рядом с суммой, когда в смену попадает окно надбавки. */
private fun premiumSuffix(state: UiState): String =
    if (state.nightPremiumPercent > 0.0) "  (надбавка ${formatPercent(state.nightPremiumPercent)}%)" else ""

private fun dayTitle(date: LocalDate): String {
    val m = listOf("января","февраля","марта","апреля","мая","июня","июля","августа","сентября","октября","ноября","декабря")[date.monthValue - 1]
    val d = listOf("понедельник","вторник","среда","четверг","пятница","суббота","воскресенье")[date.dayOfWeek.value - 1]
    return "${date.dayOfMonth} $m · $d"
}

/** Поле времени с −/+ 30 мин и диалогом-часами. */
@Composable
private fun TimeField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    onPick: (String) -> Unit,
    onNudge: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val parsed = parseTimeOrNull(value)
    Column(modifier) {
        Text(label.uppercase(), fontSize = 10.sp, color = Palette.Muted, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = { Text("ЧЧ:ММ", color = Palette.Muted) },
            isError = parsed == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.Ink
            ),
            trailingIcon = {
                Text(
                    "⏱", fontSize = 15.sp, color = Palette.Muted,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            TimePickerDialog(
                                context,
                                { _, h, m -> onPick("%02d:%02d".format(h, m)) },
                                parsed?.hour ?: 8, parsed?.minute ?: 0, true
                            ).show()
                        }
                        .padding(6.dp)
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Palette.ShiftRed,
                unfocusedBorderColor = Palette.Outline,
                errorBorderColor = Palette.ShiftRed,
                focusedTextColor = Palette.Ink,
                unfocusedTextColor = Palette.Ink
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NudgeButton("−30", Modifier.weight(1f)) { onNudge(-30) }
            NudgeButton("+30", Modifier.weight(1f)) { onNudge(30) }
        }
    }
}

@Composable
private fun TimeFieldLabel(text: String) {
    Text(text.uppercase(), fontSize = 10.sp, color = Palette.Muted, fontWeight = FontWeight.SemiBold)
}

/** Чип выбора: выбран — Ink-фон/OnInk-текст; не выбран — CardBg. Никаких белых полей. */
@Composable
private fun ChipChoice(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Palette.Ink else Palette.CardBg)
            .border(
                1.dp,
                if (selected) Palette.Ink else Palette.Outline,
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Palette.OnInk else Palette.Ink
        )
    }
}

@Composable
private fun NudgeButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Palette.CardBg)
            .border(1.dp, Palette.Outline, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 12.sp, color = Palette.Ink)
    }
}
