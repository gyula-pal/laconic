package cc.gyula.wave.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColoursTest {
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    @Test fun contrastMatchesKnownWcagValues() {
        assertEquals(21.0, contrastRatio(white, black), 1e-9)
        assertEquals(21.0, contrastRatio(black, white), 1e-9)
        assertEquals(1.0, contrastRatio(0xFF336699.toInt(), 0xFF336699.toInt()), 1e-9)
        // #777777 on white is the textbook "just under 4.5" grey.
        assertEquals(4.48, contrastRatio(0xFF777777.toInt(), white), 0.01)
        assertEquals(8.59, contrastRatio(0xFF0000FF.toInt(), white), 0.01)
    }

    @Test fun readabilityThresholdIsThreeToOne() {
        assertFalse(isHardToRead(white, null)) // white over the wallpaper: judged against black
        assertTrue(isHardToRead(0xFF202020.toInt(), null))
        assertTrue(isHardToRead(0xFF777777.toInt(), 0xFF888888.toInt()))
        assertFalse(isHardToRead(black, white))
    }

    @Test fun hexRoundTrips() {
        assertEquals("#FF8800", formatHex(0xFFFF8800.toInt()))
        assertEquals("#00000A", formatHex(0x0A))
        assertEquals(0xFFFF8800.toInt(), parseHex("#ff8800"))
        assertEquals(0xFFFF8800.toInt(), parseHex(" FF8800 "))
        for (c in listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF12AB9F.toInt())) assertEquals(c, parseHex(formatHex(c)))
        assertNull(parseHex("#FF88"))
        assertNull(parseHex("#GG8800"))
        assertNull(parseHex(""))
    }

    @Test fun hsvRoundTrips() {
        assertEquals(0xFFFF0000.toInt(), hsvToArgb(Hsv(0f, 1f, 1f)))
        assertEquals(0xFF00FF00.toInt(), hsvToArgb(Hsv(120f, 1f, 1f)))
        assertEquals(0xFF0000FF.toInt(), hsvToArgb(Hsv(240f, 1f, 1f)))
        assertEquals(white, hsvToArgb(Hsv(0f, 0f, 1f)))
        assertEquals(black, hsvToArgb(Hsv(200f, 0.5f, 0f)))
        assertEquals(Hsv(60f, 1f, 1f), argbToHsv(0xFFFFFF00.toInt()))
        for (c in listOf(0xFF12AB9F.toInt(), 0xFFFF8800.toInt(), 0xFF808080.toInt(), 0xFFFE0001.toInt())) {
            assertEquals(c, hsvToArgb(argbToHsv(c)))
        }
    }
}
