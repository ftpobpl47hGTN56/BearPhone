package com.bearphone.app.data

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SmsThread(
    val threadId: Long,
    val address: String,
    val snippet: String,
    val dateMillis: Long,
    val unread: Int,
    /** последнее сообщение отправлено нами */
    val lastOutgoing: Boolean,
)

data class SmsMessage(
    val id: Long,
    val body: String,
    val dateMillis: Long,
    val outgoing: Boolean,
    /** Telephony.Sms.MESSAGE_TYPE_*: отправляется / ошибка и т.п. */
    val type: Int,
    val address: String = "",
    /** Когда отправлено (для входящих — время у отправителя), 0 — неизвестно */
    val dateSentMillis: Long = 0,
    val serviceCenter: String? = null,
    /** Telephony.Sms.STATUS: -1 нет отчёта, 0 доставлено, 32 ожидает, 64 ошибка */
    val status: Int = -1,
    val read: Boolean = true,
    /** SIM, через которую прошло сообщение (subscription id), -1 — неизвестно */
    val subId: Int = -1,
)

/**
 * Чтение и отправка SMS БЕЗ роли SMS-приложения по умолчанию.
 * Системное приложение Xiaomi остаётся главным: оно принимает SMS и показывает уведомления,
 * а мы читаем общую базу сообщений и отправляем через SmsManager
 * (система сама сохраняет отправленные в "Отправленные").
 */
object SmsRepository {

    /** Защита от телефонов с сотнями тысяч SMS: дальше этого количества не сканируем. */
    private const val SCAN_LIMIT = 30_000
    private const val THREAD_MESSAGES_LIMIT = 1_000

    fun canRead(ctx: Context) = granted(ctx, Manifest.permission.READ_SMS)
    fun canSend(ctx: Context) = granted(ctx, Manifest.permission.SEND_SMS)

