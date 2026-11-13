package cc.gyula.wave.ui.widgets

import org.junit.Assert.assertEquals
import org.junit.Test

/** The widget picker's order: each widget's label is a PackageManager lookup, so it is looked up once. */
class SortedByLabelTest {
    private val lookups = mutableListOf<String>()

    private fun label(id: String): String {
        lookups += id
        return mapOf("a" to "Weather", "b" to "Clock", "c" to "Calendar", "d" to "Notes", "e" to "Battery").getValue(id)
    }

    @Test
    fun widgetsComeInLabelOrder() {
        assertEquals(listOf("e", "c", "b", "d", "a"), listOf("a", "b", "c", "d", "e").sortedByLabel(::label))
    }

    @Test
    fun eachLabelIsLookedUpOnce() {
        // Sorting looked the labels up on every comparison, about two per comparison.
        listOf("a", "b", "c", "d", "e").sortedByLabel(::label)
        assertEquals(listOf("a", "b", "c", "d", "e"), lookups.sorted())
    }
}
