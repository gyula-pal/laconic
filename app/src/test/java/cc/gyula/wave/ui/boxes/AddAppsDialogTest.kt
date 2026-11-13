package cc.gyula.wave.ui.boxes

import cc.gyula.wave.core.AppBox
import cc.gyula.wave.core.BoxSplit
import cc.gyula.wave.ui.home.CornerApp
import org.junit.Assert.assertEquals
import org.junit.Test

class AddAppsDialogTest {
    private val apps = listOf(
        CornerApp("k", "Káva"), CornerApp("b", "Banking"), CornerApp("a", "Árkád"), CornerApp("m", "Maps"), CornerApp("c", "camera"),
    )
    private val tree = BoxSplit(true, 0.5f, AppBox(0, listOf("b", "gone")), AppBox(1, listOf("m"), "Work"))
    private val rows = pickRows(apps, tree, 0)

    @Test
    fun rowsAreAlphabeticalAndNameTheOtherBox() {
        assertEquals(listOf("a", "b", "c", "k", "m"), rows.map { it.key }) // Árkád, Banking, camera, Káva, Maps
        assertEquals(mapOf("b" to null, "m" to "Work", "c" to null), rows.filter { it.key in setOf("b", "m", "c") }.associate { it.key to it.elsewhere })
        assertEquals("another box", pickRows(apps, tree, 1).single { it.key == "b" }.elsewhere)
    }

    @Test
    fun searchIgnoresCaseAndAccents() {
        assertEquals(listOf("a"), filterRows(rows, "arkad").map { it.key })
        assertEquals(listOf("c"), filterRows(rows, "CAM").map { it.key })
        assertEquals(listOf("k"), filterRows(rows, "kava").map { it.key })
        assertEquals(rows, filterRows(rows, "  "))
        assertEquals(emptyList<PickRow>(), filterRows(rows, "zzz"))
    }

    @Test
    fun theResultKeepsHiddenAndMissingAppsDropsUntickedAndAppendsNewAlphabetically() {
        // Box 0 holds b and "gone" (not in the list). b unticked, gone stays; m, k, a newly ticked.
        val r = pickedKeys(listOf("b", "gone"), rows, setOf("m", "k", "a"))
        assertEquals(listOf("gone", "a", "k", "m"), r)
        // Ticked and already inside: keeps its place ahead of the new ones.
        assertEquals(listOf("b", "gone", "c"), pickedKeys(listOf("b", "gone"), rows, setOf("c", "b")))
    }

    @Test
    fun applyingNothingChangedGivesTheBoxBack() {
        val request = AddAppsRequest("t", listOf("b", "gone"), rows)
        assertEquals(setOf("b"), request.initialTicks())
        assertEquals(listOf("b", "gone"), request.result(request.initialTicks()))
    }
}
