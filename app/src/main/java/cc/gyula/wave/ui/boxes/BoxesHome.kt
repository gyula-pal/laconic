package cc.gyula.wave.ui.boxes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.gyula.wave.AppsViewModel
import cc.gyula.wave.core.AppBox
import cc.gyula.wave.core.BoxArea
import cc.gyula.wave.core.BoxSplit
import cc.gyula.wave.core.ratioRange
import cc.gyula.wave.core.splitAt
import cc.gyula.wave.core.BoxLines
import cc.gyula.wave.core.BoxDivider
import cc.gyula.wave.core.BoxTree
import cc.gyula.wave.core.StripSide
import cc.gyula.wave.core.boxes
import cc.gyula.wave.core.dividers
import cc.gyula.wave.core.fitted
import cc.gyula.wave.core.layoutBoxes
import cc.gyula.wave.core.moveApp
import cc.gyula.wave.core.removeBox
import cc.gyula.wave.core.renameBox
import cc.gyula.wave.core.splitBox
import cc.gyula.wave.core.withRatio
import cc.gyula.wave.data.AppEntry
import cc.gyula.wave.ui.apps.LocalNotified
import cc.gyula.wave.ui.apps.LocalRowInset
import cc.gyula.wave.ui.apps.NameDialog
import cc.gyula.wave.ui.fadeScrollEdges
import cc.gyula.wave.ui.home.CornerApp
import cc.gyula.wave.ui.theme.Josefin
import cc.gyula.wave.ui.theme.Label
import cc.gyula.wave.ui.theme.phase
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import cc.gyula.wave.ui.theme.LocalInk
import cc.gyula.wave.ui.theme.onInk
import cc.gyula.wave.ui.theme.inkFilter

private val IconSize = 36.dp
private val GhostDisc = 64.dp // the soft disc behind a carried app
// How far a carried app floats from the finger: up, and towards the thumb's free side (left for a right thumb,
// mirrored with the strip on the left), as in Vogel & Baudisch's "Shift" (CHI 2007) for finger occlusion.
// 72 dp up felt like too much on the phone; half of it clears the fingertip.
private val GhostLift = 36.dp
private val GhostSkew = 20.dp
private val CellSize = 56.dp
private val MinBox = 64.dp
private val GrabWidth = 24.dp
private val ControlSize = 40.dp
private val ControlsInset = 12.dp

/** The prompt for a box's name: its current name, dismiss, and save. A parameter so tests can stand in for it. */
internal typealias NameDialogContent = @Composable (current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) -> Unit

/**
 * The real prompt. Robolectric cannot settle a text field inside a dialog window (Compose never reports idle),
 * so the tests use a stand-in and this one is checked on the device.
 */
private val DefaultNameDialog: NameDialogContent = { current, onDismiss, onSave ->
    NameDialog(
        title = "Name box",
        initial = current,
        placeholder = "Name",
        hint = if (current.isNotEmpty()) "Empty: no name" else null,
        onDismiss = onDismiss,
        onSave = onSave,
    )
}

/** The strip a named grid box keeps free at its top in normal mode, for the name. A lane carries its name inline. */
private val NameStripHeight = 16.dp
private val NameTop = 6.dp // the name's distance below the box's top line

/** A box's name: the clock band's date style, smaller and dimmer. */
private val BoxNameStyle: TextStyle
    @Composable get() {
        val ink = LocalInk.current
        return remember(ink) {
            TextStyle(
                fontFamily = Josefin, color = ink.copy(alpha = 0.55f), fontSize = 11.5.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Normal,
            )
        }
    }

/** The strip an edit-mode box keeps free at its top for the controls: they and 4 dp of air, so icons start below. */
private val HeaderHeight = ControlsInset + ControlSize + 4.dp

/** An icon being dragged: which, and where the finger is in root coordinates. */
internal class DragState {
    var key by mutableStateOf<String?>(null)
    var at by mutableStateOf(Offset.Zero)
}

/**
 * Where a dropped app lands in a box: the place, among the box's other icons ([cells], root rects in box order),
 * of the first one on screen that comes after [at] in reading order — a cell is before [at] if its row is above,
 * or it is in the same row and left of it. Icons scrolled out of the box report an empty rect and keep their
 * place: counting them as "before" put every drop one slot later per hidden icon (seen on the phone).
 */
internal fun dropIndex(cells: List<Rect>, at: Offset): Int {
    fun before(r: Rect) = r.bottom <= at.y || (at.y >= r.top && r.center.x < at.x)
    val after = cells.indexOfFirst { !it.isEmpty && !before(it) }
    return if (after >= 0) after else cells.indexOfLast { !it.isEmpty } + 1
}

