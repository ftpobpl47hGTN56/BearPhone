package com.bearphone.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.provider.Telephony
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bearphone.app.block.NumberMatcher
import com.bearphone.app.data.Contact
import com.bearphone.app.data.SmsMessage
import com.bearphone.app.data.SmsRepository
import com.bearphone.app.data.SmsThread
import com.bearphone.app.ui.theme.CallRed
import com.bearphone.app.ui.theme.bearFrame

/**
 * Вкладка "Сообщения": список диалогов, а при [openAddress] != null — сам диалог.
 * @param reloadTick меняется, когда в базе SMS что-то поменялось (ContentObserver в MainActivity)
 */
@Composable
fun MessagesScreen(
    threads: List<SmsThread>,
    contacts: List<Contact>,
    canRead: Boolean,
    reloadTick: Int,
    openAddress: String?,
    onOpen: (String) -> Unit,
    onClose: () -> Unit,
    onCall: (String) -> Unit,
    onRequestPermissions: () -> Unit,
) {
    if (!canRead) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Нет доступа к SMS.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(12.dp))
            Button(onClick = onRequestPermissions, modifier = Modifier.bearFrame()) { Text("Разрешить") }
            Spacer(Modifier.size(12.dp))
            Text(
                "На Xiaomi может понадобиться ещё: Настройки → Приложения → BearPhone → " +
                    "Разрешения → «SMS» и «Служебные SMS».",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    // Номер -> имя из контактов (один раз на список контактов, без запросов к базе на каждую строку)
    val names = remember(contacts) {
        HashMap<String, String>().apply {
            contacts.forEach { c -> c.numbers.forEach { n -> put(NumberMatcher.key(n), c.name) } }
        }
    }
    fun nameOf(address: String): String? = names[NumberMatcher.key(address)]

    if (openAddress != null) {
        ConversationScreen(
            address = openAddress,
            name = nameOf(openAddress),
            thread = SmsRepository.findThread(threads, openAddress),
            reloadTick = reloadTick,
            onBack = onClose,
            onCall = onCall,
        )
        return
    }

    var composeNew by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        if (threads.isEmpty()) {
            EmptyState("Сообщений нет")
        } else {
            ThreadList(threads, ::nameOf, onOpen)
        }
        FloatingActionButton(
            onClick = { composeNew = true },
            containerColor = MaterialTheme.colorScheme.primary,
            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).bearFrame(RoundedCornerShape(16.dp))
        ) { Icon(Icons.Filled.Edit, "Новое сообщение") }
    }

    if (composeNew) {
        NewMessageDialog(
            contacts = contacts,
            onDismiss = { composeNew = false },
            onPick = { composeNew = false; onOpen(it) }
        )
    }
}

@Composable
private fun ThreadList(threads: List<SmsThread>, nameOf: (String) -> String?, onOpen: (String) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    LazyColumn(Modifier.fillMaxSize()) {
        items(threads, key = { it.threadId }, contentType = { "thread" }) { t ->
            val name = nameOf(t.address)
            val title = name ?: t.address.ifBlank { "Без номера" }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(t.address) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LetterAvatar(title)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (t.unread > 0) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(relativeTime(t.dateMillis), style = MaterialTheme.typography.labelSmall, color = muted)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            (if (t.lastOutgoing) "Вы: " else "") + t.snippet,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (t.unread > 0) MaterialTheme.colorScheme.onSurface else muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (t.unread > 0) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier.size(22.dp).background(primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (t.unread > 99) "99" else t.unread.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.size(88.dp)) } // место под кнопку "Новое"
    }
}

