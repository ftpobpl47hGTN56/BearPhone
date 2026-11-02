package com.bearphone.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Сообщения и диалоги, "удалённые" в BearPhone, когда Android не разрешил удалить их по-настоящему
 * (это может только SMS-приложение по умолчанию). В системном приложении они остаются.
 *
 * Скрытый диалог снова появляется, если в нём придёт НОВОЕ сообщение.
 */
object HiddenSmsStore {

    private var prefs: SharedPreferences? = null
    private val messages = HashSet<Long>()
    /** threadId -> дата последнего сообщения на момент скрытия */
    private val threads = HashMap<Long, Long>()

    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("bear_hidden_sms", Context.MODE_PRIVATE)
        prefs = p
        p.getStringSet("messages", emptySet())?.mapNotNullTo(messages) { it.toLongOrNull() }
        p.getStringSet("threads", emptySet())?.forEach { s ->
            val (t, d) = s.split(':').let { (it.getOrNull(0)?.toLongOrNull()) to (it.getOrNull(1)?.toLongOrNull()) }
            if (t != null && d != null) threads[t] = d
        }
    }

    @Synchronized fun isMessageHidden(id: Long) = id in messages

    /** Скрыт, если с момента скрытия в диалоге не было ничего нового. */
    @Synchronized fun isThreadHidden(threadId: Long, messageDate: Long): Boolean {
        val hiddenAt = threads[threadId] ?: return false
        return messageDate <= hiddenAt
    }

    @Synchronized fun hideMessages(ids: Collection<Long>) {
        messages += ids
        save()
    }

    @Synchronized fun hideThread(threadId: Long, lastDate: Long) {
        threads[threadId] = lastDate
        save()
    }

    @Synchronized fun count() = messages.size + threads.size

    @Synchronized fun restoreAll() {
        messages.clear(); threads.clear(); save()
    }

    private fun save() {
        prefs?.edit()
            ?.putStringSet("messages", messages.map { it.toString() }.toSet())
            ?.putStringSet("threads", threads.map { "${it.key}:${it.value}" }.toSet())
            ?.apply()
    }
}
