package com.bearphone.app.sound

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Проигрывает рингтон входящего звонка СИСТЕМНЫМИ звуками.
 *
 * Порядок выбора звука:
 *   1. Личный рингтон контакта из системной книги контактов (CUSTOM_RINGTONE),
 *      тот самый, что ставится в стандартных "Контактах".
 *   2. Рингтон, выбранный в настройках BearPhone (SoundPrefs).
 *   3. Системный рингтон по умолчанию.
 *
 * Уважает режимы телефона: "Без звука" -> тишина, "Вибрация" -> только вибро.
 *
 * Работает ТОЛЬКО если в манифесте у InCallService стоит
 * android.telecom.IN_CALL_SERVICE_RINGING = true (иначе система звонит сама,
 * и будет двойной звук).
 */
class CallRinger(context: Context) {

    private val ctx = context.applicationContext
    private val prefs = SoundPrefs(ctx)
    private val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var ringtone: Ringtone? = null
    private var focusRequest: AudioFocusRequest? = null
    private var loopRunnable: Runnable? = null
    private var isRinging = false

    private val ringAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /** @param phoneNumber номер звонящего (может быть null для скрытых номеров) */
    fun start(phoneNumber: String?) {
        if (isRinging) return
        isRinging = true

        val mode = audioManager.ringerMode
        val wantVibrate = prefs.vibrateOnRing || mode == AudioManager.RINGER_MODE_VIBRATE

        if (mode != AudioManager.RINGER_MODE_SILENT && wantVibrate) startVibration()
        if (mode == AudioManager.RINGER_MODE_NORMAL) startSound(phoneNumber)
    }

    fun stop() {
        if (!isRinging) return
        isRinging = false

        loopRunnable?.let { mainHandler.removeCallbacks(it) }
        loopRunnable = null

        ringtone?.stop()
        ringtone = null

        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null

        vibrator()?.cancel()
    }

    // ---------------- звук ----------------

    private fun startSound(phoneNumber: String?) {
        val picked = pickUri(phoneNumber) ?: return   // null = пользователь выбрал "Без звука"
        // Файл удалили / пропал доступ — лучше системный рингтон, чем тишина на входящем
        val uri = if (isReadable(picked)) picked else Settings.System.DEFAULT_RINGTONE_URI

        val rt = RingtoneManager.getRingtone(ctx, uri)
            ?: RingtoneManager.getRingtone(ctx, Settings.System.DEFAULT_RINGTONE_URI)
            ?: return
        rt.audioAttributes = ringAttributes

        requestFocus()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            rt.isLooping = true
            rt.play()
        } else {
            // До Android 9 у Ringtone нет isLooping — перезапускаем вручную
            rt.play()
            val r = object : Runnable {
                override fun run() {
                    if (!isRinging) return
                    if (!rt.isPlaying) rt.play()
                    mainHandler.postDelayed(this, 500)
                }
            }
            loopRunnable = r
            mainHandler.postDelayed(r, 500)
        }
        ringtone = rt
    }

    private fun isReadable(uri: Uri): Boolean {
        if (uri.authority == "settings") return true // content://settings/system/ringtone — всегда ок
        return try {
            ctx.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun pickUri(phoneNumber: String?): Uri? {
        contactRingtone(phoneNumber)?.let { return it }
        return prefs.resolveRingtone()
    }

    /** Личный рингтон контакта из системных Контактов. */
    private fun contactRingtone(phoneNumber: String?): Uri? {
        if (phoneNumber.isNullOrBlank()) return null
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) return null

        val lookup = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        return try {
            ctx.contentResolver.query(
                lookup,
                arrayOf(ContactsContract.PhoneLookup.CUSTOM_RINGTONE),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() }?.let(Uri::parse)
                else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "contactRingtone failed", e)
            null
        }
    }

    private fun requestFocus() {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(ringAttributes)
            .build()
        audioManager.requestAudioFocus(req)
        focusRequest = req
    }

    // ---------------- вибрация ----------------

    private fun startVibration() {
        val v = vibrator() ?: return
        if (!v.hasVibrator()) return
        // пауза, вибро 1с, пауза 1с — повтор с индекса 1
        val pattern = longArrayOf(0, 1000, 1000)
        val effect = VibrationEffect.createWaveform(pattern, 1)
        if (Build.VERSION.SDK_INT >= 33) {
            v.vibrate(effect, android.os.VibrationAttributes.createForUsage(
                android.os.VibrationAttributes.USAGE_RINGTONE
            ))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(effect, ringAttributes)
        }
    }

    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private companion object {
        const val TAG = "CallRinger"
    }
}
