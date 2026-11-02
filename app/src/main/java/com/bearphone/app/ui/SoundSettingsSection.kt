package com.bearphone.app.ui

import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.bearphone.app.sound.SoundLibrary
import com.bearphone.app.sound.SoundPrefs

/** Какой пикер сейчас открыт. */
private enum class Picker { NONE, RINGTONE, NOTIFICATION }

/**
 * Блок "Звуки" экрана настроек. Использует свой SoundPickerDialog
 * (системный пикер на Xiaomi/HyperOS показывает далеко не все звуки).
 */
@Composable
fun SoundSettingsSection() {
    val context = LocalContext.current
    val prefs = remember { SoundPrefs(context) }

    var ringtoneTitle by remember { mutableStateOf(describe(context, prefs.ringtoneForPicker(), RingtoneManager.TYPE_RINGTONE)) }
    var notifTitle by remember { mutableStateOf(describe(context, prefs.notificationForPicker(), RingtoneManager.TYPE_NOTIFICATION)) }
    var vibrate by remember { mutableStateOf(prefs.vibrateOnRing) }
    var picker by remember { mutableStateOf(Picker.NONE) }
    var pending by remember { mutableStateOf(Picker.NONE) }

    // Перед первым открытием просим доступ к аудио — иначе видны только системные звуки.
    // Отказ не мешает: пикер откроется в любом случае.
    val audioPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        picker = pending
        pending = Picker.NONE
    }

    fun open(p: Picker) {
        if (SoundLibrary.hasAudioPermission(context)) picker = p
        else { pending = p; audioPerm.launch(SoundLibrary.audioPermission) }
    }

    Column(Modifier.fillMaxWidth()) {
        Text(
            "Звуки",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        SettingRow("Рингтон", ringtoneTitle) { open(Picker.RINGTONE) }
        SettingRow("Звук уведомлений", notifTitle) { open(Picker.NOTIFICATION) }

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

    when (picker) {
        Picker.RINGTONE -> SoundPickerDialog(
            title = "Рингтон",
            forNotification = false,
            current = choiceOf(prefs.ringtoneForPicker(), RingtoneManager.TYPE_RINGTONE),
            onDismiss = { picker = Picker.NONE },
            onPick = { choice ->
                prefs.saveRingtone(uriOf(choice, RingtoneManager.TYPE_RINGTONE))
                ringtoneTitle = describe(context, prefs.ringtoneForPicker(), RingtoneManager.TYPE_RINGTONE)
                picker = Picker.NONE
            }
        )
        Picker.NOTIFICATION -> SoundPickerDialog(
            title = "Звук уведомлений",
            forNotification = true,
            current = choiceOf(prefs.notificationForPicker(), RingtoneManager.TYPE_NOTIFICATION),
            onDismiss = { picker = Picker.NONE },
            onPick = { choice ->
                prefs.saveNotification(uriOf(choice, RingtoneManager.TYPE_NOTIFICATION))
                NotificationChannels.ensure(context) // пересоздать канал с новым звуком
                notifTitle = describe(context, prefs.notificationForPicker(), RingtoneManager.TYPE_NOTIFICATION)
                picker = Picker.NONE
            }
        )
        Picker.NONE -> Unit
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

/** Uri из SoundPrefs (для пикера) -> выбор в диалоге. */
private fun choiceOf(uri: Uri?, type: Int): SoundChoice = when {
    uri == null -> SoundChoice.Silent
    uri == RingtoneManager.getDefaultUri(type) -> SoundChoice.SystemDefault
    else -> SoundChoice.File(uri)
}

/** Выбор в диалоге -> Uri для SoundPrefs.save*() (там default Uri = "следовать системе"). */
private fun uriOf(choice: SoundChoice, type: Int): Uri? = when (choice) {
    SoundChoice.Silent -> null
    SoundChoice.SystemDefault -> RingtoneManager.getDefaultUri(type)
    is SoundChoice.File -> choice.uri
}

private fun describe(ctx: android.content.Context, uri: Uri?, type: Int): String = when {
    uri == null -> "Без звука"
    uri == RingtoneManager.getDefaultUri(type) -> "По умолчанию (${titleOf(ctx, uri).removePrefix("По умолчанию (").removeSuffix(")")})"
    else -> titleOf(ctx, uri).removeSuffix(".ogg").removeSuffix(".mp3")
}
