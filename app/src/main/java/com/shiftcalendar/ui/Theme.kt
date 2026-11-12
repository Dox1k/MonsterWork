package com.shiftcalendar.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ButtonDefaults
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
    val onInk: Color,          // текст на «черных» кнопках-акцентах
    val disabledBg: Color = ink.copy(alpha = 0.08f), // фон выключенной кнопки
    /**
     * Тонкая чёрная линия-разделитель. Ею отделяются друг от друга цветные
     * половины дня, когда на день легли два профиля (решение клиента).
     */
    val hairline: Color = Color(0xFF000000)
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
    // Текст не белый: чистый #FFF на тёмном фоне слепит и «звенит» по краям
    // букв. #DCDFE4 читается так же хорошо, но не бьёт по глазам.
    ink = Color(0xFFDCDFE4),
    paper = Color(0xFF101114),
    muted = Color(0xFF9BA1AB),
    cardBg = Color(0xFF1A1C21),
    outline = Color(0xFF2C2F35),
    weekendTint = Color(0xFF17191D),
    onInk = Color(0xFF0F1115)   // в тёмной теме «черная» кнопка светлая
)

val LocalAppColors = staticCompositionLocalOf { LightColors }

/**
 * Материал-схема под нашу палитру: тёмная тема реально перекрашивает всё.
 *
 * Заполняем и «редкие» роли (errorContainer, tertiary, inverseSurface,
 * surfaceContainer*): если их не задать, material3 подставит свои светлые
 * значения — отсюда и брались слепящие белые кнопки в тёмной теме.
 */
