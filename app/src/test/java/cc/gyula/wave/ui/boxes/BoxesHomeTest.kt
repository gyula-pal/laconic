package cc.gyula.wave.ui.boxes

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import cc.gyula.wave.core.AppBox
import cc.gyula.wave.core.BoxArea
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import cc.gyula.wave.core.BoxLines
import cc.gyula.wave.core.BoxSplit
import cc.gyula.wave.core.BoxTree
import cc.gyula.wave.core.StripSide
import cc.gyula.wave.core.boxes
import cc.gyula.wave.core.renameBox
import cc.gyula.wave.core.setBoxApps
import cc.gyula.wave.ui.home.CornerApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The boxes on home, driven the way the home screen drives them: a tree in, changes out. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h914dp-xxhdpi")
class BoxesHomeTest {
    @get:Rule
    val compose = createComposeRule()

    private var tree: BoxTree by mutableStateOf(AppBox(0, listOf("a", "b")))
    private var editing by mutableStateOf(false)
    private val launched = mutableListOf<String>()
    private val reachedHome = mutableListOf<String>() // what got past the boxes to the screen behind
    private val state = BoxesState()
    private val rows = HashMap<String, Rect>()

    private val applied = mutableListOf<Pair<Int, List<String>>>()

    private var lineLook by mutableStateOf(BoxLines.FAINT)
    private var stripSide = StripSide.RIGHT

    private fun show(many: Int = 0) = compose.setContent {
        Box(
            Modifier
                .size(400.dp, 600.dp)
                .background(Color.Black)
                .pointerInput(Unit) { detectTapGestures(onLongPress = { reachedHome += "settings" }, onTap = { reachedHome += "tap" }) }
        ) {
            BoxesContent(
                tree,
                onChange = { tree = it(tree) },
                label = { if (it == "gone") null else it.uppercase() },
                cachedIcon = { null },
                loadIcon = { null as ImageBitmap? },
                notified = { false },
                onLaunch = { launched += it },
                hovered = null,
                rows = rows,
                editing = editing,
                onEditingChange = { editing = it },
                onBackground = { reachedHome += "background" },
                state = state,
                stripSide = stripSide,
                enter = { 1f },
                modifier = Modifier.size(400.dp, 600.dp),
                lineLook = lineLook,
                pickable = listOf(CornerApp("a", "A"), CornerApp("b", "B"), CornerApp("c", "C")),
                onSetBoxApps = { id, keys -> applied += id to keys; tree = setBoxApps(tree, id, keys) },
                addDialog = { request, _, _, onDismiss, onApply ->
                    Column(Modifier.zIndex(10f)) {
                        Text("add:${request.title}")
                        Text("rows:" + request.rows.joinToString { "${it.key}@${it.elsewhere}" })
                        Button(onClick = { onApply(listOf("a", "c")) }) { Text("apply-ac") }
                        Button(onClick = onDismiss) { Text("cancel-add") }
                    }
                },
                nameDialog = { current, onDismiss, onSave ->
                    // On top of every box, as a real dialog window is, so its buttons get the touch.
                    Column(Modifier.zIndex(10f)) {
                        Text("dialog:$current")
                        Button(onClick = { onSave("  Work ") }) { Text("save-work") }
                        Button(onClick = { onSave("") }) { Text("save-blank") }
                        Button(onClick = onDismiss) { Text("cancel") }
                    }
                },
            )
        }
    }

    @Test
    fun iconsShowInTheirOwnBoxes() {
        tree = BoxSplit(true, 0.5f, AppBox(0, listOf("a")), AppBox(1, listOf("b")))
        show()
        compose.onNodeWithTag("box:0").assertIsDisplayed()
        compose.onNodeWithTag("box:1").assertIsDisplayed()
        compose.onNodeWithContentDescription("A").assertIsDisplayed()
        compose.onNodeWithContentDescription("B").assertIsDisplayed()
        // "a" sits in the left box, "b" in the right one.
        val a = compose.onNodeWithTag("app:a").fetchSemanticsNode().boundsInRoot
        val b = compose.onNodeWithTag("app:b").fetchSemanticsNode().boundsInRoot
        assertTrue(a.right <= b.left)
        assertEquals(setOf("a", "b"), rows.keys)
    }

