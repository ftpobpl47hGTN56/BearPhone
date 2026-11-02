package com.bearphone.app.sound

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SoundItem(
    val uri: Uri,
    val title: String,
    /** Заголовок группы в списке: "Рингтоны", "Уведомления", "Будильники", "Музыка" */
    val group: String,
)

/**
 * Собирает ВСЕ звуки телефона, а не только то, что показывает системный пикер:
 *  1. системные рингтоны / уведомления / будильники (RingtoneManager);
 *  2. файлы в папках Ringtones, Notifications, Alarms (туда кладут звуки Truecaller,
 *     мессенджеры, скачанные мелодии);
 *  3. вся музыка на телефоне (чтобы поставить на звонок любую песню).
 *
 * Пункты 2–3 требуют READ_MEDIA_AUDIO (Android 13+) или READ_EXTERNAL_STORAGE (до 12).
 */
object SoundLibrary {

    const val GROUP_RINGTONES = "Рингтоны"
    const val GROUP_NOTIFICATIONS = "Уведомления"
    const val GROUP_ALARMS = "Будильники"
    const val GROUP_MUSIC = "Музыка"

    val audioPermission: String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasAudioPermission(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, audioPermission) == PackageManager.PERMISSION_GRANTED

    /**
     * @param notificationFirst true — сначала уведомления (для выбора звука уведомлений),
     *                          false — сначала рингтоны.
     */
    suspend fun load(ctx: Context, notificationFirst: Boolean): List<SoundItem> =
        withContext(Dispatchers.IO) {
            val seen = HashSet<String>()      // дедуп по Uri
            val seenTitles = HashSet<String>() // и по "группа+название" (один файл бывает в двух таблицах)
            val out = ArrayList<SoundItem>()

            fun add(item: SoundItem) {
                val key = item.group + "|" + item.title.lowercase()
                if (seen.add(item.uri.toString()) && seenTitles.add(key)) out += item
            }

            val order = if (notificationFirst)
                listOf(RingtoneManager.TYPE_NOTIFICATION, RingtoneManager.TYPE_RINGTONE, RingtoneManager.TYPE_ALARM)
            else
                listOf(RingtoneManager.TYPE_RINGTONE, RingtoneManager.TYPE_NOTIFICATION, RingtoneManager.TYPE_ALARM)

            // 1–2. Системные + файлы, помеченные как рингтон/уведомление/будильник
            for (type in order) {
                val group = groupOf(type)
                fromRingtoneManager(ctx, type).forEach { add(it.copy(group = group)) }
                if (hasAudioPermission(ctx)) fromMediaStore(ctx, type).forEach { add(it.copy(group = group)) }
            }

            // 3. Вся музыка
            if (hasAudioPermission(ctx)) music(ctx).forEach(::add)

            out
        }

    private fun groupOf(type: Int) = when (type) {
        RingtoneManager.TYPE_NOTIFICATION -> GROUP_NOTIFICATIONS
        RingtoneManager.TYPE_ALARM -> GROUP_ALARMS
        else -> GROUP_RINGTONES
    }

    private fun fromRingtoneManager(ctx: Context, type: Int): List<SoundItem> {
        val list = ArrayList<SoundItem>()
        try {
            val rm = RingtoneManager(ctx).apply { setType(type) }
            val c = rm.cursor
            while (c.moveToNext()) {
                val title = c.getString(RingtoneManager.TITLE_COLUMN_INDEX) ?: continue
                val uri = rm.getRingtoneUri(c.position) ?: continue
                list += SoundItem(uri, title, "")
            }
        } catch (e: Exception) {
            // некоторые прошивки кидают SecurityException — просто пропускаем источник
        }
        return list
    }

    /** Аудио из общих папок Ringtones / Notifications / Alarms и с флагами IS_RINGTONE и т.п. */
    private fun fromMediaStore(ctx: Context, type: Int): List<SoundItem> {
        val (flag, folder) = when (type) {
            RingtoneManager.TYPE_NOTIFICATION -> MediaStore.Audio.Media.IS_NOTIFICATION to "Notifications"
            RingtoneManager.TYPE_ALARM -> MediaStore.Audio.Media.IS_ALARM to "Alarms"
            else -> MediaStore.Audio.Media.IS_RINGTONE to "Ringtones"
        }
        @Suppress("DEPRECATION")
        val pathColumn =
            if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.RELATIVE_PATH
            else MediaStore.Audio.Media.DATA
        val pathArg = if (Build.VERSION.SDK_INT >= 29) "%$folder/%" else "%/$folder/%"
        return queryAudio(ctx, "$flag != 0 OR $pathColumn LIKE ?", arrayOf(pathArg))
    }

    private fun music(ctx: Context): List<SoundItem> =
        queryAudio(ctx, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null)
            .map { it.copy(group = GROUP_MUSIC) }

    private fun queryAudio(ctx: Context, selection: String, args: Array<String>?): List<SoundItem> {
        val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val list = ArrayList<SoundItem>()
        try {
            ctx.contentResolver.query(
                base,
                arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.DISPLAY_NAME),
                selection, args,
                MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val title = c.getString(1)?.takeIf { it.isNotBlank() } ?: c.getString(2) ?: continue
                    list += SoundItem(ContentUris.withAppendedId(base, id), title, "")
                }
            }
        } catch (e: Exception) {
            // нет доступа — пусто
        }
        return list
    }
}