/**
 * Where each box is on screen and how far it has scrolled. The home panel asks it whether a pull-down starts
 * on a box that is at its top (a box that has scrolled keeps the pull for itself), and resets every box to
 * its top when home comes back.
 */
internal class BoxesState {
    private val rects = HashMap<Int, Rect>()
    private val scrolls = HashMap<Int, ScrollState>()

    fun place(id: Int, rect: Rect) { rects[id] = rect }
    private val lanes = HashSet<Int>() // boxes that scroll sideways

    fun attach(id: Int, scroll: ScrollState, sideways: Boolean = false) {
        scrolls[id] = scroll
        if (sideways) lanes += id else lanes -= id
    }
    fun detach(id: Int) { rects.remove(id); scrolls.remove(id); lanes -= id }

    fun rectOf(id: Int): Rect? = rects[id]

    /** The box under [rootPos], if any. */
    fun boxAt(rootPos: Offset): Int? = rects.entries.firstOrNull { it.value.contains(rootPos) }?.key

    /** True when [rootPos] is not on a box, or is on one that is scrolled to its top. */
    fun scrolledAt(rootPos: Offset): Boolean {
        val id = rects.entries.firstOrNull { it.value.contains(rootPos) }?.key ?: return true
        // A lane scrolls sideways, so it never has a pull-down of its own to keep.
        return id in lanes || (scrolls[id]?.value ?: 0) == 0
    }

    suspend fun toTop(animated: Boolean) = coroutineScope {
        scrolls.values.forEach { s -> launch { if (animated) s.animateScrollTo(0) else s.scrollTo(0) } }
    }
}

/** The boxes layout for home, wired to the app's ViewModel; [BoxesContent] does the drawing. */
@Composable
internal fun BoxesHome(
    vm: AppsViewModel,
    apps: Map<String, AppEntry>,
    hovered: String?,
    rows: MutableMap<String, Rect>,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onBackground: () -> Unit,
    state: BoxesState,
    stripSide: StripSide,
    enter: () -> Float,
    modifier: Modifier = Modifier,
) {
    val tree by vm.boxes.collectAsStateWithLifecycle()
    val hidden by vm.settings.hidden.flow.collectAsStateWithLifecycle()
    val pickable = remember(apps, hidden) { apps.values.filter { it.key !in hidden }.map { CornerApp(it.key, it.label) } }
    val notified = LocalNotified.current
    BoxesContent(
        tree,
        onChange = vm::updateBoxes,
        pickable = pickable,
        onSetBoxApps = vm::setBoxApps,
        label = { apps[it]?.label },
        cachedIcon = { key -> apps[key]?.let(vm.catalog::cachedIcon) },
        loadIcon = { key -> apps[key]?.let { vm.catalog.icon(it) } },
        notified = { key -> apps[key]?.component?.packageName in notified },
        onLaunch = { key -> apps[key]?.let(vm.catalog::launch) },
        hovered = hovered,
        rows = rows,
        editing = editing,
        onEditingChange = onEditingChange,
        onBackground = onBackground,
        state = state,
        stripSide = stripSide,
        lineLook = vm.settings.boxLines.flow.collectAsStateWithLifecycle().value,
        enter = enter,
        modifier = modifier,
    )
}

/**
 * The boxes: each a scrolling grid of icons, with 1 px lines between them and none on the outer edge.
 * [label] is null for an app that is no longer installed, which is then skipped. Edit mode adds the split and
 * remove buttons, thicker lines to drag, and tap-to-move for apps.
 */
