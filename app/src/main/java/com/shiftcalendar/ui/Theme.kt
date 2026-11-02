package com.shiftcalendar.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Цвета приложения, зависящие от темы. Раньше это был object с захардкоженной
 * светлой палитрой — тёмная тема меняла только иконку. Теперь палитра выбирается
 * по текущей теме Composable-функцией [palette] и берётся из [AppPalette], поэтому
 * обычная запись `Palette.Ink` внутри @Composable подхватывает тему.
 *
 * Ключевые цвета смены/выходного (красный/зелёный) в тёмной теме светлее,
 * чтобы читаться на тёмном фоне.
 */
data class AppColors(
    val shiftRed: Color,
    val dayOffGreen: Color,
    val ink: Color,            // основной текст
    val paper: Color,          // фон экрана
    val muted: Color,          // второстепенный текст
    val cardBg: Color,         // карточки
    val outline: Color,        // рамки/разделители
    val weekendTint: Color,
    val onInk: Color           // текст на «черных» кнопках-акцентах
) {
    val isDark: Boolean get() = paper.luminance() < 0.5f
}

val LightColors = AppColors(
    shiftRed = Color(0xFFD8433C),
    dayOffGreen = Color(0xFF2E9E5B),
    ink = Color(0xFF0F1115),
    paper = Color(0xFFFFFDFB),
    muted = Color(0xFF8A8F98),
    cardBg = Color(0xFFFFFFFF),
    outline = Color(0xFFE2E4E8),
    weekendTint = Color(0xFFF6F7F9),
    onInk = Color(0xFFFFFFFF)
)

val DarkColors = AppColors(
    shiftRed = Color(0xFFE8675F),
    dayOffGreen = Color(0xFF43B978),
    ink = Color(0xFFF2F3F5),
    paper = Color(0xFF121317),
    muted = Color(0xFF9BA1AB),
    cardBg = Color(0xFF1C1E24),
    outline = Color(0xFF2E3138),
    weekendTint = Color(0xFF191B20),
    onInk = Color(0xFF0F1115)   // в тёмной теме «черная» кнопка светлая
)

val LocalAppColors = staticCompositionLocalOf { LightColors }

/** Материал-схема под нашу палитру: тёмная тема реально перекрашивает всё. */
fun appColorScheme(colors: AppColors) = if (colors.isDark) {
    darkColorScheme(
        primary = colors.ink,
        onPrimary = colors.onInk,
        background = colors.paper,
        onBackground = colors.ink,
        surface = colors.cardBg,
        onSurface = colors.ink,
        surfaceVariant = colors.weekendTint,
        onSurfaceVariant = colors.muted,
        outline = colors.outline,
        error = colors.shiftRed,
        secondary = colors.dayOffGreen
    )
} else {
    lightColorScheme(
        primary = colors.ink,
        onPrimary = colors.onInk,
        background = colors.paper,
        onBackground = colors.ink,
        surface = colors.cardBg,
        onSurface = colors.ink,
        surfaceVariant = colors.weekendTint,
        onSurfaceVariant = colors.muted,
        outline = colors.outline,
        error = colors.shiftRed,
        secondary = colors.dayOffGreen
    )
}

/** Какая палитра сейчас активна. */
@Composable
@ReadOnlyComposable
fun palette(): AppColors = LocalAppColors.current

/**
 * Обёртка над активной палитрой. Свойства читаются из CompositionLocal,
 * поэтому их можно использовать прямо в Composable без `palette().`.
 * Значение по умолчанию (светлое) нужно только для превью без обёртки.
 */
object Palette {
    val ShiftRed: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.shiftRed
    val DayOffGreen: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.dayOffGreen
    val Ink: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.ink
    val Paper: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.paper
    val Muted: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.muted
    val CardBg: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.cardBg
    val Outline: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.outline
    val WeekendTint: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.weekendTint
    val OnInk: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.onInk
}

/**
 * Тема приложения: MaterialTheme с нашей схемой плюс CompositionLocal палитры.
 * [darkTheme] приходит из UiState (переключатель в шапке).
 */
@Composable
fun ShiftCalendarTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(colorScheme = appColorScheme(colors), content = content)
    }
}

/** Для превью/тестов: тема следует системной настройке. */
@Composable
fun ShiftCalendarTheme(content: @Composable () -> Unit) =
    ShiftCalendarTheme(darkTheme = isSystemInDarkTheme(), content = content)

/** Цвет заливки банки в текущей теме (внутренности «Black Monster» всегда тёмные). */
object BankPalette {
    val MonsterGreen: Color get() = Color(0xFF6CC24A)
    val MonsterGreenDark: Color @Composable get() = if (palette().isDark) Color(0xFF9BD873) else Color(0xFF3E8E2A)
    val CanDark: Color get() = Color(0xFF1A1C20)
    val Metal: Color get() = Color(0xFFC9CDD4)
    val Empty: Color @Composable get() = palette().weekendTint
}
