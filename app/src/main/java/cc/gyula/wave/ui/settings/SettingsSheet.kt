package cc.gyula.wave.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.gyula.wave.AppsViewModel
import cc.gyula.wave.core.ClockItem
import cc.gyula.wave.core.CornerAction
import cc.gyula.wave.core.HomeLayout
import cc.gyula.wave.core.StripSide
import cc.gyula.wave.core.formatHex
import cc.gyula.wave.core.isHardToRead
import cc.gyula.wave.core.matchRank
import cc.gyula.wave.core.pageSides
import cc.gyula.wave.data.cycle
import cc.gyula.wave.data.fetchPrice
import cc.gyula.wave.data.size
import cc.gyula.wave.data.toggle
import cc.gyula.wave.ui.clock.Clock
import cc.gyula.wave.ui.home.CornerApp
import cc.gyula.wave.ui.home.CornerChooser
import cc.gyula.wave.ui.theme.Josefin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * All of Laconic's settings in one sheet: long-press the clock or any empty spot. It rises halfway, so the
 * clock stays in view while it changes, and stays open while toggling, so several items can be switched
 * in one go. Always composed, so a dialog opened from it outlives the sheet closing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSheet(vm: AppsViewModel, mediaAccess: Boolean, visible: Boolean, onVisible: (Boolean) -> Unit) {
    val context = LocalContext.current
    val s = vm.settings
    val items by s.clockItems.flow.collectAsStateWithLifecycle()
    val ticker by s.ticker.flow.collectAsStateWithLifecycle()
    val stripSide by s.stripSide.flow.collectAsStateWithLifecycle()
    val pullSearch by s.pullSearch.flow.collectAsStateWithLifecycle()
    val searchContacts by s.searchContacts.flow.collectAsStateWithLifecycle()
    val searchEvents by s.searchEvents.flow.collectAsStateWithLifecycle()
    val bandPosition by s.bandPosition.flow.collectAsStateWithLifecycle()
    val bandLook by s.bandLook.flow.collectAsStateWithLifecycle()
    val animations by s.animations.flow.collectAsStateWithLifecycle()
    val colorArt by s.colorArt.flow.collectAsStateWithLifecycle()
    val leftCorner by s.leftCorner.flow.collectAsStateWithLifecycle()
    val rightCorner by s.rightCorner.flow.collectAsStateWithLifecycle()
    val entries by vm.catalog.apps.collectAsStateWithLifecycle()
    var cornerDialog by remember { mutableStateOf<Boolean?>(null) }
    val widgetPages by vm.widgets.pages.collectAsStateWithLifecycle()
    val leftPages = widgetPages.left
    val rightPages = widgetPages.right
    val ink by s.ink.flow.collectAsStateWithLifecycle()
    val background by s.background.flow.collectAsStateWithLifecycle()
    var tickerDialog by remember { mutableStateOf(false) }
    var inkDialog by remember { mutableStateOf(false) }
    var backgroundDialog by remember { mutableStateOf(false) }
    var widgetDialog by remember { mutableStateOf(false) }
    if (visible) ModalBottomSheet(
        onDismissRequest = { onVisible(false) },
        containerColor = Color(0xFF141414),
        scrimColor = Color.Black.copy(alpha = 0.3f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(36.dp, 4.dp)
                    .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
            )
        },
    ) {
        // One section open at a time, so the sheet stays short and the clock stays in view.
        var open by remember { mutableStateOf<String?>(null) }
        fun toggle(section: String) { open = if (open == section) null else section }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
            SettingSection(
                "Clock",
                summary = ClockItem.entries.filter { it in items }
                    .joinToString(", ") { if (it == ClockItem.BTC) ticker else it.label.lowercase() },
                open = open == "clock",
                onToggle = { toggle("clock") },
            ) {
            ClockItem.entries.forEach { item ->
                // Music needs notification access first: until it's on, the item opens that setting.
                val needsAccess = item == ClockItem.MUSIC && !mediaAccess
                SettingItem(
                    if (item == ClockItem.BTC) "$ticker price" else item.label,
                    on = item in items && !needsAccess,
                    onClick = {
                        if (needsAccess) {
                            if (item !in items) s.toggleClockItem(item)
                            onVisible(false)
                            vm.system.openMediaAccessSettings()
                        } else {
                            s.toggleClockItem(item)
                        }
                    },
                )
            }
            SettingItem("Album art in colour", on = colorArt, onClick = s.colorArt::toggle)
            SettingItem("Price ticker", value = ticker, onClick = { onVisible(false); tickerDialog = true })
            // Tapping cycles through the options; the menu stays open, so the clock above shows each one.
            SettingItem("Band", value = bandPosition.label, onClick = { s.bandPosition.cycle() })
            SettingItem("Band look", value = bandLook.label, onClick = { s.bandLook.cycle() })
            }
            SettingSection("Letters", stripSide.label.lowercase(), open == "letters", { toggle("letters") }) {
            StripSide.entries.forEach { side ->
                SettingItem(side.label, on = side == stripSide, radio = true, onClick = { s.stripSide.set(side) })
            }
            }
            AppsSettings(vm, mediaAccess, open == "apps", { toggle("apps") }, onClose = { onVisible(false) })
            SettingSection(
                "Search",
                summary = listOfNotNull("contacts".takeIf { searchContacts }, "calendar".takeIf { searchEvents })
                    .joinToString(", ").ifEmpty { "apps only" },
                open = open == "search",
                onToggle = { toggle("search") },
            ) {
            SettingItem("Contacts", on = searchContacts, onClick = s.searchContacts::toggle)
            SettingItem("Calendar", on = searchEvents, onClick = s.searchEvents::toggle)
            }
            SettingSection(
                "Home",
                summary = if (leftPages + rightPages == 0) "no widget pages" else
                    "widget pages: ${pageSides(leftPages, rightPages)}",
                open = open == "home",
                onToggle = { toggle("home") },
            ) {
            SettingItem(
                "Widget pages",
                value = if (leftPages + rightPages == 0) "none" else pageSides(leftPages, rightPages),
                onClick = { onVisible(false); widgetDialog = true },
            )
            fun cornerName(a: CornerAction) = when (a) {
                CornerAction.Torch -> "flashlight"
                CornerAction.Camera -> "camera"
                CornerAction.Off -> "off"
                is CornerAction.App -> entries.find { it.key == a.key }?.label ?: "app not installed"
            }
            SettingItem("Left corner", value = cornerName(leftCorner), onClick = { cornerDialog = true })
            SettingItem("Right corner", value = cornerName(rightCorner), onClick = { cornerDialog = false })
            SettingItem("Animations", on = animations, onClick = s.animations::toggle)
            SettingItem("Pull down for search", on = pullSearch, onClick = s.pullSearch::toggle)
            SettingItem("Wallpaper", value = "›", onClick = { onVisible(false); vm.system.openWallpaperPicker() })
            // Default or a colour of your own: either way a tap opens the picker, which can also go back to the default.
            SettingItem(
                "Ink",
                value = ink?.let(::formatHex) ?: "white",
                swatch = ink?.let(::Color),
                onClick = { onVisible(false); inkDialog = true },
            )
            SettingItem(
                "Background",
                value = background?.let(::formatHex) ?: "wallpaper",
                swatch = background?.let(::Color),
                onClick = { onVisible(false); backgroundDialog = true },
            )
            if (isHardToRead(ink ?: WHITE, background)) {
                Text("Hard to read", fontSize = 13.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            }
            }
            Text(
                "LACONIC ${remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName }}",
                fontFamily = Josefin,
                fontSize = 11.sp,
                letterSpacing = 3.sp,
                color = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 20.dp, bottom = 20.dp),
            )
        }
    }
    if (widgetDialog) {
        WidgetPagesDialog(leftPages, rightPages, onDismiss = { widgetDialog = false }, onSave = { left, right ->
            vm.widgets.setPageCounts(left, right)
            widgetDialog = false
        })
    }
    if (inkDialog) {
        ColourPickerDialog(
            "Ink",
            initial = ink ?: WHITE,
            preview = { (it) to background },
            onDismiss = { inkDialog = false },
            onSave = { s.ink.set(it.takeIf { c -> c != WHITE }); inkDialog = false },
            onReset = ink?.let { { s.ink.set(null); inkDialog = false } },
        )
    }
    if (backgroundDialog) {
        ColourPickerDialog(
            "Background",
            initial = background ?: BLACK,
            preview = { (ink ?: WHITE) to it },
            onDismiss = { backgroundDialog = false },
            onSave = { s.background.set(it); backgroundDialog = false },
            onReset = background?.let { { s.background.set(null); backgroundDialog = false } },
        )
    }
    cornerDialog?.let { onLeft ->
        CornerChooser(
            title = if (onLeft) "Left corner" else "Right corner",
            apps = entries.map { CornerApp(it.key, it.label) },
            cachedIcon = { key -> entries.find { it.key == key }?.let(vm.catalog::cachedIcon) },
            loadIcon = { key -> entries.find { it.key == key }?.let { vm.catalog.icon(it) } },
            onDismiss = { cornerDialog = null },
            onPick = { cornerDialog = null; (if (onLeft) s.leftCorner else s.rightCorner).set(it) },
        )
    }
    if (tickerDialog) {
        TickerDialog(ticker, onDismiss = { tickerDialog = false }, onSave = {
            s.ticker.set(it)
            if (ClockItem.BTC !in items) s.toggleClockItem(ClockItem.BTC)
            tickerDialog = false
        })
    }
}

