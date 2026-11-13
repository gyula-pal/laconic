package cc.gyula.wave.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Below this WCAG contrast ratio, text is flagged as hard to read. */
const val MIN_READABLE_CONTRAST = 3.0

private fun channel(argb: Int, shift: Int) = (argb shr shift) and 0xFF

private fun linear(c: Int): Double {
    val s = c / 255.0
    return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
}

private fun luminance(argb: Int) =
    0.2126 * linear(channel(argb, 16)) + 0.7152 * linear(channel(argb, 8)) + 0.0722 * linear(channel(argb, 0))

/** WCAG contrast ratio of two colours (alpha ignored): 1 for the same colour, 21 for black on white. */
fun contrastRatio(argb1: Int, argb2: Int): Double {
    val a = luminance(argb1)
    val b = luminance(argb2)
    return (max(a, b) + 0.05) / (min(a, b) + 0.05)
}

/** Whether [ink] on [background] is hard to read; over the wallpaper (null) it is judged against black. */
fun isHardToRead(ink: Int, background: Int?): Boolean =
    contrastRatio(ink, background ?: OPAQUE_BLACK) < MIN_READABLE_CONTRAST

const val OPAQUE_BLACK = 0xFF000000.toInt()

/** Hue in degrees [0, 360); saturation and value in [0, 1]. */
data class Hsv(val hue: Float, val saturation: Float, val value: Float)

/** An opaque colour from [hsv]. */
fun hsvToArgb(hsv: Hsv): Int {
    val h = ((hsv.hue % 360f) + 360f) % 360f / 60f
    val s = hsv.saturation.coerceIn(0f, 1f)
    val v = hsv.value.coerceIn(0f, 1f)
    val c = v * s
    val x = c * (1 - abs(h % 2 - 1))
    val (r, g, b) = when (h.toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = v - c
    fun byte(f: Float) = ((f + m) * 255f).roundToInt().coerceIn(0, 255)
    return OPAQUE_BLACK or (byte(r) shl 16) or (byte(g) shl 8) or byte(b)
}

fun argbToHsv(argb: Int): Hsv {
    val r = channel(argb, 16) / 255f
    val g = channel(argb, 8) / 255f
    val b = channel(argb, 0) / 255f
    val top = max(r, max(g, b))
    val delta = top - min(r, min(g, b))
    val hue = when {
        delta == 0f -> 0f
        top == r -> 60f * (((g - b) / delta + 6f) % 6f)
        top == g -> 60f * ((b - r) / delta + 2f)
        else -> 60f * ((r - g) / delta + 4f)
    }
    return Hsv(hue, if (top == 0f) 0f else delta / top, top)
}

/** "#RRGGBB", capitals; the alpha is dropped. */
fun formatHex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

/** An opaque colour from "#RRGGBB" or "RRGGBB" (any case), else null. */
fun parseHex(text: String): Int? {
    val digits = text.trim().removePrefix("#")
    if (digits.length != 6 || digits.any { it !in "0123456789abcdefABCDEF" }) return null
    return OPAQUE_BLACK or digits.toInt(16)
}
