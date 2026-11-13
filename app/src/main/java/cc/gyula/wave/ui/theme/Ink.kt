package cc.gyula.wave.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter

/** What Laconic draws in on home: text, icons, lines. White unless the owner picked a colour. */
val LocalInk = compositionLocalOf { Color.White }

/** The solid colour drawn behind home, or null to leave the wallpaper showing. */
val LocalBackdrop = compositionLocalOf<Color?> { null }

/** What goes on top of something filled with the ink: the backdrop colour, or black over the wallpaper. */
val onInk: Color
    @Composable @ReadOnlyComposable get() = LocalBackdrop.current ?: Color.Black

/** Tints the white (or grey) app icons with the ink; null for white ink, so the default draws untouched. */
@Composable
fun inkFilter(): ColorFilter? = LocalInk.current.takeIf { it != Color.White }?.let { ColorFilter.tint(it, BlendMode.Modulate) }
