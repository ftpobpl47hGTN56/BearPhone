package com.bearphone.app.sound

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes

/**
 * Каналы уведомлений BearPhone.
 *
 * ВАЖНО: после создания канала Android НЕ даёт приложению менять его звук.
 * Поэтому, когда пользователь выбирает новый звук уведомлений, мы удаляем
 * старый канал и создаём новый с другим ID (суффикс = версия из SoundPrefs).
 */
object NotificationChannels {

    /** Входящий звонок: БЕЗ звука, звонит CallRinger (иначе будет двойной звук). */
    const val INCOMING_CALL = "incoming_call"

    /** Идущий разговор: тихо, просто чтобы вернуться на экран звонка. */
    const val ONGOING_CALL = "ongoing_call"

    /** Заблокированные звонки — тихо, только в шторке. */
    const val BLOCKED_CALLS = "blocked_calls"

    private const val MISSED_PREFIX = "missed_calls_v"
    private const val SMS_PREFIX = "sms_v"

    /** SMS от диалогов в режиме "Только вибрация" / "Без звука". */
    const val SMS_VIBRATE = "sms_vibrate"
    const val SMS_SILENT = "sms_silent"

    /** Входящие SMS — со звуком уведомлений из настроек BearPhone. */
    fun smsId(context: Context): String = SMS_PREFIX + SoundPrefs(context).notificationSoundVersion

    /** Актуальный ID канала пропущенных звонков. Используйте его в NotificationCompat.Builder. */
    fun missedCallsId(context: Context): String =
        MISSED_PREFIX + SoundPrefs(context).notificationSoundVersion

    /** Вызывать в Application.onCreate() и после смены звука уведомлений в настройках. */
    fun ensure(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val prefs = SoundPrefs(context)

        // 1) Входящий звонок — high importance для full-screen intent, звук выключен
        if (nm.getNotificationChannel(INCOMING_CALL) == null) {
            val ch = NotificationChannel(
                INCOMING_CALL, "Входящие звонки", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(null, null)
                enableVibration(false) // вибрирует CallRinger
            }
            nm.createNotificationChannel(ch)
        }

        // 1б) Текущий разговор — тихое постоянное уведомление "Вернуться к звонку"
        if (nm.getNotificationChannel(ONGOING_CALL) == null) {
            val ch = NotificationChannel(
                ONGOING_CALL, "Текущий звонок", NotificationManager.IMPORTANCE_LOW
            ).apply { setSound(null, null) }
            nm.createNotificationChannel(ch)
        }

        // 1в) Заблокированные — без звука
        if (nm.getNotificationChannel(BLOCKED_CALLS) == null) {
            val ch = NotificationChannel(
                BLOCKED_CALLS, "Заблокированные звонки", NotificationManager.IMPORTANCE_LOW
            ).apply { setSound(null, null) }
            nm.createNotificationChannel(ch)
        }

        // 1г) Входящие SMS — высокий приоритет (всплывают), звук как у уведомлений
        val smsCurrent = smsId(context)
        nm.notificationChannels
            .filter { it.id.startsWith(SMS_PREFIX) && it.id != smsCurrent }
            .forEach { nm.deleteNotificationChannel(it.id) }
        if (nm.getNotificationChannel(smsCurrent) == null) {
            val ch = NotificationChannel(smsCurrent, "Входящие SMS", NotificationManager.IMPORTANCE_HIGH).apply {
                val sound = prefs.resolveNotification()
                if (sound == null) setSound(null, null)
                else setSound(
                    sound,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                enableVibration(true)
            }
            nm.createNotificationChannel(ch)
        }

        // 1д) SMS без звука: с вибрацией и совсем тихие (всё равно видны в шторке)
        if (nm.getNotificationChannel(SMS_VIBRATE) == null) {
            nm.createNotificationChannel(
                NotificationChannel(SMS_VIBRATE, "SMS: только вибрация", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 250, 150, 250)
                }
            )
        }
        if (nm.getNotificationChannel(SMS_SILENT) == null) {
            nm.createNotificationChannel(
                NotificationChannel(SMS_SILENT, "SMS: без звука", NotificationManager.IMPORTANCE_LOW).apply {
                    setSound(null, null)
                    enableVibration(false)
                }
            )
        }

        // 2) Пропущенные — системный звук уведомления (или выбранный пользователем)
        val currentId = missedCallsId(context)
        nm.notificationChannels
            .filter { it.id.startsWith(MISSED_PREFIX) && it.id != currentId }
            .forEach { nm.deleteNotificationChannel(it.id) }

        if (nm.getNotificationChannel(currentId) == null) {
            val ch = NotificationChannel(
                currentId, "Пропущенные звонки", NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                val sound = prefs.resolveNotification()
                if (sound == null) {
                    setSound(null, null)
                } else {
                    setSound(
                        sound,
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                }
            }
            nm.createNotificationChannel(ch)
        }
    }
}
