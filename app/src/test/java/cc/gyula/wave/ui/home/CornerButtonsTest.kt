package cc.gyula.wave.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import cc.gyula.wave.core.CornerAction
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** The corner buttons: what draws, what a tap does, and how the long-press chooser changes a corner. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CornerButtonsTest {
    @get:Rule
    val compose = createComposeRule()

    private var left by mutableStateOf<CornerAction>(CornerAction.Torch)
    private var right by mutableStateOf<CornerAction>(CornerAction.Camera)
    private val calls = mutableListOf<String>()
    private val apps = listOf(CornerApp("b#0", "Bank"), CornerApp("m#0", "Maps"), CornerApp("a#0", "Authenticator"))

    private fun show() = compose.setContent {
        CornerButtonsContent(
            left, right, torchOn = false, apps = apps,
            cachedIcon = { null }, loadIcon = { null as ImageBitmap? },
            onTorch = { calls += "torch" },
            onCamera = { calls += "camera" },
            onApp = { calls += "app: $it" },
            onChange = { onLeft, action -> calls += "${if (onLeft) "left" else "right"} = $action"; if (onLeft) left = action else right = action },
        )
    }

    @Test
    fun anOffCornerDrawsNoButton() {
        left = CornerAction.Off
        show()
        compose.onNodeWithContentDescription("Flashlight").assertDoesNotExist()
        compose.onNodeWithContentDescription("Camera").assertIsDisplayed()
    }

    @Test
    fun anUninstalledAppDrawsNothing() {
        right = CornerAction.App("gone#0")
        show()
        compose.onNodeWithContentDescription("Camera").assertDoesNotExist()
        compose.onNodeWithContentDescription("Flashlight").assertIsDisplayed()
    }

    @Test
    fun tappingTheDefaultsDoesTheirThing() {
        show()
        compose.onNodeWithContentDescription("Flashlight").performClick()
        compose.onNodeWithContentDescription("Camera").performClick()
        assertEquals(listOf("torch", "camera"), calls)
    }

    @Test
    fun tappingAnAppCornerLaunchesThatApp() {
        right = CornerAction.App("m#0")
        show()
        compose.onNodeWithContentDescription("Maps").performClick()
        assertEquals(listOf("app: m#0"), calls)
    }

    @Test
    fun longPressOpensTheChooserAndPickingOffRemovesTheButton() {
        show()
        compose.onNodeWithContentDescription("Flashlight").performTouchInput { longClick() }
        assertEquals(emptyList<String>(), calls) // a long-press only opens the chooser
        compose.onNodeWithText("Left corner").assertIsDisplayed()
        compose.onNodeWithText("Off").performClick()
        assertEquals(listOf("left = Off"), calls)
        compose.onNodeWithContentDescription("Flashlight").assertDoesNotExist()
    }

    @Test
    fun longPressThenAppPicksFromTheSortedListAndSearchNarrowsIt() {
        show()
        compose.onNodeWithContentDescription("Camera").performTouchInput { longClick() }
        compose.onNodeWithText("Right corner").assertIsDisplayed()
        compose.onNodeWithText("App…").performClick()
        compose.onNodeWithText("Authenticator").assertIsDisplayed()
        compose.onNodeWithText("Bank").assertIsDisplayed()
        compose.onNodeWithText("Search").performClick()
        compose.onNodeWithText("Search").performTextInput("map")
        compose.onNodeWithText("Bank").assertDoesNotExist()
        compose.onNodeWithText("Maps").performClick()
        assertEquals(listOf("right = App(key=m#0)"), calls)
        compose.onNodeWithContentDescription("Maps").assertIsDisplayed()
    }
}
