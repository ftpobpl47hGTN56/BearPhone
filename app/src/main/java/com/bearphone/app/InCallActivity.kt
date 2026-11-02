package com.bearphone.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.bearphone.app.call.CallManager
import com.bearphone.app.data.ContactsRepository
import com.bearphone.app.ui.InCallScreen
import com.bearphone.app.ui.theme.BearPhoneTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Экран звонка. Показывается поверх экрана блокировки, закрывается, когда звонков не осталось. */
class InCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        showOverLockScreen()
        handleAnswer(intent)

        setContent {
            BearPhoneTheme(dark = true) {
                val calls by CallManager.calls.collectAsState()
                val audio by CallManager.audio.collectAsState()

                // Звонков нет — закрываемся
                LaunchedEffect(calls.isEmpty()) {
                    if (calls.isEmpty()) finishAndRemoveTask()
                }

                val primary = calls.firstOrNull { it.state == android.telecom.Call.STATE_RINGING }
                    ?: calls.firstOrNull { it.state == android.telecom.Call.STATE_ACTIVE }
                    ?: calls.firstOrNull()
                    ?: return@BearPhoneTheme

                val name by produceState<String?>(null, primary.number) {
                    value = withContext(Dispatchers.IO) {
                        ContactsRepository.nameFor(this@InCallActivity, primary.number)
                    }
                }

                InCallScreen(
                    call = primary,
                    name = name,
                    audio = audio,
                    onAnswer = CallManager::answer,
                    onHangup = CallManager::hangup,
                    onMute = CallManager::setMuted,
                    onSpeaker = CallManager::toggleSpeaker,
                    onHold = CallManager::toggleHold,
                    onDtmf = CallManager::playDtmf,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAnswer(intent)
    }

    /** Кнопка "Ответить" из уведомления открывает экран с EXTRA_ANSWER. */
    private fun handleAnswer(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_ANSWER, false) == true) {
            CallManager.answer()
            intent.removeExtra(EXTRA_ANSWER)
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Экран блокировки не снимаем: звонок можно принять и без разблокировки
    }

    companion object {
        const val EXTRA_ANSWER = "bearphone.answer"
    }
}
