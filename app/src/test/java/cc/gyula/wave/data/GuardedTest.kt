package cc.gyula.wave.data

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowToast

/** A missing or refusing app must never crash the home screen: it ends in a toast the user can read. */
@RunWith(RobolectricTestRunner::class)
class GuardedTest {
    private val app = RuntimeEnvironment.getApplication()

    @Test
    fun appGoneSaysSoInAToast() {
        app.guarded { throw ActivityNotFoundException() }
        assertEquals("Can't open that", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun refusalCarriesTheReasonInTheToast() {
        app.guarded { throw SecurityException("not allowed") }
        assertEquals("Can't open that", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun lockedWorkProfileAndCancelledIntentAreToastsToo() {
        app.guarded { throw IllegalStateException("locked") }
        assertEquals("Can't open that", ShadowToast.getTextOfLatestToast())
        ShadowToast.reset()
        app.guarded { throw PendingIntent.CanceledException() }
        assertEquals("Can't open that", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun aSuccessfulHandOverShowsNothing() {
        var ran = false
        app.guarded { ran = true }
        assertTrue(ran)
        assertEquals(0, ShadowToast.shownToastCount())
    }

    @Test
    fun otherErrorsAreNotSwallowed() {
        assertThrows(IllegalArgumentException::class.java) { app.guarded { throw IllegalArgumentException("bug") } }
    }
}
