package cc.gyula.wave.core

import org.junit.Assert.assertEquals
import org.junit.Test

class OptionsTest {
    @Test
    fun settingsRowsCycleRoundTheirChoices() {
        assertEquals(BandPosition.THROUGH, next(BandPosition.ABOVE))
        assertEquals(BandPosition.ABOVE, next(BandPosition.BELOW))
        assertEquals(HomeList.PINNED, next(HomeList.RECENT))
    }

    @Test
    fun bandItemsKeepTheirOrder() {
        // The band shows its items in declaration order; the event line and the player are not on it.
        assertEquals(
            listOf(ClockItem.TIME, ClockItem.ALARM, ClockItem.WEATHER, ClockItem.DATE, ClockItem.BTC),
            ClockItem.entries.filter { it.onBand },
        )
    }
}
