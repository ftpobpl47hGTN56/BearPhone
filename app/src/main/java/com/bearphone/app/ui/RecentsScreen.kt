package com.bearphone.app.ui

import android.content.Context
import android.provider.CallLog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bearphone.app.block.BlockSettings
import com.bearphone.app.block.BlockStore
import com.bearphone.app.block.NumberMatcher
import com.bearphone.app.data.CallLogEntry
import com.bearphone.app.data.CallLogRepository
import com.bearphone.app.ui.theme.CallGreen
import com.bearphone.app.ui.theme.CallRed
import kotlinx.coroutines.launch

/**
 * Готовая к показу строка журнала (группа подряд идущих звонков одного номера и типа).
 * Строки считаются ОДИН раз при загрузке, а не при каждой прокрутке.
 */
@Immutable
private data class RecentRow(
    val entry: CallLogEntry,
    /** id всех звонков группы — удаляются вместе */
    val ids: List<Long>,
    val title: String,
    val subtitle: String,
    val missed: Boolean,
) {
    val key: Long get() = entry.id
}

private fun buildRows(entries: List<CallLogEntry>): List<RecentRow> {
    val out = ArrayList<RecentRow>(entries.size)
    var i = 0
    while (i < entries.size) {
        val e = entries[i]
        var count = 1
        while (i + count < entries.size &&
            entries[i + count].number == e.number && entries[i + count].type == e.type
        ) count++
        val name = e.name ?: e.number.ifBlank { "Скрытый номер" }
        val dur = if (e.durationSec > 0) " · ${formatDuration(e.durationSec)}" else ""
        out += RecentRow(
            entry = e,
            ids = entries.subList(i, i + count).map { it.id },
            title = if (count > 1) "$name ($count)" else name,
            subtitle = relativeTime(e.dateMillis) + dur,
            missed = e.type == CallLog.Calls.MISSED_TYPE || e.type == CallLog.Calls.REJECTED_TYPE,
        )
        i += count
    }
    return out
}

/** Заблокирован ли звонок: системой (BLOCKED_TYPE) или попадает под правило BearPhone. */
private fun isBlocked(e: CallLogEntry, block: BlockSettings): Boolean =
    e.type == CallLog.Calls.BLOCKED_TYPE ||
        block.rules.any { NumberMatcher.matches(it.pattern, e.number) }

/**
 * Список "Заблокированные": системные записи + журнал блокировки BearPhone
 * (он нужен, когда включено «Не записывать в журнал»). Дубли за ±60 с отбрасываем.
 */
private fun blockedEntries(all: List<CallLogEntry>, block: BlockSettings): List<CallLogEntry> {
    val system = all.filter { isBlocked(it, block) }
    val extra = block.log.mapNotNull { b ->
        val dup = system.any {
            kotlin.math.abs(it.dateMillis - b.timeMillis) < 60_000 &&
                NumberMatcher.key(it.number) == NumberMatcher.key(b.number ?: "")
        }
        if (dup) null else CallLogEntry(
            id = -b.timeMillis,
            number = b.number.orEmpty(),
            name = null,
            type = CallLog.Calls.BLOCKED_TYPE,
            dateMillis = b.timeMillis,
            durationSec = 0,
            numberLabel = b.reason,
            fromBlockLog = true,
        )
    }
    return (system + extra).sortedByDescending { it.dateMillis }
}

