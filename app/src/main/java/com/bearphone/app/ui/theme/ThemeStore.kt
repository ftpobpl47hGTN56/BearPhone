package com.bearphone.app.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Палитра темы: всё остальное (поверхности, текст, рамки) вычисляется из неё. */
data class BearPalette(
    val id: String,
    val name: String,
    val background: Color,
    val accent: Color,
    /** Второй акцент: статусы, избранное, таймер звонка */
    val accent2: Color,
)

/** Настройки оформления, общие для всех тем. */
data class ThemeSettings(
    val paletteId: String = Presets.AURORA.id,
    val customBackground: Color = Color(0xFF101828),
    val customAccent: Color = Color(0xFFE0457B),
    /** Толщина рамки кнопок в физических пикселях (0 — без рамки) */
    val borderPx: Int = 3,
    val shadow: Boolean = true,
) {
    val palette: BearPalette
        get() = Presets.all.firstOrNull { it.id == paletteId }
            ?: BearPalette(
                CUSTOM_ID, "Своя",
                customBackground, customAccent,
                accent2 = rotateHue(customAccent, 150f),
            )

    companion object {
        const val CUSTOM_ID = "custom"
    }
}

object Presets {
    val AURORA = BearPalette("aurora", "Северное сияние", Color(0xFF070B1F), Color(0xFF3D6BFF), Color(0xFF2EE6B8))
    val POLAR = BearPalette("polar", "Полярная ночь", Color(0xFF000000), Color(0xFF00C2FF), Color(0xFF7CFFCB))
    val HONEY = BearPalette("honey", "Медвежий мёд", Color(0xFF1A120B), Color(0xFFFFB020), Color(0xFFFF7A59))
    val SNOW = BearPalette("snow", "Снежный", Color(0xFFF3F6FF), Color(0xFF2B55E6), Color(0xFF00A884))

    val all = listOf(AURORA, POLAR, HONEY, SNOW)
}

/**
 * Хранилище темы. Один StateFlow на всё приложение: смена темы в настройках
 * сразу перекрашивает и главный экран, и экран звонка.
 */
object ThemeStore {

    private var prefs: SharedPreferences? = null
    private val _settings = MutableStateFlow(ThemeSettings())
    val settings: StateFlow<ThemeSettings> = _settings.asStateFlow()

    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("bear_theme", Context.MODE_PRIVATE)
        prefs = p
        val d = ThemeSettings()
        _settings.value = ThemeSettings(
            paletteId = p.getString(K_PALETTE, d.paletteId) ?: d.paletteId,
            customBackground = Color(p.getInt(K_BG, d.customBackground.toArgb())),
            customAccent = Color(p.getInt(K_ACCENT, d.customAccent.toArgb())),
            borderPx = p.getInt(K_BORDER, d.borderPx),
            shadow = p.getBoolean(K_SHADOW, d.shadow),
        )
    }

    fun update(transform: (ThemeSettings) -> ThemeSettings) {
        val s = transform(_settings.value)
        _settings.value = s
        prefs?.edit()
            ?.putString(K_PALETTE, s.paletteId)
            ?.putInt(K_BG, s.customBackground.toArgb())
            ?.putInt(K_ACCENT, s.customAccent.toArgb())
            ?.putInt(K_BORDER, s.borderPx)
            ?.putBoolean(K_SHADOW, s.shadow)
            ?.apply()
    }

    private const val K_PALETTE = "palette"
    private const val K_BG = "custom_bg"
    private const val K_ACCENT = "custom_accent"
    private const val K_BORDER = "border_px"
    private const val K_SHADOW = "shadow"
}

// ---------------- цветовая математика (HSV) ----------------

/** [hue 0..360, saturation 0..1, value 0..1] */
fun Color.toHsv(): FloatArray {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    return hsv
}

fun hsvColor(h: Float, s: Float, v: Float): Color =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(h.coerceIn(0f, 360f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))))

fun rotateHue(c: Color, degrees: Float): Color {
    val (h, s, v) = c.toHsv()
    return hsvColor((h + degrees) % 360f, s, v.coerceAtLeast(0.6f))
}
