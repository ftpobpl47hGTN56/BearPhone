package com.bearphone.app.sound

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.result.contract.ActivityResultContract

/**
 * Открывает системный пикер звуков (тот же, что в настройках Android).
 *
 * Вход:  [Input] — тип звука и текущий выбранный Uri (чтобы он был отмечен галочкой).
 * Выход: [Result] — выбранный Uri (null = "Без звука") или Cancelled, если пользователь нажал "Назад".
 */
class PickRingtoneContract : ActivityResultContract<PickRingtoneContract.Input, PickRingtoneContract.Result>() {

    data class Input(
        /** RingtoneManager.TYPE_RINGTONE / TYPE_NOTIFICATION / TYPE_ALARM */
        val type: Int = RingtoneManager.TYPE_RINGTONE,
        /** Текущий звук, чтобы пикер подсветил его */
        val existing: Uri? = null,
        val title: String? = null,
        val showSilent: Boolean = true,
        val showDefault: Boolean = true,
    )

    sealed interface Result {
        /** uri == null означает, что выбрано "Без звука" */
        data class Picked(val uri: Uri?) : Result
        data object Cancelled : Result
    }

    override fun createIntent(context: Context, input: Input): Intent =
        Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, input.type)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, input.showSilent)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, input.showDefault)
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                RingtoneManager.getDefaultUri(input.type)
            )
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, input.existing)
            input.title?.let { putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, it) }
        }

    override fun parseResult(resultCode: Int, intent: Intent?): Result {
        if (resultCode != Activity.RESULT_OK || intent == null) return Result.Cancelled
        val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
        return Result.Picked(uri)
    }
}
