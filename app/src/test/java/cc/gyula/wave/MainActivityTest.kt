package cc.gyula.wave

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.util.SparseArray
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainActivityTest {
    @Test
    fun backOnTheHomeScreenKeepsIt() {
        // 0.4.1: Back with nothing open closed the home screen and Android started a fresh one, which forgot
        // what was on screen — a music player swiped away came straight back.
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        activity.onBackPressedDispatcher.onBackPressed()
        assertFalse(activity.isFinishing)
    }

    @Test
    fun aRecreatedActivityLeavesNoWidgetViewsBehind() {
        // The widget host lives in the ViewModel, which outlives the activity, and keeps every view it made until
        // told to let go. Switching dark mode, font size or language makes a new activity; widget views on pages
        // not shown again kept the old one alive. Robolectric's host makes no real views, so one is put in its
        // list by hand, the way Android's own createView does.
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val old = controller.get()
        val vm = ViewModelProvider(old)[AppsViewModel::class.java]
        val views = hostViews(vm)
        views.put(1, AppWidgetHostView(old))

        controller.recreate()

        assertSame(vm, ViewModelProvider(controller.get())[AppsViewModel::class.java])
        assertEquals(0, views.size())
    }

    /** The host's own list of the views it made: `AppWidgetHost.mListeners` on the Android version the tests run (34). */
    @Suppress("UNCHECKED_CAST")
    private fun hostViews(vm: AppsViewModel): SparseArray<Any> {
        val field = AppWidgetHost::class.java.getDeclaredField("mListeners").apply { isAccessible = true }
        return field.get(vm.widgets.host) as SparseArray<Any>
    }
}
