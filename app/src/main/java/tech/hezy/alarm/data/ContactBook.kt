package tech.hezy.alarm.data

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import tech.hezy.alarm.domain.Alarm
import tech.hezy.alarm.domain.DeviceAccount
import tech.hezy.alarm.domain.LUNAR_EVENT_LABEL
import tech.hezy.alarm.domain.LunarBirthday
import tech.hezy.alarm.domain.contactLunarText
import tech.hezy.alarm.domain.contactSolarDate
import tech.hezy.alarm.domain.isSimAccount
import tech.hezy.alarm.domain.pickDeviceAccount
import tech.hezy.alarm.domain.rawWriteRank
import tech.hezy.alarm.domain.readContactEvent

data class ContactCandidate(
    val contactId: Long,
    val name: String,
    val birthday: LunarBirthday,
)

data class ContactNameMatch(
    val contactId: Long,
    val name: String,
    val birthdayLabel: String?,
)

fun alreadyImported(alarms: List<Alarm>, candidate: ContactCandidate): Boolean {
    val birthday = candidate.birthday
    return alarms.any { alarm ->
        val stored = alarm.birthday ?: return@any false
        stored.solar == birthday.solar &&
            (alarm.label == candidate.name || stored.contactId == candidate.contactId)
    }
}

class ContactBook(private val resolver: ContentResolver) {
    fun birthdays(): List<ContactCandidate> = runCatching {
        val parsed = ArrayList<Pair<Long, LunarBirthday>>()
        resolver.query(
            Data.CONTENT_URI,
            arrayOf(Data.CONTACT_ID, Event.TYPE, Event.LABEL, Event.START_DATE),
            "${Data.MIMETYPE}=?",
            arrayOf(Event.CONTENT_ITEM_TYPE),
            null,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(Data.CONTACT_ID)
            val typeCol = cursor.getColumnIndexOrThrow(Event.TYPE)
            val labelCol = cursor.getColumnIndexOrThrow(Event.LABEL)
            val dateCol = cursor.getColumnIndexOrThrow(Event.START_DATE)
            while (cursor.moveToNext()) {
                val birthday = readContactEvent(
                    cursor.getInt(typeCol),
                    cursor.getString(labelCol),
                    cursor.getString(dateCol),
                ) ?: continue
                parsed += cursor.getLong(idCol) to birthday
            }
        }
        val names = displayNames(parsed.map { it.first }.distinct())
        parsed.mapNotNull { (id, birthday) ->
            val name = names[id] ?: return@mapNotNull null
            ContactCandidate(id, name, birthday)
        }.distinctBy {
            listOf(it.contactId, it.birthday.solar, it.birthday.month, it.birthday.day, it.birthday.year)
        }
    }.getOrDefault(emptyList())

