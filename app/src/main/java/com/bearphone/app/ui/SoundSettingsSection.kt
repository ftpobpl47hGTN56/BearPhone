package com.bearphone.app.ui

import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bearphone.app.sound.NotificationChannels
import com.bearphone.app.sound.PickRingtoneContract
import com.bearphone.app.sound.SoundPrefs

/**
 * Блок "Звуки" для экрана настроек (Jetpack Compose, Material 3).
 * Вставьте SoundSettingsSection() в Ваш SettingsScreen.
 */
@Composable
fun SoundSettingsSection() {
    val context = LocalContext.current
    val prefs = remember { SoundPrefs(context) }

    var ringtoneTitle by remember { mutableStateOf(titleOf(context, prefs.ringtoneForPicker())) }
    var notifTitle by remember { mutableStateOf(titleOf(context, prefs.notificationForPicker())) }
    var vibrate by remember { mutableStateOf(prefs.vibrateOnRing) }

    val ringtoneLauncher = rememberLauncherForActivityResult(PickRingtoneContract()) { result ->
        if (result is PickRingtoneContract.Result.Picked) {
            prefs.saveRingtone(result.uri)
            ringtoneTitle = titleOf(context, prefs.ringtoneForPicker())
        }
    }

    val notifLauncher = rememberLauncherForActivityResult(PickRingtoneContract()) { result ->
        if (result is PickRingtoneContract.Result.Picked) {
            prefs.saveNotification(result.uri)
            NotificationChannels.ensure(context)   // пересоздать канал с новым звуком
            notifTitle = titleOf(context, prefs.notificationForPicker())
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Text(
            "Звуки",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        SettingRow("Рингтон", ringtoneTitle) {
            ringtoneLauncher.launch(
                PickRingtoneContract.Input(
                    type = RingtoneManager.TYPE_RINGTONE,
                    existing = prefs.ringtoneForPicker(),
                    title = "Рингтон BearPhone"
                )
            )
        }

        SettingRow("Звук уведомлений", notifTitle) {
            notifLauncher.launch(
                PickRingtoneContract.Input(
                    type = RingtoneManager.TYPE_NOTIFICATION,
                    existing = prefs.notificationForPicker(),
                    title = "Звук пропущенных"
                )
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clickable { vibrate = !vibrate; prefs.vibrateOnRing = vibrate }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Вибрация при звонке", Modifier.weight(1f))
            Switch(checked = vibrate, onCheckedChange = { vibrate = it; prefs.vibrateOnRing = it })
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Человеческое название звука ("Atria", "По умолчанию", "Без звука"). */
private fun titleOf(context: android.content.Context, uri: Uri?): String {
    if (uri == null) return "Без звука"
    return try {
        RingtoneManager.getRingtone(context, uri)?.getTitle(context) ?: "Неизвестно"
    } catch (e: Exception) {
        "Неизвестно"
    }
}
