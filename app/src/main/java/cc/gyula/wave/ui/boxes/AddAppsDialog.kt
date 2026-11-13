package cc.gyula.wave.ui.boxes

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.gyula.wave.core.BoxTree
import cc.gyula.wave.core.boxes
import cc.gyula.wave.core.fold
import cc.gyula.wave.core.searchResults
import cc.gyula.wave.ui.home.CornerApp

/** One app in the "add apps" list. [elsewhere] is the name of the other box it sits in, if it does. */
internal data class PickRow(val key: String, val label: String, val elsewhere: String?)

/** What the dialog is asked to show: for box [boxApps] are the keys it holds now. */
internal class AddAppsRequest(val title: String, val boxApps: List<String>, val rows: List<PickRow>) {
    /** The rows ticked to begin with: the apps already in this box. */
    fun initialTicks(): Set<String> = rows.mapTo(HashSet()) { it.key }.intersect(boxApps.toSet())

    /** The keys the box should end up with when [ticked] is applied; see [pickedKeys]. */
    fun result(ticked: Set<String>): List<String> = pickedKeys(boxApps, rows, ticked)
}

/** The dialog for adding apps to a box. A parameter so tests can stand in for it, as with the name dialog. */
internal typealias AddAppsDialogContent = @Composable (
    request: AddAppsRequest,
    cachedIcon: (String) -> ImageBitmap?,
    loadIcon: suspend (String) -> ImageBitmap?,
    onDismiss: () -> Unit,
    onApply: (List<String>) -> Unit,
) -> Unit

/** Every app of [apps] A–Z, each marked with the other box it is in (a box without a name is "another box"). */
internal fun pickRows(apps: List<CornerApp>, tree: BoxTree, boxId: Int): List<PickRow> {
    val home = HashMap<String, String>()
    tree.boxes().filter { it.id != boxId }.forEach { b -> b.apps.forEach { home.putIfAbsent(it, b.name.ifEmpty { "another box" }) } }
    return apps.sortedBy { fold(it.label) }.map { PickRow(it.key, it.label, home[it.key]) }
}

/** [rows] narrowed by a typed [query] the way search does it (case and accents ignored); blank shows all, A–Z. */
internal fun filterRows(rows: List<PickRow>, query: String): List<PickRow> =
    if (query.isBlank()) rows else searchResults(rows, query) { listOf(it.label) }

/**
 * The keys a box should hold once [ticked] is applied: its apps that are not in [rows] (not installed, or
 * hidden) stay, those in [rows] stay only if ticked, and ticked apps new to the box follow A–Z (rows' order).
 */
internal fun pickedKeys(boxApps: List<String>, rows: List<PickRow>, ticked: Set<String>): List<String> {
    val listed = rows.mapTo(HashSet()) { it.key }
    val kept = boxApps.filter { it !in listed || it in ticked }
    return kept + rows.map { it.key }.filter { it in ticked && it !in kept }
}

/** The real dialog: a search field over a checkbox list. Checked on the device, as the name dialog is. */
internal val DefaultAddAppsDialog: AddAppsDialogContent = { request, cachedIcon, loadIcon, onDismiss, onApply ->
    var query by remember { mutableStateOf("") }
    var ticked by remember { mutableStateOf(request.initialTicks()) }
    val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val shown = remember(request, query) { filterRows(request.rows, query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(request.title) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Search") },
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(8.dp))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it.key }) { row ->
                        val icon by produceState(cachedIcon(row.key), row.key) { value = loadIcon(row.key) }
                        val toggle = { ticked = if (row.key in ticked) ticked - row.key else ticked + row.key }
                        Row(
                            Modifier.fillMaxWidth().clickable(onClick = toggle).padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                        ) {
                            Checkbox(checked = row.key in ticked, onCheckedChange = { toggle() })
                            icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(32.dp)) }
                                ?: Spacer(Modifier.size(32.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                buildAnnotatedString {
                                    append(row.label)
                                    row.elsewhere?.let {
                                        withStyle(SpanStyle(fontSize = 12.sp, color = dim)) { append("  $it") }
                                    }
                                },
                                fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(request.result(ticked)) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
