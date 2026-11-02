package com.shiftcalendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiftcalendar.data.Shift
import com.shiftcalendar.model.*
import java.time.LocalDate
import java.time.YearMonth

/**
 * Навигация по календарю.
 *
 * Месяц показывается полным именем («Октябрь 2026») со своими стрелками ‹ › —
 * они листают месяцы. Год меняется отдельными стрелками ‹ › вокруг подписи года,
 * чтобы не было сомнений, что именно переключается.
 */
@Composable
fun MonthScreen(
    state: UiState,
    onSelectMonth: (YearMonth) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onPrevYear: () -> Unit,
    onNextYear: () -> Unit,
    onClearShifts: () -> Unit,
    onClearShiftsConfirm: () -> Unit,
    onClearShiftsDismiss: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        YearRow(state.year, onPrevYear, onNextYear)
        Spacer(Modifier.height(6.dp))
        MonthStrip(state, onSelectMonth)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            CalendarGrid(state, onOpenDay, onPrevMonth, onNextMonth)
            LegendRow()
            // Статистика за выбранный месяц
            state.current?.let { cur ->
                TotalsCard(
                    shifts = cur.worked,
                    thresholdStart = java.time.LocalTime.parse(
                        runCatching { state.nightThreshold }.getOrDefault("22:00")
                    ),
                    thresholdEnd = java.time.LocalTime.parse(
                        runCatching { state.nightEnd }.getOrDefault("06:00")
                    ),
                    percent = state.nightPremiumPercent,
                    now = state.now,
                    monthName = monthTitleRu(cur.ym)
                )
            }
            Spacer(Modifier.height(10.dp))
            // «Удалить смены» — в самом низу, под статистикой
            ClearShiftsButton(
                visible = state.current?.hasAny == true,
                onClick = onClearShifts
            )
            Spacer(Modifier.height(90.dp))
        }
    }

    if (state.clearShiftsConfirm) {
        AlertDialog(
            onDismissRequest = onClearShiftsDismiss,
            containerColor = Palette.CardBg,
            titleContentColor = Palette.Ink,
            textContentColor = Palette.Ink,
            title = { Text("Удалить все смены?", fontWeight = FontWeight.Bold, color = Palette.Ink) },
            text = {
                Text(
                    "Календарь станет пустым: дни вернутся в состояние «ничего не проставлено». Действие необратимо.",
                    fontSize = 13.sp, color = Palette.Ink
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onClearShiftsConfirm,
                    colors = ButtonDefaults.textButtonColors(contentColor = Palette.ShiftRed)
                ) {
                    Text("Удалить", color = Palette.ShiftRed, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onClearShiftsDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = Palette.Ink)
                ) { Text("Отмена", color = Palette.Ink) }
            }
        )
    }
}

/**
 * Заголовок года: ‹ 2026 › — переключает год целиком.
 *
 * Раньше справа стояли стрелки года плюс дублирующая подпись «10.2026», и
 * выглядело это как навигация по месяцу — а рядом уже есть полоса месяцев и
 * заголовок месяца со своими стрелками. Теперь в строке ровно одно действие
 * (год), поэтому спор убран: «‹ 2026 ›» по центру на всю ширину.
 */
@Composable
private fun YearRow(year: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavArrow("‹", onPrev)
        Text(
            "$year",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Palette.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 76.dp)
        )
        NavArrow("›", onNext)
    }
}

@Composable
private fun NavArrow(symbol: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Palette.CardBg)
            .border(1.dp, Palette.Outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(symbol, fontSize = 17.sp, color = Palette.Ink, fontWeight = FontWeight.SemiBold) }
}

