package com.bearphone.app.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val avatarColors = listOf(
    Color(0xFF3D6BFF), Color(0xFF2EE6B8), Color(0xFF8E6BFF),
    Color(0xFFFF7A59), Color(0xFFFFB020), Color(0xFF00B3E6),
)

/** Кружок с первой буквой имени (без фото — легко и быстро). */
@Composable
fun LetterAvatar(name: String?, size: Dp = 44.dp) {
    val label = name?.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "#"
    val color = avatarColors[(name?.hashCode() ?: 0).mod(avatarColors.size)]
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = color,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.42f).sp,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

/** "5 мин назад", "вчера", "12 окт." */
fun relativeTime(millis: Long): String =
    DateUtils.getRelativeTimeSpanString(
        millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()

/** 75 -> "1:15", 3725 -> "1:02:05" */
fun formatDuration(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