@Composable
internal fun BoxesContent(
    tree: BoxTree,
    onChange: ((BoxTree) -> BoxTree) -> Unit,
    label: (String) -> String?,
    cachedIcon: (String) -> ImageBitmap?,
    loadIcon: suspend (String) -> ImageBitmap?,
    notified: (String) -> Boolean,
    onLaunch: (String) -> Unit,
    hovered: String?,
    rows: MutableMap<String, Rect>,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onBackground: () -> Unit,
    state: BoxesState,
    stripSide: StripSide,
    enter: () -> Float,
    modifier: Modifier = Modifier,
    nameDialog: NameDialogContent = DefaultNameDialog,
    pickable: List<CornerApp> = emptyList(),
    onSetBoxApps: (boxId: Int, keys: List<String>) -> Unit = { _, _ -> },
    addDialog: AddAppsDialogContent = DefaultAddAppsDialog,
    lineLook: BoxLines = BoxLines.FAINT,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val inset = LocalRowInset.current
    val other = 16.dp
    // The letter strip's side keeps its margin; the other side, or both with the letters off, only 16 dp.
    val left = if (stripSide == StripSide.RIGHT || stripSide == StripSide.OFF) other else inset
    val right = if (stripSide == StripSide.LEFT || stripSide == StripSide.OFF) other else inset
    var selected by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(editing) { if (!editing) selected = null }
    val drag = remember { DragState() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    // Dividers being dragged, by path: the box edges follow the finger and are saved on release.
    val live = remember { mutableStateMapOf<List<Boolean>, Float>() }
    LaunchedEffect(tree) { live.clear() }
    val shown = live.entries.fold(tree) { t, (path, ratio) -> withRatio(t, path, ratio, 0f) }

    /** Root position of the top of the drop line, and its height, for [key] carried to [at]; null off every box. */
    fun dropMarker(key: String, at: Offset): Pair<Offset, Float>? {
        val target = shown.boxes().firstOrNull { it.id == state.boxAt(at) } ?: return null
        val cells = target.apps.filter { it != key && label(it) != null }.map { rows[it] ?: Rect.Zero }
        val i = dropIndex(cells, at)
        val before = cells.getOrNull(i)?.takeIf { !it.isEmpty }
        val after = cells.take(i).lastOrNull { !it.isEmpty }
        return when {
            before != null -> Offset(before.left, before.top) to before.height
            after != null -> Offset(after.right, after.top) to after.height
            else -> state.rectOf(target.id)?.let { Offset(it.left + 12f, it.top + 12f) to it.height.coerceAtMost(150f) }
        }
    }

    val background by rememberUpdatedState(onBackground)
    Box(
        modifier
            .onGloballyPositioned { origin = it.positionInRoot() }
            .graphicsLayer { alpha = phase(enter(), 0.45f, 0.3f) }
            // The margins beside the boxes, out to the screen edge: a tap there ends editing, a long-press does
            // nothing. A thumb reaching for the letter strip lands here, and edit mode kept opening (measured on the
            // phone: holding 0–19 dp from the edge or just inside the boxes' inset edited). Only a hold on a box
            // edits. Taken here, a long-press never reaches the home screen behind, which would open settings.
            .pointerInput(Unit) {
                detectTapGestures(onTap = { background() }, onLongPress = { })
            }
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().absolutePadding(left = left, right = right)) {
            val whole = BoxArea(0f, 0f, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
            val minPx = with(density) { MinBox.toPx() }
            // Drawn with every box at least 64 dp, whatever was saved: older layouts can hold a sliver.
            val laid = fitted(shown, whole, minPx)
            val boxes = layoutBoxes(laid, whole).map { (box, area) -> box to snapped(area) }
            val lines = dividers(laid, whole)
            // Edit mode draws them thick and bright to show they can be dragged; otherwise the chosen look.
            val lineColor = LocalInk.current.copy(alpha = if (editing) 0.5f else lineLook.alpha)
            val thick = if (editing) with(density) { 3.dp.toPx() }.roundToInt().coerceAtLeast(1) else lineLook.px

            fun moveHere(boxId: Int) {
                selected?.let { key -> onChange { moveApp(it, key, boxId) } }
                selected = null
            }
            fun tapIcon(boxId: Int, key: String) {
                if (!editing) return onLaunch(key)
                val picked = selected
                when {
                    picked == null -> selected = key
                    picked == key -> selected = null
                    else -> moveHere(boxId)
                }
            }

            fun drop(key: String, at: Offset) {
                val target = boxes.firstOrNull { it.first.id == state.boxAt(at) }?.first ?: return
                val all = target.apps.filter { it != key }
                val shown = all.filter { label(it) != null }
                val before = dropIndex(shown.map { rows[it] ?: Rect.Zero }, at)
                // Counted among the icons on screen; moveApp counts every key in the box, including apps that are
                // not installed right now (a paused work profile), so turn it into a place in the whole list.
                val index = if (before < shown.size) all.indexOf(shown[before]) else all.size
                onChange { moveApp(it, key, target.id, index) }
            }

            val over = drag.key?.let { state.boxAt(drag.at) }
            // One name in the layout and every box keeps the name line, so rows in boxes side by side start at the
            // same height: a named box alone pushed its icons 16 dp below its neighbours' (seen on the phone).
            val nameLine = boxes.any { it.first.name.isNotEmpty() }
            boxes.forEach { (box, area) ->
                key(box.id) {
                    BoxCell(
                        box, area, nameLine, canRemove = boxes.size > 1, editing = editing, selected = selected,
                        label, cachedIcon, loadIcon, notified, hovered, rows, state,
                        drag = drag, highlight = over == box.id,
                        onIcon = { tapIcon(box.id, it) },
                        onEmpty = {
                            // In edit mode a tap on bare box does nothing, unless an app is picked up to move here.
                            if (!editing) onBackground() else if (selected != null) moveHere(box.id)
                        },
                        onGrab = { selected = null; if (!editing) onEditingChange(true) },
                        onDrop = ::drop,
                        onEdit = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onEditingChange(true) },
                        onSplit = { sideBySide -> onChange { splitBox(it, box.id, sideBySide) } },
                        onRemove = { onChange { removeBox(it, box.id) } },
                        onName = { name -> onChange { renameBox(it, box.id, name) } },
                        nameDialog = nameDialog,
                        addRequest = { AddAppsRequest(
                            "Add to ${box.name.ifEmpty { "this box" }}", box.apps, pickRows(pickable, shown, box.id),
                        ) },
                        onSetApps = { onSetBoxApps(box.id, it) },
                        addDialog = addDialog,
                    )
                }
            }

            // The lines, snapped to whole pixels so they are all the same crispness.
            Box(
                Modifier.fillMaxSize().drawBehind {
                    lines.forEach { d ->
                        val a = snapped(d.line)
                        if (d.sideBySide) {
                            val x = (a.left + 0.5f - thick / 2f).roundToInt().toFloat()
                            drawRect(lineColor, Offset(x, a.top), Size(thick.toFloat(), a.bottom - a.top))
                        } else {
                            val y = (a.top + 0.5f - thick / 2f).roundToInt().toFloat()
                            drawRect(lineColor, Offset(a.left, y), Size(a.right - a.left, thick.toFloat()))
                        }
                    }
                }
            )
            if (editing) lines.forEach { d ->
                key(d.path) {
                    DividerHandle(d, minPx, live, split = { splitAt(laid, d.path) }) { path, ratio ->
                        onChange { withRatio(it, path, ratio, 0f) }
                    }
                }
            }
        }
        drag.key?.let { dragged ->
            val ink = LocalInk.current
            // Where it will land: a thin upright line before the icon it would go in front of, in the box under
            // the finger. The finger, not the floating icon, decides the place.
            dropMarker(dragged, drag.at)?.let { (top, height) ->
                val w = with(density) { 2.dp.toPx() }
                Box(
                    Modifier
                        .offset { IntOffset((top.x - origin.x - w / 2).roundToInt(), (top.y - origin.y).roundToInt()) }
                        .size(with(density) { w.toDp() }, with(density) { height.toDp() })
                        .background(ink.copy(alpha = 0.8f))
                        .testTag("drop-marker")
                )
            }
            // The app being carried floats above the finger, bigger, on a soft disc: right under the fingertip a
            // 36 dp icon is hidden, so it seemed to jump to its new place on release.
            val icon by produceState(cachedIcon(dragged), dragged) { value = loadIcon(dragged) }
            val disc = GhostDisc
            val half = with(density) { disc.toPx() / 2 }
            val lift = with(density) { GhostLift.toPx() }
            val skew = with(density) { GhostSkew.toPx() } * if (stripSide == StripSide.LEFT) 1 else -1
            Box(
                Modifier
                    .offset { IntOffset((drag.at.x - origin.x + skew - half).roundToInt(), (drag.at.y - origin.y - lift - half).roundToInt()) }
                    .size(disc)
                    .background(ink.copy(alpha = 0.14f), CircleShape)
                    .testTag("drag-ghost"),
                contentAlignment = Alignment.Center,
            ) {
                icon?.let { Image(it, contentDescription = null, colorFilter = inkFilter(), modifier = Modifier.size(IconSize * 1.3f)) }
            }
        }
        if (editing) {
            Text(
                "DONE",
                // Trimmed to the letters and without the label shadow: Josefin's line box has more room below than
                // above, which left the word sitting low in the pill.
                style = Label.copy(
                    fontFamily = Josefin,
                    letterSpacing = 3.sp,
                    shadow = null,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                ),
                fontSize = 15.sp,
                color = onInk,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .background(LocalInk.current, RoundedCornerShape(50))
                    .clickable { onEditingChange(false) }
                    // Josefin's capitals sit high even in a trimmed line box (measured on the phone: 27 px above the
                    // letters, 36 below), so the word is centred by measurement: a little more padding on top.
                    .padding(start = 20.dp, end = 20.dp, top = 11.7.dp, bottom = 8.3.dp),
            )
        }
    }
}

