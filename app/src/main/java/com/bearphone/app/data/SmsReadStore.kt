package com.bearphone.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * "Прочитано" внутри BearPhone. Нужен, когда система не даёт записать READ=1 в базу SMS
 * (BearPhone не SMS-приложение по умолчанию или прошивка капризничает) —
 * тогда счётчики непрочитанных всё равно гаснут.
 *
 * Храним для диалога дату, до которой всё считается прочитанным.
 */
object SmsReadStore {

    private var prefs: SharedPreferences? = null
    private val upTo = HashMap<Long, Long>()
    /** Всё, что старше этой даты, прочитано во всех диалогах ("Прочитать всё") */
    @Volatile private var allUpTo = 0L

    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("bear_sms_read", Context.MODE_PRIVATE)
        prefs = p
        allUpTo = p.getLong("all", 0L)
        p.all.forEach { (k, v) -> if (k.startsWith("t")) k.drop(1).toLongOrNull()?.let { upTo[it] = v as? Long ?: 0L } }
    }

    @Synchronized fun isRead(threadId: Long, date: Long): Boolean =
        date <= allUpTo || date <= (upTo[threadId] ?: 0L)

    @Synchronized fun markThread(threadId: Long, date: Long = System.currentTimeMillis()) {
        upTo[threadId] = date
        prefs?.edit()?.putLong("t$threadId", date)?.apply()
    }

    @Synchronized fun markAll(date: Long = System.currentTimeMillis()) {
        allUpTo = date
        upTo.clear()
        prefs?.edit()?.clear()?.putLong("all", date)?.apply()
    }
}
