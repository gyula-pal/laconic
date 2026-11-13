package cc.gyula.wave.ui.settings

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ColourPickerTest {
    @get:Rule
    val compose = createComposeRule()

    private var saved: Int? = null
    private var background: Int? = null

    private fun show(initial: Int = 0xFFFF0000.toInt()) = compose.setContent {
        ColourPickerDialog("Ink", initial, preview = { it to background }, onDismiss = {}, onSave = { saved = it })
    }

    private fun slider(tag: String) = compose.onNodeWithTag(tag)

    private fun progress(tag: String) =
        slider(tag).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current

    @Test
    fun movingASliderUpdatesTheHex() {
        show()
        compose.onNodeWithTag("hex").assertTextEquals("#FF0000", includeEditableText = true)
        slider("hue").performSemanticsAction(SemanticsActions.SetProgress) { it(1f / 3f) }
        compose.onNodeWithTag("hex").assertTextEquals("#00FF00", includeEditableText = true)
    }

    @Test
    fun typingAHexMovesTheSliders() {
        show()
        compose.onNodeWithTag("hex").performTextClearance()
        compose.onNodeWithTag("hex").performTextInput("0000ff")
        assertEquals(240f / 360f, progress("hue"), 0.01f)
        assertEquals(1f, progress("saturation"), 0.01f)
        assertEquals(1f, progress("brightness"), 0.01f)
    }

    @Test
    fun typingOneKeyAtATimeKeepsTheOrder() {
        // A keyboard sends one character at a time; adding the # after the first must not strand the cursor
        // before it (on the phone, "B48CFF" came out as "#48CFFB").
        show()
        compose.onNodeWithTag("hex").performTextClearance()
        "b48cff".forEach { compose.onNodeWithTag("hex").performTextInput(it.toString()) }
        compose.onNodeWithTag("hex").assertTextEquals("#B48CFF", includeEditableText = true)
    }

    @Test
    fun saveStoresWhatIsShown() {
        show()
        slider("brightness").performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        compose.onNodeWithTag("hex").assertTextEquals("#800000", includeEditableText = true)
        compose.onNodeWithText("Save").performClick()
        assertEquals(0xFF800000.toInt(), saved)
    }

    @Test
    fun anUnfinishedHexCannotBeSaved() {
        show()
        compose.onNodeWithTag("hex").performTextClearance()
        compose.onNodeWithTag("hex").performTextInput("12")
        compose.onNodeWithText("Save").performClick()
        assertNull(saved)
    }

    @Test
    fun warnsWhenTheInkBarelyShowsOnTheBackground() {
        background = 0xFFFFFFFF.toInt()
        show(initial = 0xFFEEEEEE.toInt())
        compose.onNodeWithTag("hard-to-read").assertExists()
        compose.onNodeWithTag("hex").performTextClearance()
        compose.onNodeWithTag("hex").performTextInput("000000")
        compose.onNodeWithTag("hard-to-read").assertDoesNotExist()
    }
}
