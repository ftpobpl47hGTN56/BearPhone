package com.bearphone.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Contact(
    val id: Long,
    val name: String,
    val numbers: List<String>,
    val photoUri: String?,
    val starred: Boolean,
)

/** Чтение системной книги контактов. Ничего не копируем к себе — только читаем. */
object ContactsRepository {

    private fun granted(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /** Все контакты с телефонами, отсортированные по имени. */
    suspend fun loadAll(ctx: Context): List<Contact> = withContext(Dispatchers.IO) {
        if (!granted(ctx)) return@withContext emptyList()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
            ContactsContract.CommonDataKinds.Phone.STARRED,
        )
        val map = LinkedHashMap<Long, Contact>()
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection, null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY + " COLLATE LOCALIZED ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = c.getString(1) ?: continue
                val number = c.getString(2)?.trim().orEmpty()
                if (number.isEmpty()) continue
                val existing = map[id]
                if (existing == null) {
                    map[id] = Contact(id, name, listOf(number), c.getString(3), c.getInt(4) == 1)
                } else if (existing.numbers.none { normalize(it) == normalize(number) }) {
                    map[id] = existing.copy(numbers = existing.numbers + number)
                }
            }
        }
        map.values.toList()
    }

    data class ContactInfo(
        val id: Long,
        val lookupKey: String,
        val name: String,
        val numbers: List<String>,
        /** Тип учётной записи, где хранится контакт ("com.google", "...sim"...) */
        val accountType: String? = null,
    ) {
        /** Контакт на SIM-карте — сторонним приложениям его редактировать нельзя */
        val onSim: Boolean
            get() = accountType?.lowercase()?.let { "sim" in it || "icc" in it } == true
    }

    /** Полная информация о контакте по номеру (для карточки). null — номера нет в контактах. */
    suspend fun lookup(ctx: Context, number: String?): ContactInfo? = withContext(Dispatchers.IO) {
        if (number.isNullOrBlank() || !granted(ctx)) return@withContext null
        try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            val (id, key, name) = ctx.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.PhoneLookup._ID,
                    ContactsContract.PhoneLookup.LOOKUP_KEY,
                    ContactsContract.PhoneLookup.DISPLAY_NAME,
                ),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) Triple(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty()) else null
            } ?: return@withContext null

            val numbers = ArrayList<String>()
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=?", arrayOf(id.toString()), null
            )?.use { c ->
                while (c.moveToNext()) {
                    val n = c.getString(0)?.trim().orEmpty()
                    if (n.isNotEmpty() && numbers.none { normalize(it) == normalize(n) }) numbers += n
                }
            }
            val account = ctx.contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts.ACCOUNT_TYPE),
                ContactsContract.RawContacts.CONTACT_ID + "=?", arrayOf(id.toString()), null
            )?.use { c ->
                // Если контакт склеен из нескольких — важна хотя бы одна НЕ-SIM запись (её можно править)
                val types = ArrayList<String>()
                while (c.moveToNext()) c.getString(0)?.let { types += it }
                types.firstOrNull { t -> t.lowercase().let { "sim" !in it && "icc" !in it } } ?: types.firstOrNull()
            }
            ContactInfo(id, key, name, numbers.ifEmpty { listOf(number) }, account)
        } catch (e: Exception) {
            null
        }
    }

    /** Имя по номеру (для входящего и журнала). null, если номера нет в контактах. */
    fun nameFor(ctx: Context, number: String?): String? {
        if (number.isNullOrBlank() || !granted(ctx)) return null
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)
        )
        return try {
            ctx.contentResolver.query(
                uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (e: Exception) {
            null
        }
    }

    /** Только цифры и ведущий '+', для сравнения номеров. */
    fun normalize(number: String): String =
        number.filterIndexed { i, ch -> ch.isDigit() || (ch == '+' && i == 0) }
}
