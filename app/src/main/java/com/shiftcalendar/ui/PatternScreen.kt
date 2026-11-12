package com.shiftcalendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import android.app.TimePickerDialog
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
 * Конструктор паттерна смен + профили. Профиль — сохранённый график: порядок
 * цикла (4/2, 2/2 5/2 3/1…), времена, ставки, перерывы и цвета (рабочие/выходной).
 * Паттерн заменяет смены в диапазоне, а не дублирует их.
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
    onStepStart: (Int, String) -> Unit,
    onStepEnd: (Int, String) -> Unit,
    onNudgeStepStart: (Int, Int) -> Unit,
    onNudgeStepEnd: (Int, Int) -> Unit,
    onStepBreak: (Int, Int) -> Unit,
    onAllRates: (Double) -> Unit,
    onStart: (LocalDate) -> Unit,
    onThreshold: (String) -> Unit,
    onNightEnd: (String) -> Unit,
    onPercent: (Double) -> Unit,
    onPremiumTitle: (String) -> Unit,
    onPremiumMode: (PremiumMode) -> Unit,
    onPremiumAmount: (Double) -> Unit,
    onPremiumBreakAfterStart: (Boolean) -> Unit,
    onUntil: (LocalDate) -> Unit,
    onAlign: (Boolean) -> Unit,
    onPreview: () -> Unit,
    onApply: () -> Unit,
    onSelectProfile: (Long) -> Unit,
    onNewProfile: () -> Unit,
    onSaveProfile: () -> Unit,
    onDeleteProfile: (Long) -> Unit,
    onProfileName: (String) -> Unit,
    onWorkColor: (Long) -> Unit,
    onDayOffColor: (Long) -> Unit
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
                    colors = accentButtonColors(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("Проставить в календарь", fontWeight = FontWeight.SemiBold)
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
            // 0. Профили: сохранённые графики
            ProfileSection(
                state = state,
                onSelect = onSelectProfile,
                onNew = onNewProfile,
                onSave = onSaveProfile,
                onDelete = onDeleteProfile,
                onName = onProfileName,
                onWorkColor = onWorkColor,
                onDayOffColor = onDayOffColor
            )

            Spacer(Modifier.height(18.dp))

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
                "Нажмите на шаг цикла, чтобы задать время, тип, ставку и перерыв",
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
                        workColor = Color(state.workColor),
                        dayOffColor = Color(state.dayOffColor),
                        onChange = { onUpdate(i, it) },
                        onStartText = { onStepStart(i, it) },
                        onEndText = { onStepEnd(i, it) },
                        onNudgeStart = { onNudgeStepStart(i, it) },
                        onNudgeEnd = { onNudgeStepEnd(i, it) },
                        onBreak = { onStepBreak(i, it) },
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
                        colors = outlinedButtonColors(),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("+ смена", fontSize = 13.sp, color = Palette.Ink) }
                    OutlinedButton(
                        onClick = { onAdd(true) },
                        colors = outlinedButtonColors(),
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
            var showNightEndPicker by remember { mutableStateOf(false) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
            onClick = { showStartPicker = true },
            colors = outlinedButtonColors()
        ) { Text("Выбрать дату начала", color = Palette.Ink) }
                OutlinedButton(
            onClick = { showThresholdPicker = true },
            colors = outlinedButtonColors()
        ) { Text("Надбавка с ${state.nightThreshold}", color = Palette.Ink) }
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
                    TimePickerDialog(context, { _, h, m -> onThreshold("%02d:%02d".format(h, m)); showThresholdPicker = false }, state.nightThreshold.take(2).toIntOrNull() ?: 22, state.nightThreshold.drop(3).take(2).toIntOrNull() ?: 0, true).show()
                }
            }
            Spacer(Modifier.height(8.dp))
            // Надбавка задаётся пользователем (решение 13-Б): название, способ
            // (процент или сумма за час) и учёт перерыва после начала надбавки.
            PremiumTitleField(value = state.premiumTitle, onChange = onPremiumTitle)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StepChoice("Процент", state.premiumMode == PremiumMode.PERCENT, Modifier.weight(1f)) {
                    onPremiumMode(PremiumMode.PERCENT)
                }
                StepChoice("Сумма ₽/ч", state.premiumMode == PremiumMode.AMOUNT, Modifier.weight(1f)) {
                    onPremiumMode(PremiumMode.AMOUNT)
                }
            }
            Spacer(Modifier.height(6.dp))
            if (state.premiumMode == PremiumMode.PERCENT) {
                PercentField(value = state.nightPremiumPercent, onChange = onPercent)
            } else {
                AmountField(value = state.premiumAmount, onChange = onPremiumAmount)
            }
            Spacer(Modifier.height(6.dp))
            ToggleRow(
                title = "Перерыв съедает надбавку",
                subtitle = "Перерыв после начала надбавки вычитается из надбавочных часов",
                checked = state.premiumBreakAfterStart,
                onChecked = onPremiumBreakAfterStart
            )
            Spacer(Modifier.height(8.dp))
            // Конец окна надбавки
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Palette.CardBg)
                    .border(1.dp, Palette.Outline, RoundedCornerShape(12.dp))
                    .clickable { showNightEndPicker = true }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Надбавка до", fontSize = 13.sp, color = Palette.Ink)
                    Text(
                        " часы после этого времени — снова базовая ставка",
                        fontSize = 11.sp, color = Palette.Muted
                    )
                }
                Text(state.nightEnd, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Palette.Ink)
            }
            if (showNightEndPicker) {
                val context = LocalContext.current
                LaunchedEffect(Unit) {
                    TimePickerDialog(context, { _, h, m -> onNightEnd("%02d:%02d".format(h, m)); showNightEndPicker = false }, state.nightEnd.take(2).toIntOrNull() ?: 6, state.nightEnd.drop(3).take(2).toIntOrNull() ?: 0, true).show()
                }
            }
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
            Spacer(Modifier.height(6.dp))
            Text(
                if (state.selectedProfileId >= 0L)
                    "Заменятся только смены профиля «${state.profileName.ifEmpty { "выбранного" }}» — остальные дни не тронутся"
                else
                    "Паттерн заменит все смены в выбранном диапазоне — дублей не будет",
                fontSize = 11.sp,
                color = Palette.Muted
            )

            Spacer(Modifier.height(18.dp))

            // 5. Предпросмотр
            OutlinedButton(
                onClick = onPreview,
                colors = outlinedButtonColors(),
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
                        if (p.replacedCount > 0) {
                            PreviewRow("Заменят существующие дни", "${p.replacedCount}")
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

// ---------- Профили ----------

@Composable
private fun ProfileSection(
    state: UiState,
    onSelect: (Long) -> Unit,
    onNew: () -> Unit,
    onSave: () -> Unit,
    onDelete: (Long) -> Unit,
    onName: (String) -> Unit,
    onWorkColor: (Long) -> Unit,
    onDayOffColor: (Long) -> Unit
) {
    Label("Профили")
    Spacer(Modifier.height(4.dp))
    Text(
        "Сохранённый график: цикл, времена, ставки и свои цвета смен",
        fontSize = 11.sp,
        color = Palette.Muted
    )
    Spacer(Modifier.height(8.dp))
    if (state.profiles.isNotEmpty()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.profiles, key = { it.id }) { p ->
                val selected = p.id == state.selectedProfileId
                Row(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Palette.Ink else Palette.CardBg)
                        .border(1.dp, if (selected) Palette.Ink else Palette.Outline, RoundedCornerShape(12.dp))
                        .clickable { onSelect(p.id) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Цветовой признак профиля: его рабочие и выходные цвета
                    Row {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(p.workColor)))
                        Spacer(Modifier.width(3.dp))
                        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(p.dayOffColor)))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        p.name,
                        fontSize = 13.sp,
                        color = if (selected) Palette.OnInk else Palette.Ink,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "✕",
                        fontSize = 12.sp,
                        color = if (selected) Palette.OnInk else Palette.Muted,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onDelete(p.id) }
                            .padding(3.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onNew,
            colors = outlinedButtonColors(),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1f)
        ) { Text("＋ новый профиль", fontSize = 13.sp, color = Palette.Ink) }
        Button(
            onClick = onSave,
            enabled = state.patternSteps.isNotEmpty(),
            colors = accentButtonColors(),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1f)
        ) { Text("Сохранить профиль", fontSize = 13.sp) }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = state.profileName,
        onValueChange = onName,
        placeholder = { Text("название: 4/2, вахта…", color = Palette.Muted) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Palette.ShiftRed,
            unfocusedBorderColor = Palette.Outline,
            focusedTextColor = Palette.Ink,
            unfocusedTextColor = Palette.Ink
        ),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    Text("Цвета профиля: рабочие / выходные", fontSize = 11.sp, color = Palette.Muted)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ColorPickRow(
            title = "Рабочие",
            current = Color(state.workColor),
            modifier = Modifier.weight(1f),
            onPick = onWorkColor
        )
        ColorPickRow(
            title = "Выходные",
            current = Color(state.dayOffColor),
            modifier = Modifier.weight(1f),
            onPick = onDayOffColor
        )
    }
}

