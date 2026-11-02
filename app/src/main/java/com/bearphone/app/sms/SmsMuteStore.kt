package com.bearphone.app.sms

import android.content.Context
import android.content.SharedPreferences
import com.bearphone.app.block.NumberMatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Как уведомлять о SMS от конкретного отправителя. */
enum class SmsMute(val title: String) {
    SOUND("Звук и вибрация"),
    VIBRATE("Только вибрация"),
    SILENT("Без звука"),
}

/** Режим уведомлений для каждого диалога (ключ — отправитель). По умолчанию — SOUND. */
object SmsMuteStore {

    private var prefs: SharedPreferences? = null
    private val _modes = MutableStateFlow<Map<String, SmsMute>>(emptyMap())
    val modes: StateFlow<Map<String, SmsMute>> = _modes.asStateFlow()

    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("bear_sms_mute", Context.MODE_PRIVATE)
        prefs = p
        _modes.value = p.all.mapNotNull { (k, v) ->
            (v as? String)?.let { runCatching { SmsMute.valueOf(it) }.getOrNull() }?.let { k to it }
        }.toMap()
    }

    fun modeFor(address: String): SmsMute = _modes.value[NumberMatcher.senderKey(address)] ?: SmsMute.SOUND

    fun set(address: String, mode: SmsMute) {
        val key = NumberMatcher.senderKey(address)
        _modes.value = if (mode == SmsMute.SOUND) _modes.value - key else _modes.value + (key to mode)
        prefs?.edit()?.apply {
            if (mode == SmsMute.SOUND) remove(key) else putString(key, mode.name)
        }?.apply()
    }
}
