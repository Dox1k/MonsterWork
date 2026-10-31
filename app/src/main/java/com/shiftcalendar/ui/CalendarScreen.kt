package com.shiftcalendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/** Палитра: смена — красный, выходной — зелёный (как на эскизе). */
object Palette {
    val ShiftRed = Color(0xFFD8433C)
    val DayOffGreen = Color(0xFF2E9E5B)
    val Ink = Color(0xFF0F1115)
    val Paper = Color(0xFFFFFDFB)
    val Muted = Color(0xFF8A8F98)
    val CardBg = Color(0xFFFFFFFF)
    val Outline = Color(0xFFE2E4E8)
    val WeekendTint = Color(0xFFF6F7F9)
}

@Composable
fun MonthStrip(
    months: List<MonthSummary>,
    selected: YearMonth,
    year: Int,
    onSelect: (YearMonth) -> Unit,
    onPrevYear: () -> Unit,
    onNextYear: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButtonSmall("<") { onPrevYear() }
            Text(
                "$year",
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                color = Palette.Ink
            )
            TextButtonSmall(">") { onNextYear() }
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(months, key = { it.ym.toString() }) { m ->
                MonthCard(m, m.ym == selected) { onSelect(m.ym) }
            }
        }
    }
}

/** Карточка месяца: выбранная залита чёрным (как на эскизе). */
@Composable
private fun MonthCard(m: MonthSummary, isSelected: Boolean, onClick: () -> Unit) {
    val bg = if (isSelected) Palette.Ink else Palette.CardBg
    val fg = if (isSelected) Color.White else Palette.Ink
    Column(
        Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, if (isSelected) Palette.Ink else Palette.Outline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            MONTH_NAMES[m.ym.monthValue - 1].take(3),
            color = fg,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (m.shiftCount > 0) "${m.shiftCount} см" else "—",
            color = if (isSelected) Color(0xFF9BE7B4) else Palette.Muted,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun TextButtonSmall(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Palette.CardBg)
            .border(1.dp, Palette.Outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontWeight = FontWeight.Bold, color = Palette.Ink)
    }
}

/**
 * Сетка месяца. Ячейки-заливки без чисел-подписей смены: красный = смена,
 * зелёный = выходной. Сегодняшний день обведён рамкой.
 */
@Composable
fun MonthGrid(
    ym: YearMonth,
    byDate: Map<String, Shift>,
    today: LocalDate,
    onDayClick: (LocalDate) -> Unit
) {
    val cells = monthCells(ym)
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            WEEKDAYS_SHORT.forEachIndexed { i, d ->
                Text(
                    d,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (i >= 5) Palette.ShiftRed else Palette.Muted
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp)) {
                        if (date != null) DayCell(
                            date = date,
                            shift = byDate[date.iso()],
                            isToday = date == today,
                            onClick = { onDayClick(date) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, shift: Shift?, isToday: Boolean, onClick: () -> Unit) {
    val isWeekend = date.dayOfWeek.value >= 6
    val fill = when {
        shift == null -> if (isWeekend) Palette.WeekendTint else Color.Transparent
        shift.isDayOff -> Palette.DayOffGreen
        else -> Palette.ShiftRed
    }
    val textColor = if (shift != null) Color.White else Palette.Ink
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(fill)
            .then(
                if (isToday) Modifier.border(2.dp, Palette.Ink, RoundedCornerShape(10.dp))
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                date.dayOfMonth.toString(),
                color = textColor,
                fontSize = 14.sp,
                fontWeight = if (shift != null) FontWeight.SemiBold else FontWeight.Normal
            )
            if (shift != null) {
                Text(
                    if (shift.isDayOff) "вых" else kindShort(shift.kind),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 9.sp
                )
            }
        }
    }
}

private fun kindShort(kind: String) = when (kind) {
    "DAY" -> "день"
    "NIGHT" -> "ночь"
    else -> "своя"
}

/** Легенда под сеткой: ● СМЕНЫ · ● ВЫХОДНЫЕ */
@Composable
fun Legend() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendDot(Palette.ShiftRed, "СМЕНЫ")
        LegendDot(Palette.DayOffGreen, "ВЫХОДНЫЕ")
        LegendDot(Palette.Ink, "СЕГОДНЯ", outline = true)
    }
}

@Composable
private fun LegendDot(color: Color, label: String, outline: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .then(if (outline) Modifier.border(2.dp, color, CircleShape) else Modifier.background(color))
        )
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 11.sp, color = Palette.Muted, fontWeight = FontWeight.Medium)
    }
}

/** Итоги месяца: смен / часов / сумма. */
@Composable
fun MonthTotals(m: MonthSummary?, onScrollToToday: () -> Unit) {
    val s = m ?: return
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.CardBg)
            .border(1.dp, Palette.Outline, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Text(monthTitle(s.ym), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Palette.Ink)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TotalItem("Смен", s.shiftCount.toString())
            TotalItem("Часов", formatHours(s.totalHours))
            TotalItem("Заработок", formatMoney(s.totalEarned), accent = Palette.ShiftRed)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Нажмите день, чтобы добавить, изменить или удалить смену",
            fontSize = 11.sp,
            color = Palette.Muted
        )
    }
}

@Composable
private fun TotalItem(label: String, value: String, accent: Color = Palette.Ink) {
    Column {
        Text(label.uppercase(), fontSize = 10.sp, color = Palette.Muted, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = accent)
    }
}

/** Список смен месяца — сводка под календарём. */
@Composable
fun ShiftList(m: MonthSummary?, onClick: (LocalDate) -> Unit) {
    val shifts = m?.shifts?.sortedBy { it.date } ?: return
    if (shifts.isEmpty()) {
        Box(
            Modifier.fillMaxWidth().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("За этот месяц смен пока нет", color = Palette.Muted, fontSize = 13.sp)
        }
        return
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.CardBg)
            .border(1.dp, Palette.Outline, RoundedCornerShape(16.dp))
    ) {
        shifts.forEachIndexed { i, shift ->
            ShiftRow(shift) { onClick(LocalDate.parse(shift.date, ISO)) }
            if (i != shifts.lastIndex) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Outline))
            }
        }
    }
}

@Composable
private fun ShiftRow(shift: Shift, onClick: () -> Unit) {
    val date = LocalDate.parse(shift.date, ISO)
    val color = if (shift.isDayOff) Palette.DayOffGreen else Palette.ShiftRed
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(color), contentAlignment = Alignment.Center) {
            Text(
                date.dayOfMonth.toString(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${WEEKDAYS_SHORT[date.dayOfWeek.value - 1]}, ${date.dayOfMonth} ${MONTH_NAMES[date.monthValue - 1].lowercase()}",
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = Palette.Ink
            )
            Spacer(Modifier.height(2.dp))
            Text(
                when {
                    shift.isDayOff -> "Выходной"
                    shift.kind == "NIGHT" -> "Ночь · ${shift.startTime ?: ""}–${NIGHT_END}"
                    shift.kind == "DAY" -> "День · ${shift.startTime ?: ""}–${DAY_END}"
                    else -> "Своя смена"
                } + if (shift.note.isNotBlank()) " · ${shift.note}" else "",
                fontSize = 12.sp,
                color = Palette.Muted
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatHours(shift.hours), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Palette.Ink)
            if (!shift.isDayOff && shift.rate > 0) {
                Text(formatMoney(shift.hours * shift.rate), fontSize = 12.sp, color = Palette.ShiftRed)
            }
        }
    }
}
