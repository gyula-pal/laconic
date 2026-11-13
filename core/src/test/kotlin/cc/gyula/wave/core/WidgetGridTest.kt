package cc.gyula.wave.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class WidgetSizeTest {
    // A Pixel 10: density 2.625; a 4-column page 1080 px wide minus 2 × 16 dp padding → ~85 dp columns.
    private val density = 2.625f
    private val column = 85f

    private fun dp(v: Int) = (v * density).roundToInt() // as Android turns dp into px

    @Test
    fun twoByTwoTakesHalfThePage() {
        // 0.3.3: small widgets used to take the full width, so two 2×2s couldn't share a row.
        val clock = WidgetSize(minWidth = dp(110), minHeight = dp(110), targetCellWidth = 2, targetCellHeight = 2)
        assertEquals(2, clock.defaultColumns(density, column))
    }

    @Test
    fun wideWidgetsTakeTheWholePage() {
        val glance = WidgetSize(minWidth = dp(300), minHeight = dp(60), targetCellWidth = 4, targetCellHeight = 1)
        assertEquals(4, glance.defaultColumns(density, column))
    }

    @Test
    fun withoutTargetCellsTheMinimumWidthDecides() {
        // Older widgets give only a minimum: Android's own launcher counts (dp + 30) / 70 cells.
        assertEquals(2 to 1, WidgetSize(minWidth = dp(110), minHeight = dp(40)).cells(density))
        assertEquals(2, WidgetSize(minWidth = dp(110), minHeight = dp(40)).defaultColumns(density, column))
    }

    @Test
    fun aHairOverAColumnBoundaryDoesNotTakeAnotherColumn() {
        // Exactly two columns plus their gap is 182 dp; rounding can make a widget a pixel wider.
        val justOver = WidgetSize(minWidth = dp(182) + 1, minHeight = dp(40), targetCellWidth = 1, targetCellHeight = 1)
        assertEquals(2, justOver.defaultColumns(density, column))
    }

    @Test
    fun heightIsTheLargerOfMinimumAndTargetCells() {
        assertEquals(200f, WidgetSize(minWidth = 0, minHeight = dp(110), targetCellHeight = 2).defaultHeightDp(density), 0.5f)
        assertEquals(110f, WidgetSize(minWidth = 0, minHeight = dp(110)).defaultHeightDp(density), 0.5f)
    }

    @Test
    fun heightRangeIsNeverEmpty() {
        // A widget whose maximum is under the 40 dp floor must not hand back an empty range: coerceIn throws on it.
        val tiny = WidgetSize(
            minWidth = dp(110), minHeight = dp(20), maxResizeHeight = dp(30), resizesVertically = true,
        )
        val range = tiny.heightRangeDp(density)!!
        assertTrue(range.start <= range.endInclusive)
        assertEquals(40f, 100f.coerceIn(range), 0f)
    }

    @Test
    fun resizeLimits() {
        assertNull(WidgetSize(minWidth = dp(110), minHeight = dp(110)).columnRange(density, column))
        assertNull(WidgetSize(minWidth = dp(110), minHeight = dp(110)).heightRangeDp(density))
        val resizable = WidgetSize(
            minWidth = dp(110), minHeight = dp(110), minResizeWidth = dp(40), minResizeHeight = dp(20),
            resizesSideways = true, resizesVertically = true,
        )
        assertEquals(1..COLUMNS, resizable.columnRange(density, column))
        // Never shorter than 40 dp, and up to 640 dp when the widget names no maximum.
        assertEquals(40f..640f, resizable.heightRangeDp(density))
        val capped = resizable.copy(maxResizeWidth = dp(182))
        assertEquals(1..2, capped.columnRange(density, column))
    }
}

class WidgetPagesTest {
    // Screen order: L2 L1 | home | R1, so two pages on the left and one on the right.
    private val pages = WidgetPages(listOf(listOf(1), listOf(2), listOf(3)), left = 2)

    @Test
    fun pagesNextToHomeStayWhenOthersGo() {
        val (next, removed) = pages.resized(newLeft = 1, newRight = 1)
        assertEquals(WidgetPages(listOf(listOf(2), listOf(3)), left = 1), next)
        assertEquals(listOf(1), removed)
    }

    @Test
    fun newPagesAreAddedAtTheFarEnds() {
        val (next, removed) = pages.resized(newLeft = 3, newRight = 2)
        assertEquals(WidgetPages(listOf(emptyList(), listOf(1), listOf(2), listOf(3), emptyList()), left = 3), next)
        assertEquals(emptyList<Int>(), removed)
    }

    @Test
    fun removingEveryPageRemovesEveryWidget() {
        val (next, removed) = pages.resized(0, 0)
        assertEquals(WidgetPages(), next)
        assertEquals(listOf(1, 2, 3), removed)
    }

    @Test
    fun addingAndRemovingWidgets() {
        assertEquals(listOf(listOf(1), listOf(2, 9), listOf(3)), pages.withWidget(1, 9)!!.pages)
        assertNull(pages.withWidget(7, 9)) // the page was taken away while the widget was being set up
        assertEquals(listOf(listOf(1), emptyList(), listOf(3)), pages.without(2).pages)
    }

    @Test
    fun oneSideLayoutFrom032Migrates() {
        // Up to 0.3.2 pages sat on one side, stored nearest home first.
        val old = listOf(listOf(1), listOf(2))
        assertEquals(WidgetPages(listOf(listOf(2), listOf(1)), left = 2), WidgetPages.fromOneSide(old, onLeft = true))
        assertEquals(WidgetPages(old, left = 0), WidgetPages.fromOneSide(old, onLeft = false))
    }

    @Test
    fun sidesDescribed() {
        assertEquals("2 left, 1 right", pageSides(2, 1))
        assertEquals("1 right", pageSides(0, 1))
        assertEquals("", pageSides(0, 0))
    }
}
