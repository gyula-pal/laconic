package cc.gyula.wave.ui.apps

import android.content.pm.ShortcutInfo
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.gyula.wave.AppsViewModel
import cc.gyula.wave.data.AppEntry
import cc.gyula.wave.data.size
import cc.gyula.wave.ui.theme.Label
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import cc.gyula.wave.ui.theme.LocalInk
import cc.gyula.wave.ui.theme.inkFilter

/** Letter strip on the left: app lists are mirrored and the player keeps clear of that edge. */
internal val LocalLeftHanded = compositionLocalOf { false }

/**
 * Left-handed, app lists are laid out mirrored (icons on the right edge, names to their left), so the
 * strip's wave bows into empty space like it does on the right. Text itself still reads left to right.
 */
@Composable
internal fun MirroredIfLeftHanded(content: @Composable () -> Unit) {
    if (LocalLeftHanded.current) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
    } else {
        content()
    }
}

/** How far app rows sit in from the edge: further when a hidden strip waits on both edges. */
internal val LocalRowInset = compositionLocalOf { 48.dp }

/** Off: app rows are names only. */
internal val LocalShowIcons = compositionLocalOf { true }

/** Packages with notifications waiting, when the dots are on; empty otherwise. */
internal val LocalNotified = compositionLocalOf { emptySet<String>() }

internal val RowHeight = 56.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AppRow(
    vm: AppsViewModel,
    entry: AppEntry,
    hovered: Boolean,
    isFavorite: Boolean,
    rows: MutableMap<String, Rect>,
    modifier: Modifier = Modifier,
    editing: Boolean = false,
    openCard: MutableState<String?>? = null, // swipe right opens this row's shortcuts card
    onLaunch: () -> Unit = {},
    onLongPress: (() -> Unit)? = null, // instead of the menu (pinned rows: enter edit mode)
    onSwipeAway: (() -> Unit)? = null,
    handle: Modifier? = null,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val nudge by animateDpAsState(if (hovered) 12.dp else 0.dp, label = "hover")
    val swipe = remember { Animatable(0f) }
    // Loaded when the row is first shown, not for every installed app up front.
    val icon by produceState(vm.catalog.cachedIcon(entry), entry.key) { value = vm.catalog.icon(entry) }

    DisposableEffect(entry.key) { onDispose { rows.remove(entry.key) } }
    Box(
        modifier
            .onGloballyPositioned { rows[entry.key] = it.boundsInRoot() }
            // The swipes, for a screen reader.
            .semantics {
                customActions = listOfNotNull(
                    openCard?.takeIf { !editing }?.let { card -> CustomAccessibilityAction("Shortcuts") { card.value = entry.key; true } },
                    onSwipeAway?.takeIf { editing }?.let { unpin -> CustomAccessibilityAction("Unpin") { unpin(); true } },
                )
            }
            .pointerInput(editing, onSwipeAway, openCard) {
                // Editing: swipe left unpins. Otherwise: swipe right opens the shortcuts card.
                val unpin = editing && onSwipeAway != null
                val shortcuts = !editing && openCard != null
                if (!unpin && !shortcuts) return@pointerInput
                val openPx = 64.dp.toPx()
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (unpin && swipe.value < -size.width * 0.35f) {
                                swipe.animateTo(-size.width.toFloat(), tween(150))
                                onSwipeAway()
                            } else {
                                if (shortcuts && swipe.value > openPx) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    openCard.value = entry.key
                                }
                                swipe.animateTo(0f)
                            }
                        }
                    },
                    onDragCancel = { scope.launch { swipe.animateTo(0f) } },
                ) { change, dx ->
                    change.consume()
                    scope.launch {
                        swipe.snapTo(
                            if (unpin) (swipe.value + dx).coerceAtMost(0f)
                            else (swipe.value + dx).coerceIn(0f, openPx * 1.5f)
                        )
                    }
                }
            }
            .graphicsLayer {
                translationX = swipe.value
                alpha = if (swipe.value < 0f) 1f + swipe.value / size.width.coerceAtLeast(1f) else 1f
            }
    ) {
        if (openCard?.value == entry.key) {
            ShortcutCard(vm, entry, icon, onDone = { openCard.value = null; onLaunch() })
            return@Box
        }
        Row(Modifier.fillMaxWidth().height(RowHeight), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .combinedClickable(
                        onClick = { if (editing) menuOpen = true else { vm.catalog.launch(entry); onLaunch() } },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (onLongPress != null && !editing) onLongPress() else menuOpen = true
                        },
                    )
                    .padding(start = LocalRowInset.current, end = if (editing) 0.dp else 64.dp)
                    .offset { IntOffset(nudge.roundToPx(), 0) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The notification dot sits in the gap before the name, so names stay lined up with or without one.
                val dot = entry.component.packageName in LocalNotified.current
                if (LocalShowIcons.current) {
                    val glyph = icon
                    if (glyph != null) Image(glyph, contentDescription = null, colorFilter = inkFilter(), modifier = Modifier.size(40.dp))
                    else Spacer(Modifier.size(40.dp))
                    Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) { if (dot) NotificationDot() }
                } else if (dot) {
                    // No icons: just left of the name, in the row's inset.
                    Box(Modifier.width(0.dp).wrapContentSize(unbounded = true).offset(x = (-12).dp)) { NotificationDot() }
                }
                Text(
                    entry.label,
                    style = Label,
                    fontSize = 18.sp,
                    fontWeight = if (hovered) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            // Outside the clickable area, so a slow drag on it can't count as a long-press on the row.
            if (editing && handle != null) {
                Box(handle.padding(end = 12.dp).size(48.dp), contentAlignment = Alignment.Center) {
                    Text("≡", style = Label.copy(color = LocalInk.current.copy(alpha = 0.7f)), fontSize = 26.sp)
                }
            }
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(if (isFavorite) "Unpin" else "Pin to home") },
                onClick = { menuOpen = false; vm.togglePin(entry) },
            )
            DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; renaming = true })
            val isHidden = entry.key in vm.settings.hidden.flow.collectAsStateWithLifecycle().value
            DropdownMenuItem(
                text = { Text(if (isHidden) "Unhide" else "Hide from lists") },
                onClick = { menuOpen = false; vm.setHidden(entry, !isHidden) },
            )
            DropdownMenuItem(text = { Text("App info") }, onClick = { menuOpen = false; vm.catalog.openAppInfo(entry) })
            DropdownMenuItem(text = { Text("Uninstall") }, onClick = { menuOpen = false; vm.catalog.uninstall(entry) })
        }
    }
    if (renaming) RenameDialog(entry, onDismiss = { renaming = false }, onSave = { vm.rename(entry, it); renaming = false })
}

