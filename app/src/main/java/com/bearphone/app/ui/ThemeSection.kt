package com.bearphone.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bearphone.app.ui.theme.Presets
import com.bearphone.app.ui.theme.ThemeSettings
import com.bearphone.app.ui.theme.ThemeStore
import com.bearphone.app.ui.theme.hsvColor
import com.bearphone.app.ui.theme.toHsv

/** Блок "Оформление": 4 готовые темы + своя, рамка кнопок, тень. Всё применяется сразу. */
@Composable
fun ThemeSection() {
    val s by ThemeStore.settings.collectAsState()
    val isCustom = s.paletteId == ThemeSettings.CUSTOM_ID

    Column(Modifier.fillMaxWidth()) {
        SectionTitle("Оформление")

        // ---- выбор темы ----
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Presets.all.forEach { p ->
                ThemeSwatch(p.name, p.background, p.accent, selected = s.paletteId == p.id) {
                    ThemeStore.update { it.copy(paletteId = p.id) }
                }
            }
            ThemeSwatch("Своя", s.customBackground, s.customAccent, selected = isCustom, custom = true) {
                ThemeStore.update { it.copy(paletteId = ThemeSettings.CUSTOM_ID) }
            }
        }

        // ---- редактор своей темы ----
        if (isCustom) {
            ColorEditor("Фон", s.customBackground) { c -> ThemeStore.update { it.copy(customBackground = c) } }
            ColorEditor("Акцент", s.customAccent) { c -> ThemeStore.update { it.copy(customAccent = c) } }
        }

        // ---- рамка и тень ----
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                "Рамка кнопок: " + if (s.borderPx == 0) "нет" else "${s.borderPx} px",
                style = MaterialTheme.typography.bodyLarge
            )
            Slider(
                value = s.borderPx.toFloat(),
                onValueChange = { v -> ThemeStore.update { it.copy(borderPx = v.toInt()) } },
                valueRange = 0f..6f,
                steps = 5,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { ThemeStore.update { it.copy(shadow = !it.shadow) } }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Тень кнопок", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "В тёмных темах — мягкое свечение акцентом",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = s.shadow, onCheckedChange = { v -> ThemeStore.update { it.copy(shadow = v) } })
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/** Кружок темы: фон + точка акцента; у выбранного — кольцо. */
@Composable
private fun ThemeSwatch(
    name: String, bg: Color, accent: Color, selected: Boolean,
    custom: Boolean = false, onClick: () -> Unit,
) {
    Column(
        Modifier.width(64.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(48.dp)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = CircleShape
                )
                .padding(4.dp)
                .clip(CircleShape)
                .background(bg),
            contentAlignment = Alignment.Center
        ) {
            if (custom) Icon(Icons.Filled.Palette, null, tint = accent, modifier = Modifier.size(22.dp))
            else Box(Modifier.size(16.dp).clip(CircleShape).background(accent))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Лёгкий редактор цвета: оттенок, насыщенность, яркость (HSV). */
@Composable
private fun ColorEditor(label: String, color: Color, onChange: (Color) -> Unit) {
    // Держим HSV локально: при чёрном/сером цвете оттенок не должен "прыгать" в 0
    val init = remember { color.toHsv() }
    var h by remember { mutableStateOf(init[0]) }
    var sat by remember { mutableStateOf(init[1]) }
    var v by remember { mutableStateOf(init[2]) }
    fun emit() = onChange(hsvColor(h, sat, v))

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(color)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
        GradientSlider(
            value = h / 360f,
            brush = Brush.horizontalGradient((0..6).map { hsvColor(it * 60f, 1f, 1f) }),
            onChange = { h = it * 360f; emit() }
        )
        GradientSlider(
            value = sat,
            brush = Brush.horizontalGradient(listOf(hsvColor(h, 0f, v), hsvColor(h, 1f, v))),
            onChange = { sat = it; emit() }
        )
        GradientSlider(
            value = v,
            brush = Brush.horizontalGradient(listOf(Color.Black, hsvColor(h, sat, 1f))),
            onChange = { v = it; emit() }
        )
    }
}

/** Слайдер, у которого дорожка — градиент (видно, какой цвет получится). */
@Composable
private fun GradientSlider(value: Float, brush: Brush, onChange: (Float) -> Unit) {
    Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(brush)
        )
        Slider(
            value = value,
            onValueChange = onChange,
            colors = SliderDefaults.colors(
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                thumbColor = Color.White,
            ),
        )
    }
}