    private fun granted(ctx: Context, p: String) =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    /** Список диалогов: последнее сообщение каждого thread_id, новые сверху. */
    suspend fun loadThreads(ctx: Context): List<SmsThread> = withContext(Dispatchers.IO) {
        if (!canRead(ctx)) return@withContext emptyList()
        val byThread = LinkedHashMap<Long, SmsThread>()
        val unread = HashMap<Long, Int>()
        try {
            ctx.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY,
                    Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.READ,
                ),
                null, null, Telephony.Sms.DATE + " DESC"
            )?.use { c ->
                var n = 0
                while (c.moveToNext() && n++ < SCAN_LIMIT) {
                    val tid = c.getLong(0)
                    val type = c.getInt(4)
                    // Диалог скрыт целиком (до прихода нового сообщения) — пропускаем
                    if (HiddenSmsStore.isThreadHidden(tid, c.getLong(3))) continue
                    if (type == Telephony.Sms.MESSAGE_TYPE_INBOX && c.getInt(5) == 0 &&
                        !SmsReadStore.isRead(tid, c.getLong(3))
                    ) {
                        unread[tid] = (unread[tid] ?: 0) + 1
                    }
                    if (tid !in byThread) {
                        byThread[tid] = SmsThread(
                            threadId = tid,
                            address = c.getString(1).orEmpty(),
                            snippet = c.getString(2).orEmpty().replace('\n', ' '),
                            dateMillis = c.getLong(3),
                            unread = 0,
                            lastOutgoing = type != Telephony.Sms.MESSAGE_TYPE_INBOX,
                        )
                    }
                }
            }
        } catch (e: Exception) {
            return@withContext emptyList()
        }
        byThread.values.map { it.copy(unread = unread[it.threadId] ?: 0) }
    }

    /** Сообщения диалога, старые сверху. */
    suspend fun loadMessages(ctx: Context, threadId: Long): List<SmsMessage> = withContext(Dispatchers.IO) {
        if (!canRead(ctx)) return@withContext emptyList()
        val out = ArrayList<SmsMessage>()
        try {
            ctx.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE,
                    Telephony.Sms.ADDRESS, Telephony.Sms.DATE_SENT, Telephony.Sms.SERVICE_CENTER,
                    Telephony.Sms.STATUS, Telephony.Sms.READ, Telephony.Sms.SUBSCRIPTION_ID,
                ),
                Telephony.Sms.THREAD_ID + "=?", arrayOf(threadId.toString()),
                Telephony.Sms.DATE + " DESC"
            )?.use { c ->
                while (c.moveToNext() && out.size < THREAD_MESSAGES_LIMIT) {
                    val type = c.getInt(3)
                    val id = c.getLong(0)
                    if (HiddenSmsStore.isMessageHidden(id)) continue
                    out += SmsMessage(
                        id = id,
                        body = c.getString(1).orEmpty(),
                        dateMillis = c.getLong(2),
                        outgoing = type != Telephony.Sms.MESSAGE_TYPE_INBOX,
                        type = type,
                        address = c.getString(4).orEmpty(),
                        dateSentMillis = c.getLong(5),
                        serviceCenter = c.getString(6)?.takeIf { it.isNotBlank() },
                        status = if (c.isNull(7)) -1 else c.getInt(7),
                        read = c.getInt(8) != 0,
                        subId = if (c.isNull(9)) -1 else c.getInt(9),
                    )
                }
            }
        } catch (e: Exception) {
            // нет доступа
        }
        out.reverse()
        out
    }

    /**
     * Отправка SMS с SIM по умолчанию. Длинный текст режется на части автоматически.
     * @return null при успехе постановки в очередь, иначе текст ошибки
     */
    fun send(ctx: Context, address: String, text: String): String? {
        if (!canSend(ctx)) return "Нет разрешения на отправку SMS"
        if (address.isBlank() || text.isBlank()) return "Пустой номер или текст"
        return try {
            val sms: SmsManager =
                if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java)
                else @Suppress("DEPRECATION") SmsManager.getDefault()
            val parts = sms.divideMessage(text)

            // Главное SMS-приложение САМО пишет исходящие в базу (система за него этого не делает).
            // Кладём в "Исходящие", а SmsSentReceiver переведёт в "Отправлено" или "Ошибка".
            val msgUri = if (isDefaultApp(ctx)) insertOutgoing(ctx, address, text) else null

            val sentIntent = Intent(ctx, com.bearphone.app.sms.SmsSentReceiver::class.java)
                .setAction(com.bearphone.app.sms.SmsSentReceiver.ACTION_SENT)
                .apply { msgUri?.let { data = it } }
            // Уникальный requestCode, чтобы PendingIntent разных сообщений не склеивались
            val code = (msgUri?.lastPathSegment?.toIntOrNull() ?: System.currentTimeMillis().toInt())
            val sent = PendingIntent.getBroadcast(
                ctx, code, sentIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            if (parts.size == 1) {
                sms.sendTextMessage(address, null, text, sent, null)
            } else {
                // Отчёт нужен только по последней части
                val intents = ArrayList<PendingIntent?>(parts.size)
                repeat(parts.size - 1) { intents += null }
                intents += sent
                sms.sendMultipartTextMessage(address, null, parts, intents, null)
            }
            null
        } catch (e: Exception) {
            e.message ?: "Ошибка отправки"
        }
    }

    // ---------------- режим "SMS-приложение по умолчанию" ----------------

    /**
     * BearPhone — SMS-приложение по умолчанию? На Android 10+ спрашиваем RoleManager:
     * getDefaultSmsPackage на некоторых прошивках (HyperOS) отдаёт устаревший ответ.
     */
    fun isDefaultApp(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 29) {
            ctx.getSystemService(android.app.role.RoleManager::class.java)
                ?.isRoleHeld(android.app.role.RoleManager.ROLE_SMS) == true ||
                Telephony.Sms.getDefaultSmsPackage(ctx) == ctx.packageName
        } else Telephony.Sms.getDefaultSmsPackage(ctx) == ctx.packageName

    /** Название приложения, которое сейчас главное для SMS (для подсказки в настройках). */
    fun defaultAppLabel(ctx: Context): String? = try {
        val pkg = Telephony.Sms.getDefaultSmsPackage(ctx) ?: return null
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { null }

    /** Отметить прочитанным диалог: и в системной базе (если можно), и у себя. */
    suspend fun markRead(ctx: Context, thread: SmsThread) = withContext(Dispatchers.IO) {
        SmsReadStore.markThread(thread.threadId)
        markThreadRead(ctx, thread.threadId)
        markAddressRead(ctx, thread.address)
        com.bearphone.app.sms.SmsNotifier.cancel(ctx, thread.address)
    }

    /** "Прочитать всё". */
    suspend fun markAllRead(ctx: Context, threads: List<SmsThread>) = withContext(Dispatchers.IO) {
        SmsReadStore.markAll()
        try {
            ctx.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                android.content.ContentValues().apply { put(Telephony.Sms.READ, 1); put(Telephony.Sms.SEEN, 1) },
                Telephony.Sms.READ + "=0", null
            )
        } catch (e: Exception) { }
        threads.forEach { com.bearphone.app.sms.SmsNotifier.cancel(ctx, it.address) }
    }

    private fun insertOutgoing(ctx: Context, address: String, text: String): android.net.Uri? = try {
        ctx.contentResolver.insert(
            Telephony.Sms.CONTENT_URI,
            android.content.ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, text)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
            }
        )
    } catch (e: Exception) { null }

    /** Сохранить входящее (вызывает приёмник). Возвращает Uri записи или null. */
    fun insertIncoming(
        ctx: Context, address: String, body: String, sentAt: Long,
        serviceCenter: String?, subId: Int, read: Boolean,
    ): android.net.Uri? = try {
        ctx.contentResolver.insert(
            Telephony.Sms.Inbox.CONTENT_URI,
            android.content.ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.DATE_SENT, sentAt)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                put(Telephony.Sms.READ, if (read) 1 else 0)
                put(Telephony.Sms.SEEN, if (read) 1 else 0)
                serviceCenter?.let { put(Telephony.Sms.SERVICE_CENTER, it) }
                if (subId >= 0) put(Telephony.Sms.SUBSCRIPTION_ID, subId)
            }
        )
    } catch (e: Exception) { null }

    /** Отметить диалог прочитанным (работает, только когда BearPhone — SMS по умолчанию). */
    suspend fun markThreadRead(ctx: Context, threadId: Long) = withContext(Dispatchers.IO) {
        try {
            ctx.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                android.content.ContentValues().apply {
                    put(Telephony.Sms.READ, 1); put(Telephony.Sms.SEEN, 1)
                },
                Telephony.Sms.THREAD_ID + "=? AND " + Telephony.Sms.READ + "=0",
                arrayOf(threadId.toString())
            )
        } catch (e: Exception) { 0 }
    }

    fun markAddressRead(ctx: Context, address: String) = try {
        ctx.contentResolver.update(
            Telephony.Sms.CONTENT_URI,
            android.content.ContentValues().apply { put(Telephony.Sms.READ, 1); put(Telephony.Sms.SEEN, 1) },
            Telephony.Sms.ADDRESS + "=? AND " + Telephony.Sms.READ + "=0",
            arrayOf(address)
        )
    } catch (e: Exception) { 0 }

    /**
     * Удалить сообщения. Android даёт удалять SMS ТОЛЬКО приложению SMS по умолчанию,
     * поэтому пробуем удалить, а что не удалилось — скрываем внутри BearPhone.
     * @return сколько удалено по-настоящему (остальные скрыты)
     */
    suspend fun deleteMessages(ctx: Context, ids: Collection<Long>): Int = withContext(Dispatchers.IO) {
        var deleted = 0
        ids.forEach { id ->
            val n = try {
                ctx.contentResolver.delete(
                    Telephony.Sms.CONTENT_URI, Telephony.Sms._ID + "=?", arrayOf(id.toString())
                )
            } catch (e: Exception) { 0 }
            if (n > 0) deleted += n else HiddenSmsStore.hideMessages(listOf(id))
        }
        deleted
    }

    /** Удалить (или скрыть) диалоги целиком. */
    suspend fun deleteThreads(ctx: Context, threads: Collection<SmsThread>): Int = withContext(Dispatchers.IO) {
        var deleted = 0
        threads.forEach { t ->
            val n = try {
                ctx.contentResolver.delete(
                    Telephony.Sms.CONTENT_URI, Telephony.Sms.THREAD_ID + "=?", arrayOf(t.threadId.toString())
                )
            } catch (e: Exception) { 0 }
            if (n > 0) deleted++ else HiddenSmsStore.hideThread(t.threadId, t.dateMillis)
        }
        deleted
    }

    data class SearchHit(val id: Long, val threadId: Long, val address: String, val body: String, val dateMillis: Long, val outgoing: Boolean)

    /**
     * Поиск по тексту ВСЕХ SMS. Фильтруем в Kotlin, а не через SQL LIKE:
     * LIKE в SQLite не умеет без учёта регистра для кириллицы ("Код" ≠ "код").
     */
    suspend fun search(ctx: Context, query: String, limit: Int = 300): List<SearchHit> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2 || !canRead(ctx)) return@withContext emptyList()
        val out = ArrayList<SearchHit>()
        try {
            ctx.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE),
                null, null, Telephony.Sms.DATE + " DESC"
            )?.use { c ->
                var n = 0
                while (c.moveToNext() && n++ < SCAN_LIMIT && out.size < limit) {
                    val body = c.getString(3) ?: continue
                    if (!body.contains(q, ignoreCase = true)) continue
                    val id = c.getLong(0)
                    if (HiddenSmsStore.isMessageHidden(id)) continue
                    out += SearchHit(
                        id = id, threadId = c.getLong(1), address = c.getString(2).orEmpty(),
                        body = body, dateMillis = c.getLong(4),
                        outgoing = c.getInt(5) != Telephony.Sms.MESSAGE_TYPE_INBOX,
                    )
                }
            }
        } catch (e: Exception) { }
        out
    }

    /** Найти диалог по номеру (для "Написать SMS" из журнала и нового сообщения). */
    fun findThread(threads: List<SmsThread>, address: String): SmsThread? {
        val key = com.bearphone.app.block.NumberMatcher.key(address)
        return threads.firstOrNull {
            it.address == address || (key.isNotEmpty() && com.bearphone.app.block.NumberMatcher.key(it.address) == key)
        }
    }
}
