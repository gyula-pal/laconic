package cc.gyula.wave.ui.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.createBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.gyula.wave.AppsViewModel
import cc.gyula.wave.core.CELL_HEIGHT_DP
import cc.gyula.wave.core.COLUMNS
import cc.gyula.wave.core.COLUMN_GAP_DP
import cc.gyula.wave.core.cells
import cc.gyula.wave.core.columnRange
import cc.gyula.wave.core.defaultColumns
import cc.gyula.wave.core.defaultHeightDp
import cc.gyula.wave.core.heightRangeDp
import cc.gyula.wave.data.REQUEST_CONFIGURE_WIDGET
import cc.gyula.wave.data.configurationOptional
import cc.gyula.wave.data.size
import cc.gyula.wave.ui.theme.Kumbh
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import cc.gyula.wave.ui.theme.LocalInk

/** Resized heights snap to this. */
private val ResizeStep = 25.dp

private val ColumnGap = COLUMN_GAP_DP.dp

/**
 * One widget page: its widgets in a four-column grid (full width unless resized narrower), then
 * "+ Add widget". Long-press a widget to resize or remove it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WidgetPage(vm: AppsViewModel, page: Int, ids: List<Int>, onResizing: (Boolean) -> Unit) {
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }

    // Bound: open the widget's own setup screen if it has one, else place it.
    fun bound(id: Int) {
        val info = vm.widgets.info(id)
        if (info == null) {
            vm.widgets.pending = null
            vm.widgets.host.deleteAppWidgetId(id)
        } else if (info.configure != null && !info.configurationOptional()) {
            try {
                vm.widgets.host.startAppWidgetConfigureActivityForResult(
                    context as Activity, id, 0, REQUEST_CONFIGURE_WIDGET, null,
                )
            } catch (e: ActivityNotFoundException) {
                vm.widgets.configured(false)
            }
        } else {
            vm.widgets.pending = null
            vm.widgets.add(page, id)
        }
    }

    // Android's one-time "Allow Laconic to create widgets?" prompt.
    val askBind = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val (_, id) = vm.widgets.pending ?: return@rememberLauncherForActivityResult
        if (result.resultCode == Activity.RESULT_OK) bound(id)
        else {
            vm.widgets.pending = null
            vm.widgets.host.deleteAppWidgetId(id)
        }
    }

    fun add(info: AppWidgetProviderInfo) {
        picking = false
        val id = vm.widgets.host.allocateAppWidgetId()
        vm.widgets.pending = page to id
        if (vm.widgets.bind(id, info)) return bound(id)
        try {
            askBind.launch(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
            )
        } catch (e: ActivityNotFoundException) {
            vm.widgets.pending = null
            vm.widgets.host.deleteAppWidgetId(id)
            Toast.makeText(context, "Can't add widgets on this phone", Toast.LENGTH_SHORT).show()
        }
    }

    val heights by vm.widgets.heights.collectAsStateWithLifecycle()
    val widths by vm.widgets.widths.collectAsStateWithLifecycle()
    var resizing by remember { mutableStateOf<Int?>(null) }
    // While resizing, sideways drags belong to the handles, not to turning the page.
    LaunchedEffect(resizing) { onResizing(resizing != null) }
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState(), enabled = resizing == null)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        // A four-column grid: a widget narrower than the page shares its row with the next one.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Whole pixels, so two half-width widgets plus the gap never round past the page and wrap.
            val density = LocalDensity.current
            val gapPx = with(density) { ColumnGap.roundToPx() }
            val column = with(density) { ((constraints.maxWidth - gapPx * (COLUMNS - 1)) / COLUMNS).toDp() }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(ColumnGap),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ids.forEach { id ->
                    key(id) {
                        WidgetCell(
                            vm, id, heights[id], widths[id], column,
                            resizing = resizing == id,
                            onResize = { resizing = if (it) id else null },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            if (resizing != null) "Done" else "+  Add widget",
            color = LocalInk.current.copy(alpha = 0.8f),
            fontFamily = Kumbh,
            fontSize = 17.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable { if (resizing != null) resizing = null else picking = true }
                .padding(16.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
    if (picking) WidgetPicker(vm, onPick = ::add, onDismiss = { picking = false })
}

@Composable
private fun WidgetCell(
    vm: AppsViewModel,
    id: Int,
    savedHeight: Int?,
    savedColumns: Int?,
    column: Dp,
    resizing: Boolean,
    onResize: (Boolean) -> Unit,
) {
    val info = remember(id) { vm.widgets.info(id) }
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val range = remember(info) { info?.size()?.heightRangeDp(density.density)?.let { it.start.dp..it.endInclusive.dp } }
    val columns = remember(info, column) { info?.size()?.columnRange(density.density, column.value) }
    fun widthOf(n: Int) = column * n + ColumnGap * (n - 1)
    val natural = remember(info, column) { info?.size()?.defaultColumns(density.density, column.value) ?: COLUMNS }
    val width = widthOf((savedColumns ?: natural).let { n -> columns?.let { n.coerceIn(it) } ?: natural })
    var dragWidth by remember { mutableFloatStateOf(-1f) }
    val shownWidth = if (dragWidth >= 0f) dragWidth.dp else width
    val height = when {
        info == null -> 56.dp
        savedHeight != null -> savedHeight.dp
        else -> info.size().defaultHeightDp(density.density).dp
    }
    // While resizing: the height following the finger, before it snaps.
    var dragHeight by remember { mutableFloatStateOf(-1f) }
    val shown = if (dragHeight >= 0f) dragHeight.dp else height
    Box(
        Modifier
            .width(shownWidth)
            .height(shown)
            // Watches before the widget does: a still press held past the long-press time opens the menu,
            // and the rest of that touch is swallowed so the widget sees it cancelled, not tapped.
            .pointerInput(id, resizing) {
                if (resizing) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val change = awaitPointerEvent(PointerEventPass.Initial).changes
                                .firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                        }
                    }
                    if (released != null) return@awaitEachGesture
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuOpen = true
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                        if (event.changes.none { it.pressed }) break
                    }
                }
            },
    ) {
        if (info == null) {
            Text("Widget no longer available — long-press to remove", color = LocalInk.current.copy(alpha = 0.6f), fontSize = 14.sp)
        } else {
            // Remembered next to the view it speaks for: a view made afresh has been told nothing yet.
            val told = remember(id) { LastSize() }
            AndroidView(
                factory = { ctx -> vm.widgets.host.createView(ctx, id, info).apply { setPadding(0, 0, 0, 0) } },
                // Tells the widget its size, so it can pick the layout that fits: the saved size, not the one following
                // the finger, so only once a drag settles, and only when it changed.
                update = { view ->
                    if (told.changed(DpSize(width, height))) {
                        if (Build.VERSION.SDK_INT >= 31) {
                            view.updateAppWidgetSize(Bundle(), listOf(SizeF(width.value, height.value)))
                        } else {
                            val w = width.value.toInt()
                            val h = height.value.toInt()
                            @Suppress("DEPRECATION")
                            view.updateAppWidgetSize(null, w, h, w, h)
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (resizing && columns != null) {
            // Drag the handle on the right edge; the width snaps to quarters of the page.
            Box(
                Modifier
                    // Inside the widget's edge (a touch outside its bounds never reaches it), which on a
                    // full-width widget is inside Android's Back-swipe zone: keep Back off just this handle,
                    // only while resizing.
                    .align(Alignment.CenterEnd)
                    .size(36.dp, 72.dp)
                    .systemGestureExclusion()
                    .pointerInput(id, column) {
                        val minW = widthOf(columns.first).value
                        val maxW = widthOf(columns.last).value
                        detectHorizontalDragGestures(
                            onDragStart = { dragWidth = shownWidth.value },
                            onDragEnd = {
                                val n = ((dragWidth + ColumnGap.value) / (column.value + ColumnGap.value)).roundToInt()
                                vm.widgets.setWidth(id, n.coerceIn(columns))
                                dragWidth = -1f
                            },
                            onDragCancel = { dragWidth = -1f },
                        ) { change, dx ->
                            change.consume()
                            dragWidth = (dragWidth + dx / density.density).coerceIn(minW, maxW)
                        }
                    },
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(Modifier.padding(end = 6.dp).size(5.dp, 40.dp).background(LocalInk.current, RoundedCornerShape(3.dp)))
            }
        }
        if (resizing) {
            Box(Modifier.fillMaxSize().border(1.5.dp, LocalInk.current.copy(alpha = 0.7f), RoundedCornerShape(16.dp)))
        }
        if (resizing && range != null) {
            // Drag the handle on the bottom edge; the height snaps to 25 dp steps within the widget's limits.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .size(72.dp, 36.dp)
                    .pointerInput(id) {
                        detectVerticalDragGestures(
                            onDragStart = { dragHeight = shown.value },
                            onDragEnd = {
                                val snapped = (dragHeight / ResizeStep.value).roundToInt() * ResizeStep.value
                                vm.widgets.setHeight(id, snapped.coerceIn(range.start.value, range.endInclusive.value).roundToInt())
                                dragHeight = -1f
                            },
                            onDragCancel = { dragHeight = -1f },
                        ) { change, dy ->
                            change.consume()
                            dragHeight = (dragHeight + dy / density.density)
                                .coerceIn(range.start.value, range.endInclusive.value)
                        }
                    },
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(Modifier.padding(bottom = 6.dp).size(40.dp, 5.dp).background(LocalInk.current, RoundedCornerShape(3.dp)))
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (range != null || columns != null) {
                DropdownMenuItem(text = { Text("Resize") }, onClick = { menuOpen = false; onResize(true) })
            }
            if (savedHeight != null || (savedColumns != null && savedColumns != natural)) {
                DropdownMenuItem(text = { Text("Original size") }, onClick = {
                    menuOpen = false
                    vm.widgets.setHeight(id, null)
                    vm.widgets.setWidth(id, null)
                })
            }
            DropdownMenuItem(text = { Text("Remove widget") }, onClick = { menuOpen = false; onResize(false); vm.widgets.remove(id) })
        }
    }
}

/**
 * The size a widget's app was last told. Telling it is a call into Android and a new size a broadcast to that
 * app, while the cell recomposes for every frame of a height drag or a menu opening.
 */
