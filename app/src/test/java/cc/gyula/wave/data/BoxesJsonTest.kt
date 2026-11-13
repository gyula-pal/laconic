package cc.gyula.wave.data

import android.content.Context
import cc.gyula.wave.core.AppBox
import cc.gyula.wave.core.BoxSplit
import cc.gyula.wave.core.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The boxes survive a restart, and a damaged save falls back to the plain default instead of crashing home. */
@RunWith(RobolectricTestRunner::class)
class BoxesJsonTest {
    private val tree = BoxSplit(
        sideBySide = true, ratio = 0.3f,
        first = AppBox(0, listOf("a/x", "b\"y")),
        second = BoxSplit(false, 0.5f, AppBox(3, emptyList()), AppBox(5, listOf("c"))),
    )

    @Test
    fun roundTrips() {
        assertEquals(tree, boxesFromJson(boxesToJson(tree)))
    }

    @Test
    fun writesTheDocumentedShape() {
        assertEquals("""{"box":3,"apps":["k1","k2"]}""", boxesToJson(AppBox(3, listOf("k1", "k2"))))
        val split = boxesToJson(BoxSplit(false, 0.5f, AppBox(0, emptyList()), AppBox(1, emptyList())))
        assertEquals("v", org.json.JSONObject(split).getString("split"))
    }

    @Test
    fun namesRoundTripAndAreOnlyWrittenWhenSet() {
        val named = BoxSplit(true, 0.5f, AppBox(0, listOf("a"), "Money \"fun\""), AppBox(1, listOf("b")))
        assertEquals(named, boxesFromJson(boxesToJson(named)))
        assertEquals("""{"box":3,"apps":["k1"],"name":"Work"}""", boxesToJson(AppBox(3, listOf("k1"), "Work")))
        assertEquals("""{"box":3,"apps":[]}""", boxesToJson(AppBox(3, emptyList(), "   ")))
    }

    @Test
    fun oldSavesWithoutNamesStillLoad() {
        val old = """{"split":"h","ratio":0.3,"first":{"box":0,"apps":["a/x"]},"second":{"box":1,"apps":["c"]}}"""
        val tree = boxesFromJson(old)
        assertEquals(
            BoxSplit(true, 0.3f, AppBox(0, listOf("a/x"), ""), AppBox(1, listOf("c"), "")),
            tree,
        )
    }

    @Test
    fun damagedInputIsNull() {
        assertNull(boxesFromJson(null))
        assertNull(boxesFromJson(""))
        assertNull(boxesFromJson("not json"))
        assertNull(boxesFromJson("""{"box":1}"""))
        assertNull(boxesFromJson("""{"split":"h","ratio":0.5,"first":{"box":0,"apps":[]}}"""))
        assertNull(boxesFromJson("""{"split":"x","ratio":0.5,"first":{"box":0,"apps":[]},"second":{"box":1,"apps":[]}}"""))
        assertNull(boxesFromJson("""{"split":"h","ratio":"wide","first":{"box":0,"apps":[]},"second":{"box":1,"apps":[]}}"""))
    }

    @Test
    fun anOutOfRangeRatioIsRejected() {
        assertNull(boxesFromJson("""{"split":"h","ratio":1.5,"first":{"box":0,"apps":[]},"second":{"box":1,"apps":[]}}"""))
    }

    @Test
    fun theStoreKeepsLayoutAndBoxes() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("wave", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val fresh = SettingsStore(prefs)
        assertEquals(HomeLayout.LIST, fresh.homeLayout.value)
        assertNull(fresh.boxes.value)
        fresh.homeLayout.set(HomeLayout.BOXES)
        fresh.boxes.set(tree)
        val again = SettingsStore(prefs)
        assertEquals(HomeLayout.BOXES, again.homeLayout.value)
        assertEquals(tree, again.boxes.value)
        prefs.edit().putString("boxes", "{oops").commit()
        assertNull(SettingsStore(prefs).boxes.value)
    }
}
