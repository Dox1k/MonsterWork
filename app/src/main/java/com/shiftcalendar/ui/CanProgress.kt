package com.shiftcalendar.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.shiftcalendar.data.Shift
import com.shiftcalendar.model.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Банка-прогресс смены: заливка растёт снизу вверх по доле прошедших часов.
 * Нарисована векторно (по мотивам Black Monster), картинка-фото не нужна.
 *
 * Сама банка внутри всегда тёмная — так задумано брендом, поэтому её цвета
 * не берём из палитры темы. Совсем светлые части (пустая банка) — из темы.
 */
@Composable
fun BankProgressCard(
    shift: Shift,
    now: LocalDateTime,
    threshold: LocalTime = LocalTime.of(22, 0),
    nightEnd: LocalTime = LocalTime.of(6, 0),
    percent: Double = 20.0,
    onOpen: () -> Unit
) {
    val progress by animateFloatAsState(
        targetValue = shiftProgress(shift, now),
        animationSpec = tween(durationMillis = 900),
        label = "bank"
    )
    val date = LocalDate.parse(shift.date, ISO)
    val worked = workedHours(shift, now)
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
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "ИДЁТ СМЕНА",
                modifier = Modifier.weight(1f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = BankPalette.MonsterGreenDark
            )
            // Живые значения перерисовываются тиком раз в минуту.
            Text(
                hhmm(now),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = Palette.Muted
            )
        }
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
                    "Прошло ${(progress * 100).toInt()}% · ${formatHours(worked)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BankPalette.MonsterGreenDark
                )
                if (shift.rate > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Заработано ${formatMoney(liveEarnings(shift, now, threshold, nightEnd, percent))}",
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

/**
 * Банки монстров: одна банка за каждый час текущей смены.
 * 12-часовая смена — 12 банок, 2-часовая — 2. Банок может быть много,
 * поэтому список горизонтально прокручивается, а не сжимается.
 */
@Composable
fun MonsterBanks(shift: Shift?, now: LocalDateTime) {
    val total = shift?.let(::canCountFor) ?: 0
    val elapsed = shift?.let { elapsedHours(it, now) } ?: 0.0
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.CardBg)
            .border(1.dp, Palette.Outline, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Банки монстров",
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Palette.Ink
            )
            Text(
                if (shift == null) "нет смены"
                else "${formatHours(elapsed)} из ${total}",
                fontSize = 11.sp,
                color = Palette.Muted
            )
        }
        Spacer(Modifier.height(8.dp))
        if (shift == null || total == 0) {
            Text(
                "Сегодня смены нет",
                fontSize = 11.sp,
                color = Palette.Muted
            )
            return@Column
        }
        LazyRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(List(total) { it }, key = { i, _ -> i }) { index, _ ->
                Can(
                    fill = canFill(index, elapsed),
                    modifier = Modifier.size(width = 28.dp, height = 50.dp)
                )
            }
        }
    }
}

/**
 * Банка: пока прогресс 0 — светлый контур, по мере смены тёмная заливка
 * поднимается снизу вверх и проявляется логотип-«когти».
 */
@Composable
fun Can(fill: Float, modifier: Modifier = Modifier) {
    val empty = BankPalette.Empty
    Canvas(modifier = modifier) {
        drawCan(fill.coerceIn(0f, 1f), empty)
    }
}

private fun DrawScope.drawCan(fill: Float, empty: Color) {
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
    drawPath(bodyPath, color = empty)

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