fun appColorScheme(colors: AppColors) = if (colors.isDark) {
    darkColorScheme(
        primary = colors.ink,
        onPrimary = colors.onInk,
        primaryContainer = colors.weekendTint,
        onPrimaryContainer = colors.ink,
        secondary = colors.dayOffGreen,
        onSecondary = colors.onInk,
        secondaryContainer = colors.dayOffGreen.copy(alpha = 0.18f),
        onSecondaryContainer = colors.ink,
        tertiary = colors.shiftRed,
        onTertiary = colors.onInk,
        background = colors.paper,
        onBackground = colors.ink,
        surface = colors.cardBg,
        onSurface = colors.ink,
        surfaceVariant = colors.weekendTint,
        onSurfaceVariant = colors.muted,
        surfaceTint = colors.cardBg,
        surfaceContainerLowest = colors.paper,
        surfaceContainerLow = colors.weekendTint,
        surfaceContainer = colors.cardBg,
        surfaceContainerHigh = colors.cardBg,
        surfaceContainerHighest = colors.outline,
        inverseSurface = colors.ink,
        inverseOnSurface = colors.paper,
        inversePrimary = colors.paper,
        outline = colors.outline,
        outlineVariant = colors.outline,
        error = colors.shiftRed,
        onError = colors.onInk,
        errorContainer = colors.shiftRed.copy(alpha = 0.18f),
        onErrorContainer = colors.ink
    )
} else {
    lightColorScheme(
        primary = colors.ink,
        onPrimary = colors.onInk,
        primaryContainer = colors.weekendTint,
        onPrimaryContainer = colors.ink,
        secondary = colors.dayOffGreen,
        onSecondary = colors.onInk,
        secondaryContainer = colors.dayOffGreen.copy(alpha = 0.14f),
        onSecondaryContainer = colors.ink,
        tertiary = colors.shiftRed,
        onTertiary = colors.onInk,
        background = colors.paper,
        onBackground = colors.ink,
        surface = colors.cardBg,
        onSurface = colors.ink,
        surfaceVariant = colors.weekendTint,
        onSurfaceVariant = colors.muted,
        surfaceTint = colors.cardBg,
        surfaceContainerLowest = colors.cardBg,
        surfaceContainerLow = colors.paper,
        surfaceContainer = colors.cardBg,
        surfaceContainerHigh = colors.cardBg,
        surfaceContainerHighest = colors.weekendTint,
        inverseSurface = colors.ink,
        inverseOnSurface = colors.paper,
        inversePrimary = colors.paper,
        outline = colors.outline,
        outlineVariant = colors.outline,
        error = colors.shiftRed,
        onError = colors.onInk,
        errorContainer = colors.shiftRed.copy(alpha = 0.12f),
        onErrorContainer = colors.ink
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
    val Hairline: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.hairline
    val DisabledBg: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.disabledBg
}

/**
 * Цвета залитой кнопки-акцента: включена — Ink на OnInk, выключена — полупрозрачный
 * Ink с приглушённым OnInk. Без явного disabled*-цвета material3 рисует выключенную
 * кнопку белым (surfaceVariant светлой схемы) — в тёмной теме это слепило.
 */
@Composable
fun accentButtonColors() = ButtonDefaults.buttonColors(
    containerColor = Palette.Ink,
    contentColor = Palette.OnInk,
    disabledContainerColor = Palette.DisabledBg,
    disabledContentColor = Palette.Muted.copy(alpha = 0.7f)
)

/** То же для OutlinedButton: фон карточки вместо белого по умолчанию. */
@Composable
fun outlinedButtonColors() = ButtonDefaults.outlinedButtonColors(
    containerColor = Palette.CardBg,
    contentColor = Palette.Ink,
    disabledContainerColor = Palette.DisabledBg,
    disabledContentColor = Palette.Muted.copy(alpha = 0.7f)
)

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

/** Цвета заливки банки в текущей теме (внутренности «Black Monster» всегда тёмные). */
object BankPalette {
    val MonsterGreen: Color get() = Color(0xFF6CC24A)
    val MonsterGreenDark: Color @Composable get() = if (palette().isDark) Color(0xFF9BD873) else Color(0xFF3E8E2A)
    val CanDark: Color get() = Color(0xFF1A1C20)
    val Metal: Color get() = Color(0xFFC9CDD4)
    val Empty: Color @Composable get() = palette().weekendTint
}

/**
 * Цвета смен внутри одного дня: первая смена — красный (как раньше у одиночной
 * смены), вторая — синий, третья — фиолетовый, четвёртая — оранжевый, дальше
 * по кругу. Выходной в любом слоте — зелёный.
 *
 * Цвет — это не украшение, а способ различить независимые смены дня и в сетке,
 * и в списке редактора; поэтому он не берётся из темы (там один «красный смены»),
 * но в тёмной теме используются более светлые оттенки, чтобы читаться.
 */
object ShiftSlotColors {

    // Цвета заданы ARGB-числами (Long), чтобы их можно было проверить юнит-тестом
    // без Compose: порядок слотов — часть поведения «смены разного цвета».
    /** Первая смена дня — красный, как раньше у одиночной смены. */
    val LIGHT: List<Long> = listOf(
        0xFFD8433C, // красный
        0xFF2F6FDB, // синий
        0xFF8E4EC6, // фиолетовый
        0xFFD98324, // оранжевый
        0xFF148F8A  // бирюзовый
    )

    /** Те же слоты в тёмной теме — светлее, чтобы читались на тёмном фоне. */
    val DARK: List<Long> = listOf(
        0xFFE8675F,
        0xFF6C9BEC,
        0xFFB47FE8,
        0xFFE8A559,
        0xFF4FC7C0
    )

    /** Сколько слотов задано явно; дальше цвета повторяются по кругу. */
    val slotCount: Int = LIGHT.size

    /** Выходной — зелёный в любой теме (светлее в тёмной). */
    const val LIGHT_DAYOFF: Long = 0xFF2E9E5B
    const val DARK_DAYOFF: Long = 0xFF43B978

    /**
     * ARGB слота [index] (0 — первая смена дня). Индекс по модулю: шестая смена
     * дня снова красная. Выходной в любом слоте — зелёный.
     */
    fun argb(index: Int, isDayOff: Boolean = false, dark: Boolean = false): Long =
        if (isDayOff) (if (dark) DARK_DAYOFF else LIGHT_DAYOFF)
        else (if (dark) DARK else LIGHT)[Math.floorMod(index, slotCount)]

    /** Цвет слота текущей темы. */
    @Composable
    @ReadOnlyComposable
    fun colorFor(index: Int, isDayOff: Boolean = false): Color =
        Color(argb(index, isDayOff, palette().isDark))
}