internal class LastSize {
    private var last: DpSize? = null

    /** True when [size] differs from the last one, which it then becomes: worth telling the app. */
    fun changed(size: DpSize): Boolean = (size != last).also { last = size }
}

/** In [label] order, each label looked up once: `sortedBy` would look both up again on every comparison. */
internal fun <T> List<T>.sortedByLabel(label: (T) -> String): List<T> =
    map { it to label(it) }.sortedBy { it.second }.map { it.first }

private class PickerApp(val label: String, val icon: ImageBitmap?, val widgets: List<AppWidgetProviderInfo>)

/** Every widget on the phone, grouped by app, each with its preview. Tap one to add it. */
@Composable
private fun WidgetPicker(vm: AppsViewModel, onPick: (AppWidgetProviderInfo) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val apps by produceState<List<PickerApp>?>(null) {
        val entries = vm.catalog.apps.value
        value = withContext(Dispatchers.Default) {
            vm.widgets.providers()
                .groupBy { it.provider.packageName to it.profile }
                .map { (key, widgets) ->
                    val entry = entries.firstOrNull { it.component.packageName == key.first && it.user == key.second }
                    val label = entry?.label ?: runCatching {
                        context.packageManager.getApplicationLabel(
                            context.packageManager.getApplicationInfo(key.first, 0)
                        ).toString()
                    }.getOrDefault(key.first)
                    PickerApp(
                        label,
                        entry?.let { vm.catalog.icon(it) },
                        widgets.sortedByLabel { it.loadLabel(context.packageManager) },
                    )
                }
                .sortedBy { it.label.lowercase() }
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color(0xFF101010))) {
            val list = apps
            if (list == null) {
                Text("Loading widgets…", color = Color.White.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                    item {
                        Text(
                            "Add a widget",
                            color = Color.White,
                            fontFamily = Kumbh,
                            fontSize = 22.sp,
                            modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp),
                        )
                    }
                    list.forEach { app ->
                        item(key = "app:${app.label}:${app.widgets.first().provider.flattenToString()}") {
                            Row(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                app.icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(28.dp)) }
                                Spacer(Modifier.width(12.dp))
                                Text(app.label, color = Color.White, fontFamily = Kumbh, fontSize = 18.sp)
                            }
                        }
                        items(app.widgets, key = { it.provider.flattenToString() + it.profile.hashCode() }) { info ->
                            PickerCard(vm, info, onClick = { onPick(info) })
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun PickerCard(vm: AppsViewModel, info: AppWidgetProviderInfo, onClick: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val (spanX, spanY) = remember(info) { info.size().cells(density) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E1E1E))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Box(Modifier.fillMaxWidth().heightIn(max = 220.dp), contentAlignment = Alignment.Center) {
            WidgetPreview(vm, info, spanX, spanY)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                info.loadLabel(context.packageManager),
                color = Color.White,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("$spanX×$spanY", color = Color.White.copy(alpha = 0.55f), fontSize = 14.sp)
        }
    }
}

/**
 * What the widget will look like: Android 15's generated preview, else its preview layout (12+), else its
 * preview image, else its icon. Layout previews are drawn at full size and scaled down to fit the card.
 */
@Composable
private fun WidgetPreview(vm: AppsViewModel, info: AppWidgetProviderInfo, spanX: Int, spanY: Int) {
    val context = LocalContext.current
    val remote = remember(info) { previewViews(vm, context, info) }
    if (remote != null) {
        val screenWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
        val cellW = (screenWidth - 32.dp) / 4
        val w = cellW * spanX.coerceAtMost(4)
        val h = CELL_HEIGHT_DP.dp * spanY
        val scale = minOf(1f, 280.dp / w, 200.dp / h)
        Box(Modifier.size(w * scale, h * scale)) {
            AndroidView(
                factory = { ctx ->
                    runCatching { remote.apply(ctx, FrameLayout(ctx)) }.getOrElse { View(ctx) }
                },
                modifier = Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .requiredSize(w, h)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    },
            )
        }
        return
    }
    val maxPx = with(LocalDensity.current) { 280.dp.roundToPx() }
    val resources = LocalResources.current
    val image by produceState<ImageBitmap?>(null, info) {
        value = withContext(Dispatchers.Default) {
            val dpi = resources.displayMetrics.densityDpi
            val drawable = runCatching { info.loadPreviewImage(context, dpi) ?: info.loadIcon(context, dpi) }.getOrNull()
                ?: return@withContext null
            val w = drawable.intrinsicWidth.coerceAtLeast(1)
            val h = drawable.intrinsicHeight.coerceAtLeast(1)
            val scale = minOf(1f, maxPx.toFloat() / maxOf(w, h))
            val bitmap = createBitmap((w * scale).roundToInt().coerceAtLeast(1), (h * scale).roundToInt().coerceAtLeast(1))
            drawable.setBounds(0, 0, bitmap.width, bitmap.height)
            drawable.draw(Canvas(bitmap))
            bitmap.asImageBitmap()
        }
    }
    image?.let { Image(it, contentDescription = null) } ?: Spacer(Modifier.height(80.dp))
}

private fun previewViews(vm: AppsViewModel, context: Context, info: AppWidgetProviderInfo): RemoteViews? {
    if (Build.VERSION.SDK_INT >= 35) {
        runCatching { vm.widgets.generatedPreview(info) }.getOrNull()?.let { return it }
    }
    if (Build.VERSION.SDK_INT >= 31 && info.previewLayout != 0) {
        return RemoteViews(info.provider.packageName, info.previewLayout)
    }
    return null
}
