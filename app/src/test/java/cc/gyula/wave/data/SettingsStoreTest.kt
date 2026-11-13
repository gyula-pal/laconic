package cc.gyula.wave.data

import android.content.Context
import android.content.SharedPreferences
import cc.gyula.wave.core.BandLook
import cc.gyula.wave.core.BoxLines
import cc.gyula.wave.core.ClockItem
import cc.gyula.wave.core.CornerAction
import cc.gyula.wave.core.StripSide
import cc.gyula.wave.core.WidgetPages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Settings survive a restart, and what older versions saved still reads right after an update. */
@RunWith(RobolectricTestRunner::class)
class SettingsStoreTest {
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        prefs = RuntimeEnvironment.getApplication().getSharedPreferences("wave", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun defaultsOnAFreshInstall() {
        val s = SettingsStore(prefs)
        assertEquals(setOf(ClockItem.WEATHER, ClockItem.DATE, ClockItem.BTC, ClockItem.EVENT, ClockItem.MUSIC), s.clockItems.value)
        assertEquals(StripSide.RIGHT, s.stripSide.value)
        assertEquals("BTC", s.ticker.value)
        assertTrue(s.animations.value)
        assertEquals(WidgetPages(), s.widgetPages.value)
    }

    @Test
    fun everythingSurvivesARestart() {
        SettingsStore(prefs).apply {
            pins.set(listOf("b", "a"))
            bandLook.cycle()
            dots.toggle()
            hidden.set(setOf("x"))
            names.set(mapOf("k" to "Bank, \"mine\""))
            toggleClockItem(ClockItem.TIME)
            widgetPages.set(WidgetPages(listOf(listOf(4), listOf(5, 6)), left = 1))
            widgetWidths.set(mapOf(5 to 2))
        }
        val again = SettingsStore(prefs)
        assertEquals(listOf("b", "a"), again.pins.value)
        assertEquals(BandLook.INVERTED, again.bandLook.value)
        assertFalse(again.dots.value)
        assertEquals(setOf("x"), again.hidden.value)
        assertEquals(mapOf("k" to "Bank, \"mine\""), again.names.value)
        assertTrue(ClockItem.TIME in again.clockItems.value)
        assertEquals(WidgetPages(listOf(listOf(4), listOf(5, 6)), left = 1), again.widgetPages.value)
        assertEquals(mapOf(5 to 2), again.widgetWidths.value)
    }

    @Test
    fun contactAndEventSearchStartOnAndSurviveARestart() {
        val fresh = SettingsStore(prefs)
        assertTrue(fresh.searchContacts.value)
        assertTrue(fresh.searchEvents.value)
        fresh.searchContacts.toggle()
        assertTrue(prefs.contains("search_contacts"))
        val again = SettingsStore(prefs)
        assertFalse(again.searchContacts.value)
        assertTrue(again.searchEvents.value)
        again.searchEvents.toggle()
        assertFalse(SettingsStore(prefs).searchEvents.value)
        assertFalse(prefs.getBoolean("search_events", true))
    }

    @Test
    fun lettersOffSurvivesARestart() {
        SettingsStore(prefs).stripSide.set(StripSide.OFF)
        assertEquals(StripSide.OFF, SettingsStore(prefs).stripSide.value)
    }

    @Test
    fun boxLinesStartFaintAndSurviveARestart() {
        assertEquals(BoxLines.FAINT, SettingsStore(prefs).boxLines.value)
        SettingsStore(prefs).boxLines.set(BoxLines.BOLD)
        assertEquals(BoxLines.BOLD, SettingsStore(prefs).boxLines.value)
    }

    @Test
    fun coloursDefaultToWhiteInkOverTheWallpaperAndSurviveARestart() {
        val fresh = SettingsStore(prefs)
        assertNull(fresh.ink.value)
        assertNull(fresh.background.value)
        fresh.ink.set(0xFF12AB9F.toInt())
        fresh.background.set(0xFF000000.toInt())
        val again = SettingsStore(prefs)
        assertEquals(0xFF12AB9F.toInt(), again.ink.value)
        assertEquals(0xFF000000.toInt(), again.background.value) // black is a colour, not "unset"
        again.ink.set(null)
        again.background.set(null)
        assertNull(SettingsStore(prefs).ink.value)
        assertNull(SettingsStore(prefs).background.value)
    }

    @Test
    fun leftHandedFlagFrom02BecomesTheLeftEdge() {
        prefs.edit().putBoolean("left_handed", true).commit()
        assertEquals(StripSide.LEFT, SettingsStore(prefs).stripSide.value)
    }

    @Test
    fun oneSideWidgetPagesFrom032AreMigratedOnce() {
        // 0.3.2 stored pages nearest home first, all on the side the flag named.
        prefs.edit().putString("widget_pages", "1,2;3").putBoolean("widgets_left", true).commit()
        assertEquals(WidgetPages(listOf(listOf(3), listOf(1, 2)), left = 2), SettingsStore(prefs).widgetPages.value)
        assertFalse(prefs.contains("widgets_left"))
        assertEquals(WidgetPages(listOf(listOf(3), listOf(1, 2)), left = 2), SettingsStore(prefs).widgetPages.value)
    }

    @Test
    fun unknownOrBrokenValuesFallBackToDefaults() {
        // A value from a newer version, or a hand-edited file, must not crash the home screen.
        prefs.edit()
            .putString("band_look", "HOLOGRAM")
            .putString("clock_items", "TIME,SUNDIAL")
            .putString("names", "{not json")
            .putString("widget_heights", "1:200,junk,3:")
            .commit()
        val s = SettingsStore(prefs)
        assertEquals(BandLook.PLAIN, s.bandLook.value)
        assertEquals(setOf(ClockItem.TIME), s.clockItems.value)
        assertEquals(emptyMap<String, String>(), s.names.value)
        assertEquals(mapOf(1 to 200), s.widgetHeights.value)
    }

    @Test
    fun cornersDefaultToFlashlightAndCamera() {
        val s = SettingsStore(prefs)
        assertEquals(CornerAction.Torch, s.leftCorner.value)
        assertEquals(CornerAction.Camera, s.rightCorner.value)
    }

    @Test
    fun theOldCornersSwitchSavedOffTurnsBothCornersOff() {
        prefs.edit().putBoolean("corner_buttons", false).commit()
        val s = SettingsStore(prefs)
        assertEquals(CornerAction.Off, s.leftCorner.value)
        assertEquals(CornerAction.Off, s.rightCorner.value)
        // Saved in the new form, so turning one back on sticks and the old key no longer matters.
        s.leftCorner.set(CornerAction.Torch)
        prefs.edit().putBoolean("corner_buttons", true).commit()
        val again = SettingsStore(prefs)
        assertEquals(CornerAction.Torch, again.leftCorner.value)
        assertEquals(CornerAction.Off, again.rightCorner.value)
    }

    @Test
    fun theOldCornersSwitchSavedOnKeepsTheDefaults() {
        prefs.edit().putBoolean("corner_buttons", true).commit()
        val s = SettingsStore(prefs)
        assertEquals(CornerAction.Torch, s.leftCorner.value)
        assertEquals(CornerAction.Camera, s.rightCorner.value)
    }

    @Test
    fun cornersSurviveARestartAndAGarbledOneIsItsDefault() {
        SettingsStore(prefs).apply { leftCorner.set(CornerAction.App("a/.B#0")); rightCorner.set(CornerAction.Off) }
        val again = SettingsStore(prefs)
        assertEquals(CornerAction.App("a/.B#0"), again.leftCorner.value)
        assertEquals(CornerAction.Off, again.rightCorner.value)
        prefs.edit().putString("corner_right", "nonsense").commit()
        assertEquals(CornerAction.Camera, SettingsStore(prefs).rightCorner.value)
    }
}
