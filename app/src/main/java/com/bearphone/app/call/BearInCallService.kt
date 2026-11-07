package com.bearphone.app.call

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import android.telecom.InCallService
import com.bearphone.app.InCallActivity
import com.bearphone.app.data.ContactsRepository
import com.bearphone.app.sound.CallRinger

/**
 * Сюда Telecom присылает все звонки, когда BearPhone — звонилка по умолчанию.
 *
 * Задачи:
 *  - пересылать события в CallManager (UI подписан на него);
 *  - звонить рингтоном (CallRinger) — в манифесте IN_CALL_SERVICE_RINGING = true;
 *  - показывать уведомления (входящий / разговор / пропущенный);
 *  - открывать InCallActivity.
 */
class BearInCallService : InCallService() {

    private val ringer by lazy { CallRinger(this) }

    /** Звонки, которые хоть раз были в RINGING и не были отвечены — для "пропущенного". */
    private val ringingNotAnswered = mutableSetOf<Call>()

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            if (state == Call.STATE_ACTIVE) ringingNotAnswered -= call
            CallManager.onCallChanged()
            refresh()
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            CallManager.onCallChanged()
        }
    }

    override fun onCreate() {
        super.onCreate()
        CallManager.attach(this)
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        call.registerCallback(callCallback)
        if (call.stateCompat() == Call.STATE_RINGING) ringingNotAnswered += call
        CallManager.onCallAdded(call)
        refresh()

        // Исходящий или входящий при включённом экране — сразу показываем экран звонка.
        // На выключенном экране входящий откроется через full-screen intent уведомления.
        try {
            startActivity(
                Intent(this, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            // фоновый запуск может быть запрещён — остаётся уведомление
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callCallback)

        val missed = call in ringingNotAnswered &&
            call.details.disconnectCause?.code == DisconnectCause.MISSED
        ringingNotAnswered -= call

        CallManager.onCallRemoved(call)
        refresh()

        if (missed) {
            val number = call.details.handle?.schemeSpecificPart
            CallNotification.showMissed(this, titleFor(number), MissedCalls.increment(this))
        }
    }

    @Deprecated("Deprecated in API 34, но работает на всех версиях")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        @Suppress("DEPRECATION")
        super.onCallAudioStateChanged(audioState)
        CallManager.onAudioStateChanged(audioState)
    }

    /** Громкость вниз / кнопка "Тихо" на входящем. */
    override fun onSilenceRinger() {
        super.onSilenceRinger()
        ringer.stop()
    }

    override fun onDestroy() {
        ringer.stop()
        CallNotification.cancelCall(this)
        CallManager.detach()
        super.onDestroy()
    }

    // ---------------- внутреннее ----------------

    /** Пересчитать звук и уведомление по текущему набору звонков. */
    private fun refresh() {
        val ringingCall = calls.firstOrNull { it.stateCompat() == Call.STATE_RINGING }
        val activeCall = calls.firstOrNull {
            val s = it.stateCompat()
            s == Call.STATE_ACTIVE || s == Call.STATE_HOLDING ||
                s == Call.STATE_DIALING || s == Call.STATE_CONNECTING
        }

        // Звук: только если входящий и нет другого разговора
        if (ringingCall != null && activeCall == null) {
            ringer.start(ringingCall.details.handle?.schemeSpecificPart)
        } else {
            ringer.stop()
        }

        // Уведомление
        when {
            ringingCall != null -> {
                val number = ringingCall.details.handle?.schemeSpecificPart
                CallNotification.showIncoming(this, titleFor(number), number)
            }
            activeCall != null -> {
                val number = activeCall.details.handle?.schemeSpecificPart
                CallNotification.showOngoing(
                    this, titleFor(number), activeCall.details.connectTimeMillis
                )
            }
            else -> CallNotification.cancelCall(this)
        }
    }

    private fun titleFor(number: String?): String =
        ContactsRepository.nameFor(this, number)
            ?: number?.takeIf { it.isNotBlank() }
            ?: "Скрытый номер"
}
