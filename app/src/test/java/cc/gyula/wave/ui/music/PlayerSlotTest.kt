package cc.gyula.wave.ui.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cc.gyula.wave.core.Dismissal
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The player under the clock, with the home view coming and going around it the way a letter list or search
 * does. The ViewModel's [Dismissal] is held outside the composition, as in the app.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerSlotTest {
    @get:Rule
    val compose = createComposeRule()

    private class Track(val app: String, val playing: Boolean)

    private val dismissal = MutableStateFlow(Dismissal())
    private val stopped = mutableListOf<String>()
    private var home by mutableStateOf(true)
    private var track by mutableStateOf<Track?>(null)

    private fun show() = compose.setContent {
        AnimatedVisibility(home) {
            PlayerSlot(
                track,
                app = { it.app },
                isPlaying = { it.playing },
                dismissal = dismissal,
                onStop = { stopped += it.app },
            ) { t, onClose -> Text("player ${t.app}", Modifier.clickable(onClick = onClose)) }
        }
    }

    private fun swipeAway(app: String) {
        compose.onNodeWithText("player $app").performClick()
        compose.waitForIdle()
    }

    /** The home view leaves the screen for a letter list or search, then comes back. */
    private fun leaveHomeAndReturn() {
        home = false
        compose.waitForIdle()
        home = true
        compose.waitForIdle()
    }

    @Test
    fun playingTrackShowsAndSwipingItAwayStopsAndHidesIt() {
        track = Track("spotify", playing = true)
        show()
        compose.onNodeWithText("player spotify").assertExists()
        swipeAway("spotify")
        compose.onNodeWithText("player spotify").assertDoesNotExist()
        assertEquals(listOf("spotify"), stopped)
    }

    @Test
    fun swipedAwayPlayerStaysHiddenAfterLetterList() {
        // The dismissal once lived in the home view, which a letter list or search replaces, so the
        // player came straight back when the list closed.
        track = Track("spotify", playing = true)
        show()
        swipeAway("spotify")
        leaveHomeAndReturn()
        compose.onNodeWithText("player spotify").assertDoesNotExist()
    }

    @Test
    fun swipedAwayPlayerStaysHiddenThroughSeveralTripsAndWhileItStops() {
        track = Track("spotify", playing = true)
        show()
        swipeAway("spotify")
        leaveHomeAndReturn()
        track = Track("spotify", playing = false) // the session lingers paused after the stop
        compose.waitForIdle()
        leaveHomeAndReturn()
        compose.onNodeWithText("player spotify").assertDoesNotExist()
    }

    @Test
    fun swipedAwayPlayerComesBackWhenItPlaysAgainAfterStopping() {
        track = Track("spotify", playing = true)
        show()
        swipeAway("spotify")
        track = null // stopped: the session is gone
        compose.waitForIdle()
        leaveHomeAndReturn()
        compose.onNodeWithText("player spotify").assertDoesNotExist()
        track = Track("spotify", playing = true)
        compose.waitForIdle()
        compose.onNodeWithText("player spotify").assertExists()
    }

    @Test
    fun anotherAppsPlayerShowsAfterOneWasSwipedAway() {
        track = Track("spotify", playing = true)
        show()
        swipeAway("spotify")
        leaveHomeAndReturn()
        track = Track("youtube", playing = true)
        compose.waitForIdle()
        compose.onNodeWithText("player youtube").assertExists()
    }
}
