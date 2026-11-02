package com.bearphone.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log
import com.bearphone.app.block.BlockStore
import com.bearphone.app.block.NumberMatcher
import com.bearphone.app.data.SmsRepository

/**
 * Приём входящих SMS. Android присылает SMS_DELIVER только приложению SMS по умолчанию,
 * и ТОЛЬКО оно отвечает за сохранение сообщения в базу. Поэтому порядок жёсткий:
 *   1) сохранить в базу (даже если дальше что-то упадёт — SMS-код не потеряется);
 *   2) показать уведомление.
 */
class SmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val pending = goAsync() // даём себе до ~10 с вместо обычных 5
        try {
            handle(context, intent)
        } catch (e: Exception) {
            Log.e(TAG, "SMS deliver failed", e)
        } finally {
            pending.finish()
        }
    }

    private fun handle(ctx: Context, intent: Intent) {
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return

        // Длинное SMS приходит частями — склеиваем по отправителю
        val byAddress = LinkedHashMap<String, MutableList<android.telephony.SmsMessage>>()
        parts.filterNotNull().forEach { m ->
            byAddress.getOrPut(m.displayOriginatingAddress ?: m.originatingAddress ?: "") { mutableListOf() } += m
        }

        val subId = intent.getIntExtra(
            SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
            intent.getIntExtra("subscription", -1)
        )
        BlockStore.init(ctx)
        val rules = BlockStore.state.value.rules

        byAddress.forEach { (address, msgs) ->
            val body = msgs.joinToString("") { it.displayMessageBody ?: it.messageBody ?: "" }
            val first = msgs.first()
            val blocked = rules.any { NumberMatcher.matches(it.pattern, address) }

            // 1) СНАЧАЛА в базу. Заблокированные тоже сохраняем (видны во вкладке
            //    «Заблокированные»), но сразу прочитанными и без уведомления.
            val uri = SmsRepository.insertIncoming(
                ctx, address, body,
                sentAt = first.timestampMillis,
                serviceCenter = first.serviceCenterAddress,
                subId = subId,
                read = blocked,
            )
            if (uri == null) Log.e(TAG, "insert failed for $address")

            // 2) Уведомление (если не удалось сохранить — всё равно показываем текст)
            if (!blocked) SmsNotifier.showIncoming(ctx, address, body, saved = uri != null)
        }
    }

    private companion object { const val TAG = "SmsDeliver" }
}

/**
 * MMS не поддерживаем, но Android не пускает в список «SMS по умолчанию» приложение
 * без этого приёмника. Поэтому он есть и просто ничего не делает.
 */
class MmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