/** [area] with its edges on whole pixels. Neighbours compute the same edge, so they still meet exactly. */
internal fun snapped(area: BoxArea) = BoxArea(
    area.left.roundToInt().toFloat(), area.top.roundToInt().toFloat(),
    area.right.roundToInt().toFloat(), area.bottom.roundToInt().toFloat(),
)

@Composable
private fun BoxCell(
    box: AppBox,
    area: BoxArea,
    nameLine: Boolean,
    canRemove: Boolean,
    editing: Boolean,
    selected: String?,
    label: (String) -> String?,
    cachedIcon: (String) -> ImageBitmap?,
    loadIcon: suspend (String) -> ImageBitmap?,
    notified: (String) -> Boolean,
    hovered: String?,
    rows: MutableMap<String, Rect>,
    state: BoxesState,
    drag: DragState,
    highlight: Boolean,
    onIcon: (String) -> Unit,
    onEmpty: () -> Unit,
    onGrab: () -> Unit,
    onDrop: (String, Offset) -> Unit,
    onEdit: () -> Unit,
    onSplit: (sideBySide: Boolean) -> Unit,
    onRemove: () -> Unit,
    onName: (String) -> Unit,
    nameDialog: NameDialogContent,
    addRequest: () -> AddAppsRequest,
    onSetApps: (List<String>) -> Unit,
    addDialog: AddAppsDialogContent,
) {
    val density = LocalDensity.current
    var naming by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val boxHeight = with(density) { (area.bottom - area.top).toDp() }
    // A lane: a box only tall enough for one row of icons (judged at its normal size, without the edit header,
    // so it doesn't turn over when edit mode starts) lays them out in one row that scrolls sideways. The name
    // strip counts only for a grid: a lane keeps its name inside the row.
    val lane = boxHeight - (if (nameLine) NameStripHeight else 0.dp) < CellSize * 2
    DisposableEffect(box.id, scroll, lane) {
        state.attach(box.id, scroll, sideways = lane)
        onDispose { state.detach(box.id) }
    }
    // The box's actions behind one ⋯ button: for narrow boxes, and inline at the start of a short lane.
    val BoxMenu: @Composable (Modifier) -> Unit = { modifier ->
        var open by remember { mutableStateOf(false) }
        Box(modifier) {
            ControlButton("menu:${box.id}", "Box actions", Glyph.DOTS) { open = true }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(
                    text = { Text("Add apps") },
                    leadingIcon = { GlyphIcon(Glyph.PLUS, LocalContentColor.current) },
                    onClick = { open = false; adding = true },
                    modifier = Modifier.testTag("add:${box.id}"),
                )
                DropdownMenuItem(
                    text = { Text("Name") },
                    leadingIcon = { GlyphIcon(Glyph.NAME, LocalContentColor.current) },
                    onClick = { open = false; naming = true },
                    modifier = Modifier.testTag("name:${box.id}"),
                )
                DropdownMenuItem(
                    text = { Text("Split side by side") },
                    leadingIcon = { GlyphIcon(Glyph.SIDE_BY_SIDE, LocalContentColor.current) },
                    onClick = { open = false; onSplit(true) },
                    modifier = Modifier.testTag("split-side:${box.id}"),
                )
                DropdownMenuItem(
                    text = { Text("Split one above the other") },
                    leadingIcon = { GlyphIcon(Glyph.STACKED, LocalContentColor.current) },
                    onClick = { open = false; onSplit(false) },
                    modifier = Modifier.testTag("split-stack:${box.id}"),
                )
                if (canRemove) DropdownMenuItem(
                    text = { Text("Remove box") },
                    leadingIcon = { GlyphIcon(Glyph.CLOSE, LocalContentColor.current) },
                    onClick = { open = false; onRemove() },
                    modifier = Modifier.testTag("remove:${box.id}"),
                )
            }
        }
    }
    val emptyTap by rememberUpdatedState(onEmpty)
    val editTap by rememberUpdatedState(onEdit)
    val widthPx = area.right - area.left
    val columns = floor(widthPx / with(density) { CellSize.toPx() }).toInt().coerceAtLeast(1)
    // A lane too short for a header row gets the one ⋯ button whatever its width: five buttons laid over a single
    // row of icons covered most of it (seen on the phone).
    val laneWithoutHeader = lane && boxHeight < HeaderHeight + CellSize
    val wide = !laneWithoutHeader && widthPx >= with(density) { (ControlSize * 5 + 12.dp).toPx() }
    Box(
        Modifier
            .offset { IntOffset(area.left.roundToInt(), area.top.roundToInt()) }
            .size(
                with(density) { widthPx.toDp() },
                with(density) { (area.bottom - area.top).toDp() },
            )
            .testTag("box:${box.id}")
            .background(if (highlight) LocalInk.current.copy(alpha = 0.06f) else Color.Transparent)
            .onGloballyPositioned { state.place(box.id, it.boundsInRoot()) }
            // Outside the scroll, so the grid and the icons get the touch first; this takes what is left.
            .pointerInput(box.id) {
                // The latest callbacks, not the first ones: this gesture starts before edit mode exists and lives on
                // through it, so a captured first onEmpty would still think edit mode is off.
                detectTapGestures(onTap = { emptyTap() }, onLongPress = { editTap() })
            }
            .fadeScrollEdges(scroll, sideways = lane)
            .then(if (lane) Modifier.horizontalScroll(scroll) else Modifier.verticalScroll(scroll))
    ) {
        val shownApps = box.apps.filter { label(it) != null }
        if (lane) {
            // The name has its own line above, as in every box (drawn over the box, see below), and the row sits
            // centred in the height left under it, not pinned to the top with an empty band below.
            // Edit mode: the header goes above the row when the lane is tall enough for both; otherwise the ⋯
            // button leads the row, so nothing is laid over its icons.
            val top = when {
                editing && !laneWithoutHeader -> HeaderHeight
                nameLine -> NameStripHeight
                else -> 0.dp
            }
            Column(Modifier.fillMaxHeight()) {
                Spacer(Modifier.height(top))
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    if (editing && laneWithoutHeader) BoxMenu(Modifier.padding(start = 4.dp))
                    shownApps.forEach { key ->
                        key(key) {
                            IconCell(
                                key, label(key).orEmpty(), key == selected, key == hovered, notified(key),
                                cachedIcon, loadIcon, rows, drag, onIcon = { onIcon(key) }, onGrab = onGrab, onDrop = { onDrop(key, it) },
                                modifier = Modifier.width(CellSize),
                            )
                        }
                    }
                }
            }
        } else Column(Modifier.fillMaxWidth()) {
            // Normal mode: a strip for the name, only when there is one. Edit mode: the header holds it.
            if (editing) Spacer(Modifier.height(HeaderHeight))
            else if (nameLine) Spacer(Modifier.height(NameStripHeight))
            shownApps.chunked(columns).forEach { rowKeys ->
                Row(Modifier.fillMaxWidth()) {
                    rowKeys.forEach { key ->
                        key(key) {
                            IconCell(
                                key, label(key).orEmpty(), key == selected, key == hovered, notified(key),
                                cachedIcon, loadIcon, rows, drag, onIcon = { onIcon(key) }, onGrab = onGrab, onDrop = { onDrop(key, it) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    repeat(columns - rowKeys.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (editing) Spacer(Modifier.height(64.dp))
        }
    }
    if (box.name.isNotEmpty()) {
        // On top of the box rather than in its scroll, so it stays in the corner; in edit mode it sits in the
        // header, left of the controls, which reach in from the right (a short lane has no header: its line).
        val inHeader = editing && !laneWithoutHeader
        val controls = if (inHeader) ControlSize * (if (wide) 5 else 1) + ControlsInset * 2 else 0.dp
        Text(
            box.name.uppercase(),
            style = BoxNameStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .offset { IntOffset(area.left.roundToInt(), area.top.roundToInt()) }
                // Out of the header, the name sits 6 dp below the box's top line: centred in its 16 dp line it touched
                // the line above (seen on the phone). It may reach a little into the first row's cells, whose icons
                // start 10 dp down, so it gains clearance without the line growing.
                .padding(start = 12.dp, top = if (inHeader) ControlsInset + (ControlSize - 14.dp) / 2 else NameTop)
                .width((with(density) { widthPx.toDp() } - 12.dp - controls).coerceAtLeast(0.dp))
                .testTag("name-label:${box.id}"),
        )
    }
    if (naming) nameDialog(box.name, { naming = false }, { naming = false; onName(it) })
    if (adding) addDialog(addRequest(), cachedIcon, loadIcon, { adding = false }, { adding = false; onSetApps(it) })
    if (editing && !laneWithoutHeader) {
        // On top of the box rather than in its scroll, so they stay in the corner. Kept off the box's own
        // edges, where the line handles sit.
        Box(
            Modifier
                .offset { IntOffset(area.left.roundToInt(), area.top.roundToInt()) }
                .size(with(density) { widthPx.toDp() }, with(density) { (area.bottom - area.top).toDp() })
                .clipToBounds()
        ) {
            val place = Modifier.align(Alignment.TopEnd).padding(top = ControlsInset, end = ControlsInset)
            if (wide) {
                Row(place) {
                    ControlButton("add:${box.id}", "Add apps", Glyph.PLUS) { adding = true }
                    ControlButton("name:${box.id}", "Name box", Glyph.NAME) { naming = true }
                    ControlButton("split-side:${box.id}", "Split side by side", Glyph.SIDE_BY_SIDE) { onSplit(true) }
                    ControlButton("split-stack:${box.id}", "Split one above the other", Glyph.STACKED) { onSplit(false) }
                    if (canRemove) ControlButton("remove:${box.id}", "Remove box", Glyph.CLOSE) { onRemove() }
                }
            } else {
                // Too narrow for the row of buttons: one, opening the same actions as a menu.
                BoxMenu(place)
            }
        }
    }
}

private enum class Glyph { PLUS, NAME, SIDE_BY_SIDE, STACKED, CLOSE, DOTS }

@Composable
private fun ControlButton(tag: String, description: String, glyph: Glyph, onClick: () -> Unit) {
    Box(
        Modifier
            .size(ControlSize)
            .testTag(tag)
            .semantics { contentDescription = description; role = Role.Button }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, LocalInk.current.copy(alpha = 0.7f))
    }
}

/** One of the box action glyphs, drawn: on the buttons, and leading the same actions in the ⋯ menu. */
@Composable
private fun GlyphIcon(glyph: Glyph, color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val stroke = Stroke(width = 1.5.dp.toPx())
        when (glyph) {
            Glyph.SIDE_BY_SIDE -> {
                square(color, stroke)
                drawLine(color, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), stroke.width)
            }
            Glyph.STACKED -> {
                square(color, stroke)
                drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), stroke.width)
            }
            Glyph.NAME -> {
                // A text cursor: an I-beam, drawn rather than typeset so it needs no font in the Canvas.
                val x = size.width / 2
                val serif = size.width * 0.2f
                drawLine(color, Offset(x, 0f), Offset(x, size.height), stroke.width)
                drawLine(color, Offset(x - serif, 0f), Offset(x + serif, 0f), stroke.width)
                drawLine(color, Offset(x - serif, size.height), Offset(x + serif, size.height), stroke.width)
            }
            Glyph.PLUS -> {
                drawLine(color, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), stroke.width)
                drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), stroke.width)
            }
            Glyph.DOTS -> {
                val r = 1.75.dp.toPx()
                listOf(0.15f, 0.5f, 0.85f).forEach { drawCircle(color, r, Offset(size.width * it, size.height / 2)) }
            }
            Glyph.CLOSE -> {
                val m = size.width * 0.15f
                drawLine(color, Offset(m, m), Offset(size.width - m, size.height - m), stroke.width)
                drawLine(color, Offset(size.width - m, m), Offset(m, size.height - m), stroke.width)
            }
        }
    }
}

