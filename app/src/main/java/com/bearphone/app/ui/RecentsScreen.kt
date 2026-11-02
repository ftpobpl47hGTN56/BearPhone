package com.bearphone.app.ui

import android.provider.CallLog
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bearphone.app.data.CallLogEntry
import com.bearphone.app.ui.theme.CallGreen
import com.bearphone.app.ui.theme.CallRed

/** Журнал звонков. Соседние звонки с одного номера схлопываются: "Мама (3)". */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentsScreen(
    entries: List<CallLogEntry>,
    hasPermission: Boolean,
    onCall: (String) -> Unit,
    onEditBeforeCall: (String) -> Unit,
    onDelete: (CallLogEntry) -> Unit,
) {
    if (!hasPermission) {
        EmptyState("Нет доступа к журналу звонков.\nРазрешите его в настройках BearPhone.")
        return
    }
    if (entries.isEmpty()) {
        EmptyState("Журнал пуст")
        return
    }

    // Группировка подряд идущих звонков одного номера и типа
    val groups = remember(entries) {
        val out = mutableListOf<Pair<CallLogEntry, Int>>()
        for (e in entries) {
            val last = out.lastOrNull()
            if (last != null && last.first.number == e.number && last.first.type == e.type) {
                out[out.lastIndex] = last.first to (last.second + 1)
            } else out += e to 1
        }
        out
    }

    LazyColumn(Modifier.fillMaxSize()) {
        items(groups, key = { it.first.id }) { (e, count) ->
            var menu by remember { mutableStateOf(false) }
            val (icon, tint) = typeIcon(e.type)
            val title = (e.name ?: e.number.ifBlank { "Скрытый номер" }) +
                if (count > 1) " ($count)" else ""
            val missed = e.type == CallLog.Calls.MISSED_TYPE || e.type == CallLog.Calls.REJECTED_TYPE

            Box {
                ListItem(
                    leadingContent = { LetterAvatar(e.name ?: e.number) },
                    headlineContent = {
                        Text(
                            title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (missed) CallRed else Color.Unspecified
                        )
                    },
                    supportingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            val dur = if (e.durationSec > 0) " · ${formatDuration(e.durationSec)}" else ""
                            Text(relativeTime(e.dateMillis) + dur)
                        }
                    },
                    trailingContent = {
                        if (e.number.isNotBlank()) {
                            IconButton(onClick = { onCall(e.number) }) {
                                Icon(Icons.Filled.Call, "Позвонить", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.combinedClickable(
                        onClick = { if (e.number.isNotBlank()) onCall(e.number) },
                        onLongClick = { menu = true }
                    )
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (e.number.isNotBlank()) {
                        DropdownMenuItem(
                            text = { Text("Изменить номер перед вызовом") },
                            onClick = { menu = false; onEditBeforeCall(e.number) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Удалить из журнала") },
                        onClick = { menu = false; onDelete(e) }
                    )
                }
            }
        }
    }
}

@Composable
private fun typeIcon(type: Int): Pair<ImageVector, Color> = when (type) {
    CallLog.Calls.OUTGOING_TYPE -> Icons.AutoMirrored.Filled.CallMade to MaterialTheme.colorScheme.primary
    CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> Icons.AutoMirrored.Filled.CallMissed to CallRed
    CallLog.Calls.BLOCKED_TYPE -> Icons.Filled.Block to MaterialTheme.colorScheme.onSurfaceVariant
    else -> Icons.AutoMirrored.Filled.CallReceived to CallGreen
}

@Composable
fun EmptyState(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
