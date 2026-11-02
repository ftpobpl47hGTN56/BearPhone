package com.bearphone.app.sms

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.widget.Toast
import androidx.core.app.RemoteInput
import com.bearphone.app.data.SmsRepository

/** Кнопки уведомления SMS: ответить, прочитано, копировать код. */
class SmsActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val address = intent.getStringExtra(EXTRA_ADDRESS) ?: return
        when (intent.action) {
            ACTION_COPY -> {
                val code = intent.getStringExtra(EXTRA_CODE) ?: return
                context.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("Код", code))
                Toast.makeText(context, "Код $code скопирован", Toast.LENGTH_SHORT).show()
                markRead(context, address)
            }
            ACTION_READ -> markRead(context, address)
            ACTION_REPLY -> {
                val text = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(SmsNotifier.KEY_REPLY)?.toString()?.trim()
                if (text.isNullOrEmpty()) return
                val err = SmsRepository.send(context, address, text)
                // Ответили — значит прочитали
                SmsRepository.markAddressRead(context, address)
                SmsNotifier.cancel(context, address)
                SmsNotifier.showReplied(context, address, text, err)
            }
        }
    }

    private fun markRead(ctx: Context, address: String) {
        SmsRepository.markAddressRead(ctx, address)
        SmsNotifier.cancel(ctx, address)
    }

    companion object {
        const val ACTION_COPY = "com.bearphone.app.SMS_COPY"
        const val ACTION_READ = "com.bearphone.app.SMS_READ"
        const val ACTION_REPLY = "com.bearphone.app.SMS_REPLY"
        const val EXTRA_ADDRESS = "address"
        const val EXTRA_CODE = "code"
    }
}

/** Результат отправки: переводим сообщение из "Исходящих" в "Отправлено" или "Ошибка". */
class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uri = intent.data ?: return // не SMS-приложение по умолчанию — система пишет сама
        val ok = resultCode == android.app.Activity.RESULT_OK
        try {
            context.contentResolver.update(
                uri,
                android.content.ContentValues().apply {
                    put(Telephony.Sms.TYPE, if (ok) Telephony.Sms.MESSAGE_TYPE_SENT else Telephony.Sms.MESSAGE_TYPE_FAILED)
                    if (!ok) put(Telephony.Sms.ERROR_CODE, resultCode)
                },
                null, null
            )
        } catch (e: Exception) { }
        if (!ok) Toast.makeText(context, "SMS не отправлено (код $resultCode)", Toast.LENGTH_LONG).show()
    }

    companion object {
        const val ACTION_SENT = "com.bearphone.app.SMS_SENT"
    }
}
