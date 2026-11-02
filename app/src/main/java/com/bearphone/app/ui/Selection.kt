package com.bearphone.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bearphone.app.ui.theme.CallRed

/**
 * Панель режима выделения (заменяет шапку списка):
 *  ✕  3 из 120   [выделить всё] [копировать] [поделиться] [сведения] [удалить]
 * "Сведения" доступны, только когда выделен ровно один элемент.
 */
@Composable
fun SelectionBar(
    count: Int,
    total: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDetails: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Отменить выделение") }
            Text(
                "$count из $total",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onSelectAll) {
                Icon(
                    Icons.Filled.SelectAll, "Выделить всё",
                    tint = if (count == total) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onCopy, enabled = count > 0) { Icon(Icons.Filled.ContentCopy, "Копировать") }
            IconButton(onClick = onShare, enabled = count > 0) { Icon(Icons.Filled.Share, "Поделиться") }
            IconButton(onClick = onDetails, enabled = count == 1) { Icon(Icons.Filled.Info, "Сведения") }
            IconButton(onClick = onDelete, enabled = count > 0) {
                Icon(Icons.Filled.Delete, "Удалить", tint = if (count > 0) CallRed else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Подвкладки над списком: "Вызовы | Заблокированные". */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SubTabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val st = com.bearphone.app.ui.theme.LocalBearStyle.current
    SecondaryTabRow(
        selectedTabIndex = selected,
        containerColor = st.tabColor,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        titles.forEachIndexed { i, t ->
            Tab(
                selected = selected == i,
                onClick = { onSelect(i) },
                text = { Text(t, maxLines = 1) },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = st.onTabColor.copy(alpha = 0.75f),
            )
        }
    }
}

/** Одна строка окна "Сведения": подпись сверху мелко, значение крупно. */
data class DetailRow(val label: String, val value: String)

/** Действие внизу окна "Сведения" (позвонить, заблокировать…). */
data class DetailAction(val title: String, val danger: Boolean = false, val onClick: () -> Unit)

@Composable
fun DetailsDialog(
    title: String,
    rows: List<DetailRow>,
    actions: List<DetailAction> = emptyList(),
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // SelectionContainer — значения можно выделить пальцем и скопировать
                SelectionContainer {
                    Column {
                        rows.forEach { r ->
                            Text(r.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                r.value,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                        }
                    }
                }
                if (actions.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    actions.forEach { a ->
                        TextButton(onClick = { onDismiss(); a.onClick() }) {
                            Text(a.title, color = if (a.danger) CallRed else MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

/** Подтверждение удаления. */
@Composable
fun ConfirmDeleteDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text("Удалить", color = CallRed) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

fun copyText(ctx: Context, text: String) {
    ctx.getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText("BearPhone", text))
    // Android 13+ сам показывает плашку "Скопировано"
    if (android.os.Build.VERSION.SDK_INT < 33) Toast.makeText(ctx, "Скопировано", Toast.LENGTH_SHORT).show()
}

fun shareText(ctx: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    ctx.startActivity(Intent.createChooser(send, "Поделиться").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Полная дата и время: "2 нояб. 2026, 17:50:12" */
fun fullDateTime(ctx: Context, millis: Long): String {
    val d = android.text.format.DateFormat.getMediumDateFormat(ctx).format(millis)
    val t = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(millis)
    return "$d, $t"
}

// ---------------- поиск ----------------

/** Подвкладки + кнопка-лупа справа. */
@Composable
fun TabsWithSearch(
    titles: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onSearch: () -> Unit,
    /** Кнопка "Прочитать всё" (✓✓), null — не показывать */
    onMarkAllRead: (() -> Unit)? = null,
) {
    val st = com.bearphone.app.ui.theme.LocalBearStyle.current
    Row(
        Modifier.background(st.tabColor),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) { SubTabs(titles, selected, onSelect) }
        if (onMarkAllRead != null) {
            IconButton(onClick = onMarkAllRead) {
                Icon(androidx.compose.material.icons.Icons.Filled.DoneAll, "Прочитать всё", tint = MaterialTheme.colorScheme.primary)
            }
        }
        IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, "Поиск", tint = st.onTabColor) }
    }
}

/** Строка поиска: сразу ставит фокус и открывает клавиатуру; ✕ закрывает поиск. */
@Composable
fun SearchField(query: String, onQuery: (String) -> Unit, placeholder: String, onClose: () -> Unit) {
    val focus = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    androidx.activity.compose.BackHandler(onBack = onClose)
    androidx.compose.material3.OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Закрыть поиск") } },
        singleLine = true,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .focusRequester(focus)
    )
}

/** Подсветка всех вхождений [query] в тексте (без учёта регистра, кириллица тоже). */
fun highlight(text: String, query: String, color: androidx.compose.ui.graphics.Color): androidx.compose.ui.text.AnnotatedString {
    val q = query.trim()
    if (q.isEmpty()) return androidx.compose.ui.text.AnnotatedString(text)
    return androidx.compose.ui.text.buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val hit = text.indexOf(q, i, ignoreCase = true)
            if (hit < 0) { append(text.substring(i)); break }
            append(text.substring(i, hit))
            withStyle(androidx.compose.ui.text.SpanStyle(background = color, fontWeight = FontWeight.Bold)) {
                append(text.substring(hit, hit + q.length))
            }
            i = hit + q.length
        }
    }
}

/** Цифры запроса, если он похож на номер ("912 34" -> "91234"), иначе пусто. */
fun queryDigits(query: String): String =
    if (query.any { it.isLetter() }) "" else query.filter { it.isDigit() }
