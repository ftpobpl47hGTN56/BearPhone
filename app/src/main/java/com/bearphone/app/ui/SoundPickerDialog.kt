package com.bearphone.app.ui

import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bearphone.app.sound.SoundItem
import com.bearphone.app.sound.SoundLibrary

/** Что выбрал пользователь. */
sealed interface SoundChoice {
    data object SystemDefault : SoundChoice
    data object Silent : SoundChoice
    data class File(val uri: Uri) : SoundChoice
}

/**
 * Свой пикер звуков вместо системного: показывает ВСЕ звуки телефона
 * (рингтоны, уведомления, будильники, файлы из папок Ringtones/Notifications, музыку),
 * с поиском и прослушиванием по тапу.
 *
 * @param forNotification true — звук уведомлений (свой файл через проводник недоступен:
 *                        такой файл система не сможет проиграть из канала уведомлений)
 */
@Composable
fun SoundPickerDialog(
    title: String,
    forNotification: Boolean,
    current: SoundChoice,
    onDismiss: () -> Unit,
    onPick: (SoundChoice) -> Unit,
) {
    val ctx = LocalContext.current
    var selected by remember { mutableStateOf(current) }
    var query by remember { mutableStateOf("") }

    val items by produceState<List<SoundItem>?>(null) {
        value = SoundLibrary.load(ctx, notificationFirst = forNotification)
    }

    // ---- прослушивание ----
    val preview = remember { PreviewPlayer() }
    DisposableEffect(Unit) { onDispose { preview.stop() } }

    fun select(choice: SoundChoice) {
        selected = choice
        when (choice) {
            SoundChoice.Silent -> preview.stop()
            SoundChoice.SystemDefault -> preview.play(
                ctx,
                RingtoneManager.getDefaultUri(
                    if (forNotification) RingtoneManager.TYPE_NOTIFICATION else RingtoneManager.TYPE_RINGTONE
                )
            )
            is SoundChoice.File -> preview.play(ctx, choice.uri)
        }
    }

    // ---- свой файл через проводник (только для рингтона) ----
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                // провайдер не даёт постоянный доступ — после перезагрузки звук может пропасть
            }
            select(SoundChoice.File(uri))
        }
    }

    val filtered = remember(items, query) {
        val q = query.trim().lowercase()
        items.orEmpty().filter { q.isEmpty() || it.title.lowercase().contains(q) }
    }

    Dialog(
        onDismissRequest = { preview.stop(); onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 32.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxSize()) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 8.dp)
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Поиск") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                )

                val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().lazyScrollbar(listState)) {
                        if (query.isBlank()) {
                            item(key = "default") {
                                ChoiceRow("По умолчанию (системный)", selected == SoundChoice.SystemDefault) {
                                    select(SoundChoice.SystemDefault)
                                }
                            }
                            item(key = "silent") {
                                ChoiceRow("Без звука", selected == SoundChoice.Silent) {
                                    select(SoundChoice.Silent)
                                }
                            }
                            if (!forNotification) {
                                item(key = "file") {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { openFile.launch(arrayOf("audio/*")) }
                                            .padding(horizontal = 20.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.FolderOpen, null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(16.dp))
                                        Text("Выбрать файл…", color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                val sel = selected
                                if (sel is SoundChoice.File && items?.none { it.uri == sel.uri } == true) {
                                    item(key = "picked-file") {
                                        ChoiceRow(titleOf(ctx, sel.uri), true) { select(sel) }
                                    }
                                }
                            }
                        }
    
                        when {
                            items == null -> item(key = "loading") {
                                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                            else -> {
                                var lastGroup: String? = null
                                filtered.forEach { s ->
                                    if (s.group != lastGroup) {
                                        lastGroup = s.group
                                        val g = s.group
                                        item(key = "h-$g", contentType = "header") { GroupHeader(g) }
                                    }
                                    item(key = s.uri.toString(), contentType = "sound") {
                                        val isSel = (selected as? SoundChoice.File)?.uri == s.uri
                                        ChoiceRow(s.title, isSel) { select(SoundChoice.File(s.uri)) }
                                    }
                                }
                                if (!SoundLibrary.hasAudioPermission(ctx)) {
                                    item(key = "noperm") {
                                        Text(
                                            "Звуки из памяти телефона скрыты: нет доступа к аудио.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    ScrollJumpButtons(listState)
                }

                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { preview.stop(); onDismiss() }) { Text("Отмена") }
                    TextButton(onClick = { preview.stop(); onPick(selected) }) { Text("Готово") }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun ChoiceRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick, modifier = Modifier.size(48.dp))
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Проигрывает один звук для прослушивания; новый звук останавливает предыдущий. */
private class PreviewPlayer {
    private var current: Ringtone? = null

    fun play(ctx: android.content.Context, uri: Uri) {
        stop()
        current = try {
            RingtoneManager.getRingtone(ctx, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                play()
            }
        } catch (e: Exception) {
            null
        }
    }

    fun stop() {
        try { current?.stop() } catch (e: Exception) { }
        current = null
    }
}

/** Человеческое название звука ("Atria", "Truecaller Message"). */
fun titleOf(context: android.content.Context, uri: Uri?): String {
    if (uri == null) return "Без звука"
    return try {
        RingtoneManager.getRingtone(context, uri)?.getTitle(context) ?: "Неизвестно"
    } catch (e: Exception) {
        "Неизвестно"
    }
}
