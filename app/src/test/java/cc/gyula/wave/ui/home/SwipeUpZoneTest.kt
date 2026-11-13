package cc.gyula.wave.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The band at the bottom that opens search on a swipe up, lying over the end of the list or the boxes. */
@RunWith(RobolectricTestRunner::class)
class SwipeUpZoneTest {
    @get:Rule
    val compose = createComposeRule()

    private var taps = 0
    private var searches = 0

    private fun show() = compose.setContent {
        Box(Modifier.size(300.dp, 200.dp)) {
            // What lies under the band: an app row or a box, drawn first, as the home panel is.
            Box(Modifier.fillMaxSize().testTag("below").clickable { taps++ })
            SwipeUpZone(Modifier.fillMaxSize().testTag("zone"), onSwipeUp = { searches++ })
        }
    }

    @Test
    fun aTapGoesThroughToWhatIsUnderneath() {
        show()
        compose.onNodeWithTag("zone").performTouchInput { click(center) }
        assertEquals(1, taps)
        assertEquals(0, searches)
    }

    @Test
    fun aSwipeUpOpensSearch() {
        show()
        compose.onNodeWithTag("zone").performTouchInput { swipeUp(startY = bottom - 5f, endY = top + 5f) }
        assertEquals(1, searches)
    }
}
