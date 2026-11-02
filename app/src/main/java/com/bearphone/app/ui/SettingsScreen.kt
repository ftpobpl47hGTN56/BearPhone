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

@Composable
fun SettingsScreen(
    isDefaultDialer: Boolean,
    missingPermissions: Boolean,
    onRequestDefault: () -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
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

        SoundSettingsSection()

        ThemeSection()

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