/** Выбор цвета из фиксированной палитры (диалог с кружками). */
@Composable
private fun ColorPickRow(title: String, current: Color, modifier: Modifier, onPick: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.CardBg)
            .border(1.dp, Palette.Outline, RoundedCornerShape(12.dp))
            .clickable { open = true }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(current)
                .border(1.dp, Palette.Outline, CircleShape)
        )
        Spacer(Modifier.height(4.dp))
        Text(title, fontSize = 11.sp, color = Palette.Muted)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            containerColor = Palette.CardBg,
            titleContentColor = Palette.Ink,
            textContentColor = Palette.Ink,
            title = { Text("Цвет: $title", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    val swatches = listOf(
                        0xFFD8433CL, 0xFFE8675FL, 0xFFF2994AL, 0xFFF2C94CL,
                        0xFF2E9E5BL, 0xFF43B978L, 0xFF2F80EDL, 0xFF56A8F5L,
                        0xFF9B51E0L, 0xFFBB6BD9L, 0xFF0F1115L, 0xFF9BA1ABL
                    )
                    swatches.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { argb ->
                                val c = Color(argb)
                                val isCurrent = current == c
                                Box(
                                    Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(c)
                                        .border(
                                            if (isCurrent) 3.dp else 1.dp,
                                            if (isCurrent) Palette.Ink else Palette.Outline,
                                            CircleShape
                                        )
                                        .clickable {
                                            onPick(argb)
                                            open = false
                                        }
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(
            onClick = { open = false },
            colors = ButtonDefaults.textButtonColors(contentColor = Palette.Ink)
        ) { Text("Готово", color = Palette.Ink) }
            }
        )
    }
}

