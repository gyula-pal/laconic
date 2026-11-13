package cc.gyula.wave.ui.widgets

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A widget's app hears its size only when it changed: every telling is a call into Android, and a new size a
 * broadcast to that app. The cell recomposes for a menu opening or every frame of a height drag.
 */
class LastSizeTest {
    private val last = LastSize()
    private val small = DpSize(180.dp, 100.dp)
    private val wide = DpSize(372.dp, 100.dp)

    private fun told(sizes: List<DpSize>) = sizes.count { last.changed(it) }

    @Test
    fun theFirstSizeIsTold() {
        assertEquals(1, told(listOf(small)))
    }

    @Test
    fun theSameSizeAgainIsNotTold() {
        // The update block ran on every recomposition of the cell and told the app the same size each time.
        assertEquals(1, told(listOf(small, small, small)))
    }

    @Test
    fun aNewSizeIsToldAndSoIsGoingBack() {
        assertEquals(3, told(listOf(small, wide, wide, small)))
    }
}
