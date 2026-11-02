package com.bearphone.app.ui

import android.provider.CallLog
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bearphone.app.block.BlockStore
import com.bearphone.app.block.NumberMatcher
import com.bearphone.app.data.CallLogEntry
import com.bearphone.app.ui.theme.CallGreen
import com.bearphone.app.ui.theme.CallRed

/**
 * Готовая к показу строка журнала. Все строки (время, длительность, заголовок)
 * считаются ОДИН раз при загрузке, а не при каждой прокрутке.
 */
@Immutable
private data class RecentRow(
    val entry: CallLogEntry,
    val title: String,
    val subtitle: String,
    val missed: Boolean,
)

private fun buildRows(entries: List<CallLogEntry>): List<RecentRow> {
    val out = ArrayList<RecentRow>(entries.size)
    var i = 0
    while (i < entries.size) {
        val e = entries[i]
        // схлопываем подряд идущие звонки одного номера и типа: "Мама (3)"
        var count = 1
        while (i + count < entries.size &&
            entries[i + count].number == e.number && entries[i + count].type == e.type
        ) count++
        val name = e.name ?: e.number.ifBlank { "Скрытый номер" }
        val dur = if (e.durationSec > 0) " · ${formatDuration(e.durationSec)}" else ""
        out += RecentRow(
            entry = e,
            title = if (count > 1) "$name ($count)" else name,
            subtitle = relativeTime(e.dateMillis) + dur,
            missed = e.type == CallLog.Calls.MISSED_TYPE || e.type == CallLog.Calls.REJECTED_TYPE,
        )
        i += count
    }
    return out
}

/** Журнал звонков. */
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

    val rows = remember(entries) { buildRows(entries) }
    // Одно меню на весь экран вместо DropdownMenu в каждой строке
    var menuFor by remember { mutableStateOf<RecentRow?>(null) }

    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    LazyColumn(Modifier.fillMaxSize()) {
        items(rows, key = { it.entry.id }, contentType = { "row" }) { row ->
            val e = row.entry
            Row(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { if (e.number.isNotBlank()) onCall(e.number) },
                        onLongClick = { menuFor = row }
                    )
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LetterAvatar(e.name ?: e.number)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        row.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (row.missed) CallRed else Color.Unspecified
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            typeIcon(e.type), null,
                            tint = typeTint(e.type, primary, muted),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(row.subtitle, style = MaterialTheme.typography.bodyMedium, color = muted)
                    }
                }
                if (e.number.isNotBlank()) {
                    IconButton(onClick = { onCall(e.number) }) {
                        Icon(Icons.Filled.Call, "Позвонить", tint = primary)
                    }
                }
            }
        }
    }

    menuFor?.let { row ->
        val e = row.entry
        val blockedBy = BlockStore.ruleFor(e.number)
        AlertDialog(
            onDismissRequest = { menuFor = null },
            title = { Text(row.title) },
            text = {
                Column {
                    if (e.number.isNotBlank()) {
                        Text(e.number, color = muted)
                        TextButton(onClick = { menuFor = null; onEditBeforeCall(e.number) }) {
                            Text("Изменить перед вызовом")
                        }
                        if (blockedBy == null) {
                            TextButton(onClick = { menuFor = null; BlockStore.addRule(e.number, e.name ?: "") }) {
                                Text("Заблокировать номер", color = CallRed)
                            }
                        } else if (!NumberMatcher.isMask(blockedBy.pattern)) {
                            TextButton(onClick = { menuFor = null; BlockStore.removeRule(blockedBy) }) {
                                Text("Разблокировать номер")
                            }
                        } else {
                            Text("Попадает под маску ${blockedBy.pattern}", color = muted)
                        }
                    }
                    TextButton(onClick = { menuFor = null; onDelete(e) }) {
                        Text("Удалить из журнала")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { menuFor = null }) { Text("Закрыть") }
            },
        )
    }
}

private fun typeIcon(type: Int): ImageVector = when (type) {
    CallLog.Calls.OUTGOING_TYPE -> Icons.AutoMirrored.Filled.CallMade
    CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> Icons.AutoMirrored.Filled.CallMissed
    CallLog.Calls.BLOCKED_TYPE -> Icons.Filled.Block
    else -> Icons.AutoMirrored.Filled.CallReceived
}

private fun typeTint(type: Int, primary: Color, muted: Color): Color = when (type) {
    CallLog.Calls.OUTGOING_TYPE -> primary
    CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> CallRed
    CallLog.Calls.BLOCKED_TYPE -> muted
    else -> CallGreen
}

@Composable
fun EmptyState(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}
