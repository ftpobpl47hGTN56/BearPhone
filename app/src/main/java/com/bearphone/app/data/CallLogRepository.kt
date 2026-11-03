package com.bearphone.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CallLogEntry(
    val id: Long,
    val number: String,
    val name: String?,
    /** CallLog.Calls.INCOMING_TYPE / OUTGOING_TYPE / MISSED_TYPE / REJECTED_TYPE / BLOCKED_TYPE */
    val type: Int,
    val dateMillis: Long,
    val durationSec: Long,
    /** Регион номера, который определил сам Android ("Новосибирская обл."), может быть null */
    val geocoded: String? = null,
    /** Тип номера в контакте: "Мобильный", "Рабочий"… */
    val numberLabel: String? = null,
    /** Запись из журнала блокировки BearPhone, а не из системного журнала (id < 0) */
    val fromBlockLog: Boolean = false,
)

/** Системный журнал звонков — читаем напрямую, без своей базы. */
object CallLogRepository {

    private const val LIMIT = 300

    suspend fun load(ctx: Context): List<CallLogEntry> = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED
        ) return@withContext emptyList()

        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.GEOCODED_LOCATION,
            CallLog.Calls.CACHED_NUMBER_TYPE,
            CallLog.Calls.CACHED_NUMBER_LABEL,
        )
        val result = ArrayList<CallLogEntry>(LIMIT)
        try {
            ctx.contentResolver.query(
                CallLog.Calls.CONTENT_URI, projection, null, null,
                CallLog.Calls.DATE + " DESC"
            )?.use { c ->
                while (c.moveToNext() && result.size < LIMIT) {
                    val label = if (c.isNull(7)) null else
                        android.provider.ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                            ctx.resources, c.getInt(7), c.getString(8)
                        )?.toString()
                    result += CallLogEntry(
                        id = c.getLong(0),
                        number = c.getString(1).orEmpty(),
                        name = c.getString(2)?.takeIf { it.isNotBlank() },
                        type = c.getInt(3),
                        dateMillis = c.getLong(4),
                        durationSec = c.getLong(5),
                        geocoded = c.getString(6)?.takeIf { it.isNotBlank() },
                        numberLabel = label?.takeIf { it.isNotBlank() },
                    )
                }
            }
        } catch (e: Exception) {
            // нет доступа / прошивка ругается на колонку — отдаём, что успели
        }
        result
    }

    /**
     * Подставить имена из контактов (в т.ч. с SIM). Поле CACHED_NAME в журнале заполняет
     * только системная звонилка — с BearPhone по умолчанию оно пустое у новых звонков.
     */
    fun withNames(entries: List<CallLogEntry>, contacts: List<Contact>): List<CallLogEntry> {
        if (contacts.isEmpty()) return entries
        val exact = HashMap<String, String>()   // полный ключ номера
        val last10 = HashMap<String, String>()  // последние 10 цифр (+7 / 8 / без кода)
        val short = ArrayList<Pair<String, String>>() // городские 5–9 цифр: "227-42-36"
        contacts.forEach { c ->
            c.numbers.forEach { n ->
                val k = com.bearphone.app.block.NumberMatcher.key(n)
                val d = n.filter(Char::isDigit)
                if (k.isNotEmpty()) exact.putIfAbsent(k, c.name)
                if (d.length >= 10) last10.putIfAbsent(d.takeLast(10), c.name)
                else if (d.length >= 5) short += d to c.name
            }
        }
        return entries.map { e ->
            if (e.name != null || e.number.isBlank()) return@map e
            val d = e.number.filter(Char::isDigit)
            val name = exact[com.bearphone.app.block.NumberMatcher.key(e.number)]
                ?: (if (d.length >= 10) last10[d.takeLast(10)] else null)
                ?: short.firstOrNull { (s, _) -> d == s || (d.length in (s.length + 1)..11 && d.endsWith(s)) }?.second
            if (name != null) e.copy(name = name) else e
        }
    }

    /** Удалить записи (нужен WRITE_CALL_LOG). Отрицательные id — это журнал BearPhone, их тут не трогаем. */
    suspend fun deleteMany(ctx: Context, ids: Collection<Long>): Int = withContext(Dispatchers.IO) {
        val real = ids.filter { it > 0 }
        if (real.isEmpty()) return@withContext 0
        var deleted = 0
        try {
            // Пачками по 500, чтобы не упереться в лимит параметров SQLite
            real.chunked(500).forEach { chunk ->
                val where = CallLog.Calls._ID + " IN (" + chunk.joinToString(",") { "?" } + ")"
                deleted += ctx.contentResolver.delete(
                    CallLog.Calls.CONTENT_URI, where, chunk.map { it.toString() }.toTypedArray()
                )
            }
        } catch (e: SecurityException) {
            // нет разрешения
        }
        deleted
    }
}
