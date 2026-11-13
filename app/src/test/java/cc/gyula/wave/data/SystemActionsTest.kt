package cc.gyula.wave.data

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows

/** What each hand-over asks Android to open. Dialling only opens the dialler: it never calls by itself. */
@RunWith(RobolectricTestRunner::class)
class SystemActionsTest {
    private val app = RuntimeEnvironment.getApplication()
    private val actions = SystemActions(app)

    private fun started(): Intent = Shadows.shadowOf(app).nextStartedActivity

    @Test
    fun dialOpensTheDiallerWithoutCalling() {
        actions.dial("+36301234567")
        val i = started()
        assertEquals(Intent.ACTION_DIAL, i.action)
        assertNotEquals(Intent.ACTION_CALL, i.action)
        assertEquals("tel", i.data?.scheme)
        assertEquals("+36301234567", i.data?.schemeSpecificPart)
    }

    @Test
    fun messageOpensAnSmsTo() {
        actions.message("+36301234567")
        val i = started()
        assertEquals(Intent.ACTION_SENDTO, i.action)
        assertEquals("smsto", i.data?.scheme)
        assertEquals("+36301234567", i.data?.schemeSpecificPart)
    }

    @Test
    fun emailOpensAMailTo() {
        actions.email("ann@example.com")
        val i = started()
        assertEquals(Intent.ACTION_SENDTO, i.action)
        assertEquals("mailto", i.data?.scheme)
        assertEquals("ann@example.com", i.data?.schemeSpecificPart)
    }

    @Test
    fun openContactViewsTheGivenAddress() {
        val uri = Uri.parse("content://com.android.contacts/contacts/lookup/abc/5")
        actions.openContact(uri)
        val i = started()
        assertEquals(Intent.ACTION_VIEW, i.action)
        assertEquals(uri, i.data)
    }

    @Test
    fun openReachViewsTheDataRowAsItsKindInTheOwningApp() {
        val mime = "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"
        actions.openReach(Reach(ReachKind.APP, "Voice call", "", 42, mime, "com.whatsapp"))
        val i = started()
        assertEquals(Intent.ACTION_VIEW, i.action)
        assertEquals("content://com.android.contacts/data/42", i.data.toString())
        assertEquals(mime, i.type)
        assertEquals("com.whatsapp", i.`package`)
    }

    @Test
    fun openReachWithoutAPackageLeavesTheChoiceToAndroid() {
        actions.openReach(Reach(ReachKind.APP, "Call", "", 7, "vnd.android.cursor.item/vnd.x", ""))
        assertEquals(null, started().`package`)
    }

    @Test
    fun openAppDetailsPointsAtOurOwnPackage() {
        actions.openAppDetails()
        val i = started()
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, i.action)
        assertEquals("package:${app.packageName}", i.data.toString())
    }
}
