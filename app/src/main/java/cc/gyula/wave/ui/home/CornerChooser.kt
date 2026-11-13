package cc.gyula.wave.ui.home

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.gyula.wave.core.CornerAction
import cc.gyula.wave.core.searchResults

/**
 * What a corner button does: flashlight, camera, an app or nothing. Shared by the long-press on the button
 * and the rows in settings. "App…" goes on to the list of installed apps.
 */
@Composable
fun CornerChooser(
    title: String,
    apps: List<CornerApp>,
    cachedIcon: (String) -> ImageBitmap?,
    loadIcon: suspend (String) -> ImageBitmap?,
    onDismiss: () -> Unit,
    onPick: (CornerAction) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    if (picking) {
        AppPickerDialog(apps, cachedIcon, loadIcon, onDismiss = onDismiss) { onPick(CornerAction.App(it)) }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                @Composable
                fun Choice(label: String, onClick: () -> Unit) = Text(
                    label,
                    fontSize = 17.sp,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
                )
                Choice("Flashlight") { onPick(CornerAction.Torch) }
                Choice("Camera") { onPick(CornerAction.Camera) }
                Choice("App…") { picking = true }
                Choice("Off") { onPick(CornerAction.Off) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Every installed app, A–Z with its icon, narrowed by the search field above. */
@Composable
private fun AppPickerDialog(
    apps: List<CornerApp>,
    cachedIcon: (String) -> ImageBitmap?,
    loadIcon: suspend (String) -> ImageBitmap?,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val sorted = remember(apps) { apps.sortedBy { it.label.lowercase() } }
    val shown = remember(sorted, query) {
        if (query.isBlank()) sorted else searchResults(sorted, query) { listOf(it.label) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose an app") },
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
                    items(shown, key = { it.key }) { app ->
                        val icon by produceState(cachedIcon(app.key), app.key) { value = loadIcon(app.key) }
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(app.key) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                        ) {
                            icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(32.dp)) }
                                ?: Spacer(Modifier.size(32.dp))
                            Spacer(Modifier.width(16.dp))
                            Text(app.label, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
