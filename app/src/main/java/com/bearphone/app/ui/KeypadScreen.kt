package com.bearphone.app.ui

import android.provider.CallLog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.bearphone.app.block.BlockStore
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.onSizeChanged
import com.bearphone.app.block.NumberMatcher
import com.bearphone.app.data.CallLogEntry
import com.bearphone.app.ui.theme.CallRed
import com.bearphone.app.ui.theme.LocalBearStyle
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bearphone.app.data.Contact
import com.bearphone.app.data.ContactsRepository
import com.bearphone.app.ui.theme.CallGreen
import com.bearphone.app.ui.theme.bearFrame

private val KEYS = listOf(
    "1" to "", "2" to "АБВГ ABC", "3" to "ДЕЖЗ DEF",
    "4" to "ИЙКЛ GHI", "5" to "МНОП JKL", "6" to "РСТУ MNO",
    "7" to "ФХЦЧ PQRS", "8" to "ШЩЪЫ TUV", "9" to "ЬЭЮЯ WXYZ",
    "*" to "", "0" to "+", "#" to "",
)

/** Буква -> цифра T9 (латиница + кириллица, как подписано на кнопках). */
private val T9: Map<Char, Char> = buildMap {
    KEYS.forEach { (digit, letters) ->
        letters.filter { it.isLetter() }.forEach { put(it.lowercaseChar(), digit[0]) }
    }
    put('ё', '3')
}

private fun t9Of(name: String): String =
    name.lowercase().map { T9[it] ?: if (it.isDigit()) it else ' ' }.joinToString("")

