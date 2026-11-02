package com.bearphone.app.block

import android.annotation.SuppressLint
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bearphone.app.R
import com.bearphone.app.data.ContactsRepository
import com.bearphone.app.sound.NotificationChannels

/**
 * Telecom спрашивает этот сервис про КАЖДЫЙ входящий звонок ещё до того, как телефон зазвонит.
 * Работает, когда BearPhone — звонилка по умолчанию.
 *
 * Ответ нужно дать быстро (у системы ~5 секунд), поэтому проверки простые и локальные.
 */
class BearScreeningService : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        // С Android 10 сюда приходят и исходящие — их не трогаем
        if (Build.VERSION.SDK_INT >= 29 && details.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(details, CallResponse.Builder().build())
            return
        }

        BlockStore.init(this)
        val s = BlockStore.state.value
        val number = details.handle?.schemeSpecificPart?.takeIf { it.isNotBlank() }

        val reason: String? = when {
            number == null -> if (s.blockHidden) "Скрытый номер" else null
            else -> {
                val rule = s.rules.firstOrNull { NumberMatcher.matches(it.pattern, number) }
                when {
                    rule != null -> if (NumberMatcher.isMask(rule.pattern)) "Маска ${rule.pattern}" else "Чёрный список"
                    s.blockNotInContacts && ContactsRepository.nameFor(this, number) == null -> "Нет в контактах"
                    else -> null
                }
            }
        }

        if (reason == null) {
            respondToCall(details, CallResponse.Builder().build())
            return
        }

        val response = CallResponse.Builder()
            .setDisallowCall(true)   // не пускать звонок
            .setRejectCall(true)     // звонящий слышит "занято"
            .setSkipNotification(true) // без системного "пропущенного"
            .setSkipCallLog(s.skipCallLog)
            .build()
        respondToCall(details, response)

        BlockStore.recordBlocked(number, reason)
        if (s.notify) notifyBlocked(number, reason)
    }

    @SuppressLint("MissingPermission")
    private fun notifyBlocked(number: String?, reason: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        val n = NotificationCompat.Builder(this, NotificationChannels.BLOCKED_CALLS)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle("Звонок заблокирован")
            .setContentText("${number ?: "Скрытый номер"} · $reason")
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify(2000 + (System.currentTimeMillis() % 1000).toInt(), n)
    }
}
