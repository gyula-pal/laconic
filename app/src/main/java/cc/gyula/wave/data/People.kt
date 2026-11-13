package cc.gyula.wave.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.CommonDataKinds.Identity
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.CommonDataKinds.Relation
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.telephony.PhoneNumberUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One contact: its row id, the stable lookup key Android uses to find it again, and the name shown. */
data class Person(val id: Long, val lookupKey: String, val name: String)

enum class ReachKind { PHONE, EMAIL, APP }

/**
 * One way to reach a person: a number or an address, with its label ("mobile", "work", or a custom one; "" if none).
 * For [ReachKind.APP], a row another app (WhatsApp, Signal, Viber …) added to the contact: [value] is the action's
 * own text ("Voice call"), [dataId] and [mime] say which Contacts row it is, [pkg] the app that owns it, "" if unknown.
 */
data class Reach(
    val kind: ReachKind,
    val value: String,
    val label: String,
    val dataId: Long = 0,
    val mime: String = "",
    val pkg: String = "",
)

/** English labels for the common types, the user's own text for a custom one, nothing for the rest. */
internal fun reachLabel(kind: ReachKind, type: Int, custom: String): String = when (kind) {
    ReachKind.APP -> ""
    ReachKind.PHONE -> when (type) {
        Phone.TYPE_MOBILE -> "mobile"
        Phone.TYPE_HOME -> "home"
        Phone.TYPE_WORK -> "work"
        Phone.TYPE_CUSTOM -> custom
        else -> ""
    }
    ReachKind.EMAIL -> when (type) {
        Email.TYPE_HOME -> "home"
        Email.TYPE_WORK -> "work"
        Email.TYPE_MOBILE -> "mobile"
        Email.TYPE_CUSTOM -> custom
        else -> ""
    }
}

/** Rows Android itself defines; any other kind was written by an app and may be an action such as "Video call". */
private val KNOWN_MIMES = setOf(
    Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE,
    StructuredName.CONTENT_ITEM_TYPE, StructuredPostal.CONTENT_ITEM_TYPE, Photo.CONTENT_ITEM_TYPE,
    GroupMembership.CONTENT_ITEM_TYPE, Organization.CONTENT_ITEM_TYPE, Nickname.CONTENT_ITEM_TYPE,
    Note.CONTENT_ITEM_TYPE, Website.CONTENT_ITEM_TYPE, Event.CONTENT_ITEM_TYPE, Relation.CONTENT_ITEM_TYPE,
    Identity.CONTENT_ITEM_TYPE,
    "vnd.android.cursor.item/im", "vnd.android.cursor.item/sip_address", // both classes are deprecated, the strings are not
)

/**
 * The phone's contacts, read when searching. Needs READ_CONTACTS, which [granted] reports; without it
 * every call answers empty rather than asking Android and getting a SecurityException. If the permission is
 * taken away between the check and the query, that too is an empty answer, not a crash.
 */
class People(private val resolver: ContentResolver, private val granted: () -> Boolean) {
    fun canRead(): Boolean = granted()

    /**
     * Everyone with a name. Matching happens in core, in memory, so accents fold the same way as app names;
     * nothing is filtered here.
     */
    suspend fun everyone(): List<Person> {
        if (!granted()) return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                resolver.query(
                    Contacts.CONTENT_URI,
                    arrayOf(Contacts._ID, Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY),
                    null,
                    null,
                    null,
                )?.use { c ->
                    buildList {
                        while (c.moveToNext()) {
                            val name = c.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                            add(Person(c.getLong(0), c.getString(1).orEmpty(), name))
                        }
                    }
                }.orEmpty()
            } catch (e: SecurityException) {
                emptyList()
            }
        }
    }

    /**
     * [person]'s phone numbers (the same number written two ways counts once), then their email addresses, then
     * what other apps added: WhatsApp's "Voice call", Signal's "Message", grouped by kind of row.
     */
    suspend fun reach(person: Person): List<Reach> {
        if (!granted()) return emptyList()
        return withContext(Dispatchers.IO) {
            val phones = LinkedHashMap<String, Reach>()
            val emails = ArrayList<Reach>()
            val apps = ArrayList<Reach>()
            try {
                resolver.query(
                    Data.CONTENT_URI,
                    arrayOf(Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3, Data._ID, Data.ACCOUNT_TYPE_AND_DATA_SET),
                    "${Data.CONTACT_ID} = ?",
                    arrayOf(person.id.toString()),
                    null,
                )?.use { c ->
                    while (c.moveToNext()) {
                        val mime = c.getString(0).orEmpty()
                        when (mime) {
                            Phone.CONTENT_ITEM_TYPE -> {
                                val value = c.getString(1)?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                                val digits = PhoneNumberUtils.normalizeNumber(value).ifEmpty { value }
                                phones.getOrPut(digits) {
                                    Reach(ReachKind.PHONE, value, reachLabel(ReachKind.PHONE, c.getInt(2), c.getString(3).orEmpty()))
                                }
                            }
                            Email.CONTENT_ITEM_TYPE -> {
                                val value = c.getString(1)?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                                emails += Reach(ReachKind.EMAIL, value, reachLabel(ReachKind.EMAIL, c.getInt(2), c.getString(3).orEmpty()))
                            }
                            in KNOWN_MIMES -> {}
                            "" -> {}
                            else -> {
                                val text = c.getString(3)?.trim()?.takeIf { it.isNotEmpty() }
                                    ?: c.getString(2)?.trim()?.takeIf { it.isNotEmpty() }
                                    ?: continue // nothing a person could read: leave it out
                                // The account type is usually the app's package; a data-set suffix ("type/set") is not part of it.
                                val pkg = c.getString(5).orEmpty().substringBefore('/')
                                apps += Reach(ReachKind.APP, text, "", c.getLong(4), mime, pkg)
                            }
                        }
                    }
                }
            } catch (e: SecurityException) {
                return@withContext emptyList()
            }
            val grouped = apps.distinctBy { it.mime to it.value }.groupBy { it.mime }.values.flatten()
            phones.values + emails + grouped
        }
    }

    /** The contact's page in the Contacts app. */
    fun uri(person: Person): Uri = Contacts.getLookupUri(person.id, person.lookupKey)
}
