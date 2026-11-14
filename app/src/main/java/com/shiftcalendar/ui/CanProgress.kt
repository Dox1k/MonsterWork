package com.shiftcalendar.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
            Can(
                fill = progress,
                modifier = Modifier.size(width = 62.dp, height = 104.dp),
                particles = progress,
                seed = shift.id
            )
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
                val f = canFill(index, elapsed)
                Can(
                    fill = f,
                    modifier = Modifier.size(width = 28.dp, height = 50.dp),
                    // Частицы идут в той банке, чей час сейчас отрабатывается:
                    // зелёная «работа» видна ровно в текущем часе смены.
                    particles = f,
                    seed = shift.id * 31L + index
                )
            }
        }
    }
}

/**
 * Банка: пока прогресс 0 — светлый контур, по мере смены тёмная заливка
 * поднимается снизу вверх и проявляется логотип-«когти».
 * [particles] — доля прошедшей смены (0..1): внутри заливки поднимаются
 * зелёные частицы, которые заполняют банку снизу вверх, как в прошлой версии.
 * [seed] — стабильное зерно частиц (id смены): без него частицы «дрожали» бы
 * при каждой перерисовке.
 *
 * Анимация: пока банка наливается и смена идёт ([particles] < 1), частицы
 * непрерывно поднимаются от дна к поверхности жидкости — бесконечный цикл,
 * чтобы заполнение было видно на глаз, а не выглядело статичной картинкой.
 */
@Composable
fun Can(fill: Float, modifier: Modifier = Modifier, particles: Float = 0f, seed: Long = 0L) {
    val empty = BankPalette.Empty
    // Плавный подъём частиц: одна фаза на ~2.4 с. Идёт, пока смена не закончена.
    val live = particles > 0f && particles < 1f
    val phase = rememberInfiniteTransition(label = "can-particles")
    val drift by phase.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particles-rise"
    )
    // Плавная заливка: при завершении часа банка «доливается» анимацией.
    val animatedFill by animateFloatAsState(
        targetValue = fill.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 700),
        label = "can-fill"
    )
    Canvas(modifier = modifier) {
        drawCan(
            fill = animatedFill,
            empty = empty,
            particles = particles.coerceIn(0f, 1f),
            seed = seed,
            drift = if (live) drift else 0f
        )
    }
}

private fun DrawScope.drawCan(
    fill: Float,
    empty: Color,
    particles: Float = 0f,
    seed: Long = 0L,
    drift: Float = 0f
) {
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
            // Зелёные частицы смены: пока идёт смена, внутри заливки
            // поднимаются вверх искорки. Их высота привязана к прогрессу,
            // поэтому частицы не «застывают» без перерисовки.
            drawShiftParticles(fillTop, bodyBottom, w, particles, seed, drift)
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
 * Зелёные частицы внутри заливки банки: маленькие точки и полоски, которые
 * заполняют банку снизу вверх. [drift] — фаза анимации (0..1, бесконечный
 * цикл): каждый кадр частицы поднимаются выше, поэтому видно, что жидкость
 * «кипит» и наполняет банку, а не нарисована статично.
 *
 * Частицы детерминированы по [seed] (id смены): набор точек и их стартовые
 * позиции не зависят от времени кадра, поэтому картинка не дрожит — двигается
 * весь слой ровно на [drift].
 */
private fun DrawScope.drawShiftParticles(
    fillTop: Float,
    bodyBottom: Float,
    w: Float,
    particles: Float,
    seed: Long,
    drift: Float
) {
    if (particles <= 0f) return
    val span = (bodyBottom - fillTop).coerceAtLeast(1f)
    val rnd = java.util.Random(if (seed != 0L) seed else 1L)
    val count = (18 + (particles * 22).toInt()).coerceAtMost(46)
    val green = BankPalette.MonsterGreen
    // Глубина, на которую частицы «налиты» в банку: снизу вверх по мере смены.
    val level = span * particles
    repeat(count) { i ->
        // У каждой частицы своя стартовая позиция у дна и свой сдвиг фазы.
        // За цикл [drift] 0→1 она поднимается на всю высоту жидкости и
        // появляется снова снизу — непрерывный поток, заполняющий банку.
        val start = rnd.nextFloat()
        val rise = ((drift + start) % 1f)
        val x = w * (0.12f + 0.76f * rnd.nextFloat())
        // Частица живёт только в пределах налитой жидкости.
        val y = bodyBottom - level * rise
        if (y < fillTop || y > bodyBottom) return@repeat
        // Выше к поверхности — ярче: виден «уровень кипения».
        val alpha = (0.30f + 0.70f * (1f - rise)).coerceIn(0f, 1f)
        val r = (w * 0.035f) * (0.6f + 0.8f * rnd.nextFloat())
        if (i % 3 == 0) {
            // Полоска-пузырёк: чуть выше обычной точки.
            drawRoundRect(
                color = green.copy(alpha = alpha * 0.75f),
                topLeft = Offset(x - r * 0.5f, y - r * 1.6f),
                size = Size(r, r * 2.6f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r * 0.5f)
            )
        } else {
            drawCircle(color = green.copy(alpha = alpha), radius = r, center = Offset(x, y))
        }
    }
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