@Composable
internal fun NotificationDot() = Box(Modifier.size(6.dp).background(LocalInk.current, CircleShape))

/** A name of your own for an app; left empty, it goes back to the app's. */
@Composable
private fun RenameDialog(entry: AppEntry, onDismiss: () -> Unit, onSave: (String) -> Unit) = NameDialog(
    title = "Rename",
    initial = entry.label,
    placeholder = entry.originalLabel,
    hint = if (entry.label != entry.originalLabel) "Empty: back to ${entry.originalLabel}" else null,
    onDismiss = onDismiss,
    onSave = onSave,
)

/** A one-line name prompt, pre-filled and all selected; shared by app renaming and box naming. */
@Composable
internal fun NameDialog(
    title: String,
    initial: String,
    placeholder: String,
    hint: String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    // All selected, so typing replaces the name and a tap places the cursor.
    var text by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = if (it.text.length <= 40) it else text },
                singleLine = true,
                placeholder = { Text(placeholder) },
                supportingText = hint?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave(text.text) }),
                modifier = Modifier.focusRequester(focus).testTag("name-field"),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text.text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** An app's shortcuts, opened in place of its row by swiping it right — a dark card like a menu. */
@Composable
private fun ShortcutCard(vm: AppsViewModel, entry: AppEntry, icon: ImageBitmap?, onDone: () -> Unit) {
    val shortcuts by produceState<List<ShortcutInfo>?>(null, entry.key) {
        value = withContext(Dispatchers.IO) { vm.catalog.shortcuts(entry) }
    }
    Column(
        Modifier
            .padding(start = 36.dp, end = 56.dp, top = 4.dp, bottom = 4.dp)
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E), RoundedCornerShape(16.dp))
            .padding(vertical = 6.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { vm.catalog.launch(entry); onDone() }
                .padding(start = 14.dp, end = 14.dp)
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null && LocalShowIcons.current) {
                Image(icon, contentDescription = null, colorFilter = inkFilter(), modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
            }
            Text(entry.label, style = Label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        val list = shortcuts ?: return@Column
        if (list.isEmpty()) {
            Text(
                "No shortcuts",
                style = Label.copy(color = LocalInk.current.copy(alpha = 0.5f)),
                fontSize = 15.sp,
                modifier = Modifier.padding(start = LocalRowInset.current, top = 4.dp, bottom = 14.dp),
            )
        }
        list.forEach { s ->
            Text(
                (s.shortLabel ?: s.longLabel ?: "").toString(),
                style = Label,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.catalog.startShortcut(s); onDone() }
                    .padding(start = LocalRowInset.current, end = 14.dp, top = 14.dp, bottom = 14.dp),
            )
        }
    }
}
