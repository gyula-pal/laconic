package cc.gyula.wave.data

import android.content.ComponentName
import android.content.pm.LauncherActivityInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Process
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The icons a row asks for: a bad app gets a blank square, never an exception on the home screen. */
@RunWith(RobolectricTestRunner::class)
class AppCatalogTest {
    private val app = RuntimeEnvironment.getApplication()
    private val job = Job()
    private val scope = CoroutineScope(job + Dispatchers.Default)
    private var failure: Throwable? = null

    @After
    fun tearDown() {
        job.cancel()
    }

    /** The loader fails while [failure] is set, else draws a plain red square. */
    private fun catalog() = AppCatalog(app, MutableStateFlow(emptyMap()), scope) { _, _ ->
        failure?.let { throw it }
        ColorDrawable(Color.RED) as Drawable
    }

    private fun entry(): AppEntry {
        val component = ComponentName("gone.app", "gone.app.Main")
        // The framework's own empty constructor: the loader under test is what stands in for its icon.
        val info = LauncherActivityInfo::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        return AppEntry("gone.app/Main#0", "Gone", "Gone", component, Process.myUserHandle(), info)
    }

    private fun isBlank(icon: ImageBitmap): Boolean {
        val bitmap = icon.asAndroidBitmap()
        val px = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(px, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return px.all { it == 0 }
    }

    @Test
    fun aGoodIconIsDrawnAndKept() {
        val catalog = catalog()
        val e = entry()
        assertFalse(isBlank(runBlocking { catalog.icon(e) }))
        assertNotNull(catalog.cachedIcon(e))
    }

    @Test
    fun anAppThatVanishedGivesABlankIconOfTheSameSizeNotAnException() {
        val e = entry()
        val good = runBlocking { catalog().icon(e) }
        failure = IllegalStateException("package removed")
        val icon = runBlocking { catalog().icon(e) }
        assertEquals(good.width, icon.width)
        assertEquals(good.height, icon.height)
        assertTrue(isBlank(icon))
    }

    @Test
    fun theBlankIconIsNotKeptSoALaterLoadCanSucceed() {
        val catalog = catalog()
        val e = entry()
        failure = RuntimeException("updating")
        runBlocking { catalog.icon(e) }
        assertNull(catalog.cachedIcon(e))
        failure = null
        assertFalse(isBlank(runBlocking { catalog.icon(e) }))
    }

    @Test
    fun cancellationStillPropagates() {
        val catalog = catalog()
        val e = entry()
        failure = CancellationException("scrolled away")
        assertThrows(CancellationException::class.java) { runBlocking { catalog.icon(e) } }
    }
}
