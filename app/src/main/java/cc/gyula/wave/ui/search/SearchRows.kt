package cc.gyula.wave.ui.search

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.gyula.wave.data.Happening
import cc.gyula.wave.data.Person
import cc.gyula.wave.data.Reach
import cc.gyula.wave.data.ReachKind
import cc.gyula.wave.ui.apps.LocalRowInset
import cc.gyula.wave.ui.apps.LocalShowIcons
import cc.gyula.wave.ui.apps.RowHeight
import cc.gyula.wave.ui.theme.Label
import cc.gyula.wave.ui.theme.LocalInk
import cc.gyula.wave.ui.theme.inkFilter

/** Width of an app row's icon slot plus its notification-dot gap: where names start when icons are on. */
private val IconSlot = 40.dp + 22.dp

/** Where a name starts in a row: past the icon slot when icons are on. */
@Composable
internal fun nameStart(): Dp = LocalRowInset.current + if (LocalShowIcons.current) IconSlot else 0.dp

@Composable
private fun faint(alpha: Float = 0.4f): Color = LocalInk.current.copy(alpha = alpha)

/** A section's word, small and spaced, then a hairline to the end of the row. */
@Composable
internal fun SectionHeader(word: String) {
    Row(
        Modifier.fillMaxWidth().padding(start = 52.dp, end = 24.dp, top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(word, style = Label.copy(color = faint(0.4f)), fontSize = 12.sp, letterSpacing = 1.5.sp)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f).height(1.dp).background(faint(0.18f)))
    }
}

/** "+N more" under a capped section. */
@Composable
internal fun MoreRow(count: Int, onClick: () -> Unit) {
    Text(
        "+$count more",
        style = Label.copy(color = faint(0.5f)),
        fontSize = 14.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = nameStart(), end = 24.dp, top = 10.dp, bottom = 10.dp),
    )
}

/** A source that is switched on but not allowed: one line to ask, and a way to stop asking. */
@Composable
internal fun AllowRow(word: String, tag: String, onAllow: () -> Unit, onHide: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(RowHeight), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.weight(1f).fillMaxHeight().testTag("allow:$tag").clickable(onClick = onAllow).padding(start = nameStart()),
            contentAlignment = Alignment.CenterStart,
        ) { Text("$word · allow", style = Label.copy(color = faint(0.5f)), fontSize = 16.sp) }
        Box(
            Modifier.fillMaxHeight().testTag("hide:$tag").clickable(onClick = onHide).padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) { Text("hide", style = Label.copy(color = faint(0.4f)), fontSize = 14.sp) }
    }
}

