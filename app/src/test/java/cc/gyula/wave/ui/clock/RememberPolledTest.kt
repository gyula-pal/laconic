package cc.gyula.wave.ui.clock

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import cc.gyula.wave.core.Polled
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A clock poller with the home view leaving the composition and coming back, the way it does for a letter
 * list or search. The last answer is held outside the composition in [store], as the ViewModel holds it.
 */
@RunWith(RobolectricTestRunner::class)
class RememberPolledTest {
    @get:Rule
    val compose = createComposeRule()

    private val store = MutableStateFlow<Polled<String>?>(null)
    private val fetched = mutableListOf<String>()
    private val gates = HashMap<String, CompletableDeferred<Unit>>()
    private var failing = false
    private var home by mutableStateOf(true)
    private var ticker by mutableStateOf("BTC")

    private fun show() {
        // The poller waits on the test's clock, which would otherwise run its five-minute waits at once, forever.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            if (home) {
                val price = rememberPolled(store, enabled = true, everyMs = 5 * 60_000L, failed = "$ticker —", key = ticker) {
                    val asked = ticker
                    fetched += asked
                    gates[asked]?.await()
                    if (failing) throw IOException("no signal")
                    "$asked 80.4K"
                }
                Text(price ?: "nothing yet")
            }
        }
        settle()
    }

    /** With the clock held, a change only shows once a frame is let through. */
    private fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private fun leaveHomeAndReturn() {
        home = false
        settle()
        home = true
        settle()
    }

    @Test
    fun comingBackHomeShowsTheLastValueWithoutFetchingAgain() {
        // The poller's last answer used to live in the clock's composition, so every return from a letter list
        // or search fetched weather, price and calendar again and the band reflowed while they loaded.
        show()
        compose.onNodeWithText("BTC 80.4K").assertExists()
        leaveHomeAndReturn()
        compose.onNodeWithText("BTC 80.4K").assertExists()
        assertEquals(listOf("BTC"), fetched)
    }

    @Test
    fun anotherTickerFetchesAtOnceAndNeverShowsTheOldPrice() {
        show()
        gates["ETH"] = CompletableDeferred()
        ticker = "ETH"
        settle()
        assertEquals(listOf("BTC", "ETH"), fetched)
        compose.onNodeWithText("BTC 80.4K").assertDoesNotExist()
        compose.onNodeWithText("nothing yet").assertExists()
        gates.getValue("ETH").complete(Unit)
        settle()
        compose.onNodeWithText("ETH 80.4K").assertExists()
    }

    @Test
    fun aFailedFetchShowsTheFailedValueAndIsMarkedForARetry() {
        failing = true
        show()
        compose.onNodeWithText("BTC —").assertExists()
        assertFalse(store.value!!.ok)
    }
}
