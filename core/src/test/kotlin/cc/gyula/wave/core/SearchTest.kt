package cc.gyula.wave.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTest {
    private data class App(val label: String, val original: String = label)

    private val apps = listOf(App("Bank", original = "OTP Bank"), App("Google Maps"), App("Maps.me"), App("Spotify"))

    private fun search(q: String) = searchResults(apps, q) { listOf(it.label, it.original) }.map { it.label }

    @Test
    fun prefixMatchesComeFirst() {
        assertEquals(listOf("Maps.me", "Google Maps"), search("map"))
    }

    @Test
    fun renamedAppsAreFoundByEitherName() {
        assertEquals(listOf("Bank"), search("otp"))
        assertEquals(listOf("Bank"), search("bank"))
    }
}
