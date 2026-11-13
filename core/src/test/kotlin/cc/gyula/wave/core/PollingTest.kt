package cc.gyula.wave.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** When the clock's weather, price and next-event pollers fetch again, and what they show meanwhile. */
class PollingTest {
    private val every = 5 * 60_000L

    @Test
    fun firstFetchIsImmediate() {
        assertEquals(0L, fetchWait(null, key = "BTC", now = 1_000, everyMs = every))
    }

    @Test
    fun laterFetchesWaitTheInterval() {
        val last = Polled("BTC", "₿80.4K", at = 1_000, ok = true)
        assertEquals(every, fetchWait(last, "BTC", now = 1_000, everyMs = every))
        assertEquals(every - 30_000, fetchWait(last, "BTC", now = 31_000, everyMs = every))
        assertEquals(0L, fetchWait(last, "BTC", now = 1_000 + every, everyMs = every))
        assertEquals(0L, fetchWait(last, "BTC", now = 1_000 + every * 3, everyMs = every))
    }

    @Test
    fun valueSurvivesLeavingHomeAndComingBackWithoutAFetch() {
        // The poller's last answer used to live in the clock's composition, which a letter list or search
        // takes off the screen; coming back fetched weather, price and calendar again straight away.
        val kept = Polled("BTC", "₿80.4K", at = 1_000, ok = true)
        val back = 1_000 + 20_000L
        assertEquals(every - 20_000, fetchWait(kept, "BTC", back, every))
        assertEquals("₿80.4K", kept.valueFor("BTC"))
    }

    @Test
    fun aNewKeyFetchesAtOnceAndNeverShowsTheOldKeysValue() {
        val btc = Polled("BTC", "₿80.4K", at = 1_000, ok = true)
        assertEquals(0L, fetchWait(btc, "ETH", now = 2_000, everyMs = every))
        assertNull(btc.valueFor("ETH"))
    }

    @Test
    fun nothingFetchedYetShowsNothing() {
        assertNull((null as Polled<String>?).valueFor("BTC"))
    }

    @Test
    fun aFailedFetchIsRetriedAfterAMinute() {
        // Home opened with no signal showed "—°C" and "—" for the whole interval.
        val failed = Polled(null, "—°C", at = 1_000, ok = false)
        assertEquals(RETRY_FAILED_MS, fetchWait(failed, null, now = 1_000, everyMs = 15 * 60_000L))
        assertEquals(0L, fetchWait(failed, null, now = 1_000 + RETRY_FAILED_MS, everyMs = 15 * 60_000L))
        assertEquals("—°C", failed.valueFor(null))
    }

    @Test
    fun aSuccessAfterARetryWaitsTheFullIntervalAgain() {
        val ok = Polled(null, "12°C", at = 70_000, ok = true)
        assertEquals(15 * 60_000L, fetchWait(ok, null, now = 70_000, everyMs = 15 * 60_000L))
    }
}
