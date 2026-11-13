package cc.gyula.wave.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** A stand-in for the Contacts provider: hands back the rows a test sets, and remembers what it was asked. */
class FakeContacts : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, args: Array<String>?, sort: String?): Cursor {
        queries += uri
        lastArgs = args?.toList()
        if (refuse) throw SecurityException("no permission")
        val rows = if (uri.path.orEmpty().contains("data")) dataRows else contactRows
        val columns = projection ?: emptyArray()
        // Rows may stop short; the rest of the columns are empty.
        return MatrixCursor(columns).also { c -> rows.forEach { r -> c.addRow(Array(columns.size) { r.getOrNull(it) }) } }
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0

    companion object {
        val queries = mutableListOf<Uri>()
        var lastArgs: List<String>? = null
        var contactRows: List<Array<Any?>> = emptyList()
        var dataRows: List<Array<Any?>> = emptyList()
        var refuse = false

        fun reset() {
            refuse = false
            queries.clear()
            lastArgs = null
            contactRows = emptyList()
            dataRows = emptyList()
        }
    }
}

/** Reading contacts: nothing without the permission, names mapped as they come, numbers and addresses tidied. */
@RunWith(RobolectricTestRunner::class)
class PeopleTest {
    private val resolver get() = RuntimeEnvironment.getApplication().contentResolver

    @Before
    fun setUp() {
        FakeContacts.reset()
        Robolectric.setupContentProvider(FakeContacts::class.java, ContactsContract.AUTHORITY)
    }

    private fun people(granted: Boolean = true) = People(resolver) { granted }

    @Test
    fun withoutPermissionNothingIsAskedAndNothingComesBack() = runBlocking {
        FakeContacts.contactRows = listOf(arrayOf(1L, "k", "Ann"))
        val p = people(granted = false)
        assertFalse(p.canRead())
        assertEquals(emptyList<Person>(), p.everyone())
        assertEquals(emptyList<Reach>(), p.reach(Person(1, "k", "Ann")))
        assertTrue(FakeContacts.queries.isEmpty())
    }

    @Test
    fun everyoneMapsRowsAndSkipsNamelessOnes() = runBlocking {
        FakeContacts.contactRows = listOf(
            arrayOf(1L, "k1", "Ann"),
            arrayOf(2L, "k2", null),
            arrayOf(3L, "k3", "  "),
            arrayOf(4L, "k4", "Éva Kiss"),
        )
        assertEquals(listOf(Person(1, "k1", "Ann"), Person(4, "k4", "Éva Kiss")), people().everyone())
        assertTrue(people().canRead())
    }

    @Test
    fun reachPutsPhonesBeforeEmailsAndKeepsEachNumberOnce() = runBlocking {
        FakeContacts.dataRows = listOf(
            arrayOf(Email.CONTENT_ITEM_TYPE, "ann@work.example", Email.TYPE_WORK, null),
            arrayOf(Phone.CONTENT_ITEM_TYPE, "+36 30 123 4567", Phone.TYPE_MOBILE, null),
            arrayOf(Phone.CONTENT_ITEM_TYPE, "+36301234567", Phone.TYPE_WORK, null),
            arrayOf(Phone.CONTENT_ITEM_TYPE, "06 1 555 0000", Phone.TYPE_CUSTOM, "Gran"),
            arrayOf(Email.CONTENT_ITEM_TYPE, "ann@home.example", 0, null),
        )
        val got = people().reach(Person(7, "k", "Ann"))
        assertEquals(
            listOf(
                Reach(ReachKind.PHONE, "+36 30 123 4567", "mobile"),
                Reach(ReachKind.PHONE, "06 1 555 0000", "Gran"),
                Reach(ReachKind.EMAIL, "ann@work.example", "work"),
                Reach(ReachKind.EMAIL, "ann@home.example", ""),
            ),
            got,
        )
        assertEquals("7", FakeContacts.lastArgs?.first())
    }

    private val whatsapp = "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"
    private val viber = "vnd.android.cursor.item/vnd.com.viber.voip.viber_out_call"

    @Test
    fun appActionsComeAfterEmailsGroupedByKindAndNeverIncludeAndroidsOwnRows() = runBlocking {
        FakeContacts.dataRows = listOf(
            arrayOf(whatsapp, "x@s.whatsapp.net", null, "Voice call +36301234567", 11L, "com.whatsapp"),
            arrayOf(viber, "+36301234567", "viber", null, 12L, "com.viber.voip"),
            arrayOf(whatsapp, "x@s.whatsapp.net", null, "Video call +36301234567", 13L, "com.whatsapp"),
            arrayOf(whatsapp, "x@s.whatsapp.net", null, "Voice call +36301234567", 14L, "com.whatsapp"), // exact duplicate
            arrayOf("vnd.android.cursor.item/vnd.x.silent", null, null, null, 15L, "x"), // nothing readable: left out
            arrayOf(Email.CONTENT_ITEM_TYPE, "a@b.example", Email.TYPE_HOME, null, 1L, "com.google"),
            arrayOf(Phone.CONTENT_ITEM_TYPE, "+36301234567", Phone.TYPE_MOBILE, null, 2L, "com.google"),
            arrayOf(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE, "Ann", "Ann", null, 3L, "com.google"),
            arrayOf(ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE, null, null, null, 4L, "com.google"),
            arrayOf(ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE, "5", null, null, 5L, "com.google"),
            arrayOf(ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE, "hi", null, null, 6L, "com.google"),
        )
        assertEquals(
            listOf(
                Reach(ReachKind.PHONE, "+36301234567", "mobile"),
                Reach(ReachKind.EMAIL, "a@b.example", "home"),
                Reach(ReachKind.APP, "Voice call +36301234567", "", 11, whatsapp, "com.whatsapp"),
                Reach(ReachKind.APP, "Video call +36301234567", "", 13, whatsapp, "com.whatsapp"),
                Reach(ReachKind.APP, "viber", "", 12, viber, "com.viber.voip"),
            ),
            people().reach(Person(7, "k", "Ann")),
        )
    }

    @Test
    fun aDataSetSuffixIsNotPartOfThePackage() = runBlocking {
        FakeContacts.dataRows = listOf(arrayOf(whatsapp, "j", null, "Message", 1L, "com.whatsapp/work"))
        assertEquals("com.whatsapp", people().reach(Person(7, "k", "Ann")).single().pkg)
    }

    @Test
    fun aRefusedQueryIsEmptyNotACrash() = runBlocking {
        FakeContacts.refuse = true
        assertEquals(emptyList<Person>(), people().everyone())
        assertEquals(emptyList<Reach>(), people().reach(Person(7, "k", "Ann")))
    }

    @Test
    fun uriPointsAtTheContactsPage() {
        val uri = people().uri(Person(5, "abc", "Ann"))
        assertEquals(ContactsContract.Contacts.getLookupUri(5, "abc"), uri)
    }
}