/**
 * Экран "Набор":
 *  - сверху строка поиска (контакты + история вызовов);
 *  - посередине история вызовов, а при наборе — T9-подсказки из контактов;
 *  - снизу клавиатура в скруглённой карточке с рамкой; её можно спрятать (⌄) и вернуть кнопкой.
 * @param number набранный номер (состояние в MainActivity, чтобы tel:-ссылки его заполняли)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeypadScreen(
    number: String,
    onNumberChange: (String) -> Unit,
    contacts: List<Contact>,
    recents: List<CallLogEntry>,
    onCall: (String) -> Unit,
    onMessage: (String) -> Unit,
    onCallsChanged: () -> Unit = {},
    sims: List<com.bearphone.app.call.SimAccount> = emptyList(),
    onCallSim: (String, com.bearphone.app.call.SimAccount) -> Unit = { n, _ -> onCall(n) },
) {
    val haptic = LocalHapticFeedback.current
    val block by BlockStore.state.collectAsState()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val uiPrefs = remember { ctx.getSharedPreferences("bear_ui", android.content.Context.MODE_PRIVATE) }
    // Спрятана клавиатура или нет — запоминается между запусками
    var padVisible by remember { mutableStateOf(uiPrefs.getBoolean("pad_visible", true)) }
    LaunchedEffect(padVisible) { uiPrefs.edit().putBoolean("pad_visible", padVisible).apply() }
    var query by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Номер пришёл снаружи (tel:-ссылка, «изменить перед вызовом») — показываем клавиатуру
    LaunchedEffect(number) { if (number.isNotEmpty()) padVisible = true }

    // ---- поле номера: настоящее текстовое поле (курсор, выделение, копировать/вставить) ----
    var field by remember { mutableStateOf(TextFieldValue(number, TextRange(number.length))) }
    // Номер поменяли снаружи (tel:-ссылка, звонок, «изменить перед вызовом») — синхронизируем
    LaunchedEffect(number) {
        if (number != field.text) field = TextFieldValue(number, TextRange(number.length))
    }
    fun setField(v: TextFieldValue) { field = v; if (v.text != number) onNumberChange(v.text) }
    /** Вставить в место курсора (или вместо выделенного). */
    fun insert(str: String) {
        val a = minOf(field.selection.start, field.selection.end)
        val b = maxOf(field.selection.start, field.selection.end)
        val t = field.text.replaceRange(a, b, str)
        setField(TextFieldValue(t, TextRange(a + str.length)))
    }
    fun backspace() {
        val a = minOf(field.selection.start, field.selection.end)
        val b = maxOf(field.selection.start, field.selection.end)
        when {
            a != b -> setField(TextFieldValue(field.text.removeRange(a, b), TextRange(a)))
            a > 0 -> setField(TextFieldValue(field.text.removeRange(a - 1, a), TextRange(a - 1)))
        }
    }
    var padHeight by remember { mutableIntStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current

    // ---- карточка контакта (кнопка → в истории) ----
    var cardFor by rememberSaveable { mutableStateOf<String?>(null) }
    cardFor?.let { n ->
        ContactCardScreen(n, recents, onBack = { cardFor = null }, onCall = onCall, onMessage = onMessage, onChanged = onCallsChanged)
        return
    }
    // Начали листать историю — прячем клавиатуру, как в обычных звонилках
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && padVisible && number.isEmpty()) padVisible = false
    }

    // История без заблокированных, подряд идущие звонки одного номера — одной строкой
    val history = remember(recents, block) {
        val clean = recents.filterNot { e ->
            e.type == CallLog.Calls.BLOCKED_TYPE || block.rules.any { NumberMatcher.matches(it.pattern, e.number) }
        }
        val out = ArrayList<Pair<CallLogEntry, Int>>()
        clean.forEach { e ->
            val last = out.lastOrNull()
            if (last != null && last.first.number == e.number && last.first.type == e.type) {
                out[out.lastIndex] = last.first to last.second + 1
            } else out += e to 1
        }
        out
    }

    // T9 при наборе номера
    val suggestions = remember(number, contacts) {
        if (number.length < 2) emptyList()
        else {
            val digits = ContactsRepository.normalize(number).trimStart('+')
            contacts.filter { c ->
                c.numbers.any { ContactsRepository.normalize(it).contains(digits) } ||
                    t9Of(c.name).split(' ').any { it.startsWith(digits) }
            }.take(20)
        }
    }

    // Поиск строкой сверху: имя, номер в любом формате
    val q = query.trim()
    val foundContacts = remember(q, contacts) {
        if (q.isEmpty()) emptyList() else {
            val d = queryDigits(q)
            contacts.filter { c ->
                c.name.contains(q, ignoreCase = true) ||
                    (d.isNotEmpty() && c.numbers.any { it.filter(Char::isDigit).contains(d) })
            }.take(30)
        }
    }
    val foundCalls = remember(q, history) {
        if (q.isEmpty()) emptyList() else {
            val d = queryDigits(q)
            history.filter { (e, _) ->
                (e.name?.contains(q, ignoreCase = true) == true) ||
                    (d.isNotEmpty() && e.number.filter(Char::isDigit).contains(d))
            }.take(50)
        }
    }

    val primary = MaterialTheme.colorScheme.primary
    val mark = primary.copy(alpha = 0.3f)

    Column(Modifier.fillMaxSize()) {
        // ---- строка поиска ----
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Поиск контактов и вызовов") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Очистить") }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
        )

        // ---- список: поиск / T9 / история ----
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().lazyScrollbar(listState)) {
                when {
                    q.isNotEmpty() -> {
                        if (foundContacts.isEmpty() && foundCalls.isEmpty()) item { EmptyState("Ничего не найдено") }
                        if (foundContacts.isNotEmpty()) {
                            item { SectionTitle("Контакты") }
                            items(foundContacts, key = { "c" + it.id }) { c ->
                                ContactRow(c, q, mark, onCall = { onCall(c.numbers.first()) }, onPick = { onNumberChange(c.numbers.first()) })
                            }
                        }
                        if (foundCalls.isNotEmpty()) {
                            item { SectionTitle("Вызовы") }
                            items(foundCalls, key = { "h" + it.first.id }) { (e, n) ->
                                HistoryRow(e, n, q, mark, onCall, onNumberChange) { cardFor = it }
                            }
                        }
                    }
                    suggestions.isNotEmpty() -> {
                        items(suggestions, key = { "s" + it.id }) { c ->
                            ContactRow(c, "", mark, onCall = { onCall(c.numbers.first()) }, onPick = { onNumberChange(c.numbers.first()) })
                        }
                    }
                    number.length >= 2 -> item { EmptyState("Нет контактов с таким номером") }
                    history.isEmpty() -> item { EmptyState("История вызовов пуста") }
                    else -> {
                        item { SectionTitle("Недавние") }
                        items(history, key = { "h" + it.first.id }) { (e, n) ->
                            HistoryRow(e, n, "", mark, onCall, onNumberChange) { cardFor = it }
                        }
                    }
                }
                // Место под клавиатуру (она лежит ПОВЕРХ списка) или под кнопку ⌨
                item {
                    Spacer(Modifier.height(if (padVisible) with(density) { padHeight.toDp() } + 8.dp else 88.dp))
                }
            }
            ScrollJumpButtons(listState)

            // Клавиатура спрятана — кнопка вернуть её (место фиксированное, ничего не "едет")
            OverlayVisibility(
                visible = !padVisible,
                enter = fadeIn(androidx.compose.animation.core.tween(120)),
                exit = fadeOut(androidx.compose.animation.core.tween(80)),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
            ) {
                FloatingActionButton(
                    onClick = { padVisible = true },
                    containerColor = primary,
                    shape = RoundedCornerShape(18.dp),
                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                    modifier = Modifier.size(64.dp).bearFrame(RoundedCornerShape(18.dp))
                ) { Icon(Icons.Filled.Dialpad, "Показать клавиатуру", Modifier.size(30.dp)) }
            }

            // ---- клавиатура: карточка ПОВЕРХ списка (список не растягивается — нет скачков) ----
            OverlayVisibility(
                visible = padVisible,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).onSizeChanged { if (it.height > 0) padHeight = it.height }
            ) {
                DialPad(
                    field = field,
                    onFieldChange = ::setField,
                    onCall = { onCall(field.text) },
                    sims = sims,
                    onCallSim = { sim -> onCallSim(field.text, sim) },
                    onHide = { padVisible = false },
                    onKey = { digit ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        insert(digit)
                    },
                    onBackspace = ::backspace,
                    onClear = { setField(TextFieldValue("")) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialPad(
    field: TextFieldValue,
    onFieldChange: (TextFieldValue) -> Unit,
    onCall: () -> Unit,
    sims: List<com.bearphone.app.call.SimAccount>,
    onCallSim: (com.bearphone.app.call.SimAccount) -> Unit,
    onHide: () -> Unit,
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
) {
    val number = field.text
    val style = LocalBearStyle.current
    val shape = RoundedCornerShape(28.dp)
    val onHideState = androidx.compose.runtime.rememberUpdatedState(onHide)
    // Смещение карточки при свайпе. Animatable — чтобы возврат/уход были плавными, без скачка
    val drag = remember { androidx.compose.animation.core.Animatable(0f) }
    val dragScope = androidx.compose.runtime.rememberCoroutineScope()
    var panelHeight by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = style.borderStroke, // рамка: толщина и цвет из Настройки → Оформление (3 px)
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
            .onSizeChanged { panelHeight = it.height }
            // Свайп вниз — спрятать. ВАЖНО: жест ловим ДО offset, в неподвижных координатах.
            // Раньше было наоборот: карточка сдвигалась, координаты пальца вместе с ней — и она дёргалась.
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        dragScope.launch {
                            if (drag.value > 80.dp.toPx()) {
                                drag.animateTo(panelHeight.toFloat(), androidx.compose.animation.core.tween(160))
                                onHideState.value()
                            } else {
                                drag.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.8f))
                            }
                        }
                    },
                    onDragCancel = { dragScope.launch { drag.animateTo(0f) } },
                ) { change, dy ->
                    change.consume()
                    dragScope.launch { drag.snapTo((drag.value + dy).coerceAtLeast(0f)) }
                }
            }
            .offset { androidx.compose.ui.unit.IntOffset(0, drag.value.roundToInt()) }
            .bearFrame(shape)
    ) {
        Column(Modifier.padding(bottom = 12.dp)) {
            // ---- ручка "спрятать" ----
            Box(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onHide)
                    .padding(top = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowDown, "Спрятать клавиатуру",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }

            // ---- поле номера ----
            Row(
                // Фиксированная высота: длинный номер мельче шрифтом, но карточка не "прыгает"
                Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.width(48.dp))
                // Системную клавиатуру не показываем — вводим своими клавишами,
                // но курсор, выделение, копировать/вставить/вырезать работают как в обычном поле
                @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
                androidx.compose.ui.platform.InterceptPlatformTextInput(
                    interceptor = { _, _ -> kotlinx.coroutines.awaitCancellation() }
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = field,
                        onValueChange = { v ->
                            // Вставили "8 (912) 345-67-89" — оставляем только то, что можно набрать
                            val clean = v.text.filter { it.isDigit() || it in "+*#,;" }
                            onFieldChange(
                                if (clean == v.text) v
                                else TextFieldValue(clean, TextRange(clean.length))
                            )
                        },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = if (number.length > 13) 24.sp else 32.sp,
                            fontWeight = FontWeight.Light,
                            textAlign = TextAlign.Center,
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f),
                    )
                }
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (number.isNotEmpty()) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .combinedClickable(onClick = onBackspace, onLongClick = onClear),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.AutoMirrored.Filled.Backspace, "Стереть") }
                    }
                }
            }

            // ---- клавиши ----
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KEYS.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { (digit, letters) ->
                            DialKey(
                                digit = digit,
                                letters = letters,
                                onClick = { onKey(digit) },
                                onLongClick = if (digit == "0") ({ onKey("+") }) else null
                            )
                        }
                    }
                }
            }

            // ---- вызов ----
            if (sims.size >= 2) {
                // Две SIM — две кнопки: сразу звонок с нужной, без вопросов
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp, start = 24.dp, end = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    sims.take(2).forEach { sim ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            FloatingActionButton(
                                onClick = { onCallSim(sim) },
                                containerColor = CallGreen,
                                contentColor = Color.White,
                                shape = RoundedCornerShape(24.dp),
                                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                                modifier = Modifier
                                    .size(width = 112.dp, height = 56.dp)
                                    .bearFrame(RoundedCornerShape(24.dp), Color.White.copy(alpha = 0.35f))
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Call, null, Modifier.size(24.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("${sim.index + 1}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (sim.label.isNotBlank()) {
                                Text(
                                    sim.label, maxLines = 1,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 3.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                    FloatingActionButton(
                        onClick = onCall,
                        containerColor = CallGreen,
                        contentColor = Color.White,
                        shape = CircleShape,
                        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                        modifier = Modifier.size(64.dp).bearFrame(CircleShape, Color.White.copy(alpha = 0.35f))
                    ) { Icon(Icons.Filled.Call, "Позвонить", Modifier.size(30.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactRow(c: Contact, q: String, mark: Color, onCall: () -> Unit, onPick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onCall, onLongClick = onPick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LetterAvatar(c.name, 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(highlight(c.name, q, mark), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(c.numbers.first(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.Call, "Позвонить", tint = MaterialTheme.colorScheme.primary)
    }
}

/** Строка истории: тап — позвонить, долгий тап — номер в поле набора. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(
    e: CallLogEntry, count: Int, q: String, mark: Color,
    onCall: (String) -> Unit, onPick: (String) -> Unit,
    onCard: (String) -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val missed = e.type == CallLog.Calls.MISSED_TYPE || e.type == CallLog.Calls.REJECTED_TYPE
    val title = (e.name ?: e.number.ifBlank { "Скрытый номер" }) + if (count > 1) " ($count)" else ""
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (e.number.isNotBlank()) onCall(e.number) },
                onLongClick = { if (e.number.isNotBlank()) onPick(e.number) }
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LetterAvatar(e.name ?: e.number, 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                highlight(title, q, mark),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                color = if (missed) CallRed else Color.Unspecified
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when (e.type) {
                        CallLog.Calls.OUTGOING_TYPE -> Icons.AutoMirrored.Filled.CallMade
                        CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> Icons.AutoMirrored.Filled.CallMissed
                        else -> Icons.AutoMirrored.Filled.CallReceived
                    },
                    null,
                    tint = when (e.type) {
                        CallLog.Calls.OUTGOING_TYPE -> MaterialTheme.colorScheme.primary
                        CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> CallRed
                        else -> CallGreen
                    },
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    relativeTime(e.dateMillis) + if (e.durationSec > 0) " · ${formatDuration(e.durationSec)}" else "",
                    style = MaterialTheme.typography.bodySmall, color = muted
                )
            }
        }
        if (e.number.isNotBlank()) {
            IconButton(onClick = { onCall(e.number) }) {
                Icon(Icons.Filled.Call, "Позвонить", tint = MaterialTheme.colorScheme.primary)
            }
            CardArrowButton { onCard(e.number) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialKey(digit: String, letters: String, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .size(width = 84.dp, height = 64.dp)
            .bearFrame(CircleShape)
            .clip(CircleShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(digit, fontSize = 28.sp, fontWeight = FontWeight.Normal)
            if (letters.isNotEmpty()) {
                Text(
                    letters,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            } else {
                Spacer(Modifier.height(11.dp))
            }
        }
    }
}

/** AnimatedVisibility без привязки к Column/Row — для наложения внутри Box. */
@Composable
private fun OverlayVisibility(
    visible: Boolean,
    enter: androidx.compose.animation.EnterTransition,
    exit: androidx.compose.animation.ExitTransition,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.animation.AnimatedVisibilityScope.() -> Unit,
) = AnimatedVisibility(visible = visible, modifier = modifier, enter = enter, exit = exit, content = content)
