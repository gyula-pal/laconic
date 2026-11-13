package cc.gyula.wave.ui.home

import android.app.Activity
import android.content.ContextWrapper
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import cc.gyula.wave.AppsViewModel
import cc.gyula.wave.MediaListener
import cc.gyula.wave.core.ClockItem
import cc.gyula.wave.core.CornerAction
import cc.gyula.wave.core.HomeLayout
import cc.gyula.wave.core.HomeList
import cc.gyula.wave.core.StripSide
import cc.gyula.wave.core.homeApps
import cc.gyula.wave.data.AppEntry
import cc.gyula.wave.data.size
import cc.gyula.wave.ui.apps.AppRow
import cc.gyula.wave.ui.apps.LocalLeftHanded
import cc.gyula.wave.ui.apps.LocalNotified
import cc.gyula.wave.ui.apps.LocalRowInset
import cc.gyula.wave.ui.apps.LocalShowIcons
import cc.gyula.wave.ui.apps.MirroredIfLeftHanded
import cc.gyula.wave.ui.apps.RowHeight
import cc.gyula.wave.ui.boxes.BoxesHome
import cc.gyula.wave.ui.boxes.BoxesState
import cc.gyula.wave.ui.clock.Clock
import cc.gyula.wave.ui.fadeScrollEdges
import cc.gyula.wave.ui.letters.AlphabetScroller
import cc.gyula.wave.ui.letters.FAVORITES
import cc.gyula.wave.ui.music.MusicPlayer
import cc.gyula.wave.ui.music.PlayerSlot
import cc.gyula.wave.ui.music.rememberMediaAccess
import cc.gyula.wave.ui.music.rememberNowPlaying
import cc.gyula.wave.ui.search.SearchPanel
import cc.gyula.wave.ui.settings.SettingsSheet
import cc.gyula.wave.ui.theme.Josefin
import cc.gyula.wave.ui.theme.Label
import cc.gyula.wave.ui.theme.phase
import cc.gyula.wave.ui.widgets.WidgetPage
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import cc.gyula.wave.ui.theme.LocalInk
import cc.gyula.wave.ui.theme.LocalBackdrop
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background

/** Home, with any widget pages beside it. Swiping sideways works wherever nothing else takes the swipe. */
@Composable
fun HomeScreen(vm: AppsViewModel, homePresses: Int) {
    val ink by vm.settings.ink.flow.collectAsStateWithLifecycle()
    val backdrop by vm.settings.background.flow.collectAsStateWithLifecycle()
    CompositionLocalProvider(
        LocalInk provides (ink?.let(::Color) ?: Color.White),
        LocalBackdrop provides backdrop?.let(::Color),
    ) {
        SystemBarIcons(light = backdrop?.let { Color(it).luminance() > 0.5f } == true)
        // A chosen colour fills the screen behind every page; without one the wallpaper shows through.
        Box(Modifier.fillMaxSize().then(backdrop?.let { Modifier.background(Color(it)) } ?: Modifier)) {
            Pages(vm, homePresses)
        }
    }
}

/** The status and navigation bar icons turn dark over a light background colour, and stay light otherwise. */
@Composable
private fun SystemBarIcons(light: Boolean) {
    val view = LocalView.current
    SideEffect {
        val window = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }
}

@Composable
private fun Pages(vm: AppsViewModel, homePresses: Int) {
    val widgetPages by vm.widgets.pages.collectAsStateWithLifecycle()
    if (widgetPages.pages.isEmpty()) return HomeView(vm, homePresses, onBusy = {})
    val homeIndex = widgetPages.left
    key(widgetPages.pages.size, widgetPages.left) {
        val pager = rememberPagerState(initialPage = homeIndex) { widgetPages.pages.size + 1 }
        val scope = rememberCoroutineScope()
        var homeBusy by remember { mutableStateOf(false) } // a letter list, search or edit mode is open
        var widgetBusy by remember { mutableStateOf(false) } // a widget is being resized
        LaunchedEffect(homePresses) { if (pager.currentPage != homeIndex) pager.animateScrollToPage(homeIndex) }
        BackHandler(enabled = pager.currentPage != homeIndex) { scope.launch { pager.animateScrollToPage(homeIndex) } }
        HorizontalPager(
            pager,
            userScrollEnabled = (!homeBusy || pager.currentPage != homeIndex) && !widgetBusy,
            beyondViewportPageCount = 1,
        ) { i ->
            if (i == homeIndex) {
                HomeView(vm, homePresses, onBusy = { homeBusy = it })
            } else {
                // Widget pages are numbered in screen order, skipping home.
                val page = if (i < homeIndex) i else i - 1
                WidgetPage(vm, page, widgetPages.pages[page], onResizing = { widgetBusy = it })
            }
        }
    }
}

