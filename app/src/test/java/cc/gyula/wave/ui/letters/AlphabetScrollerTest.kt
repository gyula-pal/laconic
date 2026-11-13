package cc.gyula.wave.ui.letters

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Picking letters on the strip with a finger, as the home screen does to open a letter's list. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h914dp-xxhdpi") // a Pixel 10's width
class AlphabetScrollerTest {
    @get:Rule
    val compose = createComposeRule()

    private val letters = listOf(FAVORITES, 'A', 'B', 'C', 'D', 'E')
    private val picked = mutableListOf<Char>()
    private val drags = mutableListOf<Boolean>() // off the strip, per move
    private val releases = mutableListOf<Boolean>()

    private fun show(onLeft: Boolean = false) = compose.setContent {
        Box(Modifier.fillMaxSize()) {
            AlphabetScroller(
                letters,
                modifier = Modifier.testTag("strip"),
                onLeft = onLeft,
                onLetter = { letter, _ -> picked += letter },
                onDrag = { _, off -> drags += off },
                onRelease = { _, off -> releases += off },
            )
        }
    }

    private val strip: SemanticsNodeInteraction get() = compose.onNodeWithTag("strip")

    /** The middle of [letter]'s stripe, and an x well inside the strip unless [x] says otherwise. */
    private fun at(letter: Char, x: Float? = null): Offset {
        val size = strip.fetchSemanticsNode().size
        val slot = size.height.toFloat() / letters.size
        return Offset(x ?: (size.width / 2f), slot * (letters.indexOf(letter) + 0.5f))
    }

    @Test
    fun touchingALetterPicksIt() {
        show()
        val c = at('C')
        strip.performTouchInput { down(c); up() }
        assertEquals(listOf('C'), picked)
    }

    @Test
    fun aScreenReaderOpensALetterWithoutDragging() {
        show()
        compose.onNodeWithText("C").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithContentDescription("Pinned apps").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf('C', FAVORITES), picked)
    }

    @Test
    fun touchingTheStarPicksFavorites() {
        show()
        val star = at(FAVORITES)
        strip.performTouchInput { down(star); up() }
        assertEquals(listOf(FAVORITES), picked)
    }

    @Test
    fun slidingAlongTheStripPicksEachLetterOnce() {
        show()
        val a = at('A')
        val b = at('B')
        val d = at('D')
        strip.performTouchInput {
            down(a)
            moveTo(a.copy(y = a.y + 1f)) // still on A: not picked again
            moveTo(b)
            moveTo(d)
            up()
        }
        assertEquals(listOf('A', 'B', 'D'), picked)
        assertEquals(listOf(false), releases)
    }

    @Test
    fun slidingOffTheStripKeepsTheLetterAndReportsItOff() {
        show()
        val b = at('B')
        val away = at('E', x = -200f) // well past the left edge: onto the app rows
        strip.performTouchInput {
            down(b)
            moveTo(away)
            up()
        }
        assertEquals(listOf('B'), picked)
        assertTrue(drags.last())
        assertEquals(listOf(true), releases)
    }

    @Test
    fun leftHandedStripGoesOffToTheRight() {
        show(onLeft = true)
        val b = at('B')
        val away = at('E', x = strip.fetchSemanticsNode().size.width + 200f)
        strip.performTouchInput {
            down(b)
            moveTo(away)
            up()
        }
        assertEquals(listOf('B'), picked)
        assertEquals(listOf(true), releases)
    }
}
