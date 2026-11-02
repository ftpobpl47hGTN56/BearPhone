package com.bearphone.app.call

import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

/** Состояние одного звонка для UI. */
data class CallUi(
    val id: Int,
    val number: String?,
    val state: Int,
    /** System.currentTimeMillis() момента соединения, 0 — ещё не соединились */
    val connectTimeMillis: Long,
    val canHold: Boolean,
)

/** Состояние аудио (микрофон, динамик, гарнитура). */
data class AudioUi(
    val muted: Boolean = false,
    val route: Int = CallAudioState.ROUTE_EARPIECE,
    val supportedRoutes: Int = CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER,
)

/**
 * Единая точка правды о звонках. BearInCallService сообщает сюда события,
 * экраны Compose подписываются на [calls] и [audio].
 */
object CallManager {

    private val rawCalls = mutableListOf<Call>()
    private var service: WeakReference<InCallService>? = null

    private val _calls = MutableStateFlow<List<CallUi>>(emptyList())
    val calls: StateFlow<List<CallUi>> = _calls.asStateFlow()

    private val _audio = MutableStateFlow(AudioUi())
    val audio: StateFlow<AudioUi> = _audio.asStateFlow()

    // ---------- события от сервиса ----------

    internal fun attach(s: InCallService) { service = WeakReference(s) }

    internal fun detach() {
        service = null
        rawCalls.clear()
        publish()
    }

    internal fun onCallAdded(call: Call) {
        if (call !in rawCalls) rawCalls += call
        publish()
    }

    internal fun onCallRemoved(call: Call) {
        rawCalls -= call
        publish()
    }

    internal fun onCallChanged() = publish()

    internal fun onAudioStateChanged(state: CallAudioState?) {
        state ?: return
        _audio.value = AudioUi(state.isMuted, state.route, state.supportedRouteMask)
    }

    // ---------- действия из UI ----------

    /** Звонок, который сейчас на экране: входящий > активный > любой. */
    fun primary(): Call? =
        rawCalls.firstOrNull { it.stateCompat() == Call.STATE_RINGING }
            ?: rawCalls.firstOrNull { it.stateCompat() == Call.STATE_ACTIVE }
            ?: rawCalls.firstOrNull()

    fun answer() {
        rawCalls.firstOrNull { it.stateCompat() == Call.STATE_RINGING }
            ?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    /** Отклонить входящий или завершить текущий. */
    fun hangup() {
        val call = primary() ?: return
        if (call.stateCompat() == Call.STATE_RINGING) call.reject(false, null)
        else call.disconnect()
    }

    fun toggleHold() {
        val call = rawCalls.firstOrNull {
            val s = it.stateCompat()
            s == Call.STATE_ACTIVE || s == Call.STATE_HOLDING
        } ?: return
        if (call.stateCompat() == Call.STATE_HOLDING) call.unhold() else call.hold()
    }

    fun setMuted(muted: Boolean) {
        service?.get()?.setMuted(muted)
    }

    @Suppress("DEPRECATION")
    fun toggleSpeaker() {
        val s = service?.get() ?: return
        val now = _audio.value.route
        val target = if (now == CallAudioState.ROUTE_SPEAKER) {
            // назад: гарнитура, если подключена, иначе разговорный динамик
            if (_audio.value.supportedRoutes and CallAudioState.ROUTE_WIRED_HEADSET != 0)
                CallAudioState.ROUTE_WIRED_HEADSET
            else CallAudioState.ROUTE_EARPIECE
        } else CallAudioState.ROUTE_SPEAKER
        s.setAudioRoute(target)
    }

    /** Звонок ждёт выбора SIM (Telecom не знает, через какую звонить). */
    fun selectSim(handle: android.telecom.PhoneAccountHandle) {
        rawCalls.firstOrNull { it.stateCompat() == android.telecom.Call.STATE_SELECT_PHONE_ACCOUNT }
            ?.phoneAccountSelected(handle, false)
    }

    fun playDtmf(c: Char) {
        val call = primary() ?: return
        call.playDtmfTone(c)
        call.stopDtmfTone()
    }

    // ---------- внутреннее ----------

    private fun publish() {
        _calls.value = rawCalls.map { c ->
            val d = c.details
            CallUi(
                id = System.identityHashCode(c),
                number = d.handle?.schemeSpecificPart,
                state = c.stateCompat(),
                connectTimeMillis = d.connectTimeMillis,
                canHold = d.can(Call.Details.CAPABILITY_HOLD),
            )
        }
    }
}

/** Call.Details.state есть только с Android 12 (API 31), раньше — Call.state. */
fun Call.stateCompat(): Int =
    if (Build.VERSION.SDK_INT >= 31) details.state
    else @Suppress("DEPRECATION") state
