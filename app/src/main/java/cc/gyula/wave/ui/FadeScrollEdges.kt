package cc.gyula.wave.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Fades the content out at whichever edge has more to scroll to, so a cut-off list reads as a list: top and
 * bottom, or left and right for a [sideways] scroll.
 */
internal fun Modifier.fadeScrollEdges(scroll: ScrollState, sideways: Boolean = false) = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val fade = 32.dp.toPx()
        if (sideways) {
            if (scroll.canScrollBackward) drawRect(
                Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = fade),
                size = Size(fade, size.height),
                blendMode = BlendMode.DstIn,
            )
            if (scroll.canScrollForward) drawRect(
                Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - fade, endX = size.width),
                topLeft = Offset(size.width - fade, 0f),
                size = Size(fade, size.height),
                blendMode = BlendMode.DstIn,
            )
            return@drawWithContent
        }
        if (scroll.canScrollBackward) drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = fade),
            size = Size(size.width, fade),
            blendMode = BlendMode.DstIn,
        )
        if (scroll.canScrollForward) drawRect(
            Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - fade, endY = size.height),
            topLeft = Offset(0f, size.height - fade),
            size = Size(size.width, fade),
            blendMode = BlendMode.DstIn,
        )
    }
