package com.shiftcalendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiftcalendar.data.ShiftKind
import com.shiftcalendar.model.*
import java.time.LocalDate

/**
 * Конструктор паттерна смен: «раб/раб/раб/раб/вых/вых» и так до конца
 * следующего года. Можно сменить порядок, задать часы и ставку, выбрать
 * способ привязки к календарю и посмотреть предпросмотр перед записью.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatternScreen(
    state: UiState,
    onClose: () -> Unit,
    onPreset: (List<PatternStep>) -> Unit,
    onAdd: (Boolean) -> Unit,
    onRemove: (Int) -> Unit,
    onUpdate: (Int, PatternStep) -> Unit,
    onAllRates: (Double) -> Unit,
    onStart: (LocalDate) -> Unit,
    onThreshold: (String) -> Unit,
    onPercent: (Double) -> Unit,
    onUntil: (LocalDate) -> Unit,
    onAlign: (Boolean) -> Unit,
    onKeep: (Boolean) -> Unit,
    onPreview: () -> Unit,
    onApply: () -> Unit
) {
    Scaffold(
        containerColor = Palette.Paper,
        topBar = {
            TopAppBar(
                title = { Text("Паттерн смен", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    TextButton(onClick = onClose) { Text("Закрыть", color = Palette.Ink) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Palette.Paper)
            )
        },
        bottomBar = {
            Column(Modifier.padding(16.dp)) {
                Button(
                    onClick = onApply,
                    enabled = state.patternSteps.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Ink),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("Проставить в календарь", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // 1. Готовые шаблоны
            Label("Быстрый выбор")
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PRESET_PATTERNS) { (name, steps) ->
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Palette.CardBg)
                            .border(1.dp, Palette.Outline, RoundedCornerShape(12.dp))
                            .clickable { onPreset(steps) }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(name, fontSize = 13.sp, color = Palette.Ink)
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            // 2. Рисунок цикла
            Label("Порядок цикла (${state.patternSteps.size} шагов)")
            Spacer(Modifier.height(4.dp))
            Text(
                "Нажмите на шаг, чтобы поменять тип, часы или ставку",
                fontSize = 11.sp,
                color = Palette.Muted
            )
            Spacer(Modifier.height(10.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Palette.CardBg)
                    .border(1.dp, Palette.Outline, RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                state.patternSteps.forEachIndexed { i, step ->
                    PatternStepRow(
                        index = i,
                        step = step,
                        onChange = { onUpdate(i, it) },
                        onRemove = { onRemove(i) }
                    )
                    if (i != state.patternSteps.lastIndex) Spacer(Modifier.height(8.dp))
                }
                if (state.patternSteps.isEmpty()) {
                    Text("Цикл пуст — добавьте шаги", fontSize = 12.sp, color = Palette.Muted)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onAdd(false) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("+ смена", fontSize = 13.sp, color = Palette.Ink) }
                    OutlinedButton(
                        onClick = { onAdd(true) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("+ выходной", fontSize = 13.sp, color = Palette.Ink) }
                }
            }

            Spacer(Modifier.height(14.dp))
            // Одна ставка на весь цикл
            Label("Ставка для всех смен цикла")
            Spacer(Modifier.height(8.dp))
            RateField(
                value = state.patternSteps.firstOrNull { !it.isDayOff }?.rate ?: 0.0,
                onChange = onAllRates
            )

            Spacer(Modifier.height(22.dp))

            // 3. Диапазон
            Label("На какой срок")
            Spacer(Modifier.height(8.dp))
            DateRow("Начало цикла", state.patternStart) { onStart(it) }
            Spacer(Modifier.height(8.dp))
            var showStartPicker by remember { mutableStateOf(false) }
            var showThresholdPicker by remember { mutableStateOf(false) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showStartPicker = true }) { Text("Выбрать дату начала") }
                OutlinedButton(onClick = { showThresholdPicker = true }) { Text("Порог ночи: ${state.nightThreshold}") }
            }
            if (showStartPicker) {
                val context = LocalContext.current
                LaunchedEffect(Unit) {
                    android.app.DatePickerDialog(context, { _, y, m, d -> onStart(LocalDate.of(y, m + 1, d)); showStartPicker = false }, state.patternStart.year, state.patternStart.monthValue - 1, state.patternStart.dayOfMonth).show()
                }
            }
            if (showThresholdPicker) {
                val context = LocalContext.current
                LaunchedEffect(Unit) {
                    android.app.TimePickerDialog(context, { _, h, m -> onThreshold("%02d:%02d".format(h, m)); showThresholdPicker = false }, state.nightThreshold.take(2).toIntOrNull() ?: 22, state.nightThreshold.drop(3).take(2).toIntOrNull() ?: 0, true).show()
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.nightPremiumPercent.toString(),
                onValueChange = { it.toDoubleOrNull()?.let(onPercent) },
                label = { Text("Ночная надбавка, %") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            ToggleRow(
                title = "Без ночной надбавки",
                subtitle = "Расчёт ведётся без доплаты за ночные часы",
                checked = state.nightPremiumPercent == 0.0,
                onChecked = { if (it) onPercent(0.0) else onPercent(20.0) }
            )
            Spacer(Modifier.height(8.dp))
            DateRow("Проставить до", state.patternUntil) { onUntil(it) }

            Spacer(Modifier.height(22.dp))

            // 4. Способ привязки
            Label("Как ложится на календарь")
            Spacer(Modifier.height(8.dp))
            ToggleRow(
                title = "Каждый месяц с начала цикла",
                subtitle = "Сдвиг дат разрешён: месяц всегда начинается с первого шага",
                checked = state.patternAlignToMonth,
                onChecked = onAlign
            )
            ToggleRow(
                title = "Не трогать уже заполненные дни",
                subtitle = "Смены и выходные, которые вы поставили руками, останутся",
                checked = state.patternKeepExisting,
                onChecked = onKeep
            )

            Spacer(Modifier.height(18.dp))

            // 5. Предпросмотр
            OutlinedButton(
                onClick = onPreview,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) { Text("Посчитать, что получится", color = Palette.Ink) }

            state.patternPreview?.let { p ->
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Palette.ShiftRed.copy(alpha = 0.07f))
                        .padding(14.dp)
                ) {
                    Column {
                        Text(
                            "Будет проставлено",
                            fontSize = 10.sp,
                            color = Palette.Muted,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        PreviewRow("Смен", "${p.workCount}")
                        PreviewRow("Выходных", "${p.dayOffCount}")
                        PreviewRow("Часов", formatHours(p.totalHours))
                        PreviewRow("Сумма", formatMoney(p.totalEarned))
                        if (p.skippedExisting > 0) {
                            PreviewRow("Пропущено занятых дней", "${p.skippedExisting}")
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "с ${p.firstDate.dayOfMonth}.${p.firstDate.monthValue}.${p.firstDate.year} " +
                                "по ${p.lastDate.dayOfMonth}.${p.lastDate.monthValue}.${p.lastDate.year}",
                            fontSize = 11.sp,
                            color = Palette.Muted
                        )
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun PatternStepRow(
    index: Int,
    step: PatternStep,
    onChange: (PatternStep) -> Unit,
    onRemove: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val accent = if (step.isDayOff) Palette.DayOffGreen else Palette.ShiftRed

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.06f))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(accent),
                contentAlignment = Alignment.Center
            ) {
                Text("${index + 1}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                when {
                    step.isDayOff -> "Выходной"
                    step.kind == ShiftKind.NIGHT -> "Ночная"
                    else -> "Дневная"
                },
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = Palette.Ink,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (step.isDayOff) "—" else "${formatHours(step.hours)} · ${formatMoney(step.rate)}/ч",
                fontSize = 12.sp,
                color = Palette.Muted
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(26.dp).clip(CircleShape).clickable { expanded = !expanded },
                contentAlignment = Alignment.Center
            ) { Text(if (expanded) "▴" else "▾", fontSize = 12.sp, color = Palette.Ink) }
            Box(
                Modifier.size(26.dp).clip(CircleShape).clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) { Text("✕", fontSize = 13.sp, color = Palette.ShiftRed) }
        }

        if (expanded) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StepChoice("День", !step.isDayOff && step.kind != ShiftKind.NIGHT, Modifier.weight(1f)) {
                    onChange(step.copy(isDayOff = false, kind = ShiftKind.DAY))
                }
                StepChoice("Ночь", !step.isDayOff && step.kind == ShiftKind.NIGHT, Modifier.weight(1f)) {
                    onChange(step.copy(isDayOff = false, kind = ShiftKind.NIGHT))
                }
                StepChoice("Вых", step.isDayOff, Modifier.weight(1f)) {
                    onChange(step.copy(isDayOff = true))
                }
            }
            if (!step.isDayOff) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(8.0, 12.0, 24.0).forEach { h ->
                        StepChoice(
                            if (h == 24.0) "сутки" else "${h.toInt()} ч",
                            step.hours == h,
                            Modifier.weight(1f)
                        ) { onChange(step.copy(hours = h)) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                RateField(value = step.rate, onChange = { onChange(step.copy(rate = it)) }, compact = true)
            }
        }
    }
}

@Composable
private fun StepChoice(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Palette.Ink else Palette.CardBg)
            .border(1.dp, if (selected) Palette.Ink else Palette.Outline, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 12.sp, color = if (selected) Color.White else Palette.Ink)
    }
}

@Composable
private fun RateField(value: Double, onChange: (Double) -> Unit, compact: Boolean = false) {
    OutlinedTextField(
        value = if (value == 0.0) "" else if (value % 1.0 == 0.0) value.toLong().toString() else value.toString().replace('.', ','),
        onValueChange = { txt ->
            onChange(txt.replace(',', '.').filter { c -> c.isDigit() || c == '.' }.toDoubleOrNull() ?: 0.0)
        },
        placeholder = { Text(if (compact) "ставка ₽/ч" else "например 500", color = Palette.Muted) },
        suffix = { Text("₽/ч", color = Palette.Muted, fontSize = 12.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Palette.ShiftRed,
            unfocusedBorderColor = Palette.Outline,
            focusedTextColor = Palette.Ink,
            unfocusedTextColor = Palette.Ink
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

/** Простой выбор даты: день / месяц / год кнопками. */
@Composable
private fun DateRow(label: String, date: LocalDate, onChange: (LocalDate) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.CardBg)
            .border(1.dp, Palette.Outline, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = Palette.Ink, modifier = Modifier.weight(1f))
        StepperMini("−") { onChange(date.minusMonths(1)) }
        Text(
            "${date.dayOfMonth}.${"%02d".format(date.monthValue)}.${date.year}",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Palette.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(94.dp)
        )
        StepperMini("+") { onChange(date.plusMonths(1)) }
    }
}

@Composable
private fun StepperMini(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(30.dp).clip(CircleShape).background(Palette.Ink).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onChecked(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = Palette.Ink, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 11.sp, color = Palette.Muted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(checkedTrackColor = Palette.DayOffGreen)
        )
    }
}

@Composable
private fun PreviewRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = Palette.Muted)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.Ink)
    }
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), fontSize = 10.sp, color = Palette.Muted, fontWeight = FontWeight.SemiBold)
}
