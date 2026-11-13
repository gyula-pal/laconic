package cc.gyula.wave.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoxesTest {
    private val whole = BoxArea(0f, 0f, 100f, 60f)

    private fun box(id: Int, vararg apps: String) = AppBox(id, apps.toList())

    /** Three levels: box 0 on the left; on the right, box 1 over a (box 2 | box 3) row. */
    private val tree = BoxSplit(
        sideBySide = true, ratio = 0.4f,
        first = box(0, "a"),
        second = BoxSplit(false, 0.5f, box(1, "b"), BoxSplit(true, 0.25f, box(2, "c"), box(3, "d"))),
    )

    private fun BoxArea.size() = (right - left) * (bottom - top)

    @Test
    fun boxesComeInReadingOrder() {
        assertEquals(listOf(0, 1, 2, 3), tree.boxes().map { it.id })
    }

    @Test
    fun areasTileTheWholeWithoutOverlap() {
        val areas = layoutBoxes(tree, whole).map { it.second }
        assertEquals(whole.size(), areas.sumOf { it.size().toDouble() }.toFloat(), 0.01f)
        for (i in areas.indices) for (j in i + 1 until areas.size) {
            val a = areas[i]
            val b = areas[j]
            val overlapW = minOf(a.right, b.right) - maxOf(a.left, b.left)
            val overlapH = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
            assertTrue("$a overlaps $b", overlapW <= 0.001f || overlapH <= 0.001f)
        }
        assertEquals(BoxArea(0f, 0f, 40f, 60f), layoutBoxes(tree, whole)[0].second)
        assertEquals(BoxArea(40f, 30f, 55f, 60f), layoutBoxes(tree, whole)[2].second)
    }

    @Test
    fun dividersAreTheInnerLinesOnly() {
        val lines = dividers(tree, whole)
        assertEquals(3, lines.size) // one per split
        for (d in lines) {
            val l = d.line
            assertTrue(l.left > whole.left && l.right < whole.right || l.top > whole.top && l.bottom < whole.bottom)
            if (d.sideBySide) {
                assertEquals(l.left, l.right, 0f)
                assertTrue(l.left > whole.left && l.right < whole.right)
            } else {
                assertEquals(l.top, l.bottom, 0f)
                assertTrue(l.top > whole.top && l.bottom < whole.bottom)
            }
        }
        val root = lines.single { it.path.isEmpty() }
        assertEquals(BoxArea(40f, 0f, 40f, 60f), root.line)
        assertEquals(whole, root.parent)
        assertEquals(listOf(true), lines.single { it.path.size == 1 }.path)
        assertEquals(listOf(true, true), lines.single { it.path.size == 2 }.path)
    }

    @Test
    fun ratioIsClamped() {
        fun ratioAt(t: BoxTree) = (t as BoxSplit).ratio
        assertEquals(0.2f, ratioAt(withRatio(tree, emptyList(), 0.05f, 0.2f)), 0f)
        assertEquals(0.8f, ratioAt(withRatio(tree, emptyList(), 0.99f, 0.2f)), 0f)
        assertEquals(0.6f, ratioAt(withRatio(tree, emptyList(), 0.6f, 0.2f)), 0f)
    }

    @Test
    fun ratioChangesOnlyTheSplitOnThePath() {
        val changed = withRatio(tree, listOf(true, true), 0.7f, 0.1f) as BoxSplit
        val inner = ((changed.second as BoxSplit).second as BoxSplit)
        assertEquals(0.7f, inner.ratio, 0f)
        assertEquals(0.4f, changed.ratio, 0f)
        assertEquals(tree.boxes(), changed.boxes())
    }

    @Test
    fun splittingThenRemovingTheNewBoxRestoresTheTree() {
        val split = splitBox(tree, 2, sideBySide = false)
        assertEquals(listOf(0, 1, 2, 4, 3), split.boxes().map { it.id })
        val newSplit = ((((split as BoxSplit).second as BoxSplit).second as BoxSplit).first as BoxSplit)
        assertEquals(BoxSplit(false, 0.5f, box(2, "c"), box(4)), newSplit)
        assertEquals(tree, removeBox(split, 4))
    }

    @Test
    fun newIdsNeverReuseOne() {
        val split = splitBox(box(7, "a"), 7, sideBySide = true)
        assertEquals(listOf(7, 8), split.boxes().map { it.id })
    }

    @Test
    fun removingABoxMovesItsAppsToTheSiblingsFirstBox() {
        val removed = removeBox(tree, 1) // sibling is the (2 | 3) split
        assertEquals(listOf(0, 2, 3), removed.boxes().map { it.id })
        assertEquals(listOf("c", "b"), removed.boxes().single { it.id == 2 }.apps)
        assertEquals(listOf("d"), removed.boxes().single { it.id == 3 }.apps)
    }

    @Test
    fun theOnlyBoxCannotBeRemoved() {
        val only = box(0, "a")
        assertEquals(only, removeBox(only, 0))
    }

    @Test
    fun anUnknownBoxChangesNothing() {
        assertEquals(tree, removeBox(tree, 99))
        assertEquals(tree, splitBox(tree, 99, true))
        assertEquals(tree, moveApp(tree, "a", 99))
    }

    @Test
    fun movingAnAppTakesItOutOfItsBox() {
        val moved = moveApp(tree, "a", 3)
        assertEquals(emptyList<String>(), moved.boxes().single { it.id == 0 }.apps)
        assertEquals(listOf("d", "a"), moved.boxes().single { it.id == 3 }.apps)
    }

    @Test
    fun movingToAnIndexInsertsThere() {
        val moved = moveApp(tree, "a", 3, index = 0)
        assertEquals(listOf("a", "d"), moved.boxes().single { it.id == 3 }.apps)
    }

    @Test
    fun movingWithinTheSameBoxReorders() {
        val one = box(0, "a", "b", "c")
        assertEquals(listOf("b", "c", "a"), (moveApp(one, "a", 0) as AppBox).apps)
        assertEquals(listOf("c", "a", "b"), (moveApp(one, "c", 0, index = 0) as AppBox).apps)
        assertEquals(listOf("b", "a", "c"), (moveApp(one, "a", 0, index = 1) as AppBox).apps)
    }

    @Test
    fun nothingSavedMeansOneBoxWithEveryPin() {
        assertEquals(box(0, "a", "b"), reconciled(null, listOf("a", "b")))
    }

    @Test
    fun unpinnedAppsDropOut() {
        val r = reconciled(tree, listOf("a", "c", "d"))
        assertEquals(emptyList<String>(), r.boxes().single { it.id == 1 }.apps)
        assertEquals(listOf("c"), r.boxes().single { it.id == 2 }.apps)
    }

    @Test
    fun newPinsAreAppendedToTheFirstBoxInPinOrder() {
        val r = reconciled(tree, listOf("z", "a", "b", "c", "d", "y"))
        assertEquals(listOf("a", "z", "y"), r.boxes().first().apps)
    }

    @Test
    fun duplicatesKeepTheFirst() {
        val twice = BoxSplit(true, 0.5f, box(0, "a", "a"), box(1, "b", "a"))
        val r = reconciled(twice, listOf("a", "b"))
        assertEquals(listOf("a"), r.boxes()[0].apps)
        assertEquals(listOf("b"), r.boxes()[1].apps)
    }

    @Test
    fun reconcilingAnUpToDateTreeChangesNothing() {
        assertEquals(tree, reconciled(tree, listOf("a", "b", "c", "d")))
    }

    @Test
    fun layoutsCycle() {
        assertEquals(HomeLayout.BOXES, next(HomeLayout.LIST))
        assertEquals(HomeLayout.LIST, next(HomeLayout.BOXES))
    }

    @Test
    fun renameSetsATrimmedNameOnlyOnThatBox() {
        val r = renameBox(tree, 2, "  Money  ")
        assertEquals(listOf("", "", "Money", ""), r.boxes().map { it.name })
        assertEquals(tree.boxes().map { it.apps }, r.boxes().map { it.apps })
    }

    @Test
    fun aBlankNameClearsTheLabel() {
        val named = renameBox(tree, 0, "Work")
        assertEquals("", renameBox(named, 0, "   ").boxes().first().name)
    }

    @Test
    fun renamingAMissingBoxChangesNothing() {
        assertEquals(tree, renameBox(tree, 99, "x"))
    }

    @Test
    fun aSplitGivesTheNewBoxNoNameAndKeepsTheOld() {
        val r = splitBox(renameBox(tree, 1, "Chat"), 1, false)
        assertEquals("Chat", r.boxes().single { it.id == 1 }.name)
        assertEquals("", r.boxes().single { it.id == 4 }.name)
    }

    @Test
    fun removingABoxDropsItsNameAndKeepsTheSiblings() {
        val named = renameBox(renameBox(tree, 0, "Left"), 1, "Gone")
        val r = removeBox(named, 1)
        assertEquals(listOf(0, 2, 3), r.boxes().map { it.id })
        assertEquals(listOf("Left", "", ""), r.boxes().map { it.name })
        val sibling = removeBox(renameBox(renameBox(tree, 2, "Keep"), 3, "Gone"), 3)
        assertEquals("Keep", sibling.boxes().single { it.id == 2 }.name)
    }

    @Test
    fun reconcilingAndMovingKeepNames() {
        val named = renameBox(tree, 0, "Home")
        assertEquals("Home", reconciled(named, listOf("a", "b", "c", "d", "z")).boxes().first().name)
        assertEquals("Home", moveApp(named, "a", 1).boxes().first().name)
    }

    private fun appsOf(t: BoxTree) = t.boxes().associate { it.id to it.apps }

    @Test
    fun setBoxAppsKeepsOrderAppendsNewAndTakesThemFromOtherBoxes() {
        val t = BoxSplit(true, 0.5f, AppBox(0, listOf("a", "b", "c")), AppBox(1, listOf("d", "e")))
        // b gone, a/c keep their order, d moves in from box 1, f is new; passed order is c, a, f, d.
        val r = setBoxApps(t, 0, listOf("c", "a", "f", "d"))
        assertEquals(mapOf(0 to listOf("a", "c", "f", "d"), 1 to listOf("e")), appsOf(r))
    }

    @Test
    fun setBoxAppsCanEmptyABoxAndIgnoresRepeats() {
        val t = BoxSplit(true, 0.5f, AppBox(0, listOf("a")), AppBox(1, listOf("d")))
        assertEquals(mapOf(0 to emptyList<String>(), 1 to listOf("d")), appsOf(setBoxApps(t, 0, emptyList())))
        assertEquals(listOf("a", "x"), setBoxApps(t, 0, listOf("x", "a", "x")).boxes().first().apps)
    }

    @Test
    fun setBoxAppsOnAnUnknownBoxChangesNothing() {
        val t = BoxSplit(true, 0.5f, AppBox(0, listOf("a")), AppBox(1, listOf("d")))
        assertEquals(t, setBoxApps(t, 9, listOf("a", "d", "z")))
    }

    @Test
    fun pinsFollowTheBoxEdit() {
        assertEquals(listOf("a", "c", "x", "y"), pinsForBoxApps(listOf("a", "b", "c"), listOf("b", "c"), listOf("c", "x", "y")))
        // Already pinned (in another box) and moved here: stays where it is in the pins.
        assertEquals(listOf("a", "b"), pinsForBoxApps(listOf("a", "b"), listOf("a"), listOf("a", "b")))
    }

    @Test
    fun aNewlyPinnedAppEndsUpInTheTargetBoxWhicheverTreeTheEditStartedFrom() {
        val pins = listOf("a", "d")
        val saved = BoxSplit(true, 0.5f, AppBox(0, listOf("a")), AppBox(1, listOf("d")))
        val keys = listOf("d", "n") // box 1 gets n, a new pin
        val newPins = pinsForBoxApps(pins, listOf("d"), keys)
        // The view model's boxes flow may still be the old reconciliation (without n) or already have n in box 0.
        val stale = reconciled(saved, pins)
        val fresh = reconciled(saved, pins + "n")
        for (start in listOf(stale, fresh)) {
            val end = reconciled(setBoxApps(start, 1, keys), newPins)
            assertEquals(mapOf(0 to listOf("a"), 1 to listOf("d", "n")), appsOf(end))
        }
    }

    @Test
    fun anOuterLineStopsWhereABoxBehindAnInnerLineWouldGetTooNarrow() {
        // A | (B | C) side by side, 300 wide, boxes at least 64: the outer line can't go past 300 - 128.
        val inner = BoxSplit(true, 0.5f, AppBox(1, emptyList()), AppBox(2, emptyList()))
        val outer = BoxSplit(true, 0.3f, AppBox(0, emptyList()), inner)
        val range = ratioRange(outer, 300f, 64f)
        assertEquals(64f / 300f, range.start, 1e-4f)
        assertEquals(1 - 128f / 300f, range.endInclusive, 1e-4f)
        // Across the axis, a stack needs only its widest part: (B over C) needs 64 wide, not 128.
        val stacked = BoxSplit(true, 0.3f, AppBox(0, emptyList()), BoxSplit(false, 0.5f, AppBox(1, emptyList()), AppBox(2, emptyList())))
        assertEquals(1 - 64f / 300f, ratioRange(stacked, 300f, 64f).endInclusive, 1e-4f)
        assertEquals(inner, splitAt(outer, listOf(true)))
        assertEquals(null, splitAt(outer, listOf(true, true)))
    }

    @Test
    fun tooLittleRoomPutsTheLineInTheMiddle() {
        val r = ratioRange(BoxSplit(true, 0.5f, AppBox(0, emptyList()), AppBox(1, emptyList())), 100f, 64f)
        assertEquals(0.5f, r.start, 1e-4f)
        assertEquals(0.5f, r.endInclusive, 1e-4f)
    }

    @Test
    fun aSavedSliverIsOpenedUpWhenLaidOut() {
        // Saved before lines respected the boxes behind them: A at 90 %, leaving B and C 15 wide each in 300.
        val tree = BoxSplit(true, 0.9f, AppBox(0, emptyList()), BoxSplit(true, 0.5f, AppBox(1, emptyList()), AppBox(2, emptyList())))
        val boxes = layoutBoxes(fitted(tree, BoxArea(0f, 0f, 300f, 500f), 64f), BoxArea(0f, 0f, 300f, 500f))
        boxes.forEach { (box, area) -> assertTrue("box ${box.id}: ${area.right - area.left}", area.right - area.left >= 64f - 0.01f) }
        // A layout that already fits is left exactly as it is.
        val fine = BoxSplit(true, 0.5f, AppBox(0, emptyList()), AppBox(1, emptyList()))
        assertEquals(fine, fitted(fine, BoxArea(0f, 0f, 300f, 500f), 64f))
    }
}
