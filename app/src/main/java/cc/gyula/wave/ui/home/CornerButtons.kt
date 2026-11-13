package cc.gyula.wave.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.foundation.Image
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import cc.gyula.wave.core.CornerAction
import cc.gyula.wave.ui.theme.inkFilter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.gyula.wave.AppsViewModel
import cc.gyula.wave.ui.theme.LocalInk
import cc.gyula.wave.ui.theme.onInk

/** Measured on the Pixel 10's lock screen: 48 dp circles, 16 dp in from the side, 32 dp up from the bottom. */
private val CornerSize = 48.dp
private val CornerSide = 16.dp
private val CornerBottom = 32.dp

/** How far above the screen's bottom edge the corner buttons reach; the letter strip and the list stop above it. */
val CornerButtonsTop: Dp = CornerBottom + CornerSize

/** What the corner button chooser lists and the corners show: an installed app by its [key] and name. */
data class CornerApp(val key: String, val label: String)

/** The two corner buttons, wired to the phone: flashlight, camera or an app each, as chosen in settings. */
@Composable
fun CornerButtons(vm: AppsViewModel) {
    val torchOn by vm.torch.on.collectAsStateWithLifecycle()
    val left by vm.settings.leftCorner.flow.collectAsStateWithLifecycle()
    val right by vm.settings.rightCorner.flow.collectAsStateWithLifecycle()
    val entries by vm.catalog.apps.collectAsStateWithLifecycle()
    val apps = remember(entries) { entries.map { CornerApp(it.key, it.label) } }
    CornerButtonsContent(
        left, right, torchOn, apps,
        cachedIcon = { key -> entries.find { it.key == key }?.let(vm.catalog::cachedIcon) },
        loadIcon = { key -> entries.find { it.key == key }?.let { vm.catalog.icon(it) } },
        onTorch = vm.torch::toggle,
        onCamera = vm.system::openCamera,
        onApp = { key -> entries.find { it.key == key }?.let(vm.catalog::launch) },
        onChange = { onLeft, action -> (if (onLeft) vm.settings.leftCorner else vm.settings.rightCorner).set(action) },
    )
}

/**
 * Bottom left and right, as on the lock screen. A tap does the thing; a long-press opens the chooser, and
 * also keeps the press from reaching the home screen behind, which would open settings. An off corner, or an
 * app that is gone, draws nothing (and keeps its setting).
 */
@Composable
fun CornerButtonsContent(
    left: CornerAction,
    right: CornerAction,
    torchOn: Boolean,
    apps: List<CornerApp>,
    cachedIcon: (String) -> ImageBitmap?,
    loadIcon: suspend (String) -> ImageBitmap?,
    onTorch: () -> Unit,
    onCamera: () -> Unit,
    onApp: (String) -> Unit,
    onChange: (onLeft: Boolean, CornerAction) -> Unit,
) {
    var choosing by remember { mutableStateOf<Boolean?>(null) }
    Box(Modifier.fillMaxSize()) {
        @Composable
        fun Corner(action: CornerAction, onLeft: Boolean) {
            val alignment = if (onLeft) Alignment.BottomStart else Alignment.BottomEnd
            val open = { choosing = onLeft }
            when (action) {
                CornerAction.Off -> {}
                CornerAction.Torch ->
                    CornerButton("Flashlight", alignment, active = torchOn, onClick = onTorch, onLongClick = open) { drawTorch(it) }
                CornerAction.Camera ->
                    CornerButton("Camera", alignment, active = false, onClick = onCamera, onLongClick = open) { drawCamera(it) }
                is CornerAction.App -> {
                    val label = apps.find { it.key == action.key }?.label ?: return
                    val icon by produceState(cachedIcon(action.key), action.key) { value = loadIcon(action.key) }
                    CornerButton(label, alignment, active = false, onClick = { onApp(action.key) }, onLongClick = open, glyph = icon)
                }
            }
        }
        Corner(left, onLeft = true)
        Corner(right, onLeft = false)
    }
    choosing?.let { onLeft ->
        CornerChooser(
            title = if (onLeft) "Left corner" else "Right corner",
            apps = apps,
            loadIcon = loadIcon,
            cachedIcon = cachedIcon,
            onDismiss = { choosing = null },
            onPick = { choosing = null; onChange(onLeft, it) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BoxScope.CornerButton(
    label: String,
    alignment: Alignment,
    active: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    glyph: ImageBitmap? = null,
    icon: DrawScope.(Color) -> Unit = {},
) {
    val ink = LocalInk.current
    val onInk = onInk
    Box(
        Modifier
            .align(alignment)
            .padding(start = CornerSide, end = CornerSide, bottom = CornerBottom)
            .size(CornerSize)
            .clip(CircleShape)
            .background(if (active) ink else ink.copy(alpha = 0.12f))
            .combinedClickable(onLongClick = onLongClick, onClick = onClick, role = Role.Button)
            .semantics {
                contentDescription = label
                if (label == "Flashlight") stateDescription = if (active) "On" else "Off"
            },
        contentAlignment = Alignment.Center,
    ) {
        if (glyph != null) Image(glyph, contentDescription = null, colorFilter = inkFilter(), modifier = Modifier.size(26.dp))
        else Canvas(Modifier.size(24.dp)) { icon(if (active) onInk else ink) }
    }
}

/** A torch: a wide head over a narrow handle, with its switch. Drawn on a 24-unit grid. */
private fun DrawScope.drawTorch(color: Color) {
    val u = size.width / 24f
    val stroke = Stroke(width = 1.6f * u)
    val head = Path().apply {
        moveTo(6f * u, 3f * u); lineTo(18f * u, 3f * u); lineTo(18f * u, 6f * u)
        lineTo(15f * u, 10f * u); lineTo(9f * u, 10f * u); lineTo(6f * u, 6f * u); close()
    }
    drawPath(head, color, style = stroke)
    drawRoundRect(color, Offset(9f * u, 10f * u), Size(6f * u, 11f * u), CornerRadius(1f * u), style = stroke)
    drawCircle(color, radius = 1.2f * u, center = Offset(12f * u, 14f * u))
}

/** A camera: body, viewfinder bump and lens. Drawn on a 24-unit grid. */
private fun DrawScope.drawCamera(color: Color) {
    val u = size.width / 24f
    val stroke = Stroke(width = 1.6f * u)
    val body = Path().apply {
        moveTo(3f * u, 8f * u); lineTo(8f * u, 8f * u); lineTo(9.5f * u, 5f * u); lineTo(14.5f * u, 5f * u)
        lineTo(16f * u, 8f * u); lineTo(21f * u, 8f * u); lineTo(21f * u, 19f * u); lineTo(3f * u, 19f * u); close()
    }
    drawPath(body, color, style = stroke)
    drawCircle(color, radius = 3.6f * u, center = Offset(12f * u, 13.3f * u), style = stroke)
}