/** Слова, по которым понимаем, что в SMS одноразовый код. */
private val CODE_HINT = Regex("код|code|пароль|password|otp|подтвержд", RegexOption.IGNORE_CASE)
private val CODE = Regex("(?<!\\d)\\d{4,8}(?!\\d)")

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationScreen(
    address: String,
    name: String?,
    thread: SmsThread?,
    reloadTick: Int,
    onBack: () -> Unit,
    onCall: (String) -> Unit,
) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    var text by rememberSaveable(address) { mutableStateOf("") }

    val messages by produceState(emptyList<SmsMessage>(), thread?.threadId, reloadTick) {
        value = thread?.let { SmsRepository.loadMessages(ctx, it.threadId) } ?: emptyList()
    }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }

    // Отправлять ответ на сервисные номера ("Tinkoff", "900") часто бессмысленно
    val canReply = address.any { it.isDigit() } && address.none { it.isLetter() }

    Column(Modifier.fillMaxSize().imePadding()) {
        // ---- шапка ----
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            Column(Modifier.weight(1f)) {
                Text(name ?: address, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (name != null) {
                    Text(address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (canReply) {
                IconButton(onClick = { onCall(address) }) {
                    Icon(Icons.Filled.Call, "Позвонить", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // ---- сообщения ----
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (messages.isEmpty()) {
                item { EmptyState(if (thread == null) "Новый диалог" else "Загрузка…") }
            }
            items(messages, key = { it.id }, contentType = { if (it.outgoing) "out" else "in" }) { m ->
                Bubble(m, onCopy = { copy(ctx, it) })
            }
        }

        // ---- ввод ----
        if (canReply) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Сообщение") },
                    maxLines = 5,
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                FloatingActionButton(
                    onClick = {
                        val err = SmsRepository.send(ctx, address, text.trim())
                        if (err == null) text = "" else Toast.makeText(ctx, err, Toast.LENGTH_SHORT).show()
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                    modifier = Modifier.size(52.dp).bearFrame(CircleShape)
                ) { Icon(Icons.AutoMirrored.Filled.Send, "Отправить") }
            }
            val parts = remember(text) { if (text.isBlank()) 0 else smsParts(text) }
            if (parts > 1) {
                Text(
                    "Будет отправлено $parts SMS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, bottom = 6.dp)
                )
            }
        } else {
            Text(
                "На этот отправитель ответить нельзя",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(m: SmsMessage, onCopy: (String) -> Unit) {
    val ctx = LocalContext.current
    val failed = m.type == Telephony.Sms.MESSAGE_TYPE_FAILED
    val pending = m.type == Telephony.Sms.MESSAGE_TYPE_OUTBOX || m.type == Telephony.Sms.MESSAGE_TYPE_QUEUED
    val bg = if (m.outgoing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (m.outgoing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val code = remember(m.id) {
        if (!m.outgoing && CODE_HINT.containsMatchIn(m.body)) CODE.find(m.body)?.value else null
    }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalAlignment = if (m.outgoing) Alignment.End else Alignment.Start
    ) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .background(
                    bg,
                    RoundedCornerShape(
                        topStart = 18.dp, topEnd = 18.dp,
                        bottomStart = if (m.outgoing) 18.dp else 4.dp,
                        bottomEnd = if (m.outgoing) 4.dp else 18.dp
                    )
                )
                .combinedClickable(onClick = {}, onLongClick = { onCopy(m.body) })
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(m.body, color = fg, style = MaterialTheme.typography.bodyLarge)
            Text(
                DateFormat.getTimeFormat(ctx).format(m.dateMillis) +
                    when {
                        failed -> " · не отправлено"
                        pending -> " · отправка…"
                        else -> ""
                    },
                color = if (failed) CallRed else fg.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.End)
            )
        }
        if (code != null) {
            AssistChip(
                onClick = { onCopy(code) },
                label = { Text("Копировать код $code") },
                leadingIcon = { Icon(Icons.Filled.ContentCopy, null, Modifier.size(16.dp)) },
            )
        }
    }
}

/** Выбор получателя: ввести номер или найти контакт. */
@Composable
private fun NewMessageDialog(contacts: List<Contact>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    val matches = remember(q, contacts) {
        val s = q.trim().lowercase()
        if (s.isEmpty()) emptyList()
        else contacts.filter { c ->
            c.name.lowercase().contains(s) || c.numbers.any { it.filter(Char::isDigit).contains(s) }
        }.take(30)
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новое сообщение") },
        text = {
            Column {
                OutlinedTextField(
                    value = q,
                    onValueChange = { q = it },
                    label = { Text("Номер или имя") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                    items(matches, key = { it.id }) { c ->
                        Column(
                            Modifier.fillMaxWidth().clickable { onPick(c.numbers.first()) }.padding(vertical = 8.dp)
                        ) {
                            Text(c.name, style = MaterialTheme.typography.bodyLarge)
                            Text(c.numbers.first(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                enabled = q.count { it.isDigit() } >= 3,
                onClick = { onPick(q.trim()) }
            ) { Text("Написать на номер") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}


private fun copy(ctx: Context, text: String) {
    val cm = ctx.getSystemService(ClipboardManager::class.java)
    cm.setPrimaryClip(ClipData.newPlainText("BearPhone", text))
    // Android 13+ сам показывает плашку "Скопировано"
    if (android.os.Build.VERSION.SDK_INT < 33) Toast.makeText(ctx, "Скопировано", Toast.LENGTH_SHORT).show()
}

/** Сколько SMS уйдёт: 160 латиницей / 70 кириллицей на одно, в склейке 153 / 67. */
private fun smsParts(text: String): Int {
    val gsm = text.all { it.code < 128 }
    val single = if (gsm) 160 else 70
    val multi = if (gsm) 153 else 67
    return if (text.length <= single) 1 else (text.length + multi - 1) / multi
}