/** Вкладка "Вызовы": две подвкладки, режим выделения, сведения. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentsScreen(
    entries: List<CallLogEntry>,
    hasPermission: Boolean,
    onCall: (String) -> Unit,
    onEditBeforeCall: (String) -> Unit,
    onMessage: (String) -> Unit,
    /** Журнал изменился (удалили записи) — перечитать */
    onChanged: () -> Unit,
) {
    if (!hasPermission) {
        EmptyState("Нет доступа к журналу звонков.\nРазрешите его в настройках BearPhone.")
        return
    }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val block by BlockStore.state.collectAsState()

    var sub by rememberSaveable { mutableIntStateOf(0) }
    val visible = remember(entries, block, sub) {
        if (sub == 0) entries.filterNot { isBlocked(it, block) } else blockedEntries(entries, block)
    }
    val rows = remember(visible) { buildRows(visible) }

    // ---- выделение ----
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    val selecting = selected.isNotEmpty()
    var details by remember { mutableStateOf<RecentRow?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    BackHandler(enabled = selecting) { selected = emptySet() }

    fun toggle(r: RecentRow) {
        selected = if (r.key in selected) selected - r.key else selected + r.key
    }
    val selectedRows = rows.filter { it.key in selected }

    Column(Modifier.fillMaxSize()) {
        if (selecting) {
            SelectionBar(
                count = selected.size,
                total = rows.size,
                onClose = { selected = emptySet() },
                onSelectAll = {
                    selected = if (selected.size == rows.size) emptySet() else rows.map { it.key }.toSet()
                },
                onCopy = { copyText(ctx, selectedRows.joinToString("\n") { describe(ctx, it) }); selected = emptySet() },
                onShare = { shareText(ctx, selectedRows.joinToString("\n") { describe(ctx, it) }) },
                onDetails = { details = selectedRows.firstOrNull() },
                onDelete = { confirmDelete = true },
            )
        } else {
            SubTabs(listOf("Вызовы", "Заблокированные"), sub) { sub = it; selected = emptySet() }
        }

        if (rows.isEmpty()) {
            EmptyState(if (sub == 0) "Журнал пуст" else "Заблокированных вызовов нет")
            return@Column
        }

        val primary = MaterialTheme.colorScheme.primary
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        val selBg = primary.copy(alpha = 0.14f)

        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().lazyScrollbar(listState)) {
                items(rows, key = { it.key }, contentType = { "row" }) { row ->
                    val e = row.entry
                    val isSel = row.key in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (isSel) selBg else Color.Transparent)
                            .combinedClickable(
                                onClick = {
                                    when {
                                        selecting -> toggle(row)
                                        e.number.isNotBlank() -> onCall(e.number)
                                        else -> details = row
                                    }
                                },
                                onLongClick = { toggle(row) }
                            )
                            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            LetterAvatar(e.name ?: e.number)
                            if (isSel) {
                                Box(
                                    Modifier.size(44.dp).background(primary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.onPrimary) }
                            }
                        }
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
                                val extra = if (e.fromBlockLog && e.numberLabel != null) " · ${e.numberLabel}" else ""
                                Text(row.subtitle + extra, style = MaterialTheme.typography.bodyMedium, color = muted, maxLines = 1)
                            }
                        }
                        if (!selecting && e.number.isNotBlank()) {
                            IconButton(onClick = { onCall(e.number) }) {
                                Icon(Icons.Filled.Call, "Позвонить", tint = primary)
                            }
                        }
                    }
                }
            }
            ScrollJumpButtons(listState)
        }
    }

    // ---- окна ----
    details?.let { row ->
        val e = row.entry
        val rule = BlockStore.ruleFor(e.number)
        DetailsDialog(
            title = "Сведения вызова",
            rows = buildList {
                e.name?.let { add(DetailRow("Контакт", it)) }
                add(DetailRow("Номер", e.number.ifBlank { "Скрытый номер" }))
                if (!e.fromBlockLog) e.numberLabel?.let { add(DetailRow("Тип номера", it)) }
                e.geocoded?.let { add(DetailRow("Регион", it)) }
                add(DetailRow("Вызов", typeName(e.type)))
                add(DetailRow("Дата и время", fullDateTime(ctx, e.dateMillis)))
                if (e.durationSec > 0) add(DetailRow("Длительность", formatDuration(e.durationSec)))
                if (row.ids.size > 1) add(DetailRow("Звонков подряд", row.ids.size.toString()))
                if (e.fromBlockLog) add(DetailRow("Заблокирован", e.numberLabel ?: "BearPhone"))
                else if (rule != null) add(DetailRow("Блокировка", "по правилу ${rule.pattern}"))
            },
            actions = buildList {
                if (e.number.isNotBlank()) {
                    add(DetailAction("Позвонить") { onCall(e.number) })
                    add(DetailAction("Написать SMS") { onMessage(e.number) })
                    add(DetailAction("Изменить перед вызовом") { onEditBeforeCall(e.number) })
                    when {
                        rule == null -> add(DetailAction("Заблокировать номер", danger = true) {
                            BlockStore.addRule(e.number, e.name ?: "")
                        })
                        !NumberMatcher.isMask(rule.pattern) -> add(DetailAction("Разблокировать номер") {
                            BlockStore.removeRule(rule)
                        })
                    }
                }
            },
            onDismiss = { details = null; selected = emptySet() }
        )
    }

    if (confirmDelete) {
        val total = selectedRows.sumOf { it.ids.size }
        ConfirmDeleteDialog(
            title = "Удалить из журнала?",
            text = "Будет удалено записей: $total. Отменить нельзя.",
            onDismiss = { confirmDelete = false },
            onConfirm = {
                val ids = selectedRows.flatMap { it.ids }
                selected = emptySet()
                scope.launch {
                    val realIds = ids.count { it > 0 }
                    val deleted = CallLogRepository.deleteMany(ctx, ids)
                    if (deleted < realIds) {
                        android.widget.Toast.makeText(
                            ctx,
                            "Удалено $deleted из $realIds. Проверьте разрешение «Журнал вызовов» для BearPhone",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                    BlockStore.removeLogEntries(ids.filter { it < 0 }.map { -it }.toSet())
                    onChanged()
                }
            }
        )
    }
}

/** Строка для копирования/отправки: "Мама · +7912… · Входящий · 2 нояб. 2026, 17:50:12 · 1:15" */
private fun describe(ctx: Context, r: RecentRow): String {
    val e = r.entry
    return listOfNotNull(
        e.name,
        e.number.ifBlank { "Скрытый номер" },
        typeName(e.type),
        fullDateTime(ctx, e.dateMillis),
        if (e.durationSec > 0) formatDuration(e.durationSec) else null,
    ).joinToString(" · ")
}

private fun typeName(type: Int) = when (type) {
    CallLog.Calls.INCOMING_TYPE -> "Входящий"
    CallLog.Calls.OUTGOING_TYPE -> "Исходящий"
    CallLog.Calls.MISSED_TYPE -> "Пропущенный"
    CallLog.Calls.REJECTED_TYPE -> "Отклонённый"
    CallLog.Calls.BLOCKED_TYPE -> "Заблокированный"
    CallLog.Calls.VOICEMAIL_TYPE -> "Голосовая почта"
    else -> "Вызов"
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
