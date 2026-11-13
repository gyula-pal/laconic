package cc.gyula.wave.ui.clock

import android.os.SystemClock
import android.Manifest
import android.content.pm.PackageManager
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import cc.gyula.wave.AppsViewModel
import cc.gyula.wave.core.BandLook
import cc.gyula.wave.core.BandPosition
import cc.gyula.wave.core.ClockItem
import cc.gyula.wave.core.Polled
import cc.gyula.wave.core.alarmText
import cc.gyula.wave.core.dateText
import cc.gyula.wave.core.eventText
import cc.gyula.wave.core.fetchWait
import cc.gyula.wave.core.priceLabel
import cc.gyula.wave.core.timeFormat
import cc.gyula.wave.core.valueFor
import cc.gyula.wave.core.weekday
import cc.gyula.wave.data.EventState
import cc.gyula.wave.data.fetchPrice
import cc.gyula.wave.data.fetchTemperature
import cc.gyula.wave.data.nextEvent
import cc.gyula.wave.data.size
import cc.gyula.wave.ui.theme.Josefin
import cc.gyula.wave.ui.theme.phase
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import cc.gyula.wave.ui.theme.LocalInk
import cc.gyula.wave.ui.theme.LocalBackdrop

@Composable
private fun rememberMinuteTicker(): LocalDateTime {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val t = LocalDateTime.now()
                now = t
                delay(60_000L - t.second * 1000L - t.nano / 1_000_000L)
            }
        }
    }
    return now
}

/** Vertical centre of the cut-out band, as a fraction of the weekday text's height. */
private const val BAND_CENTER = 0.41f

private const val BAND_HEIGHT = 0.34f

/** Open-Meteo's current temperature only changes about every 15 minutes, so asking more often gains nothing. */
private const val WEATHER_EVERY_MS = 15 * 60_000L

private const val PRICE_EVERY_MS = 5 * 60_000L

/** A calendar query is cheap and local; this is how late a newly added event may show up. */
private const val EVENT_EVERY_MS = 5 * 60_000L

/**
 * Polls [fetch] every [everyMs] while the home screen is visible, never more often than that across
 * visits: the last answer and its time live in [store], outside the composition that a letter list or
 * search takes away. A failed fetch shows [failed] rather than a stale value — an old BTC price is worse
 * than none — and is tried again after a minute.
 */
@Composable
internal fun <T> rememberPolled(
    store: MutableStateFlow<Polled<T>?>,
    enabled: Boolean,
    everyMs: Long,
    failed: T,
    key: Any? = null, // a change (e.g. another ticker) fetches again straight away
    fetch: suspend () -> T,
): T? {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val last by store.collectAsStateWithLifecycle()
    LaunchedEffect(enabled, lifecycle, key) {
        if (!enabled) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                // Uptime, not the wall clock: setting the clock back must not push the next fetch out by the same amount.
                val wait = fetchWait(store.value, key, SystemClock.elapsedRealtime(), everyMs)
                if (wait > 0) delay(wait)
                val (value, ok) = try {
                    fetch() to true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed to false
                }
                store.value = Polled(key, value, SystemClock.elapsedRealtime(), ok)
            }
        }
    }
    return last.valueFor(key)
}

/**
 * The clock: the weekday with its band, and the next event under it. Gathers what the band shows —
 * time, alarm, weather, date, price — and polls weather and price only while home is on screen.
 */