@Composable
private fun HomeView(vm: AppsViewModel, homePresses: Int, onBusy: (Boolean) -> Unit) {
    val apps by vm.catalog.apps.collectAsStateWithLifecycle()
    val favoriteKeys by vm.settings.pins.flow.collectAsStateWithLifecycle()
    val hidden by vm.settings.hidden.flow.collectAsStateWithLifecycle()
    val homeList by vm.settings.homeList.flow.collectAsStateWithLifecycle()
    val homeAz by vm.settings.homeAz.flow.collectAsStateWithLifecycle()
    val byKey = remember(apps) { apps.associateBy { it.key } }
    val visible = remember(apps, hidden) { apps.filter { it.key !in hidden } }
    val favorites = remember(byKey, favoriteKeys) { favoriteKeys.mapNotNull(byKey::get) }
    // Most used / recent: looked up again each time home comes back. Null = no usage access yet.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val used by produceState<List<String>?>(emptyList(), homeList, lifecycle) {
        if (homeList == HomeList.PINNED) return@produceState
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            value = withContext(Dispatchers.IO) { vm.usage.used(homeList) }
        }
    }
    val homeApps = remember(homeList, homeAz, favorites, visible, used) {
        homeApps(homeList, homeAz, favorites, visible, used, AppEntry::packageName, AppEntry::isPersonal, AppEntry::label)
    }
    val sections = remember(visible) { visible.groupBy { it.section } }
    val letters = remember(sections) {
        buildList {
            add(FAVORITES)
            addAll(sections.keys.filter { it != '#' })
            if ('#' in sections) add('#')
        }
    }

    // null = home view (clock + pinned apps); otherwise the letter whose apps are showing.
    var section by remember { mutableStateOf<Char?>(null) }
    var lastSection by remember { mutableStateOf('A') } // keeps the panel filled while it fades out
    var anchorY by remember { mutableFloatStateOf(0f) }
    var hovered by remember { mutableStateOf<String?>(null) }
    // Row bounds in root coordinates, per panel — the strip gesture hit-tests these to slide-launch.
    val homeRows = remember { HashMap<String, Rect>() }
    val sectionRows = remember { HashMap<String, Rect>() }
    val view = LocalView.current

    fun rowAt(pos: Offset): String? =
        (if (section == null) homeRows else sectionRows).entries.firstOrNull { it.value.contains(pos) }?.key

    val stripSide by vm.settings.stripSide.flow.collectAsStateWithLifecycle()
    // Either edge: the side last touched, so the letter lists open mirrored away from that thumb.
    var touchedLeft by remember { mutableStateOf(false) }
    val leftHanded = stripSide == StripSide.LEFT // the home view; it doesn't flip with the hand in use
    val listsLeft = if (stripSide == StripSide.EITHER) touchedLeft else leftHanded
    val pullSearch by vm.settings.pullSearch.flow.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) } // pinned list showing drag handles
    var searching by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) } // the one settings menu, under the clock
    val openCard = remember { mutableStateOf<String?>(null) } // app whose shortcuts card is open

    fun closeAll() { section = null; editing = false; searching = false; openCard.value = null }
    val busy = section != null || editing || searching || openCard.value != null
    LaunchedEffect(busy) { onBusy(busy) }
    LaunchedEffect(homePresses) { closeAll() }
    BackHandler(enabled = section != null || editing || searching || openCard.value != null) {
        if (openCard.value != null) openCard.value = null else closeAll()
    }

    val dots by vm.settings.dots.flow.collectAsStateWithLifecycle()
    val leftCorner by vm.settings.leftCorner.flow.collectAsStateWithLifecycle()
    val rightCorner by vm.settings.rightCorner.flow.collectAsStateWithLifecycle()
    // The room kept for the buttons, and the strip lifted above them, are there while either one is.
    val corners = leftCorner != CornerAction.Off || rightCorner != CornerAction.Off
    val notified by MediaListener.notified.collectAsStateWithLifecycle()
    CompositionLocalProvider(
        LocalLeftHanded provides leftHanded,
        LocalRowInset provides if (stripSide == StripSide.EITHER) 64.dp else 48.dp,
        LocalShowIcons provides vm.settings.showIcons.flow.collectAsStateWithLifecycle().value,
        LocalNotified provides if (dots) notified else emptySet(),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { if (openCard.value != null) openCard.value = null else closeAll() },
                        onLongPress = { closeAll(); settingsOpen = true },
                    )
                }
        ) {
            AnimatedVisibility(section == null && !searching, enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
                HomePanel(
                    vm, homeApps, byKey, hovered, homeRows,
                    // Reordering and swipe-to-unpin only make sense for pins in your own order.
                    editable = homeList == HomeList.PINNED && !homeAz,
                    needsUsageAccess = homeList != HomeList.PINNED && used == null,
                    favoriteKeys = favoriteKeys,
                    editing = editing, onEditingChange = { editing = it }, openCard = openCard,
                    onPullDown = { openCard.value = null; if (pullSearch) searching = true else vm.system.openNotifications() },
                    settingsOpen = settingsOpen, onSettingsOpen = { settingsOpen = it },
                    homePresses = homePresses, corners = corners,
                )
            }
            AnimatedVisibility(section != null, enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
                CompositionLocalProvider(LocalLeftHanded provides listsLeft) {
                SectionPanel(
                    vm, lastSection, sections[lastSection].orEmpty(), { anchorY }, hovered,
                    favoriteKeys, sectionRows, openCard,
                )
                }
            }
            if (searching) SearchPanel(vm, apps, favoriteKeys, openCard, onClose = { searching = false })
            AnimatedVisibility(
                corners && section == null && !searching && !editing,
                Modifier.fillMaxSize(),
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150)),
            ) { CornerButtons(vm) }
            // Swipe up from the empty band at the bottom, between the corner buttons and above Android's home
            // line, opens search with the keyboard. Starting there it can't be mistaken for scrolling the list.
            if (section == null && !searching && !editing) {
                SwipeUpZone(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = CornerButtonsTop - 16.dp)
                        .fillMaxWidth()
                        .height(56.dp),
                    onSwipeUp = { openCard.value = null; searching = true },
                )
            }

            // Sized in dp like the clock: a large system font size would overflow the 19dp letter slots.
            val density = LocalDensity.current
            // The strip starts just inside the system Back-swipe zone, so a swipe in from the very edge stays
            // Back and the strip still gets every touch that lands on it.
            val backZone = WindowInsets.systemGestures
            val ltr = LocalLayoutDirection.current
            @Composable
            fun Strip(onLeft: Boolean, hidden: Boolean, readAloud: Boolean = true) {
                val edge = with(density) {
                    (if (onLeft) backZone.getLeft(this, ltr) else backZone.getRight(this, ltr)).toDp()
                }.coerceAtLeast(7.dp)
                AlphabetScroller(
                letters = letters,
                onLeft = onLeft,
                hidden = hidden,
                edgeGap = edge,
                // Hidden: narrow enough to stay clear of the icons, which then sit 64 dp in.
                stripWidth = if (hidden) 30.dp else 36.dp,
                modifier = Modifier
                    .align(if (onLeft) Alignment.BottomStart else Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    // Above the flashlight and camera buttons, with a little air, when they're on.
                    .padding(start = if (onLeft) edge else 0.dp, end = if (onLeft) 0.dp else edge, bottom = if (corners) 68.dp else 56.dp)
                    .then(if (readAloud) Modifier else Modifier.clearAndSetSemantics {}),
                onLetter = { letter, y ->
                    touchedLeft = onLeft
                    openCard.value = null
                    section = letter.takeIf { it != FAVORITES }
                    if (letter != FAVORITES) lastSection = letter
                    anchorY = y
                    hovered = null
                },
                onDrag = { pos, offStrip ->
                    val key = if (offStrip) rowAt(pos) else null
                    if (key != hovered) {
                        hovered = key
                        if (key != null) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                },
                onRelease = { pos, offStrip ->
                    val key = if (offStrip) rowAt(pos) else null
                    hovered = null
                    key?.let(byKey::get)?.let {
                        vm.catalog.launch(it)
                        section = null
                    }
                },
                )
            }
            if (!editing && !searching) CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
                when (stripSide) {
                    StripSide.RIGHT -> Strip(onLeft = false, hidden = false)
                    StripSide.LEFT -> Strip(onLeft = true, hidden = false)
                    StripSide.OFF -> Unit // no letters: apps by search, swipe up from the bottom
                    StripSide.EITHER -> {
                        Strip(onLeft = false, hidden = true)
                        Strip(onLeft = true, hidden = true, readAloud = false) // a screen reader needs the letters once
                    }
                }
            }
        }
    }
}