    @Test
    fun tappingAnIconLaunchesIt() {
        show()
        compose.onNodeWithContentDescription("B").performClick()
        assertEquals(listOf("b"), launched)
    }

    @Test
    fun longPressOnAnIconStartsEditingAndDoesNotLaunch() {
        show()
        compose.onNodeWithContentDescription("A").performTouchInput { longClick() }
        assertTrue(editing)
        assertEquals(emptyList<String>(), launched)
        assertEquals(emptyList<String>(), reachedHome)
    }

    @Test
    fun longPressBesideTheBoxesDoesNothing() {
        // The margin by the screen edge, where a thumb going for the letter strip lands: no edit mode (it kept
        // opening on the phone), and no settings either.
        show()
        compose.onRoot().performTouchInput { longClick(Offset(399.dp.toPx(), 300.dp.toPx())) }
        assertFalse(editing)
        assertFalse("settings" in reachedHome)
    }

    @Test
    fun longPressOnEmptySpaceStartsEditingWithoutOpeningSettings() {
        show()
        compose.onNodeWithTag("box:0").performTouchInput { longClick(Offset(width - 20f, height - 20f)) }
        assertTrue(editing)
        assertFalse("settings" in reachedHome)
    }

    @Test
    fun editModeEnteredOnThePhoneKeepsBlankTapsAndMovesApps() {
        // As on the phone: the boxes are first drawn outside edit mode and edit mode starts later, by long-press.
        // The boxes' tap handlers were made before it, and must still see it (the owner's "can't move icons").
        tree = BoxSplit(true, 0.5f, AppBox(0, listOf("a")), AppBox(1, listOf("b")))
        show()
        compose.onNodeWithTag("box:0").performTouchInput { longClick(Offset(width - 20f, height - 20f)) }
        assertTrue(editing)
        compose.onNodeWithTag("box:0").performTouchInput { click(Offset(width - 20f, height / 2f)) }
        compose.onNodeWithTag("box:1").performTouchInput { click(Offset(width / 2f, height - 40f)) }
        assertTrue("a blank tap in edit mode must not end it", editing)
        assertFalse("background" in reachedHome)
        compose.onNodeWithTag("app:a").performClick() // pick "a" up
        compose.onNodeWithTag("box:1").performTouchInput { click(Offset(width / 2f, height - 40f)) } // put it down
        assertEquals(listOf("b", "a"), tree.boxes().first { it.id == 1 }.apps)
    }

    @Test
    fun splittingCreatesASecondBox() {
        editing = true
        show()
        compose.onNodeWithTag("box:1").assertDoesNotExist()
        compose.onNodeWithTag("split-side:0").performClick()
        compose.onNodeWithTag("box:1").assertIsDisplayed()
        assertEquals(2, tree.boxes().size)
        assertTrue((tree as BoxSplit).sideBySide)
        // Half a screen is too narrow for four buttons now: the menu carries them.
        compose.onNodeWithTag("menu:1").performClick()
        compose.onNodeWithTag("split-stack:1").performClick()
        assertEquals(3, tree.boxes().size)
    }

    @Test
    fun removingABoxMergesItsAppsIntoTheOther() {
        tree = BoxSplit(true, 0.5f, AppBox(0, listOf("a")), AppBox(1, listOf("b")))
        editing = true
        show()
        compose.onNodeWithTag("menu:1").performClick()
        compose.onNodeWithTag("remove:1").performClick()
        assertEquals(listOf(AppBox(0, listOf("a", "b"))), tree.boxes())
        compose.onNodeWithTag("box:1").assertDoesNotExist()
        compose.onNodeWithContentDescription("B").assertIsDisplayed()
        compose.onNodeWithTag("remove:0").assertDoesNotExist() // the only box cannot go
    }

