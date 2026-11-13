package cc.gyula.wave.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing

/** This part's own 0 → 1, eased, for a part that starts at [start] and lasts [length] of the whole. */
internal fun phase(t: Float, start: Float, length: Float): Float =
    FastOutSlowInEasing.transform(((t - start) / length).coerceIn(0f, 1f))