private const val WHITE = 0xFFFFFFFF.toInt()
private const val BLACK = 0xFF000000.toInt()

// SPY rather than "SPX": on Binance SPX is a meme coin, and the S&P 500 itself isn't listed.
private val QuickTickers = listOf("BTC", "ETH", "SOL", "XRP", "DOGE", "SPY", "QQQ", "XAU", "NVDA")

/** Pick what the clock band prices: anything Binance trades against USDT, checked before saving. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TickerDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun submit(raw: String) {
        val ticker = raw.trim().uppercase()
        if (ticker.isEmpty() || checking) return
        checking = true
        error = null
        scope.launch {
            val ok = try {
                fetchPrice(ticker); true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            checking = false
            if (ok) onSave(ticker) else error = "No price for $ticker on Binance or Coinbase (or no connection)"
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Price on the clock") },
        text = {
            Column {
                Text("Any coin on Binance or Coinbase, or a stock, ETF or metal from Binance's futures: SPY = S&P 500, QQQ = Nasdaq 100, XAU = gold.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isLetterOrDigit).uppercase().take(10); error = null },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit(text) }),
                )
                FlowRow {
                    QuickTickers.forEach { TextButton(onClick = { submit(it) }) { Text(it) } }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { submit(text) }, enabled = !checking) { Text(if (checking) "Checking…" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Settings for the app lists: what home lists and how, icons, dots, hidden apps. */
