package com.bearphone.app.sms

import android.app.Activity
import android.app.Service
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Telephony
import android.telephony.TelephonyManager
import com.bearphone.app.data.SmsRepository

/**
 * "Ответить сообщением" на входящем звонке ("Не могу говорить, перезвоню").
 * Android требует этот сервис от SMS-приложения по умолчанию и шлёт сюда номер и текст.
 */
class HeadlessSmsSendService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == TelephonyManager.ACTION_RESPOND_VIA_MESSAGE) {
            val address = intent.data?.schemeSpecificPart?.substringBefore('?')
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!address.isNullOrBlank() && !text.isNullOrBlank()) {
                SmsRepository.send(this, address, text)
            }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}

/** Роль "SMS-приложение по умолчанию". */
object SmsRole {

    fun isHeld(ctx: Context): Boolean = SmsRepository.isDefaultApp(ctx)

    /** Системный диалог "Сделать BearPhone приложением SMS по умолчанию?" */
    fun requestIntent(activity: Activity): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_SMS)
        } else {
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, activity.packageName)
        }
}
