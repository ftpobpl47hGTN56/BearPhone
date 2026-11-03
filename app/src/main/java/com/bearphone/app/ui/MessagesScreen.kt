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
import com.bearphone.app.block.BlockStore
import com.bearphone.app.block.NumberMatcher
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
    /** Что-то удалили/скрыли — перечитать */
    onChanged: () -> Unit,
    /** Текст для поля ввода (пришёл вместе с sms:-ссылкой) */
    draft: String? = null,
    /** Журнал вызовов — для карточки контакта из диалога */
    calls: List<com.bearphone.app.data.CallLogEntry> = emptyList(),
    onCallsChanged: () -> Unit = {},
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
            contacts.forEach { c -> c.numbers.forEach { n -> put(NumberMatcher.senderKey(n), c.name) } }
        }
    }
    fun nameOf(address: String): String? = names[NumberMatcher.senderKey(address)]

    // Сообщение, к которому прыгнуть из поиска (id + запрос для подсветки)
    var focus by remember { mutableStateOf<Pair<Long, String>?>(null) }

    if (openAddress != null) {
        ConversationScreen(
            address = openAddress,
            name = nameOf(openAddress),
            thread = SmsRepository.findThread(threads, openAddress),
            reloadTick = reloadTick,
            onBack = { focus = null; onClose() },
            onCall = onCall,
            onChanged = onChanged,
            focusId = focus?.first,
            highlightQuery = focus?.second.orEmpty(),
            draft = draft,
            calls = calls,
            onCallsChanged = onCallsChanged,
        )
        return
    }

    var composeNew by rememberSaveable { mutableStateOf(false) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val block by BlockStore.state.collectAsState()

    var sub by rememberSaveable { mutableIntStateOf(0) }
    fun blocked(t: SmsThread) = block.rules.any { NumberMatcher.matches(it.pattern, t.address) }
    val visible = remember(threads, block, sub) {
        if (sub == 0) threads.filterNot(::blocked) else threads.filter(::blocked)
    }
    val blockedUnread = remember(threads, block) { threads.filter(::blocked).sumOf { it.unread } }

    // ---- поиск по SMS ----
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val hits by produceState(emptyList<SmsRepository.SearchHit>(), query, sub, block) {
        val q = query.trim()
        if (q.length < 2) { value = emptyList(); return@produceState }
        kotlinx.coroutines.delay(250) // ждём, пока пользователь допечатает
        value = SmsRepository.search(ctx, q).filter { h ->
            val isBlocked = block.rules.any { NumberMatcher.matches(it.pattern, h.address) }
            if (sub == 0) !isBlocked else isBlocked
        }
    }
    val matchedThreads = remember(visible, query, names) {
        val q = query.trim()
        val digits = queryDigits(q)
        if (q.length < 2) emptyList() else visible.filter { t ->
            (nameOf(t.address)?.contains(q, ignoreCase = true) == true) ||
                t.address.contains(q, ignoreCase = true) ||
                (digits.isNotEmpty() && t.address.filter(Char::isDigit).contains(digits))
        }
    }

    var selected by remember { mutableStateOf(emptySet<Long>()) }
    val selecting = selected.isNotEmpty()
    var details by remember { mutableStateOf<SmsThread?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    BackHandler(enabled = selecting) { selected = emptySet() }
    val selectedThreads = visible.filter { it.threadId in selected }
    fun line(t: SmsThread) = "${nameOf(t.address) ?: t.address}: ${t.snippet}"

    Column(Modifier.fillMaxSize()) {
        if (selecting) {
            SelectionBar(
                count = selected.size,
                total = visible.size,
                onClose = { selected = emptySet() },
                onSelectAll = {
                    selected = if (selected.size == visible.size) emptySet() else visible.map { it.threadId }.toSet()
                },
                onCopy = { copyText(ctx, selectedThreads.joinToString("\n", transform = ::line)); selected = emptySet() },
                onShare = { shareText(ctx, selectedThreads.joinToString("\n", transform = ::line)) },
                onDetails = { details = selectedThreads.firstOrNull() },
                onDelete = { confirmDelete = true },
            )
        } else {
            TabsWithSearch(
                listOf("Входящие", if (blockedUnread > 0) "Заблокированные ($blockedUnread)" else "Заблокированные"),
                sub,
                onSelect = { sub = it; selected = emptySet() },
                onSearch = { searching = !searching; if (!searching) query = "" },
                onMarkAllRead = if (threads.any { it.unread > 0 }) {
                    { scope.launch { SmsRepository.markAllRead(ctx, threads); onChanged() } }
                } else null,
            )
            if (searching) {
                SearchField(query, { query = it }, "Текст, имя или номер") { searching = false; query = "" }
            }
        }

        Box(Modifier.fillMaxSize()) {
            if (searching && query.trim().length >= 2) {
                SearchResults(
                    threads = matchedThreads,
                    hits = hits,
                    query = query.trim(),
                    nameOf = ::nameOf,
                    onOpenThread = { onOpen(it.address) },
                    onOpenHit = { h -> focus = h.id to query.trim(); onOpen(h.address) },
                )
            } else if (visible.isEmpty()) {
                EmptyState(if (sub == 0) "Сообщений нет" else "Нет SMS от заблокированных номеров")
            } else {
                ThreadList(
                    threads = visible,
                    nameOf = ::nameOf,
                    selected = selected,
                    onClick = { t ->
                        if (selecting) selected = if (t.threadId in selected) selected - t.threadId else selected + t.threadId
                        else onOpen(t.address)
                    },
                    onLongClick = { t ->
                        selected = if (t.threadId in selected) selected - t.threadId else selected + t.threadId
                    },
                )
            }
            if (!selecting && sub == 0) {
                FloatingActionButton(
                    onClick = { composeNew = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).bearFrame(RoundedCornerShape(16.dp))
                ) { Icon(Icons.Filled.Edit, "Новое сообщение") }
            }
        }
    }

    details?.let { t ->
        DetailsDialog(
            title = "Сведения диалога",
            rows = buildList {
                nameOf(t.address)?.let { add(DetailRow("Контакт", it)) }
                add(DetailRow("Отправитель", t.address.ifBlank { "Без номера" }))
                add(DetailRow("Последнее сообщение", fullDateTime(ctx, t.dateMillis)))
                add(DetailRow("Непрочитанных", t.unread.toString()))
                BlockStore.ruleFor(t.address)?.let { add(DetailRow("Блокировка", "по правилу ${it.pattern}")) }
            },
            actions = buildList {
                add(DetailAction("Открыть диалог") { onOpen(t.address) })
                if (t.unread > 0) add(DetailAction("Отметить прочитанным") {
                    scope.launch { SmsRepository.markRead(ctx, t); onChanged() }
                })
                val rule = BlockStore.ruleFor(t.address)
                if (t.address.isNotBlank()) when {
                    rule == null -> add(DetailAction("Заблокировать отправителя", danger = true) {
                        BlockStore.addRule(t.address, nameOf(t.address) ?: "")
                    })
                    !NumberMatcher.isMask(rule.pattern) -> add(DetailAction("Разблокировать отправителя") {
                        BlockStore.removeRule(rule)
                    })
                }
            },
            onDismiss = { details = null; selected = emptySet() }
        )
    }

    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = "Удалить диалоги?",
            text = "Выбрано диалогов: ${selectedThreads.size}.\n\n" + DELETE_NOTE,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                val list = selectedThreads
                selected = emptySet()
                scope.launch {
                    val real = SmsRepository.deleteThreads(ctx, list)
                    if (real < list.size) Toast.makeText(ctx, HIDDEN_TOAST, Toast.LENGTH_LONG).show()
                    onChanged()
                }
            }
        )
    }

    if (composeNew) {
        NewMessageDialog(
            contacts = contacts,
            onDismiss = { composeNew = false },
            onPick = { composeNew = false; onOpen(it) }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThreadList(
    threads: List<SmsThread>,
    nameOf: (String) -> String?,
    selected: Set<Long>,
    onClick: (SmsThread) -> Unit,
    onLongClick: (SmsThread) -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val selBg = primary.copy(alpha = 0.14f)
    val mutes by com.bearphone.app.sms.SmsMuteStore.modes.collectAsState()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().lazyScrollbar(listState)) {
            items(threads, key = { it.threadId }, contentType = { "thread" }) { t ->
                val name = nameOf(t.address)
                val title = name ?: t.address.ifBlank { "Без номера" }
                val isSel = t.threadId in selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (isSel) selBg else androidx.compose.ui.graphics.Color.Transparent)
                        .combinedClickable(onClick = { onClick(t) }, onLongClick = { onLongClick(t) })
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        LetterAvatar(title)
                        if (isSel) {
                            Box(Modifier.size(44.dp).background(primary, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
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
                            val mute = mutes[com.bearphone.app.block.NumberMatcher.senderKey(t.address)]
                            if (mute != null) {
                                Icon(muteIcon(mute), mute.title, tint = muted, modifier = Modifier.size(16.dp).padding(end = 2.dp))
                            }
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
        ScrollJumpButtons(listState)
    }
}

/** Результаты поиска: совпавшие диалоги (по имени/номеру) и сообщения (по тексту). */
@Composable
private fun SearchResults(
    threads: List<SmsThread>,
    hits: List<SmsRepository.SearchHit>,
    query: String,
    nameOf: (String) -> String?,
    onOpenThread: (SmsThread) -> Unit,
    onOpenHit: (SmsRepository.SearchHit) -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val mark = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    val listState = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().lazyScrollbar(listState)) {
            if (threads.isEmpty() && hits.isEmpty()) {
                item { EmptyState("Ничего не найдено") }
            }
            if (threads.isNotEmpty()) {
                item { SectionTitle("Диалоги (${threads.size})") }
                items(threads, key = { "t" + it.threadId }) { t ->
                    val title = nameOf(t.address) ?: t.address
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenThread(t) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LetterAvatar(title)
                        Spacer(Modifier.width(16.dp))
                        Text(highlight(title, query, mark), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    }
                }
            }
            if (hits.isNotEmpty()) {
                item { SectionTitle("Сообщения (${if (hits.size >= 300) "300+" else hits.size})") }
                items(hits, key = { "m" + it.id }) { h ->
                    val title = nameOf(h.address) ?: h.address
                    Column(
                        Modifier.fillMaxWidth().clickable { onOpenHit(h) }.padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1)
                            Text(relativeTime(h.dateMillis), style = MaterialTheme.typography.labelSmall, color = muted)
                        }
                        Text(
                            highlight((if (h.outgoing) "Вы: " else "") + snippetAround(h.body, query), query, mark),
                            style = MaterialTheme.typography.bodyMedium,
                            color = muted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        ScrollJumpButtons(listState)
    }
}

/** Кусок текста вокруг найденного слова, чтобы совпадение было видно в 2 строках. */
private fun snippetAround(body: String, query: String): String {
    val flat = body.replace('\n', ' ')
    val i = flat.indexOf(query, ignoreCase = true)
    if (i <= 40) return flat
    return "…" + flat.substring(i - 30)
}

/**
 * Бан отправителя прямо из диалога (в т.ч. буквенных: "Sberbank Loan", "MTS").
 * Новые SMS от него сохраняются тихо — без уведомления, во вкладке «Заблокированные».
 */
@Composable
private fun BanButton(address: String, name: String?, thread: SmsThread?, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val block by BlockStore.state.collectAsState()
    val rule = block.rules.firstOrNull { NumberMatcher.matches(it.pattern, address) }
    var confirm by remember { mutableStateOf(false) }
    IconButton(onClick = {
        when {
            rule == null -> confirm = true
            !NumberMatcher.isMask(rule.pattern) -> { BlockStore.removeRule(rule); onChanged() }
            else -> Toast.makeText(ctx, "Заблокирован маской ${rule.pattern} — снимите её в «Блокировке»", Toast.LENGTH_LONG).show()
        }
    }) {
        if (rule == null) Icon(Icons.Filled.Block, "Заблокировать отправителя", tint = com.bearphone.app.ui.theme.CallRed)
        else Icon(Icons.Filled.LockOpen, "Разблокировать отправителя", tint = com.bearphone.app.ui.theme.CallGreen)
    }
    if (confirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Заблокировать «${name ?: address}»?") },
            text = {
                Text(
                    "Уведомлений от отправителя больше не будет. Новые SMS сохранятся тихо " +
                        "во вкладке «Заблокированные». Звонки с этого номера тоже будут отклоняться."
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirm = false
                    BlockStore.addRule(address, name ?: "SMS")
                    scope.launch {
                        thread?.let { SmsRepository.markRead(ctx, it) }
                        com.bearphone.app.sms.SmsNotifier.cancel(ctx, address)
                        onChanged()
                    }
                }) { Text("Заблокировать", color = com.bearphone.app.ui.theme.CallRed) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirm = false }) { Text("Отмена") } },
        )
    }
}

/** Колокольчик в шапке диалога: звук / только вибрация / без звука для этого отправителя. */
@Composable
private fun MuteButton(address: String) {
    val modes by com.bearphone.app.sms.SmsMuteStore.modes.collectAsState()
    val mode = modes[com.bearphone.app.block.NumberMatcher.senderKey(address)] ?: com.bearphone.app.sms.SmsMute.SOUND
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                muteIcon(mode), "Уведомления: ${mode.title}",
                tint = if (mode == com.bearphone.app.sms.SmsMute.SOUND) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.primary
            )
        }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            com.bearphone.app.sms.SmsMute.entries.forEach { m ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(m.title) },
                    leadingIcon = { Icon(muteIcon(m), null) },
                    trailingIcon = { if (m == mode) Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) },
                    onClick = { com.bearphone.app.sms.SmsMuteStore.set(address, m); open = false },
                )
            }
        }
    }
}