    @Test
    fun tappingAnIconThenABoxMovesIt() {
        tree = BoxSplit(true, 0.5f, AppBox(0, listOf("a", "c")), AppBox(1, listOf("b")))
        editing = true
        show()
        compose.onNodeWithContentDescription("A").performClick() // pick up
        compose.onNodeWithTag("box:1").performTouchInput { click(Offset(width - 10f, height - 10f)) }
        assertEquals(listOf(AppBox(0, listOf("c")), AppBox(1, listOf("b", "a"))), tree.boxes())
        assertEquals(emptyList<String>(), launched)
    }

    @Test
    fun tappingBlankSpaceInEditModeWithNothingPickedDoesNothing() {
        editing = true
        show()
        compose.onNodeWithTag("box:0").performTouchInput { click(Offset(width - 10f, height - 10f)) }
        assertEquals(emptyList<String>(), reachedHome)
        assertTrue(editing)
    }

    @Test
    fun tappingBlankSpaceInNormalModeGoesToTheBackground() {
        show()
        compose.onNodeWithTag("box:0").performTouchInput { click(Offset(width - 10f, height - 10f)) }
        assertEquals(listOf("background"), reachedHome)
    }

    @Test
    fun aShortTouchOnAnIconLaunchesIt() {
        show()
        compose.onNodeWithTag("app:b").performTouchInput { click() }
        assertEquals(listOf("b"), launched)
        assertFalse(editing)
        assertEquals(emptyList<String>(), reachedHome)
    }

    /** Long-press [key], then drag to [to] (root coordinates, read after edit mode has laid out) and let go. */
    private fun dragApp(key: String, to: () -> Offset) {
        val node = compose.onNodeWithTag("app:$key")
        node.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(800)
        compose.waitForIdle()
        val target = to() - node.fetchSemanticsNode().boundsInRoot.topLeft
        node.performTouchInput { moveTo(target / 2f) }
        node.performTouchInput { moveTo(target) }
        compose.onNodeWithTag("drag-ghost").assertExists() // it floats above the finger, so near the top it can be off screen
        node.performTouchInput { up() }
        compose.waitForIdle()
    }

    @Test
    fun draggingAnAppIntoAnotherBoxMovesItToTheDropIndex() {
        tree = BoxSplit(true, 0.5f, AppBox(0, listOf("a", "x")), AppBox(1, listOf("b", "c", "d")))
        show()
        assertFalse(editing)
        // Between b and c: right of b's middle, same row.
        dragApp("a") { rows.getValue("b").center + Offset(2f, 0f) }
        assertEquals(listOf(AppBox(0, listOf("x")), AppBox(1, listOf("b", "a", "c", "d"))), tree.boxes())
        assertTrue("stays in edit mode", editing)
        assertEquals(emptyList<String>(), launched)
        assertEquals(emptyList<String>(), reachedHome)
    }

    @Test
    fun iconsScrolledOutOfViewDoNotPushTheDropLater() {
        // On the phone: a box with more apps than fit; the hidden ones report an empty rect. Dropping on the left
        // half of the third icon must land third, not three slots later.
        val cell = 100f
        val cells = listOf(
            Rect(0f, 0f, cell, cell), Rect(cell, 0f, 2 * cell, cell), // row 1
            Rect(0f, cell, cell, 2 * cell), Rect(cell, cell, 2 * cell, 2 * cell), // row 2
            Rect.Zero, Rect.Zero, Rect.Zero, // scrolled out of the box
        )
        assertEquals(2, dropIndex(cells, Offset(30f, 150f)))
        // Past every icon on screen: after the last one shown, ahead of the hidden ones.
        assertEquals(4, dropIndex(cells, Offset(190f, 190f)))
        // Nothing on screen at all: first.
        assertEquals(0, dropIndex(listOf(Rect.Zero, Rect.Zero), Offset(10f, 10f)))
    }

    @Test
    fun droppingInTheSameBoxReordersIt() {
        tree = AppBox(0, listOf("a", "b", "c"))
        editing = true
        show()
        dragApp("c") { rows.getValue("a").center + Offset(2f, 0f) }
        assertEquals(listOf(AppBox(0, listOf("a", "c", "b"))), tree.boxes())
    }