@Composable
private fun AppsSettings(vm: AppsViewModel, notificationAccess: Boolean, open: Boolean, onToggle: () -> Unit, onClose: () -> Unit) {
    val homeLayout by vm.settings.homeLayout.flow.collectAsStateWithLifecycle()
    val boxLines by vm.settings.boxLines.flow.collectAsStateWithLifecycle()
    val homeList by vm.settings.homeList.flow.collectAsStateWithLifecycle()
    val homeAz by vm.settings.homeAz.flow.collectAsStateWithLifecycle()
    val icons by vm.settings.showIcons.flow.collectAsStateWithLifecycle()
    val dots by vm.settings.dots.flow.collectAsStateWithLifecycle()
    val hidden by vm.settings.hidden.flow.collectAsStateWithLifecycle()
    var hiddenDialog by remember { mutableStateOf(false) }
    val showDots = dots && notificationAccess
    SettingSection(
        "Apps",
        summary = listOfNotNull(
            homeList.label, "boxes".takeIf { homeLayout == HomeLayout.BOXES }, "A–Z".takeIf { homeAz }, "no icons".takeIf { !icons }, "dots".takeIf { showDots },
        ).joinToString(", "),
        open = open,
        onToggle = onToggle,
    ) {
        // Tapping cycles; the list on home changes behind the sheet as it does.
        SettingItem("Layout", value = homeLayout.label, onClick = { vm.settings.homeLayout.cycle() })
        // Only the boxes have lines; the menu stays open, so each tap shows the next look on home above it.
        if (homeLayout == HomeLayout.BOXES) {
            SettingItem("Lines", value = boxLines.label, onClick = { vm.settings.boxLines.cycle() })
        }
        SettingItem("Home list", value = homeList.label, onClick = { vm.settings.homeList.cycle() })
        SettingItem("Order", value = if (homeAz) "A–Z" else homeList.order, onClick = vm.settings.homeAz::toggle)
        SettingItem("App icons", on = icons, onClick = vm.settings.showIcons::toggle)
        // Dots need notification access, like the music player: until it's on, the item opens that setting.
        SettingItem("Notification dots", on = showDots, onClick = {
            if (notificationAccess) vm.settings.dots.toggle() else {
                if (!dots) vm.settings.dots.toggle()
                onClose()
                vm.system.openMediaAccessSettings()
            }
        })
        // Over the sheet: it lives in the sheet, so closing the sheet would take it along.
        SettingItem("Hidden apps", value = if (hidden.isEmpty()) "none" else "${hidden.size}", onClick = { hiddenDialog = true })
    }
    if (hiddenDialog) HiddenAppsDialog(vm, onDismiss = { hiddenDialog = false })
}

