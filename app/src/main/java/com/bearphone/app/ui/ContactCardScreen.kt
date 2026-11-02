package com.bearphone.app.ui

import android.content.Intent
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.SelectAll
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
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
import androidx.compose.runtime.setValue
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
    /** Журнал изменился (удалили звонки) — перечитать */
    onChanged: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // ---- выделение звонков в истории ----
    var selecting by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var selected by remember { androidx.compose.runtime.mutableStateOf(emptySet<Long>()) }
    var confirm by remember { androidx.compose.runtime.mutableStateOf<String?>(null) } // "selected" | "all"
    BackHandler { if (selecting) { selecting = false; selected = emptySet() } else onBack() }

    // После редактирования/добавления в системных Контактах — перечитать контакт
    var reloadInfo by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var simDialog by remember { androidx.compose.runtime.mutableStateOf(false) }
    var menuOpen by remember { androidx.compose.runtime.mutableStateOf(false) }
    var deleteDialog by remember { androidx.compose.runtime.mutableStateOf(false) }

    val contactsLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { reloadInfo++ }
    val block by BlockStore.state.collectAsState()

    // null — ещё ищем; Unit-значение "нет в контактах" выражаем через found = false
    val info by produceState<ContactsRepository.ContactInfo?>(null, number, reloadInfo) {
        value = ContactsRepository.lookup(ctx, number)
    }
    val numbers = info?.numbers ?: listOf(number)
    /** Удалить контакт из системной книги (нужен WRITE_CONTACTS). */
    fun doDelete() {
        val i = info ?: return
        scope.launch {
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    ctx.contentResolver.delete(ContactsContract.Contacts.getLookupUri(i.id, i.lookupKey), null, null) > 0
                } catch (e: Exception) { false }
            }
            if (ok) {
                android.widget.Toast.makeText(ctx, "Контакт «${i.name}» удалён", android.widget.Toast.LENGTH_SHORT).show()
                onChanged()
                reloadInfo++ // карточка станет "просто номером" с кнопкой 👤+
            } else {
                android.widget.Toast.makeText(
                    ctx,
                    if (i.onSim) "SIM-контакт можно удалить только в системных «Контактах»" else "Не удалось удалить контакт",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    val writePerm = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) doDelete() }
    val name = info?.name
    val keys = remember(numbers) { numbers.map { NumberMatcher.key(it) }.filter { it.isNotEmpty() }.toSet() }

    // История по всем номерам контакта
    val history = remember(allCalls, keys, number) {
        allCalls.filter { e -> NumberMatcher.key(e.number) in keys || e.number == number }
    }
    val totalTalk = remember(history) { history.sumOf { it.durationSec } }

    val rule = block.rules.firstOrNull { NumberMatcher.matches(it.pattern, number) }
    val style = com.bearphone.app.ui.theme.LocalBearStyle.current
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
                    androidx.compose.material3.Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = style.borderStroke,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .bearFrame(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                    ) {
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
                                if (info != null) "Редактировать контакт" else "Добавить в контакты",
                                MaterialTheme.colorScheme.primary
                            ) {
                                val i = info
                                if (i != null && i.onSim) {
                                    simDialog = true
                                    return@CardAction
                                }
                                val intent = if (i != null) {
                                    Intent(Intent.ACTION_EDIT)
                                        .setDataAndType(
                                            ContactsContract.Contacts.getLookupUri(i.id, i.lookupKey),
                                            ContactsContract.Contacts.CONTENT_ITEM_TYPE
                                        )
                                        .putExtra("finishActivityOnSaveCompleted", true)
                                } else {
                                    // Номера нет в контактах — сразу форма НОВОГО контакта с этим номером
                                    Intent(ContactsContract.Intents.Insert.ACTION)
                                        .setType(ContactsContract.RawContacts.CONTENT_TYPE)
                                        .putExtra(ContactsContract.Intents.Insert.PHONE, number)
                                        .putExtra("finishActivityOnSaveCompleted", true)
                                }
                                try { contactsLauncher.launch(intent) } catch (e: Exception) { }
                            }
                            // ⋮ — редкие действия, чтобы ряд не переполнялся
                            Box {
                                CardAction(Icons.Filled.MoreVert, "Ещё", MaterialTheme.colorScheme.primary) { menuOpen = true }
                                androidx.compose.material3.DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(if (numbers.size > 1) "Копировать номера" else "Копировать номер") },
                                        leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                                        onClick = {
                                            menuOpen = false
                                            copyText(ctx, if (numbers.size > 1) numbers.joinToString("\n") else number)
                                        }
                                    )
                                    val i = info
                                    if (i != null) {
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("Открыть в Контактах") },
                                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                                            onClick = {
                                                menuOpen = false
                                                try {
                                                    contactsLauncher.launch(
                                                        Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.getLookupUri(i.id, i.lookupKey))
                                                    )
                                                } catch (e: Exception) { }
                                            }
                                        )
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("Удалить контакт", color = CallRed) },
                                            leadingIcon = { Icon(Icons.Filled.PersonRemove, null, tint = CallRed) },
                                            onClick = { menuOpen = false; deleteDialog = true }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    }
                }

                // ---- история ----
                item {
                    Row(Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            SectionTitle(
                                when {
                                    selecting -> "Выбрано: ${selected.size} из ${history.size}"
                                    history.isEmpty() -> "Звонков не было"
                                    else -> "История звонков (${history.size}) · всего ${formatDuration(totalTalk)}"
                                }
                            )
                        }
                        if (history.isNotEmpty()) {
                            if (selecting) {
                                IconButton(onClick = {
                                    selected = if (selected.size == history.size) emptySet() else history.map { it.id }.toSet()
                                }) { Icon(Icons.Filled.SelectAll, "Выделить всё", tint = MaterialTheme.colorScheme.primary) }
                                IconButton(onClick = { if (selected.isNotEmpty()) confirm = "selected" }, enabled = selected.isNotEmpty()) {
                                    Icon(Icons.Filled.Delete, "Удалить выбранные", tint = if (selected.isNotEmpty()) CallRed else Color.Gray)
                                }
                                IconButton(onClick = { selecting = false; selected = emptySet() }) {
                                    Icon(Icons.Filled.Close, "Отменить выделение")
                                }
                            } else {
                                IconButton(onClick = { selecting = true }) {
                                    Icon(Icons.Filled.Checklist, "Выделить", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { confirm = "all" }) {
                                    Icon(Icons.Filled.DeleteSweep, "Очистить историю", tint = CallRed)
                                }
                            }
                        }
                    }
                }
                items(history, key = { it.id }) { e ->
                    HistoryLine(
                        e,
                        selecting = selecting,
                        selected = e.id in selected,
                        onToggle = { selected = if (e.id in selected) selected - e.id else selected + e.id },
                        onLongPress = { selecting = true; selected = selected + e.id },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
            ScrollJumpButtons(listState)
        }
    }

    if (deleteDialog) {
        ConfirmDeleteDialog(
            title = "Удалить контакт?",
            text = "«${info?.name ?: number}» будет удалён из контактов телефона. История звонков останется.",
            onDismiss = { deleteDialog = false },
            onConfirm = {
                if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.WRITE_CONTACTS)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) doDelete() else writePerm.launch(android.Manifest.permission.WRITE_CONTACTS)
            }
        )
    }

    // SIM-контакт: системный редактор его не меняет — предлагаем рабочие варианты
    if (simDialog) {
        val i = info
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { simDialog = false },
            title = { Text("Контакт на SIM-карте") },
            text = {
                Text(
                    "Android не даёт другим приложениям менять контакты, сохранённые на SIM. " +
                        "Можно открыть его в системных «Контактах» (они умеют править SIM) " +
                        "или скопировать в память телефона и редактировать там."
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    simDialog = false
                    // Копия в телефон: форма нового контакта с тем же именем и всеми номерами
                    val intent = Intent(ContactsContract.Intents.Insert.ACTION)
                        .setType(ContactsContract.RawContacts.CONTENT_TYPE)
                        .putExtra(ContactsContract.Intents.Insert.NAME, i?.name ?: "")
                        .putExtra(ContactsContract.Intents.Insert.PHONE, numbers.getOrNull(0) ?: number)
                        .apply {
                            numbers.getOrNull(1)?.let { putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE, it) }
                            numbers.getOrNull(2)?.let { putExtra(ContactsContract.Intents.Insert.TERTIARY_PHONE, it) }
                        }
                        .putExtra("finishActivityOnSaveCompleted", true)
                    try { contactsLauncher.launch(intent) } catch (e: Exception) { }
                }) { Text("Копировать в телефон") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    simDialog = false
                    if (i != null) {
                        val view = Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.getLookupUri(i.id, i.lookupKey))
                        try { contactsLauncher.launch(view) } catch (e: Exception) { }
                    }
                }) { Text("Открыть в Контактах") }
            }
        )
    }

    confirm?.let { what ->
        val ids = if (what == "all") history.map { it.id } else selected.toList()
        ConfirmDeleteDialog(
            title = if (what == "all") "Очистить историю звонков?" else "Удалить выбранные звонки?",
            text = "Будет удалено записей: ${ids.size}${if (name != null) " (контакт «$name»)" else ""}. Отменить нельзя.",
            onDismiss = { confirm = null },
            onConfirm = {
                scope.launch {
                    val n = com.bearphone.app.data.CallLogRepository.deleteMany(ctx, ids)
                    if (n < ids.count { it > 0 }) {
                        android.widget.Toast.makeText(ctx, "Удалено $n из ${ids.size}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    selecting = false; selected = emptySet()
                    onChanged()
                }
            }
        )
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
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun HistoryLine(
    e: CallLogEntry,
    selecting: Boolean,
    selected: Boolean,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
) {
    val ctx = LocalContext.current
    val (icon, tint, label) = when (e.type) {
        CallLog.Calls.OUTGOING_TYPE -> Triple(Icons.AutoMirrored.Filled.CallMade, MaterialTheme.colorScheme.primary, "Исходящий")
        CallLog.Calls.MISSED_TYPE -> Triple(Icons.AutoMirrored.Filled.CallMissed, CallRed, "Пропущенный")
        CallLog.Calls.REJECTED_TYPE -> Triple(Icons.AutoMirrored.Filled.CallMissed, CallRed, "Отклонённый")
        CallLog.Calls.BLOCKED_TYPE -> Triple(Icons.Filled.Block, MaterialTheme.colorScheme.onSurfaceVariant, "Заблокирован")
        else -> Triple(Icons.AutoMirrored.Filled.CallReceived, CallGreen, "Входящий")
    }
    val style = com.bearphone.app.ui.theme.LocalBearStyle.current
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            // Каждый звонок — в своей рамке (толщина/цвет из Оформления)
            .then(if (style.border > 0.dp) Modifier.border(style.border, style.borderColor, shape) else Modifier)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surfaceContainerLow,
                shape
            )
            .clip(shape).combinedClickable(onClick = { if (selecting) onToggle() }, onLongClick = onLongPress)
            .padding(horizontal = 12.dp, vertical = 10.dp),
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

