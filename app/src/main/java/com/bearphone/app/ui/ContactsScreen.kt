package com.bearphone.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bearphone.app.data.Contact

/** Список контактов с поиском. Тап — звонок, если номеров несколько — выбор номера. */
@Composable
fun ContactsScreen(
    contacts: List<Contact>,
    hasPermission: Boolean,
    onCall: (String) -> Unit,
) {
    if (!hasPermission) {
        EmptyState("Нет доступа к контактам.\nРазрешите его в настройках BearPhone.")
        return
    }

    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(query, contacts) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) contacts
        else contacts.filter { c ->
            c.name.lowercase().contains(q) || c.numbers.any { it.filter(Char::isDigit).contains(q) }
        }
    }
    // Избранные сверху
    val sorted = remember(filtered) { filtered.sortedByDescending { it.starred } }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Поиск") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (sorted.isEmpty()) {
            EmptyState(if (contacts.isEmpty()) "Контактов нет" else "Ничего не найдено")
            return@Column
        }

        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().lazyScrollbar(listState)) {
                items(sorted, key = { it.id }) { c ->
                    var expanded by remember { mutableStateOf(false) }
                    Column {
                        ListItem(
                            leadingContent = { LetterAvatar(c.name) },
                            headlineContent = { Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text(
                                    if (c.numbers.size == 1) c.numbers.first()
                                    else "${c.numbers.first()} и ещё ${c.numbers.size - 1}"
                                )
                            },
                            trailingContent = {
                                if (c.starred) Icon(Icons.Filled.Star, "Избранный", tint = MaterialTheme.colorScheme.secondary)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                if (c.numbers.size == 1) onCall(c.numbers.first()) else expanded = !expanded
                            }
                        )
                        if (expanded) {
                            c.numbers.forEach { n ->
                                Text(
                                    n,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onCall(n) }
                                        .padding(start = 76.dp, top = 10.dp, bottom = 10.dp)
                                )
                            }
                        }
                    }
                }
            }
            ScrollJumpButtons(listState)
        }
    }
}
