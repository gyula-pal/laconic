@file:OptIn(ExperimentalTextApi::class)

package cc.gyula.wave.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.gyula.wave.R


// Both are variable fonts (Google Fonts, OFL) — each weight is the same file with the wght axis set.
private fun weights(res: Int, vararg w: Int) = FontFamily(
    w.map { Font(res, FontWeight(it), variationSettings = FontVariation.Settings(FontVariation.weight(it))) }
)

/** Thin geometric caps — the big weekday and the time/date line. */
val Josefin = weights(R.font.josefin_sans, 100, 200, 300, 400, 600)

/** App labels and the letter strip. */
val Kumbh = weights(R.font.kumbh_sans, 300, 400, 500, 600)

/** Menus and dialogs in Laconic's own type and near-black greys, instead of Material's defaults. */
@Composable
fun WaveTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(
        primary = Color.White,
        onPrimary = Color.Black,
        surface = Color(0xFF141414),
        onSurface = Color.White,
        onSurfaceVariant = Color(0xFF9E9E9E),
        surfaceContainer = Color(0xFF161616), // menus
        surfaceContainerHigh = Color(0xFF181818), // dialogs
        surfaceContainerHighest = Color(0xFF242424),
        secondaryContainer = Color(0xFF353535), // a selected chip
        onSecondaryContainer = Color.White,
        outline = Color(0xFF5A5A5A),
        outlineVariant = Color(0xFF2A2A2A),
        error = Color(0xFFFFB4AB),
    ),
    typography = Typography().run {
        fun TextStyle.kumbh() = copy(fontFamily = Kumbh)
        copy(
            displayLarge = displayLarge.kumbh(), displayMedium = displayMedium.kumbh(), displaySmall = displaySmall.kumbh(),
            headlineLarge = headlineLarge.kumbh(), headlineMedium = headlineMedium.kumbh(), headlineSmall = headlineSmall.kumbh(),
            titleLarge = titleLarge.kumbh(), titleMedium = titleMedium.kumbh(), titleSmall = titleSmall.kumbh(),
            bodyLarge = bodyLarge.kumbh(), bodyMedium = bodyMedium.kumbh(), bodySmall = bodySmall.kumbh(),
            labelLarge = labelLarge.kumbh(), labelMedium = labelMedium.kumbh(), labelSmall = labelSmall.kumbh(),
        )
    },
    shapes = Shapes(extraSmall = RoundedCornerShape(14.dp)), // menus and text fields
    content = content,
)

/** App names and list text: in the ink, with a soft shadow so it reads on any wallpaper. */
internal val Label: TextStyle
    @Composable get() {
        val ink = LocalInk.current
        return remember(ink) {
            TextStyle(
                fontFamily = Kumbh,
                color = ink,
                shadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 1f), 4f),
            )
        }
    }
