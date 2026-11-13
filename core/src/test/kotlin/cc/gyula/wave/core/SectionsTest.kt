package cc.gyula.wave.core

import cc.gyula.wave.core.formatPrice
import cc.gyula.wave.core.matchRank
import cc.gyula.wave.core.priceLabel
import cc.gyula.wave.core.sectionComparator
import cc.gyula.wave.core.sectionOf
import org.junit.Assert.assertEquals
import org.junit.Test

class SectionsTest {
    @Test
    fun accentsFoldToBaseLetter() {
        assertEquals('A', sectionOf("Árkád"))
        assertEquals('O', sectionOf("Ötös lottó"))
        assertEquals('E', sectionOf("élő"))
    }

    @Test
    fun nonLettersGoToHash() {
        assertEquals('#', sectionOf("1Password"))
        assertEquals('#', sectionOf(""))
        assertEquals('#', sectionOf("Телеграм"))
    }

    @Test
    fun sortsHashFirstThenAlphabetical() {
        val sorted = listOf("Zoom", "árkád", "1Password", "Ajka", "Bolt").sortedWith(sectionComparator { it })
        assertEquals(listOf("1Password", "Ajka", "árkád", "Bolt", "Zoom"), sorted)
    }

    @Test
    fun searchRanksPrefixThenWordThenAnywhere() {
        assertEquals(0, matchRank("Spotify", "sp"))
        assertEquals(1, matchRank("Google Maps", "ma"))
        assertEquals(2, matchRank("WhatsApp", "app"))
        assertEquals(null, matchRank("Spotify", "x"))
        assertEquals(null, matchRank("Spotify", "  "))
    }

    @Test
    fun pricesStayShort() {
        assertEquals("124K", formatPrice(123_789.0))
        assertEquals("80.7K", formatPrice(80_712.5))
        assertEquals("4025", formatPrice(4_024.96))
        assertEquals("182", formatPrice(182.34))
        assertEquals("2.41", formatPrice(2.4123))
        assertEquals("0.163", formatPrice(0.16342))
        assertEquals("0.0000123", formatPrice(0.00001234))
        assertEquals("₿80.7K", priceLabel("BTC", "80.7K"))
        assertEquals("ETH 2318", priceLabel("ETH", "2318"))
    }

    @Test
    fun searchIgnoresAccentsAndCase() {
        assertEquals(0, matchRank("Árkád", "ARKAD"))
        assertEquals(1, matchRank("OTP Bank Ötös", "otos"))
    }
}
