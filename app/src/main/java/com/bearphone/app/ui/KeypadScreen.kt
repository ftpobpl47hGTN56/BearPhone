package com.bearphone.app.ui

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
 * Набор номера с T9-подсказками из контактов.
 * @param number текущий набранный номер (состояние хранит MainActivity, чтобы tel:-ссылки его заполняли)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeypadScreen(
    number: String,
    onNumberChange: (String) -> Unit,
    contacts: List<Contact>,
    onCall: (String) -> Unit,
) {
    val haptic = LocalHapticFeedback.current

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

    Column(Modifier.fillMaxSize()) {
        // ---- подсказки ----
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(suggestions, key = { it.id }) { c ->
                ListItem(
                    headlineContent = { Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(c.numbers.first()) },
                    leadingContent = { LetterAvatar(c.name, 40.dp) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.combinedClickable(
                        onClick = { onCall(c.numbers.first()) },
                        onLongClick = { onNumberChange(c.numbers.first()) }
                    )
                )
            }
        }

        // ---- поле номера ----
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(48.dp))
            Text(
                number,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontSize = if (number.length > 13) 26.sp else 34.sp,
                fontWeight = FontWeight.Light,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (number.isNotEmpty()) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .combinedClickable(
                                onClick = { onNumberChange(number.dropLast(1)) },
                                onLongClick = { onNumberChange("") }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Backspace, "Стереть")
                    }
                }
            }
        }

        // ---- клавиатура ----
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KEYS.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { (digit, letters) ->
                        DialKey(
                            digit = digit,
                            letters = letters,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNumberChange(number + digit)
                            },
                            onLongClick = when (digit) {
                                "0" -> ({ onNumberChange(number + "+") })
                                else -> null
                            }
                        )
                    }
                }
            }
        }

        // ---- кнопка вызова ----
        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            FloatingActionButton(
                onClick = { onCall(number) },
                containerColor = CallGreen,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(68.dp)
            ) {
                Icon(Icons.Filled.Call, "Позвонить", Modifier.size(30.dp))
            }
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