/** horizontal lazy strip 12 панелей-месяцев выбранного года. */
@Composable
private fun MonthStrip(state: UiState, onSelect: (YearMonth) -> Unit) {
    val listState = rememberLazyListState()
    // Прокрутка к выбранному при смене года/месяца
    androidx.compose.runtime.LaunchedEffect(state.selected, state.year) {
        val idx = state.selected.monthValue - 1
        if (idx in 0..11) listState.animateScrollToItem(idx)
    }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items((1..12).map { YearMonth.of(state.year, it) }, key = { it.toString() }) { ym ->
            val isSel = ym == state.selected
            val summary = state.months.firstOrNull { it.ym == ym }
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSel) Palette.Ink else Palette.CardBg)
                    .border(1.dp, if (isSel) Palette.Ink else Palette.Outline, RoundedCornerShape(12.dp))
                    .clickable { onSelect(ym) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        monthTitleRu(ym),
                        fontSize = 12.sp,
                        color = if (isSel) Palette.OnInk else Palette.Ink,
                        fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal
                    )
                    if (summary != null && summary.hasAny) {
                        Text(
                            "${summary.shiftCount} см",
                            fontSize = 9.sp,
                            color = if (isSel) Palette.OnInk.copy(alpha = 0.8f) else Palette.Muted
                        )
                    } else {
                        Text("—", fontSize = 9.sp, color = if (isSel) Palette.OnInk.copy(alpha = 0.7f) else Palette.Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarGrid(state: UiState, onOpenDay: (LocalDate) -> Unit, onPrevMonth: () -> Unit, onNextMonth: () -> Unit) {
    val ym = state.selected
    val summary = state.months.firstOrNull { it.ym == ym }
    // День может содержать несколько смен — в ячейку попадает весь список,
    // а не одна случайная строка (associateBy оставлял бы только последнюю).
    val shiftsByDate: Map<String, List<Shift>> = summary?.shifts?.let { MultiShift.byDate(it) } ?: emptyMap()
    val cells = monthCells(ym)
    val monthsShift = state.months
    val prevMonth = ym.minusMonths(1)
    val nextMonth = ym.plusMonths(1)

    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                monthTitleRu(ym),
                fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Ink,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(12.dp))
            // Стрелки месяца — одной группой, чтобы не разъезжались и не сжимались
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavArrow("‹", onPrevMonth)
                NavArrow("›", onNextMonth)
            }
        }
        Spacer(Modifier.height(8.dp))
        // Дни недели Пн..Вс
        Row(Modifier.fillMaxWidth()) {
            listOf("Пн","Вт","Ср","Чт","Пт","Сб","Вс").forEach { d ->
                Text(d, fontSize = 11.sp, color = Palette.Muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(6.dp))
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                week.forEach { day ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (day != null) {
                            DayCell(
                                date = day,
                                dayShifts = shiftsByDate[day.iso()].orEmpty(),
                                isToday = day == state.today,
                                inSelectedMonth = day.monthValue == ym.monthValue,
                                state = state,
                                onOpen = onOpenDay
                            )
                        } else {
                            Spacer(Modifier.height(44.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Ячейка дня. Работает и для одного денька, и для дня с несколькими сменами:
 *  - фон — цвет первой смены (или цвета профиля, если он их задал);
 *  - под числом — по тонкой полоске на каждую смену, каждая своим цветом,
 *    так что вторая смена видна даже если она накладывается на первую;
 *  - у дня с одной сменой подпись остаётся временем «08:00–20:00», у дня с
 *    несколькими — «2 см · 14 ч», иначе в 44 dp не влезет ничего читаемого.
 */
@Composable
private fun DayCell(
    date: LocalDate,
    dayShifts: List<Shift>,
    isToday: Boolean,
    inSelectedMonth: Boolean,
    state: UiState,
    onOpen: (LocalDate) -> Unit
) {
    val ordered = MultiShift.ordered(dayShifts)
    val work = ordered.filter { !it.isDayOff }
    val dayOff = ordered.any { it.isDayOff }
    // Цвет дня: свой профиль — его цвета; иначе слот первой смены / зелёный.
    val profColor: Color? = ordered.firstNotNullOfOrNull { profileColorFor(state, it) }
    val base: Color? = when {
        ordered.isEmpty() -> null
        dayOff -> profColor ?: Palette.DayOffGreen
        else -> profColor ?: ShiftSlotColors.colorFor(0)
    }
    val summary = summarizeDay(ordered)
    // Сколько смен в работе прямо сейчас — только у сегодняшней ячейки.
    val runningNow = if (isToday) activeShiftsAt(ordered, state.now) else 0
    val bg = base
    Box(
        Modifier
            .padding(1.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (ordered.isNotEmpty()) (bg ?: Palette.CardBg).copy(alpha = 0.16f) else Color.Transparent)
            .border(
                width = if (isToday) 2.dp else 1.dp,
                color = when {
                    isToday -> Palette.Ink
                    ordered.isNotEmpty() -> (bg ?: Palette.CardBg).copy(alpha = 0.55f)
                    else -> Palette.Outline.copy(alpha = 0.5f)
                },
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(enabled = inSelectedMonth) { onOpen(date) }
            .semantics {
                contentDescription = buildString {
                    append("День ").append(date.dayOfMonth)
                    if (summary.workCount > 1) append(", смен ").append(summary.workCount)
                    if (runningNow > 0) append(", сейчас идут ").append(runningNow)
                    if (dayOff) append(", выходной")
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${date.dayOfMonth}",
                fontSize = 13.sp,
                fontWeight = if (ordered.isNotEmpty() || isToday) FontWeight.Bold else FontWeight.Normal,
                color = Palette.Ink
            )
            when {
                dayOff && summary.workCount == 0 ->
                    Text("вых", fontSize = 7.sp, color = Palette.Muted)
                summary.workCount == 1 && !dayOff -> {
                    val only = work.first()
                    if (only.startTime != null) {
                        Text(
                            "${only.startTime}–${only.endTime ?: "?"}",
                            fontSize = 7.sp,
                            color = Palette.Muted,
                            maxLines = 1
                        )
                    }
                }
                summary.workCount > 1 -> {
                    // Полоски-слоты: цвет = смена, порядок = время начала.
                    val slotOf = shiftSlots(ordered)
                    // Идущие сейчас смены подчёркнуты жирной точкой «•».
                    Row(horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
                        work.take(4).forEach { shift ->
                            Box(
                                Modifier
                                    .width(4.dp)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(ShiftSlotColors.colorFor(slotOf[shift.id] ?: 0))
                            )
                        }
                        if (work.size > 4) {
                            Text("+", fontSize = 7.sp, color = Palette.Muted)
                        }
                    }
                    Text(
                        if (runningNow > 0) {
                            "${summary.workCount} см · идут $runningNow"
                        } else {
                            "${summary.workCount} см · ${formatHours(summary.hours)}"
                        },
                        fontSize = 6.sp,
                        color = if (runningNow > 0) Palette.ShiftRed else Palette.Muted,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendRow() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LegendDot(Palette.ShiftRed, "1-я смена")
        LegendDot(ShiftSlotColors.colorFor(1), "2-я")
        LegendDot(ShiftSlotColors.colorFor(2), "3-я")
        LegendDot(Palette.DayOffGreen, "выходной")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 10.sp, color = Palette.Muted)
    }
}

@Composable
private fun TotalsCard(
    shifts: List<Shift>,
    thresholdStart: java.time.LocalTime,
    thresholdEnd: java.time.LocalTime,
    percent: Double,
    now: java.time.LocalDateTime,
    monthName: String
) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.CardBg)
            .border(1.dp, Palette.Outline, RoundedCornerShape(16.dp))
            .clickable { expanded = !expanded }
            .padding(16.dp)
    ) {
        Column {
            Row {
                Text(
                    "Статистика · $monthName",
                    fontSize = 10.sp,
                    color = Palette.Muted,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(if (expanded) "▴" else "▾", fontSize = 11.sp, color = Palette.Muted)
            }
            Spacer(Modifier.height(12.dp))
            val earned = shifts.sumOf { shiftEarnings(it, thresholdStart, thresholdEnd, percent, it.breakMinutes) }
            val planned = shifts.filter { LocalDate.parse(it.date, ISO).isAfter(now.toLocalDate()) }
            val plannedEarned = planned.sumOf { shiftEarnings(it, thresholdStart, thresholdEnd, percent, it.breakMinutes) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TotalItem("Смен", "${shifts.size}")
                TotalItem("Часов", formatHours(shifts.sumOf { it.hours }))
                TotalItem("Зарплата", formatMoney(earned), accent = Palette.DayOffGreen)
            }
            if (expanded && planned.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "запланировано: ${planned.size} смен · ${formatMoney(plannedEarned)}",
                    fontSize = 11.sp, color = Palette.Muted
                )
            }
        }
    }
}

@Composable
private fun ClearShiftsButton(visible: Boolean, onClick: () -> Unit) {
    if (!visible) return
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.ShiftRed.copy(alpha = 0.10f))
            .border(1.dp, Palette.ShiftRed.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Удалить смены месяца",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Palette.ShiftRed
        )
    }
}

private fun monthTitleRu(ym: YearMonth): String {
    val names = listOf("Январь","Февраль","Март","Апрель","Май","Июнь","Июль","Август","Сентябрь","Октябрь","Ноябрь","Декабрь")
    return names[ym.monthValue - 1]
}

@Composable
private fun TotalItem(label: String, value: String, accent: Color = Palette.Ink) {
    Column {
        Text(label.uppercase(), fontSize = 10.sp, color = Palette.Muted, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = accent)
    }
}

/** Цвет дня по профилю смены: работы/выходной; null — стандартные цвета темы. */
private fun profileColorFor(state: UiState, shift: Shift?): Color? {
    val pid = shift?.profileId ?: return null
    if (pid <= 0L) return null
    val p = state.profiles.firstOrNull { it.id == pid } ?: return null
    return Color(if (shift.isDayOff) p.dayOffColor else p.workColor)
}
