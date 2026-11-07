package com.bearphone.app.call

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bearphone.app.InCallActivity
import com.bearphone.app.MainActivity
import com.bearphone.app.R
import com.bearphone.app.sound.NotificationChannels

/** Уведомления звонка: входящий (full-screen), текущий разговор, пропущенный. */
object CallNotification {

    private const val ID_CALL = 1001
    private const val ID_MISSED = 1002

    private fun canPost(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED || android.os.Build.VERSION.SDK_INT < 33

    private fun openCallScreen(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun answerAndOpen(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx, 2,
            Intent(ctx, InCallActivity::class.java)
                .putExtra(InCallActivity.EXTRA_ANSWER, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun action(ctx: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, code,
            Intent(ctx, CallActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    @SuppressLint("MissingPermission")
    fun showIncoming(ctx: Context, title: String, number: String?) {
        if (!canPost(ctx)) return
        val n = NotificationCompat.Builder(ctx, NotificationChannels.INCOMING_CALL)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(title)
            .setContentText(if (number != null && number != title) number else "Входящий звонок")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setContentIntent(openCallScreen(ctx))
            // На заблокированном/выключенном экране сразу откроется InCallActivity
            .setFullScreenIntent(openCallScreen(ctx), true)
            .addAction(0, "Сбросить", action(ctx, CallActionReceiver.ACTION_DECLINE, 1))
            // Ответ сразу открывает экран звонка (через receiver нельзя — Android 12+ блокирует такие "трамплины")
            .addAction(0, "Ответить", answerAndOpen(ctx))
            .build()
        NotificationManagerCompat.from(ctx).notify(ID_CALL, n)
    }

    @SuppressLint("MissingPermission")
    fun showOngoing(ctx: Context, title: String, connectTimeMillis: Long) {
        if (!canPost(ctx)) return
        val b = NotificationCompat.Builder(ctx, NotificationChannels.ONGOING_CALL)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(title)
            .setContentText("Идёт разговор")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openCallScreen(ctx))
            .addAction(0, "Завершить", action(ctx, CallActionReceiver.ACTION_DECLINE, 3))
        if (connectTimeMillis > 0) {
            b.setWhen(connectTimeMillis).setUsesChronometer(true).setShowWhen(true)
        }
        NotificationManagerCompat.from(ctx).notify(ID_CALL, b.build())
    }

    fun cancelCall(ctx: Context) {
        NotificationManagerCompat.from(ctx).cancel(ID_CALL)
    }

    @SuppressLint("MissingPermission")
    fun cancelMissed(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(ID_MISSED)

    /** @param count сколько пропущенных накопилось (для текста и цифры на иконке лаунчера) */
    fun showMissed(ctx: Context, title: String, count: Int = 1) {
        if (!canPost(ctx)) return
        val open = PendingIntent.getActivity(
            ctx, 10,
            Intent(ctx, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_RECENTS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, NotificationChannels.missedCallsId(ctx))
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(if (count > 1) "Пропущенных звонков: $count" else "Пропущенный звонок")
            .setContentText(if (count > 1) "Последний: $title" else title)
            .setNumber(count) // цифра на иконке приложения (бейдж лаунчера)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(ctx).notify(ID_MISSED, n)
    }
}
