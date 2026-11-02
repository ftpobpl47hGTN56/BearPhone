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
    val block by BlockStore.state.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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

        SoundSettingsSection()

        ThemeSection()

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
}

private fun appVersion(ctx: android.content.Context): String = try {
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
} catch (e: Exception) {
    ""
}