    @Test
    fun aCarriedAppFloatsAboveTheFingerWithAMarkerWhereItWillLand() {
        // Right under the fingertip the icon was hidden: it seemed to jump on release (owner, on the phone).
        tree = AppBox(0, listOf("a", "b", "c"))
        editing = true
        show()
        val node = compose.onNodeWithTag("app:a")
        node.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(800)
        compose.waitForIdle()
        val finger = rows.getValue("c").center + Offset(2f, 0f)
        val local = finger - node.fetchSemanticsNode().boundsInRoot.topLeft
        node.performTouchInput { moveTo(local) }
        val ghost = compose.onNodeWithTag("drag-ghost").fetchSemanticsNode().boundsInRoot
        assertTrue("floats above the finger", ghost.bottom < finger.y)
        val marker = compose.onNodeWithTag("drop-marker").fetchSemanticsNode().boundsInRoot
        // Past c's middle: the line sits at c's right edge.
        assertEquals(rows.getValue("c").right, marker.center.x, 3f)
        node.performTouchInput { up() }
    }

    /** Pixels across the line between two side-by-side boxes, low down where no icon is, and the brightest one. */
    private fun lineAcross(): Pair<Int, Float> {
        val box = compose.onNodeWithTag("box:0").fetchSemanticsNode().boundsInRoot
        val map = compose.onRoot().captureToImage().toPixelMap()
        val y = box.bottom.toInt() - 40
        // White ink over the black home screen: the line's pixels are the ones that aren't black.
        val lit = (0 until map.width).map { map[it, y].red }.filter { it > 0.05f }
        return lit.size to (lit.maxOrNull() ?: 0f)
    }

    @Test
    fun theLineLookSetsThicknessAndBrightness() {
        tree = BoxSplit(true, 0.5f, AppBox(0, emptyList()), AppBox(1, emptyList()))
        lineLook = BoxLines.FAINT
        show()
        val (faintWidth, faintAlpha) = lineAcross()
        assertEquals(1, faintWidth)
        lineLook = BoxLines.BOLD
        compose.waitForIdle()
        val (boldWidth, boldAlpha) = lineAcross()
        assertEquals(2, boldWidth)
        assertTrue("bold is brighter", boldAlpha > faintAlpha + 0.2f)
    }

    @Test
    fun aShortLaneInEditModeHasOneMenuButtonNotARowOverItsIcons() {
        // A full-width lane 90 dp tall: wide enough for five buttons, but they would sit on its only row of icons.
        tree = BoxSplit(false, 0.15f, AppBox(0, (1..8).map { "l$it" }), AppBox(1, listOf("g")))
        editing = true
        show()
        compose.onNodeWithTag("menu:0").assertExists()
        compose.onNodeWithTag("split-side:0").assertDoesNotExist() // until the menu is opened
        compose.onNodeWithTag("split-side:1").assertExists() // the tall box keeps its row of buttons
    }

    @Test
    fun aOneRowBoxIsALaneThatScrollsSideways() {
        // 600 dp split 15 / 85: the top box (90 dp) has room for one row of 56 dp cells, the bottom one for many.
        val many = (1..10).map { "l$it" }
        tree = BoxSplit(false, 0.15f, AppBox(0, many), AppBox(1, (1..10).map { "g$it" }))
        show()
        // Icons scrolled out of the lane report an empty rect: only the ones on screen say where the row is.
        val tops = many.map { compose.onNodeWithTag("app:$it").fetchSemanticsNode().boundsInRoot }
            .filter { !it.isEmpty }.map { it.top }.distinct()
        assertEquals("the lane is one row", 1, tops.size)
        val before = compose.onNodeWithTag("app:l1").fetchSemanticsNode().boundsInRoot.left
        compose.onNodeWithTag("box:0").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertTrue("it scrolled sideways", compose.onNodeWithTag("app:l1").fetchSemanticsNode().boundsInRoot.left < before - 20f)
        // The tall box is still a grid: its apps take more than one row.
        val gridTops = (1..10).map { compose.onNodeWithTag("app:g$it").fetchSemanticsNode().boundsInRoot.top }.distinct()
        assertTrue(gridTops.size > 1)
    }