/** Every app with a switch: on hides it. Typing filters the list; hidden ones are listed first. */
@Composable
private fun HiddenAppsDialog(vm: AppsViewModel, onDismiss: () -> Unit) {
    val apps by vm.catalog.apps.collectAsStateWithLifecycle()
    val hidden by vm.settings.hidden.flow.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    // Order fixed when the dialog opens, so a row doesn't jump to the top the moment it's switched.
    val order = remember { apps.sortedBy { it.key !in hidden } }
    val shown = order.filter { query.isBlank() || matchRank(it.label, query) != null || matchRank(it.originalLabel, query) != null }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF141414)).windowInsetsPadding(WindowInsets.systemBars)) {
            Row(Modifier.padding(start = 24.dp, end = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("HIDDEN APPS", fontFamily = Josefin, fontSize = 14.sp, letterSpacing = 3.sp, color = Color.White, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Done") }
            }
            Text(
                "Hidden apps stay out of the letter lists and the used-apps list. Search still finds them.",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Filter") },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                items(shown, key = { it.key }) { entry ->
                    SettingItem(entry.label, on = entry.key in hidden, onClick = { vm.setHidden(entry, entry.key !in hidden) })
                }
            }
        }
    }
}

@Composable
private fun SettingSection(
    title: String,
    summary: String,
    open: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val turn by animateFloatAsState(if (open) 90f else 0f, label = "chevron")
    // In the clock's thin spaced capitals; closed, it shows what's set inside.
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title.uppercase(), fontFamily = Josefin, fontSize = 14.sp, letterSpacing = 3.sp, color = Color.White)
        Spacer(Modifier.width(20.dp))
        Text(
            if (open) "" else summary,
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.45f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text("›", fontSize = 20.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.graphicsLayer { rotationZ = turn })
    }
    AnimatedVisibility(open) { Column(Modifier.padding(bottom = 8.dp)) { content() } }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Color.White.copy(alpha = 0.06f))
}

/**
 * One settings row: a label, and either its current value (dim, on the right; tapping changes it), a
 * switch, or for one-of-several choices ([radio]) a dot — filled when picked, a ring when not.
 */
@Composable
private fun SettingItem(
    label: String,
    value: String? = null,
    on: Boolean? = null,
    radio: Boolean = false,
    swatch: Color? = null,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f))
                if (value != null) {
                    Spacer(Modifier.width(20.dp))
                    if (swatch != null) {
                        Box(Modifier.size(14.dp).background(swatch, CircleShape).border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(value, fontSize = 15.sp, color = Color.White.copy(alpha = 0.5f))
                }
            }
        },
        trailingIcon = on?.let {
            {
                if (radio) Box(
                    Modifier
                        .size(10.dp)
                        .then(
                            if (it) Modifier.background(Color.White, CircleShape)
                            else Modifier.border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                        )
                ) else Switch(
                    checked = it,
                    onCheckedChange = null, // the whole row toggles
                    modifier = Modifier.scale(0.8f),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = Color.White,
                        uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                        uncheckedTrackColor = Color(0xFF242424),
                        uncheckedBorderColor = Color.White.copy(alpha = 0.3f),
                    ),
                )
            }
        },
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 16.dp),
    )
}

/** How many widget pages on each side of home. */
@Composable
fun WidgetPagesDialog(left: Int, right: Int, onDismiss: () -> Unit, onSave: (left: Int, right: Int) -> Unit) {
    var l by remember { mutableIntStateOf(left) }
    var r by remember { mutableIntStateOf(right) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Widget pages") },
        text = {
            Column {
                Text("Pages of Android widgets beside the home screen. Swipe sideways on the clock or an empty spot to reach them.")
                Spacer(Modifier.height(12.dp))
                @Composable
                fun Side(name: String, n: Int, set: (Int) -> Unit) {
                    Text(name, modifier = Modifier.padding(top = 8.dp))
                    Row {
                        (0..3).forEach { i ->
                            FilterChip(selected = n == i, onClick = { set(i) }, label = { Text(if (i == 0) "None" else "$i") })
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                }
                Side("Left of home", l) { l = it }
                Side("Right of home", r) { r = it }
                if (l < left || r < right) {
                    Spacer(Modifier.height(8.dp))
                    Text("Removing a page removes its widgets.", color = Color(0xFFFFB4AB))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(l, r) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
