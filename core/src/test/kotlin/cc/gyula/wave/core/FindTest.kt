package cc.gyula.wave.core

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FindTest {
    @Test
    fun prefixIsBest() {
        assertEquals(1f, fit("Spotify", "spot")!!, 0f)
        assertEquals(1f, fit("Ágnes", "agnes")!!, 0f)
        assertEquals(1f, fit("Spotify", "  SPOT ")!!, 0f)
    }

    @Test
    fun laterWordPrefix() {
        assertEquals(0.95f, fit("Google Maps", "map")!!, 0f)
        assertEquals(0.95f, fit("Mail-Client", "client")!!, 0f)
        assertEquals(0.95f, fit("my_notes", "notes")!!, 0f)
        assertEquals(0.95f, fit("a/b report", "report")!!, 0f)
        assertEquals(0.95f, fit("Kis Ágnes", "agnes")!!, 0f)
    }

    @Test
    fun initials() {
        assertEquals(0.92f, fit("Google Maps", "gm")!!, 0f)
        assertEquals(0.92f, fit("YouTube Music", "ytm")!!, 0f)
        assertEquals(0.92f, fit("Google Maps Go", "gm")!!, 0f)
        assertNull(fit("Google Maps", "gz"))
    }

    @Test
    fun initialsMustRunFromTheFirstWord() {
        assertNull(fit("Alpha Beta Gamma", "bg"))
    }

    @Test
    fun appearsAnywhere() {
        assertEquals(0.90f, fit("Spotify", "tif")!!, 0f)
        assertEquals(0.90f, fit("Signal", "gna")!!, 0f)
        assertEquals(0.90f, fit("Big Gym", "m")!!, 0f)
    }

    @Test
    fun lettersInOrderWithinTwiceTheirCount() {
        assertEquals(0.85f, fit("Calendar", "cld")!!, 0f) // c..l..d covers 6 = 2 × 3
        assertNull(fit("Calendar", "cz"))
    }

    @Test
    fun spreadOutLettersDoNotFit() {
        assertNull(fit("a12345678a12345678a", "aaa")) // covers 19 > 6
        assertEquals(0.85f, fit("a1a1a", "aaa")!!, 0f) // covers 5
    }

    @Test
    fun shortTyposAllowOne() {
        assertEquals(0.80f, fit("Chrome", "chrime")!!, 0f) // substitution
        assertEquals(0.85f, fit("Chrome", "chrme")!!, 0f) // a letter missing: caught earlier, as letters in order
        assertEquals(0.80f, fit("Chrome", "chroome")!!, 0f) // extra letter
        assertEquals(0.80f, fit("Chrome", "chrmoe")!!, 0f) // swap
        assertNull(fit("Chrome", "chxxme")) // two off
    }

    @Test
    fun longTyposAllowTwo() {
        assertEquals(0.80f, fit("Spotify", "spxtxfy")!!, 0f) // two off at 7
        assertNull(fit("Spotify", "sxxtxfy")) // three
        assertEquals(0.80f, fit("Calendar", "calxndxr")!!, 0f)
        assertNull(fit("Calendar", "calxnxxr"))
    }

    @Test
    fun typoIsComparedAgainstEachWordToo() {
        assertEquals(0.80f, fit("Google Maps", "mpas")!!, 0f)
    }

    @Test
    fun queriesWithSpacesStillFit() {
        assertEquals(1f, fit("Google Maps", "google m")!!, 0f)
        assertEquals(0.90f, fit("Google Maps", "goo maps")!!, 0f) // each typed word starts a word, in order
        assertNull(fit("Google Maps", "maps google"))
    }

    @Test
    fun aQueryLongerThanTheLabelDoesNotCrash() {
        assertNull(fit("Mail", "mailbox pro"))
        assertEquals(0.80f, fit("Maps", "mapss")!!, 0f) // extra letter
    }

    @Test
    fun threeLetterTyposDoNotCount() {
        assertNull(fit("Spotify", "sxo"))
    }

    @Test
    fun blankOrUnrelatedIsNull() {
        assertNull(fit("Spotify", ""))
        assertNull(fit("Spotify", "   "))
        assertNull(fit("Spotify", "zzzz"))
    }

    private data class Thing(val name: String, val use: Float = 0f)

    private fun rank(q: String, things: List<Thing>) = ranked(things, q, { listOf(it.name) }, { it.use }).map { it.name }

    @Test
    fun bestFitFirstThenInputOrder() {
        val things = listOf(Thing("Big Maps"), Thing("Mapz"), Thing("Maps"), Thing("Other"))
        assertEquals(listOf("Mapz", "Maps", "Big Maps"), rank("map", things))
    }

    @Test
    fun useReordersAPrefixAndALaterWord() {
        assertEquals(listOf("Maps", "Big Maps"), rank("map", listOf(Thing("Big Maps"), Thing("Maps"))))
        assertEquals(listOf("Big Maps", "Maps"), rank("map", listOf(Thing("Maps"), Thing("Big Maps", use = 1f))))
    }

    @Test
    fun aFullyUsedTypoLosesToAnUnusedPrefix() {
        val things = listOf(Thing("Sprtify", use = 1f), Thing("Spotify"))
        assertEquals(listOf("Spotify", "Sprtify"), rank("spotif", things))
    }

    @Test
    fun bestOfSeveralNamesCounts() {
        val r = ranked(listOf("a", "b"), "otp", { if (it == "a") listOf("Bank", "OTP Bank") else listOf("Other") })
        assertEquals(listOf("a"), r)
    }

    @Test
    fun parseQueryScopes() {
        assertEquals(Query(Scope.CONTACTS, "Anna"), parseQuery("C Anna"))
        assertEquals(Query(Scope.CONTACTS, "anna"), parseQuery("c anna"))
        assertEquals(Query(Scope.APPS, "maps"), parseQuery("a maps"))
        assertEquals(Query(Scope.EVENTS, "dentist"), parseQuery("e dentist"))
        assertEquals(Query(Scope.WEB, "pizza"), parseQuery("w  pizza "))
    }

    @Test
    fun parseQueryLeavesOtherLinesAlone() {
        assertEquals(Query(Scope.ALL, "c"), parseQuery("c"))
        assertEquals(Query(Scope.ALL, "cat"), parseQuery("cat"))
        assertEquals(Query(Scope.ALL, "c anna"), parseQuery(" c anna"))
        assertEquals(Query(Scope.ALL, "x anna"), parseQuery("x anna"))
        assertEquals(Query(Scope.ALL, ""), parseQuery("   "))
        assertEquals(Query(Scope.CONTACTS, ""), parseQuery("c "))
    }

    private fun target(raw: String, app: Boolean = false, contact: Boolean = false, event: Boolean = false) =
        enterTarget(parseQuery(raw), app, contact, event)

    @Test
    fun webScopeGoesStraightToTheWeb() {
        assertEquals(Target.WEB, target("w github.com", app = true))
        assertEquals(Target.NONE, target("w "))
    }

    @Test
    fun addressBeatsHits() {
        assertEquals(Target.ADDRESS, target("github.com", app = true))
        assertEquals(Target.APP, target("a github.com", app = true))
    }

    @Test
    fun appThenContactThenEvent() {
        assertEquals(Target.APP, target("anna", true, true, true))
        assertEquals(Target.CONTACT, target("anna", false, true, true))
        assertEquals(Target.EVENT, target("anna", false, false, true))
    }

    @Test
    fun scopeLimitsWhichHitsCount() {
        assertEquals(Target.CONTACT, target("c anna", true, true, true))
        assertEquals(Target.EVENT, target("e anna", true, true, true))
        assertEquals(Target.APP, target("a anna", true, true, true))
        assertEquals(Target.WEB, target("c anna", true, false, true))
    }

    @Test
    fun sumThenWebThenNone() {
        assertEquals(Target.SUM, target("12*7"))
        assertEquals(Target.WEB, target("c 12*7"))
        assertEquals(Target.WEB, target("weather vienna"))
        assertEquals(Target.NONE, target("  "))
    }

    @Test
    fun shownCapsUnlessExpanded() {
        val items = listOf(1, 2, 3, 4)
        assertEquals(listOf(1, 2), shown(items, false, 2))
        assertEquals(items, shown(items, true, 2))
        assertEquals(items, shown(items, false, 9))
    }

    private val budapest = ZoneId.of("Europe/Budapest")
    private val utc = ZoneId.of("UTC")
    private fun ms(y: Int, mo: Int, d: Int, h: Int = 0, mi: Int = 0, zone: ZoneId = budapest) =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone).toInstant().toEpochMilli()

    // Wed 11 Nov 2026, 09:00 Budapest
    private val now = ms(2026, 11, 11, 9)

    private fun at(begin: Long, allDay: Boolean = false) = eventWhen(begin, allDay, now, budapest)

    @Test
    fun today() {
        assertEquals("18:00", at(ms(2026, 11, 11, 18)))
        assertEquals("today", at(ms(2026, 11, 11, zone = utc), allDay = true))
    }

    @Test
    fun tomorrow() {
        assertEquals("tomorrow 08:05", at(ms(2026, 11, 12, 8, 5)))
        assertEquals("tomorrow", at(ms(2026, 11, 12, zone = utc), allDay = true))
    }

    @Test
    fun withinSixDays() {
        assertEquals("Fri 18:00", at(ms(2026, 11, 13, 18)))
        assertEquals("Tue 18:00", at(ms(2026, 11, 17, 18)))
        assertEquals("Fri", at(ms(2026, 11, 13, zone = utc), allDay = true))
    }

    @Test
    fun laterAndOtherYear() {
        assertEquals("18 Nov 18:00", at(ms(2026, 11, 18, 18)))
        assertEquals("14 Dec", at(ms(2026, 12, 14, zone = utc), allDay = true))
        assertEquals("14 Nov 2027 18:00", at(ms(2027, 11, 14, 18)))
        assertEquals("14 Nov 2027", at(ms(2027, 11, 14, zone = utc), allDay = true))
    }

    @Test
    fun allDayUsesUtcNotThePhoneZone() {
        // UTC midnight of 12 Nov is 19:00 on the 11th in New York: read in the phone's zone it would say "today".
        val newYork = ZoneId.of("America/New_York")
        val begin = ms(2026, 11, 12, zone = utc)
        assertEquals("tomorrow", eventWhen(begin, true, ms(2026, 11, 11, 9, zone = newYork), newYork))
        // And near midnight in Budapest (23:30 on the 11th) it is still tomorrow.
        assertEquals("tomorrow", eventWhen(begin, true, ms(2026, 11, 11, 23, 30), budapest))
    }

    @Test
    fun timedEventJustAfterMidnightUsesThePhoneZone() {
        // 00:30 Budapest on the 12th is 23:30 UTC on the 11th, yet it is tomorrow here.
        assertEquals("tomorrow 00:30", at(ms(2026, 11, 12, 0, 30)))
    }

    @Test
    fun lettersInOrderStayInsideOneWord() {
        assertNull(fit("Tihanyi Ármin", "anya")) // tih-ANY-i + Á-rmin: spread over two names, not a match
        assertNull(fit("Tihanyi Ármin", "anyar"))
        assertEquals(1f, fit("Anya", "anya")!!, 0f)
        assertEquals(0.85f, fit("Kovács Anna", "kvcs")!!, 0f) // inside one word still fits
    }
}