// ---------- Шаг цикла ----------

@Composable
private fun PatternStepRow(
    index: Int,
    step: PatternStep,
    workColor: Color,
    dayOffColor: Color,
    onChange: (PatternStep) -> Unit,
    onStartText: (String) -> Unit,
    onEndText: (String) -> Unit,
    onNudgeStart: (Int) -> Unit,
    onNudgeEnd: (Int) -> Unit,
    onBreak: (Int) -> Unit,
    onRemove: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val accent = when {
        step.isDayOff -> dayOffColor
        step.kind == ShiftKind.NIGHT -> workColor
        else -> workColor
    }
    val onAccent = if (accent.luminanceCompat() > 0.5f) Color(0xFF0F1115) else Color.White

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.06f))
            .border(
                1.dp,
                if (expanded) accent.copy(alpha = 0.5f) else Color.Transparent,
                RoundedCornerShape(10.dp)
            )
            // Вся карточка шага — зона нажатия: раскрывает настройки.
            .clickable { expanded = !expanded }
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(accent),
                contentAlignment = Alignment.Center
            ) {
                Text("${index + 1}", color = onAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        step.isDayOff -> "Выходной"
                        step.kind == ShiftKind.NIGHT -> "Ночная"
                        else -> "Дневная"
                    },
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = Palette.Ink
                )
                Text(
                    if (step.isDayOff) "—"
                    else "${step.start}–${step.end}" +
                        (if (step.breakMinutes > 0) " · перерыв ${step.breakMinutes} мин" else "") +
                        " · ${formatHours(step.paidHours)}",
                    fontSize = 11.sp,
                    color = Palette.Muted
                )
            }
            Text(
                if (step.isDayOff) "" else "${formatMoney(step.rate)}/ч",
                fontSize = 12.sp,
                color = Palette.Muted
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(26.dp).clip(CircleShape).clickable { expanded = !expanded },
                contentAlignment = Alignment.Center
            ) { Text(if (expanded) "▴" else "▾", fontSize = 12.sp, color = Palette.Ink) }
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) { Text("✕", fontSize = 13.sp, color = Palette.ShiftRed) }
        }

        if (expanded) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StepChoice("День", !step.isDayOff && step.kind != ShiftKind.NIGHT, Modifier.weight(1f)) {
                    val (s, e) = defaultInterval(ShiftKind.DAY)
                    onChange(
                        if (step.startTime == null || step.endTime == null)
                            step.copy(isDayOff = false, kind = ShiftKind.DAY, startTime = s, endTime = e)
                        else step.copy(isDayOff = false, kind = ShiftKind.DAY)
                    )
                }
                StepChoice("Ночь", !step.isDayOff && step.kind == ShiftKind.NIGHT, Modifier.weight(1f)) {
                    val (s, e) = defaultInterval(ShiftKind.NIGHT)
                    onChange(
                        if (step.startTime == null || step.endTime == null)
                            step.copy(isDayOff = false, kind = ShiftKind.NIGHT, startTime = s, endTime = e)
                        else step.copy(isDayOff = false, kind = ShiftKind.NIGHT)
                    )
                }
                StepChoice("Вых", step.isDayOff, Modifier.weight(1f)) {
                    onChange(step.copy(isDayOff = true))
                }
            }
            if (!step.isDayOff) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    TimeField(
                        label = "Начало",
                        value = timeFieldText(step.startTime ?: step.start),
                        onChange = onStartText,
                        onPick = { onChange(step.copy(startTime = step.startTime ?: step.start, endTime = step.endTime ?: step.end)) },
                        onNudge = onNudgeStart,
                        invalid = intervalHours(step.start, step.end) == null && step.endTime != null,
                        modifier = Modifier.weight(1f)
                    )
                    TimeField(
                        label = "Конец",
                        value = timeFieldText(step.endTime ?: step.end),
                        onChange = onEndText,
                        onPick = { onChange(step.copy(startTime = step.startTime ?: step.start, endTime = step.endTime ?: step.end)) },
                        onNudge = onNudgeEnd,
                        invalid = intervalHours(step.start, step.end) == null && step.endTime != null,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                // Перерыв: −5/+5 мин, вычитается из времени смены
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
                            fontSize = 10.sp,
                            color = Palette.Muted
                        )
                    }
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(Palette.WeekendTint).clickable { onBreak(step.breakMinutes - 5) },
                        contentAlignment = Alignment.Center
                    ) { Text("−", fontSize = 16.sp, color = Palette.Ink, fontWeight = FontWeight.Bold) }
                    Text(
                        if (step.breakMinutes > 0) "${step.breakMinutes} мин" else "нет",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Palette.Ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(min = 62.dp)
                    )
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(Palette.WeekendTint).clickable { onBreak(step.breakMinutes + 5) },
                        contentAlignment = Alignment.Center
                    ) { Text("+", fontSize = 16.sp, color = Palette.Ink, fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Шаг: ${formatHours(step.effectiveHours)}" +
                        (if (step.breakMinutes > 0) " − перерыв = ${formatHours(step.paidHours)}" else "") +
                        if (intervalHours(step.start, step.end) == null) " (проверьте ЧЧ:ММ)" else "",
                    fontSize = 11.sp,
                    color = if (intervalHours(step.start, step.end) == null) Palette.ShiftRed else Palette.Muted
                )
                Spacer(Modifier.height(8.dp))
                RateField(value = step.rate, onChange = { onChange(step.copy(rate = it)) }, compact = true)
            }
        }
    }
}