/** Marks the row Enter would open, at its trailing edge. */
@Composable
internal fun EnterGlyph(what: String, modifier: Modifier = Modifier) {
    val color = faint(0.4f)
    Canvas(modifier.size(16.dp).testTag("enter:$what").semantics { contentDescription = "Enter opens this" }) {
        val w = 1.5.dp.toPx()
        val path = Path().apply {
            moveTo(size.width, 0f)
            lineTo(size.width, size.height * 0.7f)
            lineTo(size.width * 0.05f, size.height * 0.7f)
            moveTo(size.width * 0.35f, size.height * 0.4f)
            lineTo(size.width * 0.05f, size.height * 0.7f)
            lineTo(size.width * 0.35f, size.height)
        }
        drawPath(path, color, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Material's "call" handset (Apache 2.0) drawn as a thin outline, so it reads as a phone at 20 dp. */
private val HANDSET = PathParser().parsePathString(
    "M20.01,15.38c-1.23,0 -2.42,-0.2 -3.53,-0.56 -0.35,-0.12 -0.74,-0.03 -1.01,0.24l-1.57,1.97c-2.83,-1.35 " +
        "-5.48,-3.9 -6.89,-6.83l1.95,-1.66c0.27,-0.28 0.35,-0.67 0.24,-1.02 -0.37,-1.11 -0.56,-2.3 -0.56,-3.53 " +
        "0,-0.54 -0.45,-0.99 -0.99,-0.99H4.19C3.65,3 3,3.24 3,3.99 3,13.28 10.73,21 20.01,21c0.71,0 0.99,-0.63 " +
        "0.99,-1.18v-3.45c0,-0.54 -0.45,-0.99 -0.99,-0.99z",
).toPath()

private fun DrawScope.drawPhone(color: Color) {
    val k = size.minDimension / 24f
    scale(k, k, pivot = Offset.Zero) {
        drawPath(HANDSET, color, style = Stroke(1.5.dp.toPx() / k, join = StrokeJoin.Round))
    }
}

private fun DrawScope.drawBubble(color: Color) {
    val w = size.width
    val h = size.height
    val stroke = 1.5.dp.toPx()
    drawRoundRect(color, Offset(.05f * w, .12f * h), Size(.9f * w, .60f * h), CornerRadius(3.dp.toPx()), style = Stroke(stroke))
    val tail = Path().apply {
        moveTo(.25f * w, .72f * h)
        lineTo(.25f * w, .92f * h)
        lineTo(.5f * w, .72f * h)
    }
    drawPath(tail, color, style = Stroke(stroke, join = StrokeJoin.Round))
}

/** A drawn glyph on a 48 dp touch target. */
@Composable
private fun GlyphButton(tag: String, description: String, draw: DrawScope.(Color) -> Unit, onClick: () -> Unit) {
    val color = faint(0.7f)
    Box(
        Modifier
            .size(48.dp)
            .testTag(tag)
            .semantics { contentDescription = description; role = Role.Button }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Canvas(Modifier.size(20.dp)) { draw(color) } }
}

/** A thin circle with the name's first letter, as big as an app icon. */
@Composable
private fun Initial(name: String) {
    val ink = LocalInk.current
    Box(Modifier.size(40.dp).border(1.dp, ink, CircleShape), contentAlignment = Alignment.Center) {
        Text(name.trim().take(1).uppercase(), style = Label, fontSize = 16.sp)
    }
}

/**
 * A contact in the style of an app row. Tap opens it in place with every way to reach them; long-press opens
 * it in Contacts. [reach] is null until it has loaded.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ContactRow(
    person: Person,
    reach: List<Reach>?,
    expanded: Boolean,
    enter: Boolean,
    loadAppIcon: suspend (String) -> ImageBitmap?,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onDial: (String) -> Unit,
    onMessage: (String) -> Unit,
    onEmail: (String) -> Unit,
    onReach: (Reach) -> Unit,
) {
    val phone = reach?.firstOrNull { it.kind == ReachKind.PHONE }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(RowHeight), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("contact:${person.id}")
                    .combinedClickable(onClick = onToggle, onLongClick = onOpen)
                    .padding(start = LocalRowInset.current),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (LocalShowIcons.current) {
                    Initial(person.name)
                    Spacer(Modifier.width(22.dp))
                }
                Text(person.name, style = Label, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (enter) EnterGlyph("contact", Modifier.padding(start = 8.dp, end = 16.dp)) // clear of the call button
            if (phone != null) GlyphButton("dial:${person.id}", "Call ${person.name}", { drawPhone(it) }) { onDial(phone.value) }
            Spacer(Modifier.width(12.dp))
        }
        if (expanded && reach != null) {
            val start = nameStart()
            reach.forEach { r ->
                when (r.kind) {
                    ReachKind.PHONE -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        ReachLine(Modifier.weight(1f), start, { onDial(r.value) }) {
                            Text(r.value, style = Label, fontSize = 16.sp, maxLines = 1)
                            if (r.label.isNotEmpty()) Faint(r.label)
                        }
                        GlyphButton("message:${r.value}", "Message ${r.value}", { drawBubble(it) }) { onMessage(r.value) }
                        Spacer(Modifier.width(12.dp))
                    }
                    ReachKind.EMAIL -> ReachLine(Modifier.fillMaxWidth(), start, { onEmail(r.value) }) {
                        Text(r.value, style = Label, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (r.label.isNotEmpty()) Faint(r.label)
                    }
                    ReachKind.APP -> ReachLine(Modifier.fillMaxWidth(), start, { onReach(r) }) {
                        val icon by produceState<ImageBitmap?>(null, r.pkg) { value = if (r.pkg.isBlank()) null else loadAppIcon(r.pkg) }
                        icon?.let {
                            Image(it, contentDescription = null, colorFilter = inkFilter(), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(r.value, style = Label, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            ReachLine(Modifier.fillMaxWidth(), start, onOpen) {
                Text("open contact ›", style = Label.copy(color = faint(0.5f)), fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ReachLine(modifier: Modifier, start: Dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        modifier
            .defaultMinSize(minHeight = 44.dp)
            .clickable(onClick = onClick)
            .padding(start = start, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun Faint(text: String) {
    Spacer(Modifier.width(8.dp))
    Text(text, style = Label.copy(color = faint(0.4f)), fontSize = 14.sp, maxLines = 1)
}

/** An event: its title, then when, faint. */
@Composable
internal fun EventRow(happening: Happening, whenText: String, enter: Boolean, onClick: () -> Unit) {
    val dim = faint(0.4f)
    Row(
        Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .testTag("event:${happening.eventId}")
            .clickable(onClick = onClick)
            .padding(start = nameStart(), end = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            buildAnnotatedString {
                append(happening.title)
                withStyle(SpanStyle(color = dim)) { append(" · $whenText") }
            },
            style = Label,
            fontSize = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (enter) EnterGlyph("event", Modifier.padding(start = 8.dp))
    }
}
