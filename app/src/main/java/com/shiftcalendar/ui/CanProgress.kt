package com.shiftcalendar.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import com.shiftcalendar.data.Shift
import com.shiftcalendar.model.*

/**
 * Банка-прогресс смены: заливка растёт снизу вверх по доле прошедших часов.
 * Нарисована векторно (по мотивам Black Monster), картинка-фото не нужна.
 */
object BankPalette {
    val MonsterGreen = Color(0xFF6CC24A)
    val MonsterGreenDark = Color(0xFF3E8E2A)
    val CanDark = Color(0xFF1A1C20)
    val Metal = Color(0xFFC9CDD4)
    val Empty = Color(0xFFEDEFF2)
}

/**
 * Доля прошедшей смены, 0..1. Для ночной смены конец наступает на
 * следующий день — учитываем переход через полночь.
 */
fun shiftProgress(shift: Shift, now: LocalDateTime = LocalDateTime.now()): Float {
    if (shift.isDayOff) return 0f
    val start = shift.startTime?.let { LocalTime.parse(it) } ?: return 0f
    val endStr = shift.endTime ?: when (shift.kind) {
        "NIGHT" -> NIGHT_END
        "DAY" -> DAY_END
        else -> return 0f
    }
    val date = LocalDate.parse(shift.date, ISO)
    val startDt = LocalDateTime.of(date, start)
    var endDt = LocalDateTime.of(date, LocalTime.parse(endStr))
    if (!endDt.isAfter(startDt)) endDt = endDt.plusDays(1)  // ночь через полночь
    if (now.isBefore(startDt)) return 0f
    if (now.isAfter(endDt)) return 1f
    val total = Duration.between(startDt, endDt).seconds.toFloat()
    if (total <= 0f) return 0f
    return (Duration.between(startDt, now).seconds.toFloat() / total).coerceIn(0f, 1f)
}

/**
 * Найти смену, идущую прямо сейчас (в том числе ночную, начавшуюся вчера).
 */
fun currentShift(months: List<com.shiftcalendar.ui.MonthSummary>, now: LocalDateTime): Shift? {
    val all = months.flatMap { it.shifts }.filter { !it.isDayOff && it.startTime != null }
    return all.firstOrNull { shiftProgress(it, now) > 0f && shiftProgress(it, now) < 1f }
}

@Composable
fun BankProgressCard(shift: Shift, now: LocalDateTime, onOpen: () -> Unit) {
    val progress by animateFloatAsState(
        targetValue = shiftProgress(shift, now),
        animationSpec = tween(durationMillis = 900),
        label = "bank"
    )
    val date = LocalDate.parse(shift.date, ISO)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.CardBg)
            .border(1.dp, Palette.Outline, RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .padding(16.dp)
    ) {
        Text(
            "ИДЁТ СМЕНА",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = BankPalette.MonsterGreenDark
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Can(fill = progress, modifier = Modifier.size(width = 62.dp, height = 104.dp))
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when (shift.kind) {
                        "NIGHT" -> "Ночь"
                        "DAY" -> "День"
                        else -> "Смена"
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Palette.Ink
                )
                Text(
                    "${shift.startTime ?: ""} · ${formatHours(shift.hours)} · " +
                        "${date.dayOfMonth} ${MONTH_NAMES[date.monthValue - 1].lowercase()}",
                    fontSize = 12.sp,
                    color = Palette.Muted
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Прошло ${(progress * 100).toInt()}%",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BankPalette.MonsterGreenDark
                )
                if (shift.rate > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Заработано ${formatMoney(shift.hours * shift.rate * progress)}",
                        fontSize = 12.sp,
                        color = Palette.Muted
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Нажмите, чтобы изменить смену",
                    fontSize = 11.sp,
                    color = Palette.Muted
                )
            }
        }
    }
}

