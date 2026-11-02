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
        )
        val result = ArrayList<CallLogEntry>(LIMIT)
        ctx.contentResolver.query(
            CallLog.Calls.CONTENT_URI, projection, null, null,
            CallLog.Calls.DATE + " DESC"
        )?.use { c ->
            while (c.moveToNext() && result.size < LIMIT) {
                result += CallLogEntry(
                    id = c.getLong(0),
                    number = c.getString(1).orEmpty(),
                    name = c.getString(2)?.takeIf { it.isNotBlank() },
                    type = c.getInt(3),
                    dateMillis = c.getLong(4),
                    durationSec = c.getLong(5),
                )
            }
        }
        result
    }

    /** Удалить одну запись (нужен WRITE_CALL_LOG). */
    suspend fun delete(ctx: Context, id: Long) = withContext(Dispatchers.IO) {
        try {
            ctx.contentResolver.delete(
                CallLog.Calls.CONTENT_URI, CallLog.Calls._ID + "=?", arrayOf(id.toString())
            )
        } catch (e: SecurityException) {
            0
        }
    }
}
