package com.bearphone.app.ui.theme

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Цвета звонка одинаковы во всех темах — зелёный/красный должны читаться сразу
val CallGreen = Color(0xFF22C55E)
val CallRed = Color(0xFFEF4444)

/** Стиль кнопок: рамка и тень. Берётся через LocalBearStyle.current. */
@Immutable
data class BearStyle(
    val border: Dp,
    val borderColor: Color,
    val shadow: Dp,
    val shadowColor: Color,
    val isDark: Boolean,
    /** Фон навбара и подвкладок */
    val tabColor: Color = Color.Unspecified,
    /** Текст/иконки на вкладках (контрастный к tabColor) */
    val onTabColor: Color = Color.Unspecified,
) {
    val borderStroke: BorderStroke? get() = if (border > 0.dp) BorderStroke(border, borderColor) else null
}

val LocalBearStyle = staticCompositionLocalOf {
    BearStyle(1.dp, Color.Gray, 4.dp, Color.Black, isDark = true)
}

/**
 * Рамка + тень для любой кнопки. Ставить ДО clip/background:
 *   Modifier.size(64.dp).bearFrame(CircleShape).clip(CircleShape)...
 */
@Composable
fun Modifier.bearFrame(shape: Shape = CircleShape, borderColor: Color? = null): Modifier {
    val s = LocalBearStyle.current
    var m = this
    if (s.shadow > 0.dp) m = m.shadow(s.shadow, shape, clip = false, ambientColor = s.shadowColor, spotColor = s.shadowColor)
    if (s.border > 0.dp) m = m.border(s.border, borderColor ?: s.borderColor, shape)
    return m
}

/** Строит полную схему Material 3 из двух цветов палитры. */
fun BearPalette.toColorScheme(): ColorScheme {
    val dark = background.luminance() < 0.4f
    val tint = if (dark) Color.White else Color.Black
    val on = if (dark) Color(0xFFE8EBFF) else Color(0xFF14172B)
    // Поверхности = фон, слегка подмешанный к белому/чёрному и к акценту
    fun surf(k: Float) = lerp(lerp(background, tint, k), accent, k * 0.35f)
    val onAccent = if (accent.luminance() > 0.5f) Color(0xFF111111) else Color.White
    val onAccent2 = if (accent2.luminance() > 0.5f) Color(0xFF111111) else Color.White
    val muted = lerp(on, background, 0.35f)
    val outline = lerp(background, on, 0.35f)

    return if (dark) darkColorScheme(
        primary = accent, onPrimary = onAccent,
        primaryContainer = lerp(background, accent, 0.35f), onPrimaryContainer = on,
        secondary = accent2, onSecondary = onAccent2,
        secondaryContainer = lerp(background, accent, 0.25f), onSecondaryContainer = on,
        tertiary = accent2, onTertiary = onAccent2,
        background = background, onBackground = on,
        surface = background, onSurface = on,
        surfaceVariant = surf(0.10f), onSurfaceVariant = muted,
        surfaceContainerLowest = background,
        surfaceContainerLow = surf(0.04f),
        surfaceContainer = surf(0.07f),
        surfaceContainerHigh = surf(0.11f),
        surfaceContainerHighest = surf(0.15f),
        outline = outline, outlineVariant = lerp(background, on, 0.18f),
    ) else lightColorScheme(
        primary = accent, onPrimary = onAccent,
        primaryContainer = lerp(background, accent, 0.20f), onPrimaryContainer = on,
        secondary = accent2, onSecondary = onAccent2,
        secondaryContainer = lerp(background, accent, 0.15f), onSecondaryContainer = on,
        tertiary = accent2, onTertiary = onAccent2,
        background = background, onBackground = on,
        surface = background, onSurface = on,
        surfaceVariant = surf(0.06f), onSurfaceVariant = muted,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = surf(0.03f),
        surfaceContainer = surf(0.05f),
        surfaceContainerHigh = surf(0.08f),
        surfaceContainerHighest = surf(0.11f),
        outline = outline, outlineVariant = lerp(background, on, 0.15f),
    )
}

@Composable
fun BearPhoneTheme(content: @Composable () -> Unit) {
    val settings by ThemeStore.settings.collectAsState()
    val palette = settings.palette
    val scheme = remember(palette) { palette.toColorScheme() }
    val dark = palette.background.luminance() < 0.4f
    val density = LocalDensity.current

    val style = remember(settings, density, scheme) {
        BearStyle(
            // "3 px" — именно физические пиксели, как просили
            border = with(density) { settings.borderPx.toFloat().toDp() },
            borderColor = palette.accent.copy(alpha = if (dark) 0.55f else 0.45f),
            shadow = if (settings.shadow) 6.dp else 0.dp,
            // В тёмных темах тень окрашена акцентом — получается мягкое свечение
            shadowColor = if (dark) palette.accent else Color.Black,
            isDark = dark,
            tabColor = settings.tabColor ?: scheme.surfaceContainer,
            onTabColor = (settings.tabColor ?: scheme.surfaceContainer).let {
                if (it.luminance() > 0.5f) Color(0xFF14172B) else Color(0xFFE8EBFF)
            },
        )
    }

    // Иконки статус-бара: светлые на тёмной теме, тёмные на светлой
    val activity = LocalContext.current as? ComponentActivity
    LaunchedEffect(dark, activity) {
        val t = Color.Transparent.toArgb()
        activity?.enableEdgeToEdge(
            statusBarStyle = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t),
            navigationBarStyle = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t),
        )
    }

    CompositionLocalProvider(LocalBearStyle provides style) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
