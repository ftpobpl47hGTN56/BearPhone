package com.bearphone.app.ui

import android.content.Intent
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bearphone.app.block.BlockStore
import com.bearphone.app.block.NumberMatcher
import com.bearphone.app.data.CallLogEntry
import com.bearphone.app.data.ContactsRepository
import com.bearphone.app.ui.theme.CallGreen
import com.bearphone.app.ui.theme.CallRed
import com.bearphone.app.ui.theme.bearFrame

/**
 * Карточка контакта (или просто номера, если его нет в контактах):
 * аватар, имя, номера; кнопки-иконки вызов / SMS / бан / редактировать;
 * ниже — вся история звонков с этим человеком с полной датой.
 */
@Composable
fun ContactCardScreen(
    number: String,
    allCalls: List<CallLogEntry>,
    onBack: () -> Unit,
    onCall: (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val block by BlockStore.state.collectAsState()

    // null — ещё ищем; Unit-значение "нет в контактах" выражаем через found = false
    val info by produceState<ContactsRepository.ContactInfo?>(null, number) {
        value = ContactsRepository.lookup(ctx, number)
    }
    val numbers = info?.numbers ?: listOf(number)
    val name = info?.name
    val keys = remember(numbers) { numbers.map { NumberMatcher.key(it) }.filter { it.isNotEmpty() }.toSet() }

    // История по всем номерам контакта
    val history = remember(allCalls, keys, number) {
        allCalls.filter { e -> NumberMatcher.key(e.number) in keys || e.number == number }
    }
    val totalTalk = remember(history) { history.sumOf { it.durationSec } }

    val rule = block.rules.firstOrNull { NumberMatcher.matches(it.pattern, number) }
    val listState = rememberLazyListState()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            Text(if (name != null) "Контакт" else "Номер", style = MaterialTheme.typography.titleLarge)
        }

        Box(Modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().lazyScrollbar(listState)) {
                // ---- шапка ----
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LetterAvatar(name ?: number, 96.dp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            name ?: number,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                        // номера можно выделить и скопировать
                        SelectionContainer {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                (if (name != null) numbers else emptyList()).forEach {
                                    Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (rule != null) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Заблокирован" + if (NumberMatcher.isMask(rule.pattern)) " по маске ${rule.pattern}" else "",
                                color = CallRed,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        Spacer(Modifier.height(20.dp))

                        // ---- кнопки-иконки ----
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            CardAction(Icons.Filled.Call, "Позвонить", CallGreen) { onCall(number) }
                            CardAction(Icons.AutoMirrored.Filled.Message, "Написать SMS", MaterialTheme.colorScheme.primary) {
                                onMessage(number)
                            }
                            when {
                                rule == null -> CardAction(Icons.Filled.Block, "Заблокировать", CallRed) {
                                    BlockStore.addRule(number, name ?: "")
                                }
                                !NumberMatcher.isMask(rule.pattern) -> CardAction(Icons.Filled.LockOpen, "Разблокировать", CallGreen) {
                                    BlockStore.removeRule(rule)
                                }
                                else -> CardAction(Icons.Filled.Block, "Под маской — снимите в Блокировке", Color.Gray) {}
                            }
                            CardAction(
                                if (info != null) Icons.Filled.Edit else Icons.Filled.PersonAdd,
                                if (info != null) "Редактировать" else "Добавить в контакты",
                                MaterialTheme.colorScheme.primary
                            ) {
                                val i = info
                                val intent = if (i != null) {
                                    Intent(Intent.ACTION_EDIT)
                                        .setDataAndType(
                                            ContactsContract.Contacts.getLookupUri(i.id, i.lookupKey),
                                            ContactsContract.Contacts.CONTENT_ITEM_TYPE
                                        )
                                        .putExtra("finishActivityOnSaveCompleted", true)
                                } else {
                                    Intent(Intent.ACTION_INSERT_OR_EDIT)
                                        .setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE)
                                        .putExtra(ContactsContract.Intents.Insert.PHONE, number)
                                }
                                try { ctx.startActivity(intent) } catch (e: Exception) { }
                            }
                        }
                    }
                }

                // ---- история ----
                item {
                    SectionTitle(
                        if (history.isEmpty()) "Звонков не было"
                        else "История звонков (${history.size}) · всего ${formatDuration(totalTalk)}"
                    )
                }
                items(history, key = { it.id }) { e -> HistoryLine(e) }
                item { Spacer(Modifier.height(24.dp)) }
            }
            ScrollJumpButtons(listState)
        }
    }
}

@Composable
private fun CardAction(icon: ImageVector, description: String, color: Color, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = color.copy(alpha = 0.16f),
            contentColor = color,
        ),
        modifier = Modifier.size(56.dp).bearFrame(CircleShape)
    ) { Icon(icon, description, Modifier.size(26.dp)) }
}

@Composable
private fun HistoryLine(e: CallLogEntry) {
    val ctx = LocalContext.current
    val (icon, tint, label) = when (e.type) {
        CallLog.Calls.OUTGOING_TYPE -> Triple(Icons.AutoMirrored.Filled.CallMade, MaterialTheme.colorScheme.primary, "Исходящий")
        CallLog.Calls.MISSED_TYPE -> Triple(Icons.AutoMirrored.Filled.CallMissed, CallRed, "Пропущенный")
        CallLog.Calls.REJECTED_TYPE -> Triple(Icons.AutoMirrored.Filled.CallMissed, CallRed, "Отклонённый")
        CallLog.Calls.BLOCKED_TYPE -> Triple(Icons.Filled.Block, MaterialTheme.colorScheme.onSurfaceVariant, "Заблокирован")
        else -> Triple(Icons.AutoMirrored.Filled.CallReceived, CallGreen, "Входящий")
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).background(tint.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(fullDateTime(ctx, e.dateMillis), style = MaterialTheme.typography.bodyLarge)
            Text(
                label + (if (e.durationSec > 0) " · ${formatDuration(e.durationSec)}" else "") +
                    (e.numberLabel?.let { " · $it" } ?: "") + " · " + e.number,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
    }
}