@Composable
internal fun Clock(vm: AppsViewModel, enter: () -> Float, onLongPress: () -> Unit) {
    val context = LocalContext.current
    val items by vm.settings.clockItems.flow.collectAsStateWithLifecycle()
    val ticker by vm.settings.ticker.flow.collectAsStateWithLifecycle()
    val bandPosition by vm.settings.bandPosition.flow.collectAsStateWithLifecycle()
    val bandLook by vm.settings.bandLook.flow.collectAsStateWithLifecycle()
    val now = rememberMinuteTicker()
    fun hasLocation() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    var locationGranted by remember { mutableStateOf(hasLocation()) }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { locationGranted = it }
    val temperature = rememberPolled(vm.lastWeather, ClockItem.WEATHER in items && locationGranted, WEATHER_EVERY_MS, "—°C") {
        fetchTemperature(context)
    }
    val price = rememberPolled(vm.lastPrice, ClockItem.BTC in items, PRICE_EVERY_MS, priceLabel(ticker, "—"), key = ticker) {
        fetchPrice(ticker)
    }
    val time = timeFormat(DateFormat.is24HourFormat(context))
    // Read again every minute and each time home comes back, so a changed alarm shows straight away.
    val alarm = remember(now) { vm.system.nextAlarm() }
    val texts = ClockItem.entries.filter { it in items && it.onBand }.mapNotNull {
        when (it) {
            ClockItem.TIME -> now.format(time)
            ClockItem.ALARM -> alarm?.let { at ->
                ALARM_MARK + alarmText(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDateTime(), now, time)
            }
            ClockItem.WEATHER -> if (locationGranted) temperature else "?°C" // tap it to allow location
            ClockItem.DATE -> dateText(now)
            ClockItem.BTC -> price
            ClockItem.EVENT, ClockItem.MUSIC -> null
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ClockFace(
            weekday(now), texts, bandPosition, bandLook, enter,
            onClick = vm.system::openAlarms,
            onLongClick = onLongPress,
            onAskLocation = { askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
        )
        if (ClockItem.EVENT in items) {
            NextEventLine(vm.lastEvent, now, BandStyle.copy(fontSize = 13.5.sp), enter, onOpen = vm.system::openEvent)
        }
    }
}

/** The band's type: thin spaced capitals. */
private val BandStyle: TextStyle
    @Composable get() {
        val ink = LocalInk.current
        return remember(ink) {
            TextStyle(fontFamily = Josefin, color = ink, fontSize = 15.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Normal)
        }
    }

/**
 * Big thin weekday with a band through, above or below it, drawn plain, inverted or between two lines;
 * [texts] run along the band. Stateless: it draws what it's given, so it can be previewed and screenshot-tested.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClockFace(
    day: String,
    texts: List<String>,
    bandPosition: BandPosition,
    bandLook: BandLook,
    enter: () -> Float = { 1f },
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onAskLocation: () -> Unit = {},
) {
    // Where the band sits, from the last layout; only the inverted and below styles draw it.
    var bandY by remember { mutableFloatStateOf(0f) }
    var dayTop by remember { mutableFloatStateOf(0f) }
    var bandHeightPx by remember { mutableFloatStateOf(0f) }
    val bandScale = if (bandLook == BandLook.LINES) 1.3f else 1f // lines want some air around the items
    val small = BandStyle
    val ink = LocalInk.current
    val backdrop = LocalBackdrop.current
    Box {
        Layout(
            content = {
                Text(
                    day,
                    color = ink,
                    fontFamily = Josefin,
                    fontWeight = FontWeight.ExtraLight,
                    fontSize = 80.sp,
                    letterSpacing = 4.sp,
                    modifier = Modifier
                        // Coming home, the day comes out of the band: rising up out of it when the band is
                        // below, dropping down out of it when above, and opening up and down from it when
                        // it runs through. The clip stays put (it's outside the moving layer); the day moves.
                        .drawWithContent {
                            val p = phase(enter(), 0.2f, 0.45f)
                            val band = bandY - dayTop
                            when (bandPosition) {
                                BandPosition.BELOW -> clipRect(top = -size.height, bottom = band - bandHeightPx / 2) {
                                    this@drawWithContent.drawContent()
                                }
                                BandPosition.ABOVE -> clipRect(top = band + bandHeightPx / 2, bottom = size.height * 2) {
                                    this@drawWithContent.drawContent()
                                }
                                BandPosition.THROUGH -> {
                                    val reach = p * maxOf(band, size.height - band)
                                    clipRect(top = band - reach, bottom = band + reach) { this@drawWithContent.drawContent() }
                                }
                            }
                        }
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                            val p = phase(enter(), 0.2f, 0.45f)
                            translationY = (1f - p) * size.height * when (bandPosition) {
                                BandPosition.BELOW -> 1f
                                BandPosition.ABOVE -> -1f
                                BandPosition.THROUGH -> 0f
                            }
                        }
                        .drawWithContent {
                            drawContent()
                            if (bandPosition == BandPosition.THROUGH) drawRect( // the gap the band items sit in
                                Color.Black,
                                topLeft = Offset(0f, size.height * (BAND_CENTER - BAND_HEIGHT * bandScale / 2)),
                                size = Size(size.width, size.height * BAND_HEIGHT * bandScale),
                                blendMode = BlendMode.Clear,
                            )
                        },
                )
                texts.forEach {
                    Box(Modifier.graphicsLayer { alpha = phase(enter(), 0.3f, 0.35f) }) {
                        when {
                            it.startsWith('₿') -> BitcoinText(it.drop(1), small)
                            it.startsWith(ALARM_MARK) -> AlarmText(it.drop(1), small)
                            it == "?°C" -> Text(
                                it,
                                style = small,
                                modifier = Modifier.clickable(onClickLabel = "Allow location for weather", onClick = onAskLocation),
                            )
                            else -> Text(it, style = small)
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "Alarms",
                    onClick = onClick,
                    onLongClickLabel = "Settings",
                    onLongClick = onLongClick,
                )
                // The band's look, screen-wide. Inverted is a white band drawn over everything as a difference,
                // so the white items it covers come out black.
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    val top = bandY - bandHeightPx / 2
                    val bottom = bandY + bandHeightPx / 2
                    // Coming home, the band grows out from the middle to the screen edges.
                    val width = size.width * phase(enter(), 0f, 0.35f)
                    val left = (size.width - width) / 2
                    when (bandLook) {
                        BandLook.PLAIN -> {}
                        BandLook.INVERTED -> {
                            val topLeft = Offset(left, top)
                            val size = Size(width, bandHeightPx)
                            if (ink == Color.White && backdrop == null) {
                                drawRect(Color.White, topLeft, size, blendMode = BlendMode.Difference)
                            } else {
                                // Whatever is drawn in the band takes the backdrop colour (black over the wallpaper)
                                // and the empty rest of it fills with ink, behind.
                                drawRect(backdrop ?: Color.Black, topLeft, size, blendMode = BlendMode.SrcIn)
                                drawRect(ink, topLeft, size, blendMode = BlendMode.DstOver)
                            }
                        }
                        BandLook.LINES -> {
                            // Whole pixels, so both lines come out exactly as thick as each other.
                            val stroke = Size(width, 1.dp.roundToPx().toFloat())
                            drawRect(ink, Offset(left, top.roundToInt().toFloat()), stroke)
                            drawRect(ink, Offset(left, (bottom.roundToInt() - stroke.height)), stroke)
                        }
                    }
                },
        ) { measurables, constraints ->
            val big = measurables[0].measure(constraints.copy(minWidth = 0))
            val items = measurables.drop(1).map { it.measure(Constraints()) }
            val w = constraints.maxWidth
            val margin = 20.dp.roundToPx()
            // Outside the day, the band is a little slimmer: it has no letters to cut.
            bandHeightPx = big.height * BAND_HEIGHT * bandScale * (if (bandPosition == BandPosition.THROUGH) 1f else 0.8f)
            val band = bandHeightPx.roundToInt()
            val gap = 6.dp.roundToPx()
            val (dayY, y, height) = when (bandPosition) {
                BandPosition.ABOVE -> Triple(band + gap, band / 2, band + gap + big.height)
                BandPosition.THROUGH -> Triple(0, (big.height * BAND_CENTER).roundToInt(), big.height)
                BandPosition.BELOW -> Triple(0, big.height + gap + band / 2, big.height + gap + band)
            }
            bandY = y.toFloat()
            dayTop = dayY.toFloat()
            // Items are centred on their capitals, not on the font's line box, which has more room below
            // the baseline than above the caps and left them sitting ~2 dp high.
            val capHalf = (small.fontSize.toPx() * JOSEFIN_CAP / 2).roundToInt()
            fun Placeable.top(): Int {
                val baseline = this[FirstBaseline]
                return if (baseline == AlignmentLine.Unspecified) y - height / 2 else y + capHalf - baseline
            }
            layout(w, height) {
                big.place((w - big.width) / 2, dayY)
                if (items.size == 3) {
                    // Equal ~64dp gaps either side of the centred middle item, whatever the
                    // outer widths, tightened only if the longer outer value (BTC) would reach the edge.
                    val (l, m, r) = items
                    val mLeft = (w - m.width) / 2
                    val mRight = mLeft + m.width
                    val g = minOf(64.dp.roundToPx(), mLeft - l.width - margin, w - margin - mRight - r.width)
                        .coerceAtLeast(8.dp.roundToPx())
                    l.place(mLeft - g - l.width, l.top())
                    m.place(mLeft, m.top())
                    r.place(mRight + g, r.top())
                } else if (items.size == 1) {
                    items[0].place((w - items[0].width) / 2, items[0].top())
                } else if (items.isNotEmpty()) {
                    // 2, 4 or 5 items: spread evenly across 1.45x the weekday's width, or wider if they'd crowd.
                    val crowded = (items.sumOf { it.width } + 24.dp.roundToPx() * (items.size - 1)) / 2
                    val half = maxOf((big.width * 1.45f / 2).roundToInt(), crowded).coerceAtMost(w / 2 - margin)
                    val step = (2 * half - items.sumOf { it.width }) / (items.size - 1)
                    var x = w / 2 - half
                    items.forEach { p ->
                        p.place(x, p.top())
                        x += p.width + step
                    }
                }
            }
        }
    }
}

/**
 * "DENTIST · 18:00" under the weekday — the next timed event from now to the end of tomorrow.
 * Until calendar access is granted it says so and asks on tap, rather than silently showing nothing.
 */
@Composable
private fun NextEventLine(
    polled: MutableStateFlow<Polled<EventState>?>,
    now: LocalDateTime,
    style: TextStyle,
    enter: () -> Float,
    onOpen: (id: Long, begin: Long, end: Long) -> Unit,
) {
    val context = LocalContext.current
    fun hasAccess() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
        PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(hasAccess()) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val event = rememberPolled(polled, granted, EVENT_EVERY_MS, EventState.Failed) { nextEvent(context) }

    val text = if (!granted) "TAP TO SHOW NEXT EVENT" else when (event) {
        null, EventState.None -> null
        EventState.Failed -> "CALENDAR —"
        is EventState.Next -> eventText(
            event.title,
            Instant.ofEpochMilli(event.begin).atZone(ZoneId.systemDefault()).toLocalDateTime(),
            now,
            timeFormat(DateFormat.is24HourFormat(context)),
        )
    } ?: return

    Text(
        text,
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .padding(top = 14.dp, start = 40.dp, end = 40.dp)
            // Coming home, it opens out from the middle after the band, the way the band does.
            .drawWithContent {
                val p = phase(enter(), 0.45f, 0.4f)
                val half = size.width * p / 2
                clipRect(left = size.width / 2 - half, right = size.width / 2 + half) { this@drawWithContent.drawContent() }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = if (granted) "Open event" else "Allow calendar",
            ) {
                when {
                    !granted -> ask.launch(Manifest.permission.READ_CALENDAR)
                    event is EventState.Next -> onOpen(event.id, event.begin, event.end)
                }
            },
    )
}

/** Marks the alarm's text in the band's list, which is otherwise plain strings. */
internal const val ALARM_MARK = '\u23F0'

/** "6:30" after a small drawn alarm clock, outlined at the weight of the band's type. */
@Composable
private fun AlarmText(value: String, style: TextStyle) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clearAndSetSemantics { contentDescription = "Alarm $value" }) {
        Canvas(Modifier.size(12.dp)) {
            val w = 1.2.dp.toPx()
            val r = size.minDimension * 0.4f
            val c = Offset(size.width / 2, size.height / 2 + size.height * 0.06f)
            val stroke = Stroke(w, cap = StrokeCap.Round)
            drawCircle(style.color, r, c, style = stroke)
            drawLine(style.color, c, c + Offset(0f, -r * 0.55f), w, StrokeCap.Round)
            drawLine(style.color, c, c + Offset(r * 0.45f, 0f), w, StrokeCap.Round)
            // The two bells on top.
            for (side in floatArrayOf(-1f, 1f)) {
                drawArc(
                    style.color, startAngle = if (side < 0) 200f else 280f, sweepAngle = 60f, useCenter = false,
                    topLeft = c + Offset(side * r * 0.75f - r * 0.5f, -r * 1.35f), size = Size(r, r), style = stroke,
                )
            }
        }
        Text(" $value", style = style)
    }
}

