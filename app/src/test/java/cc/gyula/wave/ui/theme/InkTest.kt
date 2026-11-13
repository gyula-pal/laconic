package cc.gyula.wave.ui.theme

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import cc.gyula.wave.core.BandLook
import cc.gyula.wave.core.BandPosition
import cc.gyula.wave.ui.clock.ClockFace
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** The ink given at the top reaches the text the home screen draws. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InkTest {
    @get:Rule
    val compose = createComposeRule()

    private fun SemanticsNodeInteraction.textColor(): Color {
        val layouts = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single().layoutInput.style.color
    }

    @Test
    fun clockWeekdayAndBandTextTakeTheInk() {
        val ink = Color(0xFF12AB9F)
        compose.setContent {
            CompositionLocalProvider(LocalInk provides ink) {
                ClockFace("TUE", listOf("14°C"), BandPosition.THROUGH, BandLook.PLAIN)
            }
        }
        assertEquals(ink, compose.onNodeWithText("TUE").textColor())
        assertEquals(ink, compose.onNodeWithText("14°C").textColor())
    }

    @Test
    fun labelsTakeTheInk() {
        val ink = Color(0xFFFF8800)
        compose.setContent { CompositionLocalProvider(LocalInk provides ink) { Text("Maps", style = Label) } }
        assertEquals(ink, compose.onNodeWithText("Maps").textColor())
    }

    @Test
    fun withoutAnyProviderEverythingIsWhite() {
        compose.setContent {
            Text("Maps", style = Label)
            ClockFace("TUE", listOf("14°C"), BandPosition.THROUGH, BandLook.PLAIN)
        }
        assertEquals(Color.White, compose.onNodeWithText("Maps").textColor())
        assertEquals(Color.White, compose.onNodeWithText("TUE").textColor())
    }
}
