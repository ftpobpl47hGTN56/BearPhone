package com.bearphone.app.sound

import android.content.Context
import android.content.SharedPreferences
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings

/**
 * Хранилище выбора звуков. Храним ТОЛЬКО Uri-строки на системные звуки,
 * сами звуки в APK не лежат.
 *
 * Логика значений:
 *  - ключ отсутствует          -> "Системный по умолчанию" (следуем за настройкой телефона)
 *  - значение "" (пустая строка) -> "Без звука"
 *  - любая другая строка        -> конкретный Uri, выбранный в пикере
 */
class SoundPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("bear_sounds", Context.MODE_PRIVATE)

    // ---------- Рингтон входящего звонка ----------

    /** Uri для пикера (чтобы подсветить текущий выбор). */
    fun ringtoneForPicker(): Uri? = readForPicker(KEY_RINGTONE, RingtoneManager.TYPE_RINGTONE)

    /** Итоговый Uri для проигрывания. null = без звука. */
    fun resolveRingtone(): Uri? = resolve(KEY_RINGTONE, Settings.System.DEFAULT_RINGTONE_URI)

    fun saveRingtone(uri: Uri?) = save(KEY_RINGTONE, uri, RingtoneManager.TYPE_RINGTONE)

    // ---------- Звук уведомлений (пропущенный звонок и т.п.) ----------

    fun notificationForPicker(): Uri? = readForPicker(KEY_NOTIFICATION, RingtoneManager.TYPE_NOTIFICATION)

    fun resolveNotification(): Uri? = resolve(KEY_NOTIFICATION, Settings.System.DEFAULT_NOTIFICATION_URI)

    fun saveNotification(uri: Uri?) = save(KEY_NOTIFICATION, uri, RingtoneManager.TYPE_NOTIFICATION)

    // ---------- Вибрация ----------

    var vibrateOnRing: Boolean
        get() = prefs.getBoolean(KEY_VIBRATE, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATE, value).apply()

    /**
     * Счётчик версии звука уведомлений. Нужен, потому что звук канала уведомлений
     * нельзя поменять после создания канала (см. NotificationChannels.kt).
     */
    val notificationSoundVersion: Int
        get() = prefs.getInt(KEY_NOTIF_VERSION, 0)

    // ---------- внутреннее ----------

    private fun readForPicker(key: String, type: Int): Uri? {
        if (!prefs.contains(key)) return RingtoneManager.getDefaultUri(type)
        val raw = prefs.getString(key, null)
        return if (raw.isNullOrEmpty()) null else Uri.parse(raw)
    }

    private fun resolve(key: String, systemDefault: Uri): Uri? {
        if (!prefs.contains(key)) return systemDefault
        val raw = prefs.getString(key, null)
        return if (raw.isNullOrEmpty()) null else Uri.parse(raw)
    }

    private fun save(key: String, uri: Uri?, type: Int) {
        val editor = prefs.edit()
        when {
            // Пользователь выбрал пункт "По умолчанию" -> следуем за системой
            uri != null && uri == RingtoneManager.getDefaultUri(type) -> editor.remove(key)
            uri == null -> editor.putString(key, "")          // "Без звука"
            else -> editor.putString(key, uri.toString())
        }
        if (key == KEY_NOTIFICATION) {
            editor.putInt(KEY_NOTIF_VERSION, notificationSoundVersion + 1)
        }
        editor.apply()
    }

    private companion object {
        const val KEY_RINGTONE = "ringtone_uri"
        const val KEY_NOTIFICATION = "notification_uri"
        const val KEY_VIBRATE = "vibrate_on_ring"
        const val KEY_NOTIF_VERSION = "notification_sound_version"
    }
}
