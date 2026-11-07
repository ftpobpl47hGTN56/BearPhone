package com.bearphone.app.call

import android.content.ContentValues
import android.content.Context
import android.provider.CallLog
import com.bearphone.app.data.CallLogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Счётчик пропущенных звонков.
 *  - бейдж на вкладке «Вызовы» = пропущенные после последнего открытия вкладки;
 *  - число в уведомлении (по нему лаунчер Xiaomi рисует цифру на иконке приложения).
 * Сбрасывается, когда пользователь открывает вкладку «Вызовы».
 */
object MissedCalls {

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("bear_missed", Context.MODE_PRIVATE)

    /** Момент, когда вкладку «Вызовы» последний раз открывали. */
    fun seenAt(ctx: Context): Long = prefs(ctx).getLong("seen_at", System.currentTimeMillis())

    /** Пропущенные после последнего просмотра (по уже загруженному журналу). */
    fun count(ctx: Context, recents: List<CallLogEntry>): Int {
        val seen = seenAt(ctx)
        return recents.count { it.type == CallLog.Calls.MISSED_TYPE && it.dateMillis > seen }
    }

    /** +1 для уведомления (журнал в момент звонка может ещё не записаться). */
    fun increment(ctx: Context): Int {
        val n = prefs(ctx).getInt("pending", 0) + 1
        prefs(ctx).edit().putInt("pending", n).apply()
        return n
    }

    /** Открыли «Вызовы» — всё просмотрено: бейдж, уведомление и флаг NEW в системном журнале. */
    suspend fun markSeen(ctx: Context) = withContext(Dispatchers.IO) {
        prefs(ctx).edit().putLong("seen_at", System.currentTimeMillis()).putInt("pending", 0).apply()
        CallNotification.cancelMissed(ctx)
        try {
            // NEW=0 — системные лаунчеры/виджеты тоже перестают считать их новыми
            ctx.contentResolver.update(
                CallLog.Calls.CONTENT_URI,
                ContentValues().apply { put(CallLog.Calls.NEW, 0); put(CallLog.Calls.IS_READ, 1) },
                CallLog.Calls.TYPE + "=? AND " + CallLog.Calls.NEW + "=1",
                arrayOf(CallLog.Calls.MISSED_TYPE.toString())
            )
        } catch (e: Exception) { }
    }
}