    @Test
    fun droppingOutsideEveryBoxChangesNothing() {
        tree = BoxSplit(true, 0.5f, AppBox(0, listOf("a")), AppBox(1, listOf("b")))
        editing = true
        show()
        dragApp("a") { Offset(2f, 2f) }
        assertEquals(listOf(AppBox(0, listOf("a")), AppBox(1, listOf("b"))), tree.boxes())
    }

    @Test
    fun startingADragClearsThePickedApp() {
        tree = AppBox(0, listOf("a", "b"))
        editing = true
        show()
        compose.onNodeWithTag("app:a").performClick() // pick up a
        dragApp("b") { rows.getValue("a").center + Offset(2f, 0f) }
        // b went after a, and nothing is still picked: tapping blank space moves nothing.
        assertEquals(listOf(AppBox(0, listOf("a", "b"))), tree.boxes())
        compose.onNodeWithTag("box:0").performTouchInput { click(Offset(width - 10f, height - 10f)) }
        assertEquals(listOf(AppBox(0, listOf("a", "b"))), tree.boxes())
    }

    @Test
    fun aNarrowBoxHasOneMenuButtonThatSplitsAndRemoves() {
        tree = BoxSplit(true, 0.2f, AppBox(0, listOf("a")), AppBox(1, listOf("b")))
        editing = true
        show()
        compose.onNodeWithTag("menu:0").assertIsDisplayed()
        compose.onNodeWithTag("split-side:0").assertDoesNotExist() // closed until opened
        compose.onNodeWithTag("menu:0").performClick()
        compose.onNodeWithTag("split-stack:0").performClick()
        assertEquals(3, tree.boxes().size)
        assertEquals(false, (tree as BoxSplit).first.let { it is BoxSplit && it.sideBySide })
        compose.onNodeWithTag("menu:0").performClick()
        compose.onNodeWithTag("remove:0").performClick()
        assertEquals(2, tree.boxes().size)
    }

    @Test
    fun iconsStartBelowTheControls() {
        tree = BoxSplit(true, 0.2f, AppBox(0, listOf("a")), AppBox(1, listOf("b")))
        editing = true
        show()
        // The narrow box's menu button and the wide box's row of three: neither reaches an icon.
        val narrow = compose.onNodeWithTag("menu:0").fetchSemanticsNode().boundsInRoot.bottom
        val wide = compose.onNodeWithTag("split-side:1").fetchSemanticsNode().boundsInRoot.bottom
        assertTrue(compose.onNodeWithTag("app:a").fetchSemanticsNode().boundsInRoot.top >= narrow)
        assertTrue(compose.onNodeWithTag("app:b").fetchSemanticsNode().boundsInRoot.top >= wide)
    }

    @Test
    fun draggingALineResizesAndSavesOnRelease() {
        tree = BoxSplit(true, 0.5f, AppBox(0, listOf("a")), AppBox(1, listOf("b")))
        editing = true
        show()
        compose.onNodeWithTag("divider:").performTouchInput {
            down(center)
            moveBy(Offset(60f, 0f))
            moveBy(Offset(60f, 0f))
            up()
        }
        // 411 dp at 3x is ~1100 px wide: 120 px is a bit over a tenth.
        val ratio = (tree as BoxSplit).ratio
        assertTrue("ratio $ratio", ratio > 0.55f && ratio < 0.65f)
        assertEquals(emptyList<String>(), reachedHome)
    }

    @Test
    fun aLineCannotBeDraggedPastTheMinimumBoxSize() {
        tree = BoxSplit(true, 0.5f, AppBox(0, listOf("a")), AppBox(1, listOf("b")))
        editing = true
        show()
        compose.onNodeWithTag("divider:").performTouchInput {
            down(center)
            moveBy(Offset(-5000f, 0f))
            up()
        }
        val ratio = (tree as BoxSplit).ratio
        // 64 dp of about 411 - 64 (the row inset and margin): never thinner than that.
        assertTrue("ratio $ratio", ratio > 0.1f && ratio < 0.3f)
    }

