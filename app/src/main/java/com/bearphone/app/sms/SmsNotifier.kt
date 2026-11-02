package com.bearphone.app.sms

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.bearphone.app.MainActivity
import com.bearphone.app.R
import com.bearphone.app.block.NumberMatcher
import com.bearphone.app.data.ContactsRepository
import com.bearphone.app.sound.NotificationChannels
import org.json.JSONArray

/**
 * Уведомления о входящих SMS: одно на отправителя, новые сообщения добавляются в него
 * (как в мессенджерах). Кнопки: Ответить (прямо из шторки), Прочитано, Копировать код.
 */
object SmsNotifier {

    const val KEY_REPLY = "bearphone.reply"
    private const val MAX_LINES = 6

    private val CODE_HINT = Regex("код|code|пароль|password|otp|подтвержд", RegexOption.IGNORE_CASE)
    private val CODE = Regex("(?<!\\d)\\d{4,8}(?!\\d)")

    fun codeIn(body: String): String? =
        if (CODE_HINT.containsMatchIn(body)) CODE.find(body)?.value else null

    fun notificationId(address: String): Int =
        3000 + (NumberMatcher.senderKey(address).hashCode() and 0xFFFF)

    private fun canPost(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun showIncoming(ctx: Context, address: String, body: String, saved: Boolean) {
        if (!canPost(ctx)) return
        val id = notificationId(address)
        val name = ContactsRepository.nameFor(ctx, address) ?: address
        val lines = pushLine(ctx, id, body)

        val me = Person.Builder().setName("Я").build()
        val sender = Person.Builder().setName(name).setKey(address).build()
        val style = NotificationCompat.MessagingStyle(me)
        lines.forEach { (time, text) -> style.addMessage(text, time, sender) }

        val open = PendingIntent.getActivity(
            ctx, id,
            Intent(ctx, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SMS_ADDRESS, address)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        SmsMuteStore.init(ctx)
        val channel = when (SmsMuteStore.modeFor(address)) {
            SmsMute.SOUND -> NotificationChannels.smsId(ctx)
            SmsMute.VIBRATE -> NotificationChannels.SMS_VIBRATE
            SmsMute.SILENT -> NotificationChannels.SMS_SILENT
        }
        val b = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat_sms)
            .setStyle(style)
            .setContentTitle(name)
            .setContentText(body)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setOnlyAlertOnce(false)

        if (!saved) b.setSubText("⚠ не удалось сохранить в базу")

        // Копировать код — первым, это самое частое действие
        codeIn(body)?.let { code ->
            b.addAction(0, "Копировать $code", action(ctx, SmsActionReceiver.ACTION_COPY, id, address) {
                putExtra(SmsActionReceiver.EXTRA_CODE, code)
            })
        }

        // Ответ из шторки — только тем, кому можно ответить (не "Tinkoff"/"900")
        if (address.any { it.isDigit() } && address.none { it.isLetter() }) {
            val input = RemoteInput.Builder(KEY_REPLY).setLabel("Ответ").build()
            val replyPi = PendingIntent.getBroadcast(
                ctx, id + 1,
                Intent(ctx, SmsActionReceiver::class.java)
                    .setAction(SmsActionReceiver.ACTION_REPLY)
                    .putExtra(SmsActionReceiver.EXTRA_ADDRESS, address),
                // RemoteInput требует MUTABLE — система дописывает в Intent введённый текст
                PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            )
            b.addAction(
                NotificationCompat.Action.Builder(0, "Ответить", replyPi)
                    .addRemoteInput(input)
                    .setAllowGeneratedReplies(true)
                    .build()
            )
        }

        b.addAction(0, "Прочитано", action(ctx, SmsActionReceiver.ACTION_READ, id + 2, address))

        NotificationManagerCompat.from(ctx).notify(id, b.build())
    }

    /** Уведомление после ответа из шторки: показываем ответ и закрываем через пару секунд. */
    @SuppressLint("MissingPermission")
    fun showReplied(ctx: Context, address: String, text: String, error: String?) {
        if (!canPost(ctx)) return
        val id = notificationId(address)
        val name = ContactsRepository.nameFor(ctx, address) ?: address
        val n = NotificationCompat.Builder(ctx, NotificationChannels.smsId(ctx))
            .setSmallIcon(R.drawable.ic_stat_sms)
            .setContentTitle(name)
            .setContentText(if (error == null) "Вы: $text" else "Не отправлено: $error")
            .setSilent(true)
            .setTimeoutAfter(if (error == null) 3000 else 0)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }

    fun cancel(ctx: Context, address: String) {
        val id = notificationId(address)
        NotificationManagerCompat.from(ctx).cancel(id)
        clearLines(ctx, id)
    }

    private fun action(
        ctx: Context, act: String, code: Int, address: String, extra: Intent.() -> Unit = {},
    ): PendingIntent = PendingIntent.getBroadcast(
        ctx, code,
        Intent(ctx, SmsActionReceiver::class.java)
            .setAction(act)
            .putExtra(SmsActionReceiver.EXTRA_ADDRESS, address)
            .apply(extra),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // ---- последние строки уведомления (чтобы новые SMS добавлялись, а не заменяли) ----

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("bear_sms_notif", Context.MODE_PRIVATE)

    private fun pushLine(ctx: Context, id: Int, body: String): List<Pair<Long, String>> {
        val p = prefs(ctx)
        val arr = try { JSONArray(p.getString("n$id", "[]")) } catch (e: Exception) { JSONArray() }
        val list = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i); o.getLong("t") to o.getString("b")
        }.toMutableList()
        list += System.currentTimeMillis() to body
        val trimmed = list.takeLast(MAX_LINES)
        p.edit().putString("n$id", JSONArray(trimmed.map {
            org.json.JSONObject().put("t", it.first).put("b", it.second)
        }).toString()).apply()
        return trimmed
    }

    private fun clearLines(ctx: Context, id: Int) {
        prefs(ctx).edit().remove("n$id").apply()
    }
}
