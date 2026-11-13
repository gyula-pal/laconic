package cc.gyula.wave.core

import kotlin.math.ceil

/** Widths snap to quarters of the page. */
const val COLUMNS = 4

/** Between columns, in dp. */
const val COLUMN_GAP_DP = 12f

/** Roughly one home-screen cell, in dp; widgets ask for a height in cells. */
const val CELL_HEIGHT_DP = 100f

/** What a widget says about its size: px as Android reports them, 0 where it says nothing. */
data class WidgetSize(
    val minWidth: Int,
    val minHeight: Int,
    val minResizeWidth: Int = 0,
    val minResizeHeight: Int = 0,
    val maxResizeWidth: Int = 0,
    val maxResizeHeight: Int = 0,
    val targetCellWidth: Int = 0,
    val targetCellHeight: Int = 0,
    val resizesSideways: Boolean = false,
    val resizesVertically: Boolean = false,
)

/** How many columns of [columnDp] a width of [px] covers, counting the gaps between them. */
fun columnSpan(px: Int, density: Float, columnDp: Float) = (px / density + COLUMN_GAP_DP) / (columnDp + COLUMN_GAP_DP)

/** Width and height in home-screen cells, as Android's own launcher counts them. */
fun WidgetSize.cells(density: Float): Pair<Int, Int> {
    if (targetCellWidth > 0 && targetCellHeight > 0) return targetCellWidth to targetCellHeight
    // Android's rule is 70 × cells − 30 dp. The size comes back rounded to whole px, so 110 dp can read as
    // 110.1: without a little slack that would be a third cell.
    fun cells(px: Int) = ceil((px / density + 30f) / 70f - 0.05f).toInt().coerceIn(1, 5)
    return cells(minWidth) to cells(minHeight)
}

/** The columns a widget takes before it's resized: its own cell width, so a 2×2 sits on half the page. */
fun WidgetSize.defaultColumns(density: Float, columnDp: Float): Int {
    // A hair of slack: a widget a pixel wider than two columns shouldn't take three.
    val atLeast = ceil(columnSpan(minWidth, density, columnDp) - 0.05f).toInt()
    return maxOf(cells(density).first, atLeast).coerceIn(1, COLUMNS)
}

/** The height a widget asks for, in dp: its preferred cell count, else its minimum. */
fun WidgetSize.defaultHeightDp(density: Float): Float =
    maxOf(minHeight / density, if (targetCellHeight > 0) CELL_HEIGHT_DP * targetCellHeight else 0f)

/** How many columns a widget may span; null if it can't be resized sideways. */
fun WidgetSize.columnRange(density: Float, columnDp: Float): IntRange? {
    if (!resizesSideways) return null
    fun span(px: Int) = columnSpan(px, density, columnDp)
    val min = ceil(span(minResizeWidth.takeIf { it > 0 } ?: minWidth)).toInt().coerceIn(1, COLUMNS)
    val max = if (maxResizeWidth > 0) span(maxResizeWidth).toInt() else COLUMNS
    return min..max.coerceIn(min, COLUMNS)
}

/** How far a widget may be resized, in dp; null if it can't be resized vertically. */
fun WidgetSize.heightRangeDp(density: Float): ClosedFloatingPointRange<Float>? {
    if (!resizesVertically) return null
    val min = ((minResizeHeight.takeIf { it > 0 } ?: minHeight) / density).coerceAtLeast(40f)
    val max = if (maxResizeHeight > 0) maxResizeHeight / density else 640f
    // A widget may name a maximum under the floor; an empty range would make the caller's coerceIn throw.
    return min..max.coerceAtLeast(min)
}

/** Widget pages beside home, in screen order from the left; [left] of them sit left of home. */
data class WidgetPages(val pages: List<List<Int>> = emptyList(), val left: Int = 0) {
    val right: Int get() = pages.size - left

    /**
     * [newLeft] pages on the left of home and [newRight] on its right. Pages come and go at the far ends,
     * so the ones next to home stay put; returns the new pages and the widgets on the pages taken away.
     */
    fun resized(newLeft: Int, newRight: Int): Pair<WidgetPages, List<Int>> {
        val leftNow = pages.take(left)
        val rightNow = pages.drop(left)
        val removed = (leftNow.dropLast(newLeft) + rightNow.drop(newRight)).flatten()
        val next = List(newLeft) { leftNow.getOrNull(leftNow.size - newLeft + it).orEmpty() } +
            List(newRight) { rightNow.getOrNull(it).orEmpty() }
        return WidgetPages(next, newLeft) to removed
    }

    fun withWidget(page: Int, id: Int): WidgetPages? =
        if (page !in pages.indices) null else copy(pages = pages.mapIndexed { i, ids -> if (i == page) ids + id else ids })

    fun without(id: Int): WidgetPages = copy(pages = pages.map { it - id })

    companion object {
        /** Up to 0.3.2 every page sat on one side of home, stored nearest home first. */
        fun fromOneSide(pages: List<List<Int>>, onLeft: Boolean) =
            if (onLeft) WidgetPages(pages.reversed(), pages.size) else WidgetPages(pages, 0)
    }
}

/** "2 left, 1 right", or just the side that has pages. */
fun pageSides(left: Int, right: Int) = listOfNotNull(
    "$left left".takeIf { left > 0 },
    "$right right".takeIf { right > 0 },
).joinToString(", ")