@Composable
private fun HomePanel(
    vm: AppsViewModel,
    homeApps: List<AppEntry>,
    byKey: Map<String, AppEntry>,
    hovered: String?,
    rows: MutableMap<String, Rect>,
    editable: Boolean,
    needsUsageAccess: Boolean,
    favoriteKeys: List<String>,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    openCard: MutableState<String?>,
    onPullDown: () -> Unit,
    settingsOpen: Boolean,
    onSettingsOpen: (Boolean) -> Unit,
    homePresses: Int,
    corners: Boolean,
) {
    val clockItems by vm.settings.clockItems.flow.collectAsStateWithLifecycle()
    val enter = rememberEnterAnimation(vm)
    val mediaAccess = rememberMediaAccess()
    val playing = rememberNowPlaying(ClockItem.MUSIC in clockItems && mediaAccess)
    val scroll = rememberScrollState()
    val boxesLayout = vm.settings.homeLayout.flow.collectAsStateWithLifecycle().value == HomeLayout.BOXES
    val stripSide by vm.settings.stripSide.flow.collectAsStateWithLifecycle()
    val boxes = remember { BoxesState() }
    var panelOrigin by remember { mutableStateOf(Offset.Zero) } // where the pull-down detector's own space starts
    // Home always comes back at the top of the list: from an app, from recents, or with the screen turning
    // on. Done on start, before the first frame, so a long list doesn't visibly jump.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { scroll.scrollTo(0); boxes.toTop(animated = false) } }
    // Home pressed while already home: glide up.
    LaunchedEffect(homePresses) { scroll.animateScrollTo(0); boxes.toTop(animated = true) }
    val pullPx = with(LocalDensity.current) { 72.dp.toPx() }
    val onPull by rememberUpdatedState(onPullDown)
    val pullEnabled by rememberUpdatedState(!editing)
    val boxesOn by rememberUpdatedState(boxesLayout)
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            // Pulling down while the list is at its top opens search. This only watches the finger
            // (Initial pass, nothing consumed), so taps, swipes and scrolling below work as before.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    // The boxes scroll on their own: a pull only counts if the box under the finger is at its top.
                    val atTop = if (boxesOn) boxes.scrolledAt(down.position + panelOrigin) else scroll.value == 0
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes
                            .firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val d = change.position - down.position
                        if (atTop && pullEnabled && d.y > pullPx && d.y > abs(d.x) * 1.5f) {
                            onPull()
                            break
                        }
                    }
                }
            }
            .onGloballyPositioned { panelOrigin = it.positionInRoot() }
    ) {
        val topGap = maxHeight * 0.15f
        // With widget pages beside home, the pinned apps scroll in a shorter window of their own and the
        // space under it stays empty, for swiping between pages. Otherwise the whole home view scrolls.
        val windowed = vm.widgets.pages.collectAsStateWithLifecycle().value.pages.isNotEmpty()
        val swipeRoom = maxHeight * 0.11f
        // With the corner buttons on, the scrolling stops above them, so no app row sits between the two circles.
        // The column already stops at the navigation bar, so only the rest of the buttons' height is added.
        val navBar = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
        val cornerRoom = if (corners && !windowed) (CornerButtonsTop - navBar + 8.dp).coerceAtLeast(0.dp) else 0.dp
        Column(
            Modifier
                .fillMaxSize()
                .padding(bottom = cornerRoom)
                .then(if (windowed || boxesLayout) Modifier else Modifier.fadeScrollEdges(scroll).verticalScroll(scroll))
        ) {
            Spacer(Modifier.height(topGap))
            // The clock is a drawn graphic sized to its band: a large system font size would only break it.
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
                Clock(vm, enter, onLongPress = { onSettingsOpen(true) })
                SettingsSheet(vm, mediaAccess, visible = settingsOpen, onVisible = onSettingsOpen)
            }
            Spacer(Modifier.height(28.dp))
            PlayerSlot(
                playing,
                app = { it.controller.packageName },
                isPlaying = { it.playing },
                dismissal = vm.dismissal,
                onStop = { it.controller.transportControls.stop() },
            ) { np, onClose ->
                MusicPlayer(
                    np,
                    colorArt = vm.settings.colorArt.flow.collectAsStateWithLifecycle().value,
                    onOpen = { vm.system.openPlayer(np) },
                    onClose = onClose,
                )
            }
            val pinned = @Composable {
                MirroredIfLeftHanded {
                    Column {
                        if (needsUsageAccess) {
                            Text(
                                "TAP TO ALLOW USAGE ACCESS",
                                style = Label.copy(fontFamily = Josefin, letterSpacing = 3.sp),
                                fontSize = 13.5.sp,
                                modifier = Modifier
                                    .padding(start = LocalRowInset.current)
                                    .clickable { vm.system.openUsageAccessSettings() }
                                    .padding(vertical = 16.dp),
                            )
                        }
                        PinnedList(vm, homeApps, hovered, rows, editable, favoriteKeys, editing, onEditingChange, openCard, enter)
                    }
                }
            }
            if (boxesLayout) {
                // Fills what is left under the player; the boxes scroll inside themselves, not the column.
                BoxesHome(
                    vm, byKey, hovered, rows, editing, onEditingChange,
                    onBackground = { openCard.value = null; onEditingChange(false) },
                    state = boxes, stripSide = stripSide, enter = enter,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.height(if (windowed) swipeRoom else 24.dp))
            } else if (windowed) {
                Box(Modifier.weight(1f, fill = false).fadeScrollEdges(scroll).verticalScroll(scroll)) { pinned() }
                Spacer(Modifier.height(swipeRoom))
            } else {
                pinned()
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Length of the coming-home animation; each part runs in its own slice of it, see [phase]. */
private const val ENTER_MS = 900

/**
 * 0 → 1 each time the home screen comes back (from an app, recents, or the screen turning on), read only
 * while drawing, so it costs no recomposition. Stays at 1 with animations off.
 */
@Composable
private fun rememberEnterAnimation(vm: AppsViewModel): () -> Float {
    val enabled by vm.settings.animations.flow.collectAsStateWithLifecycle()
    val progress = remember { Animatable(1f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(enabled, lifecycle) {
        if (!enabled) return@LaunchedEffect progress.snapTo(1f)
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(ENTER_MS, easing = LinearEasing))
        }
    }
    return { progress.value }
}

/**
 * The pinned apps. Long-press one to edit: a handle appears on every row — drag it to reorder —
 * and a row swiped right-to-left is unpinned. Done / Back / Home / a tap on empty space ends it.
 */
@Composable
private fun PinnedList(
    vm: AppsViewModel,
    favorites: List<AppEntry>,
    hovered: String?,
    rows: MutableMap<String, Rect>,
    editable: Boolean,
    favoriteKeys: List<String>,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    openCard: MutableState<String?>,
    enter: () -> Float,
) {
    val rowPx = with(LocalDensity.current) { RowHeight.toPx() }
    // Working copy reordered live under the finger; saved once on release, re-synced from the saved order.
    val order = remember(favorites) { favorites.toMutableStateList() }
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val view = LocalView.current

    order.forEachIndexed { i, entry ->
        key(entry.key) {
            val dragging = entry.key == dragKey
            AppRow(
                vm, entry, hovered == entry.key, isFavorite = entry.key in favoriteKeys, rows,
                modifier = Modifier
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer {
                        // Coming home, rows drop in one after another, top to bottom, once the day is out.
                        val p = phase(enter(), 0.45f + 0.04f * i.coerceAtMost(8), 0.23f) // the 9th row ends right at 1
                        alpha = p
                        translationY = (if (dragging) dragY else 0f) - (1f - p) * 16.dp.toPx()
                        scaleX = if (dragging) 1.03f else 1f
                        scaleY = scaleX
                    },
                editing = editing,
                openCard = openCard,
                onLongPress = if (editable) ({ openCard.value = null; onEditingChange(true) }) else null,
                onSwipeAway = if (editable) ({ vm.togglePin(entry) }) else null,
                handle = if (!editable) null else Modifier.pointerInput(entry.key) {
                    awaitEachGesture {
                        // Claim the touch on contact: otherwise holding the handle before moving reads as a
                        // long-press on the home screen behind it and opens the wallpaper picker.
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        dragKey = entry.key
                        dragY = 0f
                        try {
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                dragY += change.positionChange().y
                                change.consume()
                                val i = order.indexOfFirst { it.key == entry.key }
                                // Swap with the neighbour once the row is dragged past half of it.
                                if (dragY > rowPx / 2 && i < order.lastIndex) {
                                    order.add(i + 1, order.removeAt(i)); dragY -= rowPx
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                } else if (dragY < -rowPx / 2 && i > 0) {
                                    order.add(i - 1, order.removeAt(i)); dragY += rowPx
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            }
                        } finally {
                            dragKey = null
                            dragY = 0f
                            vm.setPinOrder(order.map { it.key })
                        }
                    }
                },
            )
        }
    }
    if (editing) {
        Text(
            "DONE",
            style = Label.copy(fontFamily = Josefin, letterSpacing = 3.sp),
            fontSize = 15.sp,
            modifier = Modifier
                .padding(start = LocalRowInset.current, top = 12.dp)
                .clickable { onEditingChange(false) }
                .padding(vertical = 12.dp),
        )
    }
}

/** One letter's apps, centred on the picked letter's height so the thumb slides straight across. */
@Composable
private fun SectionPanel(
    vm: AppsViewModel,
    letter: Char,
    apps: List<AppEntry>,
    anchorY: () -> Float,
    hovered: String?,
    favoriteKeys: List<String>,
    rows: MutableMap<String, Rect>,
    openCard: MutableState<String?>,
) {
    var originY by remember { mutableFloatStateOf(0f) }
    Layout(
        content = {
            MirroredIfLeftHanded {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    letter.toString(),
                    style = Label,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 52.dp, bottom = 12.dp),
                )
                // Keyed, so a row's swipe and icon state follow its app when the letter changes, not its position.
                apps.forEach { key(it.key) { AppRow(vm, it, hovered == it.key, it.key in favoriteKeys, rows, openCard = openCard) } }
            }
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .onGloballyPositioned { originY = it.positionInRoot().y },
    ) { measurables, constraints ->
        val panel = measurables.first().measure(constraints.copy(minHeight = 0))
        layout(constraints.maxWidth, constraints.maxHeight) {
            val y = (anchorY() - originY - panel.height / 2f).roundToInt()
                .coerceIn(0, (constraints.maxHeight - panel.height).coerceAtLeast(0))
            panel.place(0, y)
        }
    }
}

/**
 * Watches for a swipe up that starts here, without taking taps or anything else from what lies below
 * (with the corner buttons off, the end of the list or the lowest boxes reach into this band).
 */
@Composable
internal fun SwipeUpZone(modifier: Modifier, onSwipeUp: () -> Unit) {
    Box(modifier.clearAndSetSemantics {}.then(SwipeUpElement(onSwipeUp)))
}

/**
 * Compose gives a touch to only the top one of two overlapping siblings, whether or not it consumes it; this
 * node shares it ([sharePointerInputWithSiblings]), so the list or box under the band still gets its taps.
 */
private class SwipeUpElement(val onSwipeUp: () -> Unit) : ModifierNodeElement<SwipeUpNode>() {
    override fun create() = SwipeUpNode(onSwipeUp)
    override fun update(node: SwipeUpNode) { node.onSwipeUp = onSwipeUp }
    override fun equals(other: Any?) = other is SwipeUpElement && other.onSwipeUp === onSwipeUp
    override fun hashCode() = onSwipeUp.hashCode()
}

private class SwipeUpNode(var onSwipeUp: () -> Unit) : DelegatingNode(), PointerInputModifierNode {
    private val input = delegate(SuspendingPointerInputModifierNode {
        val trigger = 48.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes
                    .firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val d = change.position - down.position
                if (-d.y > trigger && -d.y > abs(d.x) * 1.5f) {
                    change.consume()
                    onSwipeUp()
                    break
                }
            }
        }
    })

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) =
        input.onPointerEvent(pointerEvent, pass, bounds)

    override fun onCancelPointerInput() = input.onCancelPointerInput()

    override fun sharePointerInputWithSiblings() = true
}
