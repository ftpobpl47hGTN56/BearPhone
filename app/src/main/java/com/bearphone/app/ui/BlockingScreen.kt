package com.bearphone.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bearphone.app.block.BlockStore
import com.bearphone.app.block.NumberMatcher
import com.bearphone.app.ui.theme.CallRed
import com.bearphone.app.ui.theme.bearFrame

/** Настройки блокировки: переключатели, чёрный список и маски, журнал заблокированных. */
@Composable
fun BlockingScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val s by BlockStore.state.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxSize()) {
        // ---- заголовок ----
        item {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                Text("Блокировка", style = MaterialTheme.typography.titleLarge)
            }
        }

        // ---- переключатели ----
        item {
            SwitchRow("Скрытые номера", "Звонки без определителя номера", s.blockHidden) { v ->
                BlockStore.update { it.copy(blockHidden = v) }
            }
            SwitchRow("Все, кого нет в контактах", "Жёсткий режим: пропускает только знакомых", s.blockNotInContacts) { v ->
                BlockStore.update { it.copy(blockNotInContacts = v) }
            }
            SwitchRow("Уведомлять о блокировке", "Тихое уведомление в шторке", s.notify) { v ->
                BlockStore.update { it.copy(notify = v) }
            }
            SwitchRow("Не записывать в журнал", "Заблокированные не появятся в «Недавних»", s.skipCallLog) { v ->
                BlockStore.update { it.copy(skipCallLog = v) }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
        }

        // ---- добавление правила ----
        item {
            SectionTitle("Чёрный список и маски")
            Column(Modifier.padding(horizontal = 16.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.filter { c -> c.isDigit() || c in "+*?()- " }; error = null },
                    label = { Text("Номер или маска") },
                    placeholder = { Text("+7495*  или  8800*") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = {
                        Text(error ?: "* — любые цифры, ? — одна цифра. 8 и +7 считаются одинаково.")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Заметка (необязательно)") },
                    placeholder = { Text("Спам из банка") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.padding(4.dp))
                Button(
                    onClick = {
                        if (BlockStore.addRule(input, note)) { input = ""; note = "" }
                        else error = if (NumberMatcher.key(input).isEmpty()) "Введите номер" else "Такое правило уже есть"
                    },
                    modifier = Modifier.bearFrame()
                ) {
                    Icon(Icons.Filled.Block, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Заблокировать")
                }
            }
        }

        if (s.rules.isEmpty()) {
            item {
                Text(
                    "Правил пока нет. Номер можно заблокировать и долгим тапом в «Недавних».",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        items(s.rules, key = { "r-" + it.pattern }) { rule ->
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(rule.pattern, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    val kind = if (NumberMatcher.isMask(rule.pattern)) "маска" else "номер"
                    Text(
                        if (rule.note.isBlank()) kind else "$kind · ${rule.note}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { BlockStore.removeRule(rule) }) { Icon(Icons.Filled.Close, "Удалить правило") }
            }
        }

        // ---- журнал заблокированных ----
        item {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { SectionTitle("Заблокированные звонки (${s.log.size})") }
                if (s.log.isNotEmpty()) {
                    TextButton(onClick = BlockStore::clearLog, modifier = Modifier.padding(end = 8.dp)) { Text("Очистить") }
                }
            }
        }
        items(s.log.size, key = { "l-" + s.log[it].timeMillis + "-" + it }) { i ->
            val e = s.log[i]
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Block, null, tint = CallRed)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(e.number ?: "Скрытый номер", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${relativeTime(e.timeMillis)} · ${e.reason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item { Spacer(Modifier.padding(16.dp)) }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