private fun muteIcon(m: com.bearphone.app.sms.SmsMute) = when (m) {
    com.bearphone.app.sms.SmsMute.SOUND -> Icons.Filled.NotificationsActive
    com.bearphone.app.sms.SmsMute.VIBRATE -> Icons.Filled.Vibration
    com.bearphone.app.sms.SmsMute.SILENT -> Icons.Filled.NotificationsOff
}

private const val DELETE_NOTE =
    "Android разрешает удалять SMS только приложению SMS по умолчанию. " +
        "Если система не даст удалить, BearPhone скроет сообщения у себя " +
        "(в системном приложении «Сообщения» они останутся)."
private const val HIDDEN_TOAST = "Скрыто в BearPhone: удалить SMS может только системное приложение «Сообщения»"

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
    onChanged: () -> Unit,
    focusId: Long? = null,
    highlightQuery: String = "",
    draft: String? = null,
    calls: List<com.bearphone.app.data.CallLogEntry> = emptyList(),
    onCallsChanged: () -> Unit = {},
) {
    val ctx = LocalContext.current

    // ---- карточка отправителя (кнопка → в шапке) ----
    var cardOpen by rememberSaveable(address) { mutableStateOf(false) }
    if (cardOpen) {
        ContactCardScreen(
            number = address,
            allCalls = calls,
            onBack = { cardOpen = false },
            onCall = onCall,
            onMessage = { cardOpen = false },
            onChanged = onCallsChanged,
        )
        return
    }
    val scope = rememberCoroutineScope()
    var text by rememberSaveable(address) { mutableStateOf(draft.orEmpty()) }

    // Открыли диалог — снимаем уведомление и (если BearPhone — SMS по умолчанию) отмечаем прочитанным
    LaunchedEffect(thread?.threadId, thread?.unread) {
        com.bearphone.app.sms.SmsNotifier.cancel(ctx, address)
        val t = thread ?: return@LaunchedEffect
        if (t.unread > 0) {
            // В системную базу (если разрешено) + у себя, чтобы счётчик гас в любом случае
            SmsRepository.markRead(ctx, t)
            onChanged()
        }
    }

    // ---- выделение сообщений ----
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    val selecting = selected.isNotEmpty()
    var details by remember { mutableStateOf<SmsMessage?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    // "Назад": сначала снимаем выделение, потом выходим из диалога
    BackHandler { if (selecting) selected = emptySet() else onBack() }
    fun toggle(m: SmsMessage) { selected = if (m.id in selected) selected - m.id else selected + m.id }

    val messages by produceState(emptyList<SmsMessage>(), thread?.threadId, reloadTick) {
        value = thread?.let { SmsRepository.loadMessages(ctx, it.threadId) } ?: emptyList()
    }
    val listState = rememberLazyListState()
    // Из поиска — прыгаем к найденному сообщению и подсвечиваем его, иначе — к последнему
    var flashId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(messages.size, focusId) {
        if (messages.isEmpty()) return@LaunchedEffect
        val idx = focusId?.let { id -> messages.indexOfFirst { it.id == id } } ?: -1
        if (idx >= 0) {
            listState.scrollToItem(idx)
            flashId = focusId
            kotlinx.coroutines.delay(2500)
            flashId = null
        } else listState.scrollToItem(messages.lastIndex)
    }

    // Отправлять ответ на сервисные номера ("Tinkoff", "900") часто бессмысленно
    val canReply = address.any { it.isDigit() } && address.none { it.isLetter() }

    val selectedMsgs = messages.filter { it.id in selected }
    fun line(m: SmsMessage) =
        (if (m.outgoing) "Я" else (name ?: address)) + " (" + fullDateTime(ctx, m.dateMillis) + "): " + m.body

    Column(Modifier.fillMaxSize().imePadding()) {
        // ---- шапка / панель выделения ----
        if (selecting) SelectionBar(
            count = selected.size,
            total = messages.size,
            onClose = { selected = emptySet() },
            onSelectAll = {
                selected = if (selected.size == messages.size) emptySet() else messages.map { it.id }.toSet()
            },
            // Одно сообщение копируем как есть (удобно для кодов), несколько — с отправителем и временем
            onCopy = {
                copyText(ctx, if (selectedMsgs.size == 1) selectedMsgs[0].body else selectedMsgs.joinToString("\n\n", transform = ::line))
                selected = emptySet()
            },
            onShare = { shareText(ctx, if (selectedMsgs.size == 1) selectedMsgs[0].body else selectedMsgs.joinToString("\n\n", transform = ::line)) },
            onDetails = { details = selectedMsgs.firstOrNull() },
            onDelete = { confirmDelete = true },
        ) else Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            Column(Modifier.weight(1f)) {
                Text(name ?: address, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (name != null) {
                    Text(address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if ((thread?.unread ?: 0) > 0) {
                IconButton(onClick = { scope.launch { thread?.let { SmsRepository.markRead(ctx, it) }; onChanged() } }) {
                    Icon(Icons.Filled.DoneAll, "Отметить прочитанным", tint = MaterialTheme.colorScheme.primary)
                }
            }
            MuteButton(address)
            BanButton(address, name, thread, onChanged)
            if (canReply) CardArrowButton { cardOpen = true }
            if (canReply) {
                IconButton(onClick = { onCall(address) }) {
                    Icon(Icons.Filled.Call, "Позвонить", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // ---- сообщения ----
        androidx.compose.foundation.layout.Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().lazyScrollbar(listState),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (messages.isEmpty()) {
                    item { EmptyState(if (thread == null) "Новый диалог" else "Загрузка…") }
                }
                items(messages, key = { it.id }, contentType = { if (it.outgoing) "out" else "in" }) { m ->
                    Bubble(
                        m,
                        selected = m.id in selected || m.id == flashId,
                    highlightQuery = highlightQuery,
                        onClick = { if (selecting) toggle(m) },
                        onLongClick = { toggle(m) },
                        onCopyCode = { copyText(ctx, it) },
                    )
                }
            }
            ScrollJumpButtons(listState)
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

    MessageDialogs(
        details = details,
        name = name,
        address = address,
        onDetailsDismiss = { details = null; selected = emptySet() },
        confirmDelete = confirmDelete,
        deleteCount = selectedMsgs.size,
        onDeleteDismiss = { confirmDelete = false },
        onDelete = {
            val ids = selectedMsgs.map { it.id }
            selected = emptySet()
            scope.launch {
                val real = SmsRepository.deleteMessages(ctx, ids)
                if (real < ids.size) Toast.makeText(ctx, HIDDEN_TOAST, Toast.LENGTH_LONG).show()
                onChanged()
            }
        },
    )
}

@Composable
private fun MessageDialogs(
    details: SmsMessage?,
    name: String?,
    address: String,
    onDetailsDismiss: () -> Unit,
    confirmDelete: Boolean,
    deleteCount: Int,
    onDeleteDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    val ctx = LocalContext.current
    details?.let { m ->
        DetailsDialog(
            title = "Сведения сообщения",
            rows = buildList {
                add(DetailRow(if (m.outgoing) "Кому" else "От кого", listOfNotNull(name, m.address.ifBlank { address }).joinToString("\n")))
                add(DetailRow("Тип", smsTypeName(m.type)))
                add(DetailRow(if (m.outgoing) "Отправлено" else "Получено", fullDateTime(ctx, m.dateMillis)))
                if (!m.outgoing && m.dateSentMillis > 0) add(DetailRow("Отправлено отправителем", fullDateTime(ctx, m.dateSentMillis)))
                if (m.outgoing && m.status >= 0) add(DetailRow("Доставка", deliveryName(m.status)))
                add(DetailRow("Размер", "${m.body.length} симв. · ${smsParts(m.body)} SMS"))
                if (!m.outgoing) add(DetailRow("Прочитано", if (m.read) "Да" else "Нет"))
                m.serviceCenter?.let { add(DetailRow("SMS-центр", it)) }
                if (m.subId >= 0) add(DetailRow("SIM (subscription id)", m.subId.toString()))
            },
            actions = listOf(DetailAction("Копировать текст") { copyText(ctx, m.body) }),
            onDismiss = onDetailsDismiss,
        )
    }
    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = "Удалить сообщения?",
            text = "Выбрано: $deleteCount.\n\n" + DELETE_NOTE,
            onDismiss = onDeleteDismiss,
            onConfirm = onDelete,
        )
    }
}

private fun smsTypeName(type: Int) = when (type) {
    Telephony.Sms.MESSAGE_TYPE_INBOX -> "Входящее"
    Telephony.Sms.MESSAGE_TYPE_SENT -> "Отправленное"
    Telephony.Sms.MESSAGE_TYPE_OUTBOX -> "Отправляется"
    Telephony.Sms.MESSAGE_TYPE_QUEUED -> "В очереди"
    Telephony.Sms.MESSAGE_TYPE_FAILED -> "Не отправлено"
    Telephony.Sms.MESSAGE_TYPE_DRAFT -> "Черновик"
    else -> "SMS"
}

private fun deliveryName(status: Int) = when {
    status == Telephony.Sms.STATUS_COMPLETE -> "Доставлено"
    status == Telephony.Sms.STATUS_PENDING -> "Ожидает доставки"
    status >= Telephony.Sms.STATUS_FAILED -> "Не доставлено"
    else -> "Нет отчёта"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(
    m: SmsMessage,
    selected: Boolean,
    highlightQuery: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCopyCode: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val failed = m.type == Telephony.Sms.MESSAGE_TYPE_FAILED
    val pending = m.type == Telephony.Sms.MESSAGE_TYPE_OUTBOX || m.type == Telephony.Sms.MESSAGE_TYPE_QUEUED
    val bg = if (m.outgoing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (m.outgoing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val code = remember(m.id) {
        if (!m.outgoing && CODE_HINT.containsMatchIn(m.body)) CODE.find(m.body)?.value else null
    }
    val style = com.bearphone.app.ui.theme.LocalBearStyle.current
    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp, topEnd = 18.dp,
        bottomStart = if (m.outgoing) 18.dp else 4.dp,
        bottomEnd = if (m.outgoing) 4.dp else 18.dp
    )

    Column(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalAlignment = if (m.outgoing) Alignment.End else Alignment.Start
    ) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .background(bg, bubbleShape)
                // Рамка той же толщины, что у кнопок (Настройки → Оформление), цвет — акцент.
                // На своих (залитых акцентом) пузырях — светлее, иначе рамку не видно.
                .then(
                    if (style.border > 0.dp) Modifier.border(
                        style.border,
                        if (m.outgoing) androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.primary, androidx.compose.ui.graphics.Color.White, 0.45f)
                        else MaterialTheme.colorScheme.primary,
                        bubbleShape
                    ) else Modifier
                )
                .clip(bubbleShape)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                highlight(m.body, highlightQuery, if (m.outgoing) fg.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                color = fg,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                smsDateTime(ctx, m.dateMillis) +
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
                onClick = { onCopyCode(code) },
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



/** Сколько SMS уйдёт: 160 латиницей / 70 кириллицей на одно, в склейке 153 / 67. */
private fun smsParts(text: String): Int {
    val gsm = text.all { it.code < 128 }
    val single = if (gsm) 160 else 70
    val multi = if (gsm) 153 else 67
    return if (text.length <= single) 1 else (text.length + multi - 1) / multi
}

/** Полная дата и время сообщения: "2 нояб. 2026 г., 18:39" */
private fun smsDateTime(ctx: Context, millis: Long): String =
    DateFormat.getMediumDateFormat(ctx).format(millis) + ", " + DateFormat.getTimeFormat(ctx).format(millis)