/** Яркость цвета для выбора контрастного текста (без зависимости от graphics.IColor). */
private fun Color.luminanceCompat(): Float =
    (0.2126f * red + 0.7152f * green + 0.0722f * blue)

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
        // Текст на «чёрной» кнопке: OnInk (в тёмной теме кнопка светлая, текст тёмный)
        Text(label, fontSize = 12.sp, color = if (selected) Palette.OnInk else Palette.Ink)
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
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = Palette.Ink),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Palette.ShiftRed,
            unfocusedBorderColor = Palette.Outline,
            focusedTextColor = Palette.Ink,
            unfocusedTextColor = Palette.Ink
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Поле процентов надбавки: 0–100. Показывает значения как пользователь ввёл:
 * «20,5» не рассыпается в «20,50», «22» не превращается в «22,00».
 */
@Composable
private fun PercentField(value: Double, onChange: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(if (value == 0.0) "" else formatPercent(value)) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val cleaned = raw.replace(',', '.').filter { c -> c.isDigit() || c == '.' }.take(6)
            text = cleaned.replace('.', ',')
            parsePercent(cleaned)?.let { if (it in 0.0..100.0) onChange(it) }
        },
        label = { Text("Надбавка, % (0–100)", color = Palette.Muted) },
        placeholder = { Text("например 20 или 20,5", color = Palette.Muted) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = Palette.Ink),
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

