package cc.gyula.wave.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalcTest {
    @Test fun arithmetic() {
        assertEquals("84", calculate("12*7"))
        assertEquals("14", calculate("2+3*4"))
        assertEquals("20", calculate("(2+3)*4"))
        assertEquals("0.3", calculate("0,1+0,2"))
        assertEquals("2.5", calculate("5 / 2"))
        assertEquals("30", calculate("200*15%"))
        assertEquals("1024", calculate("2^10"))
        assertEquals("-6", calculate("-2*3"))
        assertEquals("12", calculate("3x4"))
        assertEquals("0.3333333333", calculate("1/3"))
    }

    @Test fun notSums() {
        assertNull(calculate("maps"))
        assertNull(calculate("42"))
        assertNull(calculate("-5"))
        assertNull(calculate("1/0"))
        assertNull(calculate("2+"))
        assertNull(calculate("(2+3"))
        assertNull(calculate(""))
        assertNull(calculate("max"))
    }

    @Test fun absurdlyLongInputIsNotASum() {
        // Nested far enough, the parser would run out of stack and take the home screen down while typing.
        assertNull(calculate("(".repeat(100_000) + "1+1" + ")".repeat(100_000)))
        assertNull(calculate("-".repeat(100_000) + "1+1"))
        assertEquals("32", calculate("1" + "+1".repeat(31))) // 63 characters: still a sum
        assertNull(calculate("1" + "+1".repeat(32))) // 65: past the cap
    }
}

class WebAddressTest {
    @Test fun addresses() {
        assertEquals("https://github.com", webAddress("github.com"))
        assertEquals("https://en.wikipedia.org/wiki/Wave", webAddress(" en.wikipedia.org/wiki/Wave "))
        assertEquals("http://example.com", webAddress("http://example.com"))
        assertEquals("http://192.168.1.10:3001", webAddress("192.168.1.10:3001"))
        assertEquals("https://intranet.example.com", webAddress("intranet.example.com"))
    }

    @Test fun notAddresses() {
        assertNull(webAddress("maps"))
        assertNull(webAddress("maps.g"))
        assertNull(webAddress("1.5"))
        assertNull(webAddress("hello world.com"))
        assertNull(webAddress("12*7"))
        assertNull(webAddress(""))
    }
}