@Composable
fun MonsterBanks(shift: Shift?, now: LocalDateTime) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(16.dp)).background(Palette.CardBg).border(1.dp, Palette.Outline, RoundedCornerShape(16.dp)).padding(12.dp)) {
        Text("Банки монстров", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
        Spacer(Modifier.height(8.dp))
        val elapsedHours = if (shift == null) 0.0 else {
            val start = shift.startTime?.let { LocalDateTime.of(LocalDate.parse(shift.date, ISO), LocalTime.parse(it)) }
            val end = shift.endTime?.let { LocalDateTime.of(LocalDate.parse(shift.date, ISO), LocalTime.parse(it)) }
            if (start == null || end == null) 0.0 else {
                val endAdjusted = if (!end.isAfter(start)) end.plusDays(1) else end
                (Duration.between(start, now).toMinutes().coerceIn(0, Duration.between(start, endAdjusted).toMinutes()) / 60.0)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            (0 until 8).forEach { index ->
                val fill = (elapsedHours - index).coerceIn(0.0, 1.0).toFloat()
                Can(fill = fill, modifier = Modifier.size(width = 30.dp, height = 54.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(if (shift == null) "Выберите смену для прогресса" else "${elapsedHours.toInt()} / 8 часов", fontSize = 11.sp, color = Palette.Muted)
    }
}

/**
 * Банка: пока прогресс 0 — серый контур, по мере смены зелёная заливка
 * поднимается снизу вверх и проявляется логотип-«когти».
 */
@Composable
fun Can(fill: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawCan(fill)
    }
}

private fun DrawScope.drawCan(fill: Float) {
    val w = size.width
    val h = size.height
    val bodyTop = h * 0.10f
    val bodyBottom = h * 0.93f
    val bodyHeight = bodyBottom - bodyTop
    val corner = w * 0.14f

    // Корпус банки: пустая часть
    val bodyPath = Path().apply {
        moveTo(0f, bodyTop + corner)
        quadraticBezierTo(0f, bodyTop, corner, bodyTop)
        lineTo(w - corner, bodyTop)
        quadraticBezierTo(w, bodyTop, w, bodyTop + corner)
        lineTo(w, bodyBottom - corner)
        quadraticBezierTo(w, bodyBottom, w - corner, bodyBottom)
        lineTo(corner, bodyBottom)
        quadraticBezierTo(0f, bodyBottom, 0f, bodyBottom - corner)
        close()
    }
    drawPath(bodyPath, color = BankPalette.Empty)

    // Заливка снизу вверх по доле прошедшей смены
    if (fill > 0f) {
        val fillTop = bodyBottom - bodyHeight * fill
        clipPath(bodyPath) {
            drawRect(
                color = BankPalette.CanDark,
                topLeft = Offset(0f, fillTop),
                size = Size(w, bodyBottom - fillTop)
            )
            // зелёный логотип-«когти» проявляется вместе с заливкой
            val alpha = (fill * 1.6f).coerceIn(0f, 1f)
            val cx = w / 2f
            val cy = (fillTop + bodyBottom) / 2f
            val claw = w * 0.09f
            for (i in -1..1) {
                val x = cx + i * claw * 1.9f
                val p = Path().apply {
                    moveTo(x, cy - claw * 2.2f)
                    lineTo(x + claw, cy + claw * 2.2f)
                    lineTo(x - claw * 0.2f, cy + claw * 1.4f)
                    close()
                }
                drawPath(p, color = BankPalette.MonsterGreen.copy(alpha = alpha))
            }
        }
    }

    // Металлические ободки сверху и снизу
    val rimH = h * 0.055f
    drawRoundRect(
        color = BankPalette.Metal,
        topLeft = Offset(w * 0.08f, 0f),
        size = Size(w * 0.84f, rimH),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(rimH / 2)
    )
    drawRoundRect(
        color = BankPalette.Metal,
        topLeft = Offset(w * 0.10f, bodyBottom),
        size = Size(w * 0.80f, rimH * 0.8f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(rimH / 2)
    )

    // Контур
    drawPath(bodyPath, color = BankPalette.CanDark.copy(alpha = 0.35f), style = Stroke(width = 1.5f))
}

/**
 * Иконка приложения рисуется тем же кодом: при старте приложения заливка
 * соответствует прогрессу текущей смены.
 */
@Composable
fun CanIconPreview(progress: Float) {
    Box(
        Modifier
            .size(108.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Palette.Ink),
        contentAlignment = Alignment.Center
    ) {
        Can(fill = progress, modifier = Modifier.size(width = 48.dp, height = 80.dp))
    }
}