/** Счётчик даты: шаг назад/вперёд цветами темы (Ink-заливка, не белая). */
@Composable
private fun StepperMini(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Palette.WeekendTint)
            .border(1.dp, Palette.Outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, color = Palette.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
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
            colors = SwitchDefaults.colors(
                checkedThumbColor = Palette.OnInk,
                checkedTrackColor = Palette.DayOffGreen,
                checkedBorderColor = Palette.DayOffGreen,
                uncheckedThumbColor = Palette.Muted,
                uncheckedTrackColor = Palette.WeekendTint,
                uncheckedBorderColor = Palette.Outline
            )
        )
    }
}

/** Название надбавки (решение 13-Б): свободный текст, до 40 знаков. */
@Composable
private fun PremiumTitleField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(40)) },
        label = { Text("Название надбавки", color = Palette.Muted) },
        placeholder = { Text("например Ночная надбавка", color = Palette.Muted) },
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = Palette.Ink),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Palette.ShiftRed,
            unfocusedBorderColor = Palette.Outline,
            focusedTextColor = Palette.Ink,
            unfocusedTextColor = Palette.Ink
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

/** Надбавка суммой за час, ₽/ч (решение 13-Б): 0–100 000. */
@Composable
private fun AmountField(value: Double, onChange: (Double) -> Unit) {
    var text by remember(value) {
        mutableStateOf(if (value == 0.0) "" else if (value % 1.0 == 0.0) value.toLong().toString() else value.toString().replace('.', ','))
    }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val cleaned = raw.replace(',', '.').filter { c -> c.isDigit() || c == '.' }.take(7)
            text = cleaned.replace('.', ',')
            cleaned.toDoubleOrNull()?.let { if (it in 0.0..100_000.0) onChange(it) }
        },
        label = { Text("Надбавка, ₽/час", color = Palette.Muted) },
        placeholder = { Text("например 50", color = Palette.Muted) },
        suffix = { Text("₽/ч", color = Palette.Muted, fontSize = 12.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = Palette.Ink),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Palette.ShiftRed,
            unfocusedBorderColor = Palette.Outline,
            focusedTextColor = Palette.Ink,
            unfocusedTextColor = Palette.Ink
        ),
        modifier = Modifier.fillMaxWidth()
    )
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
