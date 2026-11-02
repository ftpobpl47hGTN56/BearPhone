package com.bearphone.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import com.bearphone.app.ui.theme.bearFrame
import com.bearphone.app.block.BlockStore
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.Icon
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import com.bearphone.app.data.HiddenSmsStore

@Composable
fun SettingsScreen(
    isDefaultDialer: Boolean,
    isDefaultSms: Boolean,
    sims: List<com.bearphone.app.call.SimAccount>,
    onRequestSms: () -> Unit,
    missingPermissions: Boolean,
    onRequestDefault: () -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    // Подстраница "Блокировка" открывается внутри вкладки "Настройки"
    var blockingOpen by rememberSaveable { mutableStateOf(false) }
    if (blockingOpen) {
        BlockingScreen(onBack = { blockingOpen = false })
        return
    }
    // Подстраница "Темы и оформление"
    var themesOpen by rememberSaveable { mutableStateOf(false) }
    if (themesOpen) {
        ThemesScreen(onBack = { themesOpen = false })
        return
    }
    val block by BlockStore.state.collectAsState()

    val scroll = rememberScrollState()
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .columnScrollbar(scroll) // до verticalScroll — рисуем в видимой области
                .verticalScroll(scroll)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth().padding(16.dp).bearFrame(RoundedCornerShape(12.dp))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        if (isDefaultDialer) "BearPhone — звонилка по умолчанию ✓"
                        else "BearPhone не звонилка по умолчанию",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (!isDefaultDialer) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Без этого входящие звонки будет показывать системное приложение.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onRequestDefault, modifier = Modifier.bearFrame()) { Text("Сделать по умолчанию") }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (isDefaultSms) "BearPhone — SMS-приложение по умолчанию ✓"
                        else "BearPhone не SMS-приложение по умолчанию",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (!isDefaultSms) {
                        com.bearphone.app.data.SmsRepository.defaultAppLabel(LocalContext.current)?.let { label ->
                            Text(
                                "Сейчас SMS принимает: $label",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Тогда BearPhone сам принимает SMS, показывает уведомления, отмечает прочитанные " +
                                "и по-настоящему удаляет. MMS не поддерживаются.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onRequestSms, modifier = Modifier.bearFrame()) { Text("Сделать SMS по умолчанию") }
                    }
                    if (missingPermissions) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = onRequestPermissions, modifier = Modifier.bearFrame()) { Text("Выдать разрешения") }
                    }
                }
            }
    
            // ---- блокировка ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { blockingOpen = true }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Block, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Блокировка номеров", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Правил: ${block.rules.size} · заблокировано: ${block.log.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
            }
    
            // ---- темы — отдельный раздел ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { themesOpen = true }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(androidx.compose.material.icons.Icons.Filled.Palette, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Темы и оформление", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Готовые темы, редактор цветов, вкладки, рамки и тени",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
            }

            // ---- SIM для вызовов ----
            SimSection(sims)

            SoundSettingsSection()
    
            // Скрытые SMS (Android не дал удалить их по-настоящему) — можно вернуть
            var hiddenCount by remember { mutableIntStateOf(HiddenSmsStore.count()) }
            if (hiddenCount > 0) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { HiddenSmsStore.restoreAll(); hiddenCount = 0 },
                    modifier = Modifier.padding(horizontal = 16.dp).bearFrame()
                ) { Text("Показать скрытые SMS ($hiddenCount)") }
            }
    
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onOpenAppSettings,
                modifier = Modifier.padding(horizontal = 16.dp).bearFrame()
            ) { Text("Системные настройки приложения") }
    
            Text(
                "BearPhone " + appVersion(LocalContext.current),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
        ScrollJumpButtons(scroll)
    }
}

private fun appVersion(ctx: android.content.Context): String = try {
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
} catch (e: Exception) {
    ""
}

/** Выбор SIM для исходящих: SIM 1 / SIM 2 / Всегда спрашивать. */
@Composable
private fun SimSection(sims: List<com.bearphone.app.call.SimAccount>) {
    val ctx = LocalContext.current
    var mode by remember { mutableStateOf(com.bearphone.app.call.SimAccounts.mode(ctx)) }
    SectionTitle("SIM для вызовов")
    if (sims.size < 2) {
        Text(
            if (sims.isEmpty()) "SIM-карта не найдена (или нет разрешения «Телефон»)"
            else "Одна SIM: ${sims.first().title}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        return
    }
    com.bearphone.app.call.SimMode.entries.forEach { m ->
        val label = when (m) {
            com.bearphone.app.call.SimMode.SIM1 -> sims[0].title
            com.bearphone.app.call.SimMode.SIM2 -> sims[1].title
            com.bearphone.app.call.SimMode.ASK -> m.title
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { mode = m; com.bearphone.app.call.SimAccounts.setMode(ctx, m) }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.RadioButton(
                selected = mode == m,
                onClick = { mode = m; com.bearphone.app.call.SimAccounts.setMode(ctx, m) }
            )
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
    Text(
        "На экране набора всегда есть две кнопки — SIM 1 и SIM 2. Настройка действует для звонков " +
            "из истории, контактов, карточек и SMS.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

/** Отдельный раздел "Темы и оформление". */
@Composable
fun ThemesScreen(onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
            }
            Text("Темы и оформление", style = MaterialTheme.typography.titleLarge)
        }
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().columnScrollbar(scroll).verticalScroll(scroll)
            ) {
                ThemeSection()
                Spacer(Modifier.height(24.dp))
            }
            ScrollJumpButtons(scroll)
        }
    }
}