// Josefin Sans metrics (units per em = 1000): cap height 702; "B" advance 573.
private const val JOSEFIN_CAP = 0.702f

private const val JOSEFIN_B_ADVANCE = 0.573f

/**
 * "₿80.4K". Neither bundled font has ₿, and a fallback-font glyph would look pasted in, so this is
 * Josefin's own B with the two short strokes drawn above and below it, at the B's stem weight.
 */
@Composable
private fun BitcoinText(value: String, style: TextStyle) {
    var baseline by remember { mutableFloatStateOf(0f) }
    Row(Modifier.clearAndSetSemantics { contentDescription = "Bitcoin $value" }) {
        Text(
            "B",
            style = style,
            onTextLayout = { baseline = it.firstBaseline },
            modifier = Modifier.drawWithContent {
                drawContent()
                val em = style.fontSize.toPx()
                // Letter spacing is split evenly either side of the glyph, so the B starts half of it in.
                val originX = (size.width - em * JOSEFIN_B_ADVANCE) / 2
                val capTop = baseline - em * JOSEFIN_CAP
                val stroke = em * 0.06f
                val reach = em * 0.14f
                for (unitsX in floatArrayOf(0.200f, 0.330f)) {
                    val x = originX + em * unitsX
                    drawLine(style.color, Offset(x, capTop - reach), Offset(x, capTop + stroke), stroke)
                    drawLine(style.color, Offset(x, baseline - stroke), Offset(x, baseline + reach), stroke)
                }
            },
        )
        Text(" $value", style = style) // same gap as the space in "26 OCT"
    }
}