private fun DrawScope.square(color: Color, stroke: Stroke) =
    drawRect(color, Offset.Zero, size, style = stroke)

/** What a finger on an icon turned out to be. */
private enum class Touch { TAP, LONG_PRESS, GAVE_UP }

@Composable
private fun IconCell(
    key: String,
    label: String,
    selected: Boolean,
    hovered: Boolean,
    dot: Boolean,
    cachedIcon: (String) -> ImageBitmap?,
    loadIcon: suspend (String) -> ImageBitmap?,
    rows: MutableMap<String, Rect>,
    drag: DragState,
    onIcon: () -> Unit,
    onGrab: () -> Unit,
    onDrop: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val slop = LocalViewConfiguration.current.touchSlop
    val longPress = LocalViewConfiguration.current.longPressTimeoutMillis
    val icon by produceState(cachedIcon(key), key) { value = loadIcon(key) }
    DisposableEffect(key) { onDispose { rows.remove(key) } }
    val tap by rememberUpdatedState(onIcon)
    val grab by rememberUpdatedState(onGrab)
    val drop by rememberUpdatedState(onDrop)
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Box(
        modifier
            .height(CellSize)
            .onGloballyPositioned { rows[key] = it.boundsInRoot(); coords = it }
            .graphicsLayer {
                val scale = if (hovered) 1.15f else 1f
                scaleX = scale; scaleY = scale
                alpha = if (drag.key == key) 0.3f else 1f
            }
            .testTag("app:$key")
            .semantics { contentDescription = label; role = Role.Button }
            // One gesture for tap, long-press and drag. A finger that moves before the long-press is a scroll:
            // it is left alone, so the box scrolls.
            .pointerInput(key) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val outcome = withTimeoutOrNull(longPress) {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                ?: return@withTimeoutOrNull Touch.GAVE_UP
                            if (!change.pressed) { change.consume(); return@withTimeoutOrNull Touch.TAP }
                            if ((change.position - down.position).getDistance() > slop) return@withTimeoutOrNull Touch.GAVE_UP
                        }
                    } ?: Touch.LONG_PRESS
                    when (outcome) {
                        Touch.TAP -> tap()
                        Touch.GAVE_UP -> Unit
                        Touch.LONG_PRESS -> {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            fun root(p: Offset) = coords?.takeIf { it.isAttached }?.localToRoot(p) ?: (rows[key]?.topLeft ?: Offset.Zero) + p
                            grab()
                            drag.key = key
                            drag.at = root(down.position)
                            var dropped = false
                            try {
                                while (true) {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                    change.consume()
                                    drag.at = root(change.position)
                                    if (!change.pressed) { dropped = true; break }
                                }
                            } finally {
                                val at = drag.at
                                drag.key = null
                                if (dropped) drop(at)
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(IconSize + 8.dp)
                .then(if (selected) Modifier.border(2.dp, LocalInk.current, RoundedCornerShape(10.dp)) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(IconSize)) {
                icon?.let { Image(it, contentDescription = null, colorFilter = inkFilter(), modifier = Modifier.size(IconSize)) }
                if (dot) Box(Modifier.align(Alignment.TopEnd).size(6.dp).background(LocalInk.current, CircleShape))
            }
        }
    }
}

/**
 * The strip along a line that drags it. It takes the touch on contact: otherwise holding it before moving
 * reads as a long-press on the home screen behind and opens settings.
 */
@Composable
private fun DividerHandle(
    divider: BoxDivider,
    minPx: Float,
    live: MutableMap<List<Boolean>, Float>,
    split: () -> BoxSplit?,
    onCommit: (path: List<Boolean>, ratio: Float) -> Unit,
) {
    val density = LocalDensity.current
    val latest by rememberUpdatedState(divider)
    val latestSplit by rememberUpdatedState(split)
    val grab = with(density) { GrabWidth.toPx() }
    val line = snapped(divider.line)
    val modifier = if (divider.sideBySide) {
        Modifier
            .offset { IntOffset((line.left - grab / 2).roundToInt(), line.top.roundToInt()) }
            .size(GrabWidth, with(density) { (line.bottom - line.top).toDp() })
    } else {
        Modifier
            .offset { IntOffset(line.left.roundToInt(), (line.top - grab / 2).roundToInt()) }
            .size(with(density) { (line.right - line.left).toDp() }, GrabWidth)
    }
    Box(
        modifier
            .testTag("divider:${divider.path.joinToString("") { if (it) "1" else "0" }}")
            .pointerInput(divider.path) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val d = latest
                    val start = if (d.sideBySide) d.parent.left else d.parent.top
                    val extent = if (d.sideBySide) d.parent.right - d.parent.left else d.parent.bottom - d.parent.top
                    var ratio = ((if (d.sideBySide) d.line.left else d.line.top) - start) / extent
                    // Worked out per drag, from the area as it is now (the player appearing or a parent line moving
                    // changes it): every box on each side keeps its 64 dp, not just the two next to this line.
                    val range = latestSplit()?.let { ratioRange(it, extent, minPx) }
                        ?: (minPx / extent).coerceAtMost(0.5f).let { it..(1 - it) }
                    var moved = false
                    var saved = false
                    try {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val delta = change.positionChange().let { if (d.sideBySide) it.x else it.y }
                            if (delta != 0f) {
                                ratio = (ratio + delta / extent).coerceIn(range)
                                live[d.path] = ratio
                                moved = true
                            }
                            change.consume()
                        }
                        if (moved) { onCommit(d.path, ratio); saved = true }
                    } finally {
                        // A drag cut off midway (another finger, the screen turning off) saves nothing: put the
                        // line back rather than leave it drawn where the finger was.
                        if (!saved) live.remove(d.path)
                    }
                }
            }
    )
}
