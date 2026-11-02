package com.bearphone.app.ui

import android.telecom.Call
import android.telecom.CallAudioState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bearphone.app.call.AudioUi
import com.bearphone.app.call.CallUi
import com.bearphone.app.ui.theme.CallGreen
import com.bearphone.app.ui.theme.CallRed
import com.bearphone.app.ui.theme.bearFrame
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.ui.graphics.lerp
import kotlinx.coroutines.delay

@Composable
fun InCallScreen(
    call: CallUi,
    name: String?,
    audio: AudioUi,
    onAnswer: () -> Unit,
    onHangup: () -> Unit,
    onMute: (Boolean) -> Unit,
    onSpeaker: () -> Unit,
    onHold: () -> Unit,
    onDtmf: (Char) -> Unit,
) {
    val ringing = call.state == Call.STATE_RINGING
    val bg = MaterialTheme.colorScheme.background
    val fg = MaterialTheme.colorScheme.onBackground
    val accent = MaterialTheme.colorScheme.primary
    val accent2 = MaterialTheme.colorScheme.secondary
    var showPad by rememberSaveable { mutableStateOf(false) }
    var dtmf by rememberSaveable { mutableStateOf("") }

    // Таймер разговора
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(call.state) {
        while (call.state == Call.STATE_ACTIVE) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val status = when (call.state) {
        Call.STATE_RINGING -> "Входящий звонок"
        Call.STATE_DIALING, Call.STATE_CONNECTING -> "Вызов…"
        Call.STATE_HOLDING -> "На удержании"
        Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED -> "Завершение…"
        Call.STATE_ACTIVE ->
            if (call.connectTimeMillis > 0) formatDuration((now - call.connectTimeMillis).coerceAtLeast(0) / 1000)
            else "Разговор"
        else -> ""
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(lerp(bg, accent, 0.25f), bg, bg, lerp(bg, accent2, 0.12f))
                )
            )
            .safeDrawingPadding()
    ) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))
            LetterAvatar(name ?: call.number, 112.dp)
            Spacer(Modifier.height(20.dp))
            Text(
                name ?: call.number ?: "Скрытый номер",
                fontSize = 30.sp,
                fontWeight = FontWeight.Medium,
                color = fg,
                maxLines = 2
            )
            if (name != null && call.number != null) {
                Text(call.number, color = fg.copy(alpha = 0.7f), fontSize = 16.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(status, color = accent2, fontSize = 18.sp)

            Spacer(Modifier.weight(1f))

            if (ringing) {
                // ----- входящий: две большие кнопки -----
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RoundAction(Icons.Filled.CallEnd, "Сбросить", CallRed, onHangup)
                    RoundAction(Icons.Filled.Call, "Ответить", CallGreen, onAnswer)
                }
            } else {
                if (showPad) {
                    Text(dtmf, color = fg, fontSize = 26.sp, modifier = Modifier.padding(bottom = 8.dp))
                    DtmfPad { c -> dtmf += c; onDtmf(c) }
                    Spacer(Modifier.height(16.dp))
                }
                // ----- панель управления -----
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Toggle(Icons.Filled.MicOff, "Микрофон", audio.muted) { onMute(!audio.muted) }
                    Toggle(Icons.Filled.Dialpad, "Клавиши", showPad) { showPad = !showPad }
                    Toggle(
                        Icons.AutoMirrored.Filled.VolumeUp, "Динамик",
                        audio.route == CallAudioState.ROUTE_SPEAKER
                    ) { onSpeaker() }
                    if (call.canHold) {
                        Toggle(Icons.Filled.Pause, "Удержать", call.state == Call.STATE_HOLDING) { onHold() }
                    }
                }
                Spacer(Modifier.height(32.dp))
                RoundAction(Icons.Filled.CallEnd, "Завершить", CallRed, onHangup)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    val fg = MaterialTheme.colorScheme.onBackground
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = color,
            contentColor = Color.White,
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
            modifier = Modifier.size(76.dp).bearFrame(CircleShape, Color.White.copy(alpha = 0.35f))
        ) { Icon(icon, label, Modifier.size(34.dp)) }
        Spacer(Modifier.height(8.dp))
        Text(label, color = fg.copy(alpha = 0.8f))
    }
}

@Composable
private fun Toggle(icon: ImageVector, label: String, checked: Boolean, onClick: () -> Unit) {
    val fg = MaterialTheme.colorScheme.onBackground
    val bg = MaterialTheme.colorScheme.background
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconToggleButton(
            checked = checked,
            onCheckedChange = { onClick() },
            modifier = Modifier.size(60.dp).bearFrame(CircleShape),
            colors = IconButtonDefaults.filledIconToggleButtonColors(
                containerColor = fg.copy(alpha = 0.10f),
                contentColor = fg,
                checkedContainerColor = fg,
                checkedContentColor = bg,
            )
        ) { Icon(icon, label) }
        Spacer(Modifier.height(6.dp))
        Text(label, color = fg.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun DtmfPad(onKey: (Char) -> Unit) {
    val fg = MaterialTheme.colorScheme.onBackground
    val rows = listOf("123", "456", "789", "*0#")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                r.forEach { c ->
                    FloatingActionButton(
                        onClick = { onKey(c) },
                        containerColor = fg.copy(alpha = 0.08f),
                        contentColor = fg,
                        shape = CircleShape,
                        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                        modifier = Modifier.size(56.dp).bearFrame(CircleShape)
                    ) { Text(c.toString(), fontSize = 22.sp) }
                }
            }
        }
    }
}