    fun matches(name: String): List<ContactNameMatch> = runCatching {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return emptyList()
        val found = ArrayList<Pair<Long, String>>()
        val uri = Uri.withAppendedPath(Contacts.CONTENT_FILTER_URI, Uri.encode(trimmed))
        resolver.query(uri, arrayOf(Contacts._ID, Contacts.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(Contacts._ID)
            val nameCol = cursor.getColumnIndexOrThrow(Contacts.DISPLAY_NAME)
            while (cursor.moveToNext() && found.size < 30) {
                val display = cursor.getString(nameCol)?.trim().orEmpty()
                if (display.isEmpty()) continue
                found += cursor.getLong(idCol) to display
            }
        }
        if (found.isEmpty()) return emptyList()
        val labels = birthdayLabels(found.map { it.first })
        val folded = fold(trimmed)
        val exact = ArrayList<ContactNameMatch>()
        val fuzzy = ArrayList<ContactNameMatch>()
        for ((id, display) in found) {
            val match = ContactNameMatch(id, display, labels[id])
            if (fold(display) == folded) exact += match else fuzzy += match
        }
        (exact + fuzzy).take(5)
    }.getOrDefault(emptyList())

    fun contactId(uri: Uri): Long? = runCatching {
        resolver.query(uri, arrayOf(Contacts._ID), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        }
    }.getOrNull()

    /** create=true 新建联系人。否则只替换同一种历法的那一条，公历和农历互不覆盖。 */
    fun write(name: String, birthday: LunarBirthday, contactId: Long?, create: Boolean): Long? = runCatching {
        val id = if (create) {
            if (name.isBlank()) return null
            createContact(name.trim())
        } else {
            contactId
        } ?: return null
        val rawIds = writableRawIds(id)
        if (rawIds.isEmpty() || rawIds.none { writeOn(it, id, birthday) }) return null
        id
    }.getOrNull()

    private fun writeOn(rawId: Long, contactId: Long, birthday: LunarBirthday): Boolean {
        val ops = ArrayList<ContentProviderOperation>()
        if (birthday.solar) {
            ops += ContentProviderOperation.newDelete(Data.CONTENT_URI)
                .withSelection(
                    "${Data.CONTACT_ID}=? AND ${Data.MIMETYPE}=? AND ${Event.TYPE}=?",
                    arrayOf(contactId.toString(), Event.CONTENT_ITEM_TYPE, Event.TYPE_BIRTHDAY.toString()),
                )
                .build()
            ops += eventInsert(rawId, Event.TYPE_BIRTHDAY, null, contactSolarDate(birthday.year, birthday.month, birthday.day))
        } else {
            val labels = arrayOf("农历生日", "阴历生日", "旧历生日", "农历", "阴历", "旧历", "生日(农历)", "生日（农历）")
            val marks = labels.joinToString(",") { "?" }
            ops += ContentProviderOperation.newDelete(Data.CONTENT_URI)
                .withSelection(
                    "${Data.CONTACT_ID}=? AND ${Data.MIMETYPE}=? AND ${Event.LABEL} IN ($marks)",
                    arrayOf(contactId.toString(), Event.CONTENT_ITEM_TYPE, *labels),
                )
                .build()
            ops += eventInsert(rawId, Event.TYPE_CUSTOM, LUNAR_EVENT_LABEL, contactLunarText(birthday))
        }
        return runCatching {
            resolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        }.getOrDefault(false)
    }

    private fun eventInsert(rawId: Long, type: Int, label: String?, start: String) =
        ContentProviderOperation.newInsert(Data.CONTENT_URI)
            .withValue(Data.RAW_CONTACT_ID, rawId)
            .withValue(Data.MIMETYPE, Event.CONTENT_ITEM_TYPE)
            .withValue(Event.TYPE, type)
            .withValue(Event.LABEL, label)
            .withValue(Event.START_DATE, start)
            .build()

    private fun createContact(name: String): Long? {
        val account = pickDeviceAccount(deviceAccounts())
        val ops = arrayListOf(
            ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                .withValue(RawContacts.ACCOUNT_TYPE, account.type)
                .withValue(RawContacts.ACCOUNT_NAME, account.name)
                .build(),
            ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                .withValue(StructuredName.DISPLAY_NAME, name)
                .withValue(StructuredName.GIVEN_NAME, name)
                .build(),
        )
        val results = runCatching { resolver.applyBatch(ContactsContract.AUTHORITY, ops) }.getOrNull() ?: return null
        val rawUri = results.firstOrNull()?.uri ?: return null
        val rawId = runCatching { ContentUris.parseId(rawUri) }.getOrNull() ?: return null
        return contactIdForRaw(rawId)
    }

    private fun deviceAccounts(): List<DeviceAccount> {
        val found = LinkedHashMap<Pair<String?, String?>, DeviceAccount>()
        resolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE),
            "${RawContacts.DELETED}=0",
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val account = DeviceAccount(cursor.getString(0), cursor.getString(1))
                found.putIfAbsent(account.name to account.type, account)
            }
        }
        return found.values.toList()
    }

    private fun contactIdForRaw(rawId: Long): Long? =
        resolver.query(
            ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawId),
            arrayOf(RawContacts.CONTACT_ID),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) null else cursor.getLong(0).takeIf { it > 0 }
        }

    private fun writableRawIds(contactId: Long): List<Long> {
        val rows = ArrayList<Triple<Long, String?, String?>>()
        resolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts._ID, RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE),
            "${RawContacts.CONTACT_ID}=? AND ${RawContacts.DELETED}=0",
            arrayOf(contactId.toString()),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val type = cursor.getString(2)
                if (isSimAccount(type)) continue
                rows += Triple(cursor.getLong(0), cursor.getString(1), type)
            }
        }
        return rows.sortedBy { rawWriteRank(it.third) }.map { it.first }
    }

    private fun displayNames(ids: List<Long>): Map<Long, String> {
        val out = HashMap<Long, String>()
        for (chunk in ids.chunked(80)) {
            val marks = chunk.joinToString(",") { "?" }
            resolver.query(
                Contacts.CONTENT_URI,
                arrayOf(Contacts._ID, Contacts.DISPLAY_NAME),
                "${Contacts._ID} IN ($marks)",
                chunk.map { it.toString() }.toTypedArray(),
                null,
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(Contacts._ID)
                val nameCol = cursor.getColumnIndexOrThrow(Contacts.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol)?.trim().orEmpty()
                    if (name.isNotEmpty()) out[cursor.getLong(idCol)] = name
                }
            }
        }
        return out
    }

    private fun birthdayLabels(ids: List<Long>): Map<Long, String> {
        if (ids.isEmpty()) return emptyMap()
        val marks = ids.joinToString(",") { "?" }
        val args = ids.map { it.toString() }.toTypedArray() + Event.CONTENT_ITEM_TYPE
        val grouped = HashMap<Long, MutableList<String>>()
        resolver.query(
            Data.CONTENT_URI,
            arrayOf(Data.CONTACT_ID, Event.TYPE, Event.LABEL, Event.START_DATE),
            "${Data.CONTACT_ID} IN ($marks) AND ${Data.MIMETYPE}=?",
            args,
            null,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(Data.CONTACT_ID)
            val typeCol = cursor.getColumnIndexOrThrow(Event.TYPE)
            val labelCol = cursor.getColumnIndexOrThrow(Event.LABEL)
            val dateCol = cursor.getColumnIndexOrThrow(Event.START_DATE)
            while (cursor.moveToNext()) {
                val text = readContactEvent(cursor.getInt(typeCol), cursor.getString(labelCol), cursor.getString(dateCol))
                    ?.let { if (it.solar) contactSolarDate(it.year, it.month, it.day) else contactLunarText(it) }
                    ?: continue
                grouped.getOrPut(cursor.getLong(idCol)) { ArrayList() }.add(text)
            }
        }
        return grouped.mapValues { it.value.distinct().joinToString(" · ") }
    }

    private fun fold(value: String) = value.lowercase().replace(" ", "")
}
