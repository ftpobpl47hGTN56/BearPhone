package com.bearphone.app.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Кнопки уведомления, которые НЕ открывают экран ("Сбросить", "Завершить").
 * "Ответить" идёт напрямую в InCallActivity (EXTRA_ANSWER).
 */
class CallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DECLINE -> CallManager.hangup()
        }
    }

    companion object {
        const val ACTION_DECLINE = "com.bearphone.app.DECLINE"
    }
}
