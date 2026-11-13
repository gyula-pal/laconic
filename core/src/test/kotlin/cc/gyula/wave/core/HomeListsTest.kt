package cc.gyula.wave.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RankUsageTest {
    private fun opens(vararg pkgs: String) = pkgs.mapIndexed { i, p -> AppOpen(p, i * 1_000L) }.asSequence()

    @Test
    fun countsSwitchesNotScreens() {
        // Three screens of Maps in a row are one visit; Spotify was visited twice.
        val ranked = rankUsage(opens("maps", "maps", "maps", "spotify", "chat", "spotify"), HomeList.FREQUENT, emptySet())
        assertEquals(listOf("spotify", "maps", "chat"), ranked)
    }

    @Test
    fun recentIsLatestFirst() {
        assertEquals(listOf("spotify", "chat", "maps"), rankUsage(opens("maps", "chat", "spotify"), HomeList.RECENT, emptySet()))
    }

    @Test
    fun homeScreensDontCount() {
        // An old launcher shows up between every app; it isn't one you open.
        val ranked = rankUsage(opens("oldlauncher", "maps", "oldlauncher", "chat", "oldlauncher"), HomeList.FREQUENT, setOf("oldlauncher"))
        assertEquals(listOf("maps", "chat"), ranked)
    }

    @Test
    fun pinnedNeedsNoUsage() {
        assertEquals(emptyList<String>(), rankUsage(opens("maps"), HomeList.PINNED, emptySet()))
    }
}

class HomeAppsTest {
    private data class App(val pkg: String, val label: String, val personal: Boolean = true)

    private fun list(mode: HomeList, az: Boolean, pins: List<App>, visible: List<App>, used: List<String>?, count: Int = USED_COUNT) =
        homeApps(mode, az, pins, visible, used, App::pkg, App::personal, App::label, count)

    private val maps = App("maps", "Maps")
    private val chat = App("chat", "Chat")
    private val bank = App("bank", "Bank")

    @Test
    fun pinnedKeepsYourOrderUnlessAz() {
        assertEquals(listOf(maps, chat), list(HomeList.PINNED, false, listOf(maps, chat), emptyList(), null))
        assertEquals(listOf(chat, maps), list(HomeList.PINNED, true, listOf(maps, chat), emptyList(), null))
    }

    @Test
    fun usedAppsInUsageOrderSkippingHiddenAndUninstalled() {
        // "gone" was uninstalled; bank is hidden, so not among the visible apps.
        val shown = list(HomeList.FREQUENT, false, emptyList(), listOf(maps, chat), listOf("chat", "gone", "bank", "maps"))
        assertEquals(listOf(chat, maps), shown)
    }

    @Test
    fun oneRowPerAppPreferringThePersonalCopy() {
        val workChat = App("chat", "Chat", personal = false)
        assertEquals(listOf(chat), list(HomeList.RECENT, false, emptyList(), listOf(workChat, chat), listOf("chat")))
        assertEquals(listOf(workChat), list(HomeList.RECENT, false, emptyList(), listOf(workChat), listOf("chat")))
    }

    @Test
    fun cappedAtCount() {
        val shown = list(HomeList.FREQUENT, false, emptyList(), listOf(maps, chat, bank), listOf("bank", "chat", "maps"), count = 2)
        assertEquals(listOf(bank, chat), shown)
    }

    @Test
    fun withoutUsageAccessTheListIsEmpty() {
        assertEquals(emptyList<App>(), list(HomeList.FREQUENT, false, listOf(maps), listOf(maps), null))
    }
}

class PinsAndNamesTest {
    @Test
    fun reorderingKeepsPinsOfAppsNotInstalledAtTheEnd() {
        // "work" is pinned but its profile is paused, so it wasn't on screen to be dragged.
        assertEquals(listOf("b", "a", "work"), pinsReordered(listOf("a", "work", "b"), listOf("b", "a")))
    }

    @Test
    fun pinningAddsToTheEndAndUnpinningRemoves() {
        assertEquals(listOf("a", "b"), pinsToggled(listOf("a"), "b"))
        assertEquals(listOf("b"), pinsToggled(listOf("a", "b"), "a"))
    }

    @Test
    fun renaming() {
        assertEquals(mapOf("k" to "Bank"), renamed(emptyMap(), "k", "OTP Bank", "  Bank "))
        // Blank, or the app's own name, removes the rename rather than storing a copy.
        assertEquals(emptyMap<String, String>(), renamed(mapOf("k" to "Bank"), "k", "OTP Bank", " "))
        assertEquals(emptyMap<String, String>(), renamed(mapOf("k" to "Bank"), "k", "OTP Bank", "OTP Bank"))
    }
}
