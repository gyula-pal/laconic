package cc.gyula.wave.data

import android.content.Context
import android.content.SharedPreferences
import cc.gyula.wave.core.WidgetPages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Android's widget service is not there under Robolectric, so this host just remembers which ids are handed out. */
private class FakeHost(context: Context) : WidgetHost(context) {
    private val live = linkedSetOf<Int>()
    private var last = 100

    override fun allocateAppWidgetId(): Int = (++last).also { live += it }
    override fun deleteAppWidgetId(appWidgetId: Int) {
        live -= appWidgetId
    }

    override fun getAppWidgetIds(): IntArray = live.toIntArray()

    /** How often the host was told to let go of its views. */
    var viewsCleared = 0

    override fun clearViews() {
        viewsCleared++
    }
}

/** Widgets stay where they were put, and Android's widget ids are handed back whenever Laconic lets go of one. */
@RunWith(RobolectricTestRunner::class)
class WidgetStoreTest {
    private lateinit var prefs: SharedPreferences
    private lateinit var settings: SettingsStore
    private lateinit var host: FakeHost
    private lateinit var store: WidgetStore

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        prefs = app.getSharedPreferences("wave", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        settings = SettingsStore(prefs)
        host = FakeHost(app)
        store = WidgetStore(app, settings, host)
    }

    /** Android hands out an id; Laconic then decides where it goes. */
    private fun newId() = host.allocateAppWidgetId()

    private fun pages(vararg page: List<Int>, left: Int = 0) = settings.widgetPages.set(WidgetPages(page.toList(), left))

    private fun ids() = host.appWidgetIds.toSet()

    @Test
    fun addedWidgetLandsOnItsPage() {
        pages(emptyList(), emptyList(), left = 1)
        val a = newId()
        val b = newId()
        store.add(0, a)
        store.add(1, b)
        assertEquals(WidgetPages(listOf(listOf(a), listOf(b)), left = 1), settings.widgetPages.value)
        assertEquals(setOf(a, b), ids())
    }

    @Test
    fun addingToAPageThatDoesNotExistFreesTheId() {
        pages(emptyList())
        val id = newId()
        store.add(3, id)
        assertEquals(WidgetPages(listOf(emptyList())), settings.widgetPages.value)
        assertEquals(emptySet<Int>(), ids())
    }

    @Test
    fun removedWidgetLeavesItsPageItsSizesAndItsId() {
        val a = newId()
        val b = newId()
        pages(listOf(a, b))
        store.setHeight(a, 300)
        store.setWidth(a, 2)
        store.setHeight(b, 200)
        store.remove(a)
        assertEquals(listOf(listOf(b)), settings.widgetPages.value.pages)
        assertEquals(mapOf(b to 200), settings.widgetHeights.value)
        assertEquals(emptyMap<Int, Int>(), settings.widgetWidths.value)
        assertEquals(setOf(b), ids())
    }

    @Test
    fun sizeIsSavedAndClearedWithNull() {
        store.setHeight(7, 240)
        store.setWidth(7, 3)
        assertEquals(mapOf(7 to 240), settings.widgetHeights.value)
        assertEquals(mapOf(7 to 3), settings.widgetWidths.value)
        store.setHeight(7, null)
        store.setWidth(7, null)
        assertEquals(emptyMap<Int, Int>(), settings.widgetHeights.value)
        assertEquals(emptyMap<Int, Int>(), settings.widgetWidths.value)
    }

    @Test
    fun takingAPageAwayTakesItsWidgetsSizesAndIdsWithIt() {
        val far = newId()
        val near = newId()
        val right = newId()
        // One page left of home (near) plus one more beyond it (far), one right of home.
        pages(listOf(far), listOf(near), listOf(right), left = 2)
        store.setHeight(far, 111)
        store.setWidth(far, 1)
        store.setHeight(near, 222)
        store.setWidth(right, 2)

        store.setPageCounts(left = 1, right = 1)

        assertEquals(WidgetPages(listOf(listOf(near), listOf(right)), left = 1), settings.widgetPages.value)
        assertEquals(mapOf(near to 222), settings.widgetHeights.value)
        assertEquals(mapOf(right to 2), settings.widgetWidths.value)
        assertEquals(setOf(near, right), ids())
    }

    @Test
    fun takingAllRightPagesAwayKeepsTheLeftOnes() {
        val l = newId()
        val r = newId()
        pages(listOf(l), listOf(r), left = 1)
        store.setPageCounts(left = 1, right = 0)
        assertEquals(WidgetPages(listOf(listOf(l)), left = 1), settings.widgetPages.value)
        assertEquals(setOf(l), ids())
    }

    @Test
    fun addingPagesRemovesNothing() {
        val a = newId()
        pages(listOf(a))
        store.setHeight(a, 150)
        store.setPageCounts(left = 1, right = 2)
        assertEquals(3, settings.widgetPages.value.pages.size)
        assertEquals(setOf(a), settings.widgetPages.value.pages.flatten().toSet())
        assertEquals(mapOf(a to 150), settings.widgetHeights.value)
        assertEquals(setOf(a), ids())
    }

    @Test
    fun widgetSetupFinishedPlacesItOnThePageItWasPickedFor() {
        pages(emptyList(), emptyList())
        val id = newId()
        store.pending = 1 to id
        store.configured(ok = true)
        assertEquals(listOf(emptyList(), listOf(id)), settings.widgetPages.value.pages)
        assertNull(store.pending)
        assertEquals(setOf(id), ids())
    }

    @Test
    fun widgetSetupCancelledFreesTheIdAndPlacesNothing() {
        pages(emptyList())
        val id = newId()
        store.pending = 0 to id
        store.configured(ok = false)
        assertEquals(listOf(emptyList<Int>()), settings.widgetPages.value.pages)
        assertNull(store.pending)
        assertEquals(emptySet<Int>(), ids())
    }

    @Test
    fun setupResultWithNothingPendingChangesNothing() {
        pages(listOf(1))
        val id = newId()
        store.configured(ok = false)
        assertEquals(listOf(listOf(1)), settings.widgetPages.value.pages)
        assertEquals(setOf(id), ids())
    }

    @Test
    fun releaseUnplacedFreesOnlyIdsNoPageHolds() {
        val placed = newId()
        val orphan1 = newId()
        val orphan2 = newId()
        pages(listOf(placed))
        store.releaseUnplaced()
        assertEquals(setOf(placed), ids())
        assertEquals(listOf(listOf(placed)), settings.widgetPages.value.pages)
        assertEquals(false, orphan1 in ids() || orphan2 in ids())
    }

    @Test
    fun droppingViewsLetsGoOfThemButKeepsEveryWidget() {
        // Views made in an activity that is gone would keep it alive; the widgets themselves must stay.
        val a = newId()
        pages(listOf(a))
        store.setHeight(a, 180)
        store.dropViews()
        assertEquals(1, host.viewsCleared)
        assertEquals(listOf(listOf(a)), settings.widgetPages.value.pages)
        assertEquals(mapOf(a to 180), settings.widgetHeights.value)
        assertEquals(setOf(a), ids())
    }

    @Test
    fun placementsAndSizesSurviveARestart() {
        pages(emptyList())
        val id = newId()
        store.add(0, id)
        store.setHeight(id, 180)
                assertEquals(listOf(listOf(id)), SettingsStore(prefs).widgetPages.value.pages)
        assertEquals(mapOf(id to 180), SettingsStore(prefs).widgetHeights.value)
    }
}
