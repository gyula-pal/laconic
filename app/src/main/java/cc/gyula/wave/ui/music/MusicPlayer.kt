package cc.gyula.wave.ui.music

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import cc.gyula.wave.core.pausedTimeLeft
import cc.gyula.wave.data.NowPlaying
import cc.gyula.wave.data.hasMediaAccess
import cc.gyula.wave.data.nowPlaying
import cc.gyula.wave.data.size
import cc.gyula.wave.ui.apps.LocalLeftHanded
import cc.gyula.wave.ui.theme.Label
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import cc.gyula.wave.ui.theme.LocalInk

/** Notification access, re-checked whenever the home screen comes back (e.g. from Settings). */
@Composable
internal fun rememberMediaAccess(): Boolean {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var granted by remember { mutableStateOf(hasMediaAccess(context)) }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { granted = hasMediaAccess(context) }
    }
    return granted
}

/** What's playing, while the home screen is visible; null when nothing is, or it's been paused a while. */
@Composable
internal fun rememberNowPlaying(enabled: Boolean): NowPlaying? {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val artPx = with(LocalDensity.current) { ArtSize.roundToPx() }
    var current by remember { mutableStateOf<NowPlaying?>(null) }
    var stale by remember { mutableStateOf(false) }
    LaunchedEffect(enabled, lifecycle) {
        current = null
        if (!enabled) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            nowPlaying(context, artPx).collect { current = it }
        }
    }
    LaunchedEffect(current) {
        stale = false
        val np = current ?: return@LaunchedEffect
        if (!np.playing) {
            delay(pausedTimeLeft(np.since, SystemClock.elapsedRealtime()))
            stale = true
        }
    }
    return current?.takeUnless { stale }
}

private val ArtSize = 80.dp

/** Album art drawn without colour, to sit with the monochrome icons. */
private val Greyscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** Album art, title and artist, and ⏮ ⏯ ⏭ — under the clock while something plays. Swipe left to stop it. */
@Composable
internal fun MusicPlayer(np: NowPlaying, colorArt: Boolean, onOpen: () -> Unit, onClose: () -> Unit) {
    val controls = np.controller.transportControls
    val scope = rememberCoroutineScope()
    val swipe = remember { Animatable(0f) }
    // Shown again before it finished animating away (it started playing): slide back in, never leave a gap.
    LaunchedEffect(np) { if (swipe.value != 0f) swipe.animateTo(0f) }
    Row(
        Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (swipe.value < -size.width * 0.3f) {
                                swipe.animateTo(-size.width.toFloat(), tween(150))
                                onClose()
                            } else {
                                swipe.animateTo(0f)
                            }
                        }
                    },
                    onDragCancel = { scope.launch { swipe.animateTo(0f) } },
                ) { change, dx ->
                    change.consume()
                    scope.launch { swipe.snapTo((swipe.value + dx).coerceAtMost(0f)) }
                }
            }
            .graphicsLayer {
                translationX = swipe.value
                alpha = 1f + swipe.value / size.width.coerceAtLeast(1f)
            }
            // Swiping it away, for a screen reader.
            .semantics { customActions = listOf(CustomAccessibilityAction("Stop and hide") { onClose(); true }) }
            .padding(
                start = if (LocalLeftHanded.current) 64.dp else 48.dp,
                end = if (LocalLeftHanded.current) 48.dp else 64.dp,
                bottom = 20.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(ArtSize)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1E1E1E))
                .clickable(onClick = onOpen)
        ) {
            np.art?.let {
                Image(
                    it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = if (colorArt) null else Greyscale,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Column(Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpen,
            )) {
                Text(np.title, style = Label, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (np.artist.isNotEmpty()) {
                    Text(
                        np.artist,
                        style = Label.copy(color = LocalInk.current.copy(alpha = 0.75f)),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MediaButton(Glyph.PREVIOUS, "Previous track") { controls.skipToPrevious() }
                MediaButton(if (np.playing) Glyph.PAUSE else Glyph.PLAY, if (np.playing) "Pause" else "Play", circled = true) {
                    if (np.playing) controls.pause() else controls.play()
                }
                MediaButton(Glyph.NEXT, "Next track") { controls.skipToNext() }
            }
        }
    }
}

private enum class Glyph { PREVIOUS, PLAY, PAUSE, NEXT }

/** Thin outline media icons, drawn so they match the light type rather than Material's filled ones. */
@Composable
private fun MediaButton(glyph: Glyph, label: String, circled: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        val ink = LocalInk.current
        Canvas(Modifier.size(if (circled) 40.dp else 18.dp)) {
            val w = 1.5.dp.toPx()
            val stroke = Stroke(w, join = StrokeJoin.Round, cap = StrokeCap.Round)
            if (circled) drawCircle(ink, radius = size.minDimension / 2 - w / 2, style = stroke)
            val g = 15.dp.toPx() // glyph box
            val cx = size.width / 2
            val cy = size.height / 2
            val top = cy - g * 0.45f
            val bottom = cy + g * 0.45f
            fun triangle(tipX: Float, baseX: Float) = Path().apply {
                moveTo(baseX, top); lineTo(tipX, cy); lineTo(baseX, bottom); close()
            }
            when (glyph) {
                Glyph.PLAY -> drawPath(triangle(cx + g * 0.45f, cx - g * 0.3f), ink, style = stroke)
                Glyph.PAUSE -> {
                    val barW = g * 0.2f
                    drawRect(ink, Offset(cx - g * 0.3f, top), Size(barW, bottom - top), style = stroke)
                    drawRect(ink, Offset(cx + g * 0.1f, top), Size(barW, bottom - top), style = stroke)
                }
                Glyph.NEXT -> {
                    drawPath(triangle(cx + g * 0.25f, cx - g * 0.45f), ink, style = stroke)
                    drawLine(ink, Offset(cx + g * 0.45f, top), Offset(cx + g * 0.45f, bottom), w, StrokeCap.Round)
                }
                Glyph.PREVIOUS -> {
                    drawPath(triangle(cx - g * 0.25f, cx + g * 0.45f), ink, style = stroke)
                    drawLine(ink, Offset(cx - g * 0.45f, top), Offset(cx - g * 0.45f, bottom), w, StrokeCap.Round)
                }
            }
        }
    }
}
