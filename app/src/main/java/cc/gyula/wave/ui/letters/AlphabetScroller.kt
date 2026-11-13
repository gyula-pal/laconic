package cc.gyula.wave.ui.letters

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.gyula.wave.data.size
import cc.gyula.wave.ui.theme.Kumbh
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import cc.gyula.wave.ui.theme.LocalInk

/** Strip entry above A: picking it shows the pinned apps (the home view). */
const val FAVORITES = '☆'

private val MaxSlot = 19.dp

private val Bubble = 46.dp

// The line box trimmed to the letters themselves, so a capital is centred on its stripe; with the
// font's own padding it sat about 2 dp low.
private val LetterStyle: TextStyle
    @Composable get() {
        val ink = LocalInk.current
        return remember(ink) {
            TextStyle(
                color = ink,
                fontFamily = Kumbh,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 16.sp,
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            )
        }
    }

/** A five-pointed outline star of outer radius [r] around [c]. */
private fun DrawScope.drawStar(c: Offset, r: Float, stroke: Float, color: Color) {
    val path = Path()
    for (k in 0 until 10) {
        val radius = if (k % 2 == 0) r else r * 0.45f
        val a = (-90.0 + k * 36.0) * PI / 180
        val p = Offset(c.x + radius * cos(a).toFloat(), c.y + radius * sin(a).toFloat() + r * 0.1f)
        if (k == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    drawPath(path, color, style = Stroke(stroke, join = StrokeJoin.Round))
}

/**
 * Edge letter strip. While a finger is down, the whole strip bows out to the left in a wide
 * gaussian wave centred on the finger, and a grey bubble shows the picked letter.
 *
 * Sliding the finger left, off the strip, freezes the letter so it can travel onto the app rows;
 * [onDrag] / [onRelease] report that position (root coordinates) so the caller can hover / launch.
 */
@Composable
fun AlphabetScroller(
    letters: List<Char>,
    modifier: Modifier = Modifier,
    onLeft: Boolean = false, // left-handed: on the left edge, bowing out to the right
    hidden: Boolean = false, // only a dot until touched; the letters fade in with the wave
    edgeGap: Dp = 7.dp, // strip to screen edge, matching the caller's padding: the stripes start there
    stripWidth: Dp = 44.dp,
    onLetter: (letter: Char, centerY: Float) -> Unit,
    onDrag: (pos: Offset, offStrip: Boolean) -> Unit,
    onRelease: (pos: Offset, offStrip: Boolean) -> Unit,
) {
    if (letters.isEmpty()) return
    val view = LocalView.current
    val dir = if (onLeft) 1f else -1f // which way the wave bows
    val onLetterLatest by rememberUpdatedState(onLetter)
    val onDragLatest by rememberUpdatedState(onDrag)
    val onReleaseLatest by rememberUpdatedState(onRelease)

    // 19dp per letter, squeezed on short screens so the strip stays in the lower ~55%.
    val density = LocalDensity.current
    val screenHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val Slot = minOf(MaxSlot, screenHeight * 0.55f / letters.size)
    val slotPx = with(density) { Slot.toPx() }
    val maxShift = with(density) { 60.dp.toPx() }
    val offStripPx = with(density) { 32.dp.toPx() }
    val bubbleGap = with(density) { 44.dp.toPx() }
    val halfStrip = with(density) { (stripWidth / 2).toPx() }
    val halfBubble = with(density) { (Bubble / 2).toPx() }
    val edgePx = with(density) { edgeGap.toPx() }
    val dotRadius = with(density) { 3.5.dp.toPx() }
    val barLead = with(density) { 15.dp.toPx() } // how far a stripe reaches past its letter
    val barGap = with(density) { 3.dp.toPx() }
    val ink = LocalInk.current

    var origin by remember { mutableStateOf(Offset.Zero) }
    var touching by remember { mutableStateOf(false) }
    var waveY by remember { mutableFloatStateOf(0f) } // kept after lift so the wave settles in place
    var selected by remember { mutableStateOf<Char?>(null) }
    val strength by animateFloatAsState(
        targetValue = if (touching) 1f else 0f,
        animationSpec = tween(if (touching) 160 else 320),
        label = "wave",
    )
    fun bumpAt(i: Int): Float {
        val d = (slotPx * (i + 0.5f) - waveY) / (slotPx * 5f)
        return exp(-0.5f * d * d) * strength
    }

    Box(
        modifier
            .width(stripWidth)
            .height(Slot * letters.size)
            .onGloballyPositioned { origin = it.positionInRoot() }
            // Each letter rides out on its own stripe from the screen edge, longer the nearer the finger.
            .drawBehind {
                if (hidden && strength < 0.99f) {
                    drawCircle(ink.copy(alpha = 0.6f * (1f - strength)), dotRadius, Offset(halfStrip, size.height / 2))
                }
                if (strength < 0.01f) return@drawBehind
                val h = slotPx - barGap
                letters.indices.forEach { i ->
                    val bump = bumpAt(i)
                    if (bump < 0.02f) return@forEach
                    val tip = halfStrip + dir * (maxShift * bump + barLead)
                    val edge = if (onLeft) -edgePx else size.width + edgePx
                    drawRoundRect(
                        color = ink.copy(alpha = (if (letters[i] == selected) 0.32f else 0.16f) * bump),
                        topLeft = Offset(minOf(tip, edge), slotPx * i + barGap / 2),
                        size = Size(abs(edge - tip), h),
                        cornerRadius = CornerRadius(h / 2),
                    )
                }
            }
            .pointerInput(letters, onLeft) {
                awaitEachGesture {
                    var last: Char? = null
                    fun pick(y: Float) {
                        waveY = y.coerceIn(0f, size.height.toFloat())
                        val i = (y / slotPx).toInt().coerceIn(0, letters.lastIndex)
                        if (letters[i] != last) {
                            last = letters[i]
                            selected = letters[i]
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onLetterLatest(letters[i], origin.y + slotPx * (i + 0.5f))
                        }
                    }

                    val down = awaitFirstDown()
                    down.consume()
                    touching = true
                    try {
                        pick(down.position.y)
                        var pos = down.position
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            pos = change.position
                            if (!change.pressed) break
                            change.consume()
                            val off = if (onLeft) pos.x > size.width + offStripPx else pos.x < -offStripPx
                            if (!off) pick(pos.y)
                            onDragLatest(origin + pos, off)
                        }
                        onReleaseLatest(origin + pos, if (onLeft) pos.x > size.width + offStripPx else pos.x < -offStripPx)
                    } finally {
                        touching = false
                    }
                }
            }
    ) {
        letters.forEachIndexed { i, letter ->
            val cell = Modifier
                .offset { IntOffset((dir * maxShift * bumpAt(i)).roundToInt(), (slotPx * i).roundToInt()) }
                // For a screen reader, which can't drag: each letter opens its list when activated. Outside the
                // fade below, so the hidden strip's letters can still be found.
                .semantics {
                    onClick(if (letter == FAVORITES) "Show pinned apps" else "Open $letter") {
                        onLetterLatest(letter, origin.y + slotPx * (i + 0.5f))
                        true
                    }
                    if (letter == FAVORITES) contentDescription = "Pinned apps"
                }
                .graphicsLayer { if (hidden) alpha = strength }
                .width(stripWidth)
                .height(Slot)
            if (letter == FAVORITES) {
                // Drawn: Kumbh has no ☆, and the fallback font's star sits low on its stripe.
                Canvas(cell) { drawStar(center, 6.5.dp.toPx(), 1.2.dp.toPx(), ink) }
            } else {
                Text(
                    text = letter.toString(),
                    style = LetterStyle,
                    textAlign = TextAlign.Center,
                    modifier = cell.wrapContentHeight(Alignment.CenterVertically),
                )
            }
        }

        val sel = selected
        val selIndex = sel?.let(letters::indexOf) ?: -1
        if (selIndex >= 0 && strength > 0.01f) {
            Box(
                Modifier
                    .offset {
                        val letterX = halfStrip + dir * maxShift * bumpAt(selIndex)
                        IntOffset(
                            (letterX + dir * bubbleGap - halfBubble).roundToInt(),
                            (slotPx * (selIndex + 0.5f) - halfBubble).roundToInt(),
                        )
                    }
                    .wrapContentSize(Alignment.TopStart, unbounded = true) // wider than the strip
                    .size(Bubble)
                    .graphicsLayer { alpha = strength }
                    .background(Color(0xFFBDBDBD), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (sel == FAVORITES) {
                    Canvas(Modifier.size(Bubble)) { drawStar(center, 10.dp.toPx(), 1.6.dp.toPx(), Color(0xFF424242)) }
                } else {
                    Text(sel.toString(), color = Color(0xFF424242), fontFamily = Kumbh, fontSize = 26.sp)
                }
            }
        }
    }
}
