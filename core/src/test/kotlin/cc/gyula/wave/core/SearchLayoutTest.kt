package cc.gyula.wave.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchLayoutTest {
    private val all = setOf(Part.APPS, Part.CONTACTS, Part.EVENTS)

    @Test
    fun sectionsComeInOrderAndOnlyWithHits() {
        assertEquals(listOf(Part.APPS, Part.EVENTS), partsShown(Scope.ALL, setOf(Part.EVENTS, Part.APPS)))
        assertEquals(emptyList<Part>(), partsShown(Scope.ALL, emptySet()))
    }

    @Test
    fun aScopeKeepsOnlyItsOwnSection() {
        assertEquals(listOf(Part.CONTACTS), partsShown(Scope.CONTACTS, all))
        assertEquals(listOf(Part.EVENTS), partsShown(Scope.EVENTS, all))
        assertEquals(emptyList<Part>(), partsShown(Scope.WEB, all))
    }

    @Test
    fun headersNeedTwoSections() {
        assertFalse(showHeaders(listOf(Part.APPS)))
        assertTrue(showHeaders(listOf(Part.APPS, Part.CONTACTS)))
    }

    @Test
    fun theScopeWordIsNamedUnlessAll() {
        assertNull(scopeWord(Scope.ALL))
        assertEquals("web", scopeWord(Scope.WEB))
    }

    @Test
    fun theMoreRowCountsWhatIsHidden() {
        assertEquals(2, hiddenCount(7, false, 5))
        assertEquals(0, hiddenCount(7, true, 5))
        assertEquals(0, hiddenCount(3, false, 5))
    }

    @Test
    fun capsAreFiveThreeThree() {
        assertEquals(listOf(5, 3, 3), Part.entries.map { it.cap })
    }
}