    @Test
    fun holdingALineHandleDoesNotReachTheHomeScreen() {
        tree = BoxSplit(true, 0.5f, AppBox(0, listOf("a")), AppBox(1, listOf("b")))
        editing = true
        show()
        compose.onNodeWithTag("divider:").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1500) // well past the long-press time
        compose.onNodeWithTag("divider:").performTouchInput { up() }
        assertEquals(emptyList<String>(), reachedHome)
    }

    @Test
    fun tappingALineHandleDoesNotCountAsATapOnTheBoxBehindIt() {
        tree = BoxSplit(true, 0.5f, AppBox(0, listOf("a")), AppBox(1, listOf("b")))
        editing = true
        show()
        compose.onNodeWithTag("divider:").performClick()
        assertEquals(emptyList<String>(), reachedHome)
        assertTrue(editing)
    }

    @Test
    fun aScrolledBoxHoldsOnToAPullDown() {
        tree = AppBox(0, (1..200).map { "app$it" })
        show()
        val box = compose.onNodeWithTag("box:0").fetchSemanticsNode().boundsInRoot
        val inside = box.center
        val outside = Offset(1f, 1f)
        assertTrue(state.scrolledAt(inside))
        compose.onNodeWithTag("box:0").performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertFalse(state.scrolledAt(inside))
        assertTrue(state.scrolledAt(outside))
        compose.runOnIdle { runBlocking { state.toTop(animated = false) } }
        assertTrue(state.scrolledAt(inside))
    }

    @Test
    fun uninstalledAppsAreSkipped() {
        tree = AppBox(0, listOf("a", "gone"))
        show()
        compose.onNodeWithTag("app:gone").assertDoesNotExist()
        compose.onNodeWithTag("app:a").assertIsDisplayed()
    }

    @Test
    fun linesAreSnappedSoNeighboursMeetExactly() {
        val left = snapped(BoxArea(0f, 0f, 205.4f, 600f))
        val right = snapped(BoxArea(205.4f, 0f, 410f, 600f))
        assertEquals(left.right, right.left)
        assertEquals(205f, left.right)
    }

    @Test
    fun doneEndsEditing() {
        editing = true
        show()
        compose.onNodeWithText("DONE").performClick()
        assertFalse(editing)
    }

    @Test
    fun aNamedBoxShowsItsNameUppercasedAndPushesIconsDown() {
        tree = AppBox(0, listOf("a"), "money")
        show()
        compose.onNodeWithTag("name-label:0").assertIsDisplayed()
        compose.onNodeWithText("MONEY").assertIsDisplayed()
        val box = compose.onNodeWithTag("box:0").fetchSemanticsNode().boundsInRoot
        val icon = compose.onNodeWithTag("app:a").fetchSemanticsNode().boundsInRoot
        val strip = 16 * 3f // 16 dp at xxhdpi
        assertTrue("icon top ${icon.top - box.top}", icon.top - box.top >= strip - 1)
    }

    @Test
    fun anUnnamedBoxHasNoLabelAndItsIconsStartAtTheTop() {
        show()
        compose.onNodeWithTag("name-label:0").assertDoesNotExist()
        val box = compose.onNodeWithTag("box:0").fetchSemanticsNode().boundsInRoot
        val icon = compose.onNodeWithTag("app:a").fetchSemanticsNode().boundsInRoot
        assertEquals(box.top, icon.top, 1f)
    }

    @Test
    fun aWideBoxIsNamedThroughTheButtonAndTheDialog() {
        editing = true
        show()
        compose.onNodeWithTag("name:0").performClick()
        compose.onNodeWithText("save-work").performClick()
        compose.onNodeWithText("save-work").assertDoesNotExist()
        assertEquals("Work", (tree as AppBox).name)
        compose.onNodeWithTag("name-label:0").assertIsDisplayed()
        // Blank removes it.
        compose.onNodeWithTag("name:0").performClick()
        compose.onNodeWithText("dialog:Work").assertIsDisplayed() // pre-filled with the current name
        compose.onNodeWithText("save-blank").performClick()
        assertEquals("", (tree as AppBox).name)
        compose.onNodeWithTag("name-label:0").assertDoesNotExist()
    }

    @Test
    fun aNarrowBoxIsNamedThroughTheMenu() {
        tree = BoxSplit(true, 0.2f, AppBox(0, listOf("a")), AppBox(1, listOf("b")))
        editing = true
        show()
        compose.onNodeWithTag("name:0").assertDoesNotExist() // inside the closed menu
        compose.onNodeWithTag("menu:0").performClick()
        compose.onNodeWithTag("name:0").performClick()
        compose.onNodeWithText("save-work").performClick()
        assertEquals(listOf("Work", ""), tree.boxes().map { it.name })
    }

    @Test
    fun cancellingTheNameDialogChangesNothing() {
        editing = true
        show()
        compose.onNodeWithTag("name:0").performClick()
        compose.onNodeWithText("cancel").performClick()
        compose.onNodeWithText("cancel").assertDoesNotExist()
        assertEquals("", (tree as AppBox).name)
    }

    @Test
    fun aNameStaysClearOfTheControlsInEditMode() {
        tree = AppBox(0, listOf("a"), "A very long box name that cannot fit")
        editing = true
        show()
        val label = compose.onNodeWithTag("name-label:0").fetchSemanticsNode().boundsInRoot
        val button = compose.onNodeWithTag("name:0").fetchSemanticsNode().boundsInRoot
        assertTrue(label.right <= button.left)
        assertTrue(compose.onNodeWithTag("app:a").fetchSemanticsNode().boundsInRoot.top >= button.bottom)
    }

    @Test
    fun boxesSideBySideStartTheirRowsAtTheSameHeightWhenOnlyOneHasAName() {
        tree = BoxSplit(true, 0.5f, AppBox(0, listOf("a"), "money"), AppBox(1, listOf("b")))
        show()
        val a = compose.onNodeWithTag("app:a").fetchSemanticsNode().boundsInRoot
        val b = compose.onNodeWithTag("app:b").fetchSemanticsNode().boundsInRoot
        assertEquals(a.top, b.top, 1f)
    }

    @Test
    fun draggingTheOuterLineCannotSqueezeABoxBehindTheInnerOne() {
        // A | (B | C): dragging the A line right must stop while B and C each keep 64 dp (seen: a sliver column).
        tree = BoxSplit(true, 0.3f, AppBox(0, listOf("a")), BoxSplit(true, 0.5f, AppBox(1, listOf("b")), AppBox(2, listOf("c"))))
        editing = true
        show()
        compose.onNodeWithTag("divider:").performTouchInput { down(center); moveBy(Offset(2000f, 0f)); up() }
        compose.waitForIdle()
        val b = compose.onNodeWithTag("box:1").fetchSemanticsNode().boundsInRoot
        val c = compose.onNodeWithTag("box:2").fetchSemanticsNode().boundsInRoot
        assertTrue("B ${b.width / 3} dp", b.width >= 64 * 3f - 2f)
        assertTrue("C ${c.width / 3} dp", c.width >= 64 * 3f - 2f)
    }

    @Test
    fun withTheLettersOffTheBoxesKeepOnly16DpEitherSide() {
        stripSide = StripSide.OFF
        tree = AppBox(0, listOf("a"))
        show()
        val box = compose.onNodeWithTag("box:0").fetchSemanticsNode().boundsInRoot
        assertEquals(16 * 3f, box.left, 1f)
        assertEquals((400 - 16) * 3f, box.right, 1f)
    }

    @Test
    fun aSliverSavedBeforeTheFixIsDrawnAtLeast64DpWide() {
        // Saved on the phone before lines respected the boxes behind them: B and C a few dp wide.
        tree = BoxSplit(true, 0.92f, AppBox(0, listOf("a")), BoxSplit(true, 0.5f, AppBox(1, listOf("b")), AppBox(2, listOf("c"))))
        show()
        listOf("box:1", "box:2").forEach {
            val w = compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot.width
            assertTrue("$it ${w / 3} dp", w >= 64 * 3f - 2f)
        }
    }

    @Test
    fun aNamedGridBoxStartsItsIconsSixteenDpDown() {
        tree = AppBox(0, listOf("a"), "money")
        show()
        val box = compose.onNodeWithTag("box:0").fetchSemanticsNode().boundsInRoot
        val icon = compose.onNodeWithTag("app:a").fetchSemanticsNode().boundsInRoot
        assertEquals(16 * 3f, icon.top - box.top, 1f)
        // The name keeps clear of the line above it (it touched the line on the phone).
        val name = compose.onNodeWithTag("name-label:0").fetchSemanticsNode().boundsInRoot
        assertEquals(6 * 3f, name.top - box.top, 1f)
    }

    @Test
    fun aNamedLaneHasItsNameOnALineAboveAndTheRowCentredBelow() {
        // Owner: a name in the row took an icon's place, which is not OK; the earlier line above was right, only the
        // empty band under a lane's row bothered him. 600 dp * 0.15 = 90 dp: name line 16, row centred in the 74 left.
        tree = BoxSplit(false, 0.15f, AppBox(0, listOf("a", "b")), AppBox(1, listOf("c")))
        tree = renameBox(tree, 0, "money")
        show()
        val label = compose.onNodeWithTag("name-label:0").fetchSemanticsNode().boundsInRoot
        val icon = compose.onNodeWithTag("app:a").fetchSemanticsNode().boundsInRoot
        val box = compose.onNodeWithTag("box:0").fetchSemanticsNode().boundsInRoot
        assertTrue("name above the row", label.bottom <= icon.top + 1f)
        assertEquals(12 * 3f, label.left - box.left, 1f)
        assertEquals("first icon at the row start, no name before it", box.left, icon.left, 1f)
        val below = box.top + 16 * 3f
        assertEquals("row centred under the name line", (below + box.bottom) / 2, icon.center.y, 2f)
    }

    @Test
    fun anUnnamedLaneCentresItsRowInTheWholeBox() {
        tree = BoxSplit(false, 0.15f, AppBox(0, listOf("a", "b")), AppBox(1, listOf("c")))
        show()
        val icon = compose.onNodeWithTag("app:a").fetchSemanticsNode().boundsInRoot
        val box = compose.onNodeWithTag("box:0").fetchSemanticsNode().boundsInRoot
        assertEquals(box.center.y, icon.center.y, 2f)
    }

    @Test
    fun thePlusOnAWideBoxOpensTheAddDialogAndApplyingMovesApps() {
        tree = BoxSplit(false, 0.5f, AppBox(0, listOf("a", "b"), "Top"), AppBox(1, listOf("c")))
        editing = true
        show()
        compose.onNodeWithTag("add:0").performClick()
        compose.onNodeWithText("add:Add to Top").assertIsDisplayed()
        compose.onNodeWithText("rows:a@null, b@null, c@another box").assertIsDisplayed()
        compose.onNodeWithText("apply-ac").performClick()
        assertEquals(listOf(0 to listOf("a", "c")), applied)
        assertEquals(listOf(listOf("a", "c"), emptyList<String>()), tree.boxes().map { it.apps })
    }

    @Test
    fun theMenuItemOnANarrowBoxOpensTheAddDialogAndCancelChangesNothing() {
        tree = BoxSplit(true, 0.2f, AppBox(0, listOf("a")), AppBox(1, listOf("b"), "Other"))
        editing = true
        show()
        compose.onNodeWithTag("add:0").assertDoesNotExist() // inside the closed menu
        compose.onNodeWithTag("menu:0").performClick()
        compose.onNodeWithTag("add:0").performClick()
        compose.onNodeWithText("add:Add to this box").assertIsDisplayed()
        compose.onNodeWithText("rows:a@null, b@Other, c@null").assertIsDisplayed()
        compose.onNodeWithText("cancel-add").performClick()
        assertEquals(emptyList<Pair<Int, List<String>>>(), applied)
        assertEquals(listOf(listOf("a"), listOf("b")), tree.boxes().map { it.apps })
    }
}
