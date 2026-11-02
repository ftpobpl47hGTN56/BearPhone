package com.bearphone.app.call

import android.annotation.SuppressLint
import android.content.Context
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager

/** Одна SIM-карта, через которую можно звонить. */
data class SimAccount(
    /** 0 — SIM 1, 1 — SIM 2 */
    val index: Int,
    val handle: PhoneAccountHandle,
    /** Имя оператора или то, как SIM подписана в настройках ("MTS", "Tele2") */
    val label: String,
) {
    val shortTitle: String get() = "SIM ${index + 1}"
    val title: String get() = if (label.isBlank()) shortTitle else "$shortTitle · $label"
}

/** Какую SIM использовать для исходящих. */
enum class SimMode(val title: String) {
    SIM1("SIM 1"),
    SIM2("SIM 2"),
    ASK("Всегда спрашивать"),
}

object SimAccounts {

    /** SIM-карты, через которые можно звонить (обычно 1 или 2). Нужен READ_PHONE_STATE. */
    @SuppressLint("MissingPermission")
    fun list(ctx: Context): List<SimAccount> = try {
        val tm = ctx.getSystemService(TelecomManager::class.java)
        tm.callCapablePhoneAccounts.mapIndexed { i, h ->
            SimAccount(i, h, tm.getPhoneAccount(h)?.label?.toString().orEmpty())
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("bear_sim", Context.MODE_PRIVATE)

    fun mode(ctx: Context): SimMode =
        runCatching { SimMode.valueOf(prefs(ctx).getString("mode", SimMode.ASK.name)!!) }.getOrDefault(SimMode.ASK)

    fun setMode(ctx: Context, m: SimMode) = prefs(ctx).edit().putString("mode", m.name).apply()

    /**
     * SIM для вызова по настройке. null — нужно спросить пользователя
     * (режим "Всегда спрашивать" и SIM больше одной).
     */
    fun resolve(ctx: Context, sims: List<SimAccount> = list(ctx)): SimAccount? {
        if (sims.size <= 1) return sims.firstOrNull()
        return when (mode(ctx)) {
            SimMode.SIM1 -> sims.getOrNull(0)
            SimMode.SIM2 -> sims.getOrNull(1) ?: sims.first()
            SimMode.ASK -> null
        }
    }
}
