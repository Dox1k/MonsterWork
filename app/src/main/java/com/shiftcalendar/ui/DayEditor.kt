package com.shiftcalendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiftcalendar.data.ShiftKind
import com.shiftcalendar.model.*
import java.time.LocalDate

/**
 * Редактор дня — открывается тапом по дню календаря.
 * Внизу меню с сохранением и удалением, как в комментарии на эскизе:
 * «открывается менюшка где можно убрать или редачить смену».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayEditor(
    state: UiState,
    onKind: (ShiftKind) -> Unit,
    onDayOff: (Boolean) -> Unit,
    onStepHours: (Double) -> Unit,
    onRate: (Double) -> Unit,
    onNote: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val date = state.editingDate ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Palette.Paper
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                "${date.dayOfMonth} ${MONTH_NAMES[date.monthValue - 1].lowercase()} ${date.year}",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Palette.Ink
            )
            Spacer(Modifier.height(2.dp))
            Text(
                WEEKDAYS_SHORT[date.dayOfWeek.value - 1] + " · " +
                    if (state.editingShift == null) "новая запись" else "изменить запись",
                fontSize = 12.sp,
                color = Palette.Muted
            )
            Spacer(Modifier.height(18.dp))

            // Тип записи
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeChip("День", state.editingKind == ShiftKind.DAY && !state.editingDayOff, Palette.ShiftRed, Modifier.weight(1f)) {
                    onDayOff(false); onKind(ShiftKind.DAY)
                }
                TypeChip("Ночь", state.editingKind == ShiftKind.NIGHT && !state.editingDayOff, Palette.ShiftRed, Modifier.weight(1f)) {
                    onDayOff(false); onKind(ShiftKind.NIGHT)
                }
                TypeChip("Выходной", state.editingDayOff, Palette.DayOffGreen, Modifier.weight(1f)) {
                    onDayOff(true)
                }
            }

            Spacer(Modifier.height(10.dp))
            val interval = when {
                state.editingDayOff -> "Отмечено как выходной"
                state.editingKind == ShiftKind.DAY -> "Интервал $DAY_START – $DAY_END"
                state.editingKind == ShiftKind.NIGHT -> "Интервал $NIGHT_START – $NIGHT_END"
                else -> "Время не задано — считаются только часы"
            }
            Text(interval, fontSize = 12.sp, color = Palette.Muted)

            if (!state.editingDayOff) {
                Spacer(Modifier.height(20.dp))
                SectionTitle("Часы смены")
                Spacer(Modifier.height(10.dp))
                // Ряд как на эскизе: «СМЕНА / 8 ч / + −»
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Palette.CardBg)
                        .border(1.dp, Palette.Outline, RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StepperButton("−") { onStepHours(-1.0) }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("СМЕНА", fontSize = 10.sp, color = Palette.Muted, fontWeight = FontWeight.Medium)
                        Text(formatHours(state.editingHours), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                    }
                    StepperButton("+") { onStepHours(1.0) }
                }

                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(8.0, 12.0, 16.0, 24.0).forEach { h ->
                        SmallChip(
                            label = if (h == 24.0) "сутки" else "${h.toInt()} ч",
                            selected = state.editingHours == h,
                            modifier = Modifier.weight(1f)
                        ) { onStepHours(h - state.editingHours) }
                    }
                }

                Spacer(Modifier.height(22.dp))
                SectionTitle("Ставка за час")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = if (state.editingRate == 0.0) "" else trimNum(state.editingRate),
                    onValueChange = { txt ->
                        onRate(txt.replace(',', '.').filter { c -> c.isDigit() || c == '.' }.toDoubleOrNull() ?: 0.0)
                    },
                    placeholder = { Text("например 500", color = Palette.Muted) },
                    suffix = { Text("₽/ч", color = Palette.Muted) },
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

                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Palette.ShiftRed.copy(alpha = 0.08f))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Заработок за смену", fontSize = 13.sp, color = Palette.Ink)
                    Text(
                        formatMoney(state.editingHours * state.editingRate),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Palette.ShiftRed
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            SectionTitle("Заметка")
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.editingNote,
                onValueChange = onNote,
                placeholder = { Text("объект, напарник, примечание", color = Palette.Muted) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Palette.ShiftRed,
                    unfocusedBorderColor = Palette.Outline,
                    focusedTextColor = Palette.Ink,
                    unfocusedTextColor = Palette.Ink
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(26.dp))
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Ink),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Сохранить", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
            if (state.editingShift != null) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onDelete,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.ShiftRed),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("Удалить запись", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun trimNum(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else v.toString().replace('.', ',')

@Composable
private fun SectionTitle(text: String) {
    Text(text.uppercase(), fontSize = 10.sp, color = Palette.Muted, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun StepperButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Palette.Ink)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TypeChip(
    label: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accent else Palette.CardBg)
            .border(1.dp, if (selected) accent else Palette.Outline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Color.White else Palette.Ink,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun SmallChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Palette.Ink else Palette.CardBg)
            .border(1.dp, if (selected) Palette.Ink else Palette.Outline, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) Color.White else Palette.Ink, fontSize = 13.sp)
    }
}
