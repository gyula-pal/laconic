package cc.gyula.wave.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.gyula.wave.core.Hsv
import cc.gyula.wave.core.argbToHsv
import cc.gyula.wave.core.formatHex
import cc.gyula.wave.core.hsvToArgb
import cc.gyula.wave.core.isHardToRead
import cc.gyula.wave.core.parseHex
import cc.gyula.wave.ui.theme.Josefin

/**
 * Pick a colour with hue, saturation and brightness sliders or a hex code; the two stay in step. The preview
 * shows the pick as [preview] places it: the ink and background (null = wallpaper) to draw a sample with.
 * [onReset], when given, goes back to the default and closes.
 */
@Composable
internal fun ColourPickerDialog(
    title: String,
    initial: Int,
    preview: (picked: Int) -> Pair<Int, Int?>,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
    onReset: (() -> Unit)? = null,
) {
    var hsv by remember { mutableStateOf(argbToHsv(initial)) }
    // The text and its cursor: tidying what was typed (adding the #, dropping other characters) must not leave
    // the cursor behind, or the next characters land in the middle ("B48CFF" came out as "#48CFFB").
    var field by remember { mutableStateOf(formatHex(initial).let { TextFieldValue(it, TextRange(it.length)) }) }
    val hex = field.text
    val picked = parseHex(hex)
    val (ink, background) = preview(picked ?: initial)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Preview(ink, background)
                if (isHardToRead(ink, background)) {
                    Text(
                        "Hard to read",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp).testTag("hard-to-read"),
                    )
                }
                Spacer(Modifier.height(8.dp))
                fun set(new: Hsv) { hsv = new; field = formatHex(hsvToArgb(new)).let { TextFieldValue(it, TextRange(it.length)) } }
                ColourSlider("Hue", "hue", hsv.hue / 360f, Brush.horizontalGradient(
                    (0..6).map { Color(hsvToArgb(Hsv(it * 60f, 1f, 1f))) },
                )) { set(hsv.copy(hue = it * 360f)) }
                ColourSlider("Saturation", "saturation", hsv.saturation, Brush.horizontalGradient(
                    listOf(Color(hsvToArgb(hsv.copy(saturation = 0f))), Color(hsvToArgb(hsv.copy(saturation = 1f)))),
                )) { set(hsv.copy(saturation = it)) }
                ColourSlider("Brightness", "brightness", hsv.value, Brush.horizontalGradient(
                    listOf(Color.Black, Color(hsvToArgb(hsv.copy(value = 1f)))),
                )) { set(hsv.copy(value = it)) }
                OutlinedTextField(
                    value = field,
                    onValueChange = { typed ->
                        val clean = "#" + typed.text.removePrefix("#").filter { it in "0123456789abcdefABCDEF" }.uppercase().take(6)
                        // Same length: only the case changed, so the cursor stays where it was; otherwise it goes to the end.
                        field = TextFieldValue(clean, if (clean.length == typed.text.length) typed.selection else TextRange(clean.length))
                        // Moving the sliders to a typed colour, but keeping the hue of a grey the sliders lost.
                        parseHex(clean)?.let { if (hsvToArgb(hsv) != it) hsv = argbToHsv(it) }
                    },
                    singleLine = true,
                    isError = picked == null,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.padding(top = 8.dp).testTag("hex"),
                )
                if (onReset != null) TextButton(onClick = onReset, modifier = Modifier.testTag("reset")) { Text("Back to default") }
            }
        },
        confirmButton = { TextButton(onClick = { picked?.let(onSave) }, enabled = picked != null) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A sample of the clock's weekday and a line, in [ink] on [background]; black stands in for the wallpaper. */
@Composable
private fun Preview(ink: Int, background: Int?) {
    val inkColor = Color(ink)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(background ?: 0xFF000000.toInt()))
            .padding(16.dp)
            .testTag("preview"),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text("THU", color = inkColor, fontFamily = Josefin, fontWeight = FontWeight.ExtraLight, fontSize = 44.sp, letterSpacing = 4.sp)
            Box(Modifier.fillMaxWidth().height(1.dp).background(inkColor))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColourSlider(label: String, tag: String, value: Float, track: Brush, onChange: (Float) -> Unit) {
    Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    Slider(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.testTag(tag),
        colors = SliderDefaults.colors(thumbColor = Color.White),
        track = { Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(track)) },
    )
}
