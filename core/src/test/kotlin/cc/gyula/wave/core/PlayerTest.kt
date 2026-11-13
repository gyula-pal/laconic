package cc.gyula.wave.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseClockTest {
    @Test
    fun spotifyRestartingDoesNotCountAsANewPause() {
        // 0.4.1: Spotify's process restarts in the background and re-sends its session, paused at the same
        // spot but stamped "just now". The player used to come back for another 10 minutes.
        val clock = PauseClock()
        assertEquals(1_000L, clock.since("spotify", playing = false, position = 25_880, stamp = 1_000))
        assertEquals(1_000L, clock.since("spotify", playing = false, position = 25_880, stamp = 5_000_000))
    }

    @Test
    fun aRealPauseElsewhereInTheSongStartsAgain() {
        val clock = PauseClock()
        clock.since("spotify", playing = false, position = 25_880, stamp = 1_000)
        assertEquals(9_000L, clock.since("spotify", playing = false, position = 61_200, stamp = 9_000))
    }

    @Test
    fun playingForgetsThePause() {
        val clock = PauseClock()
        clock.since("spotify", playing = false, position = 25_880, stamp = 1_000)
        assertEquals(2_000L, clock.since("spotify", playing = true, position = 25_880, stamp = 2_000))
        // Paused again at that very spot later: a new pause, not the old one.
        assertEquals(3_000L, clock.since("spotify", playing = false, position = 25_880, stamp = 3_000))
    }

    @Test
    fun appsAreKeptApart() {
        val clock = PauseClock()
        clock.since("spotify", playing = false, position = 100, stamp = 1_000)
        assertEquals(7_000L, clock.since("youtube", playing = false, position = 100, stamp = 7_000))
    }

    @Test
    fun aPausedPlayerStaysTenMinutes() {
        assertEquals(PAUSED_KEEP_MS, pausedTimeLeft(since = 0, now = 0))
        assertEquals(60_000L, pausedTimeLeft(since = 0, now = PAUSED_KEEP_MS - 60_000))
        assertEquals(0L, pausedTimeLeft(since = 0, now = PAUSED_KEEP_MS))
        assertEquals(0L, pausedTimeLeft(since = 0, now = 3 * PAUSED_KEEP_MS))
    }
}

class DismissalTest {
    private val swiped = Dismissal("spotify")

    @Test
    fun nothingDismissedHidesNothing() {
        assertFalse(Dismissal().hides("spotify"))
        assertEquals(Dismissal(), Dismissal().next("spotify", playing = true))
    }

    @Test
    fun staysHiddenWhileItStops() {
        // Swiping sends stop; the app may report playing a moment longer, then paused or nothing.
        val stillPlaying = swiped.next("spotify", playing = true)
        assertTrue(stillPlaying.hides("spotify"))
        val stopped = stillPlaying.next("spotify", playing = false)
        assertTrue(stopped.hides("spotify"))
        assertTrue(stopped.next(null, playing = false).hides("spotify"))
    }

    @Test
    fun comesBackWhenItPlaysAgainAfterStopping() {
        val stopped = swiped.next("spotify", playing = false)
        assertFalse(stopped.next("spotify", playing = true).hides("spotify"))
    }

    @Test
    fun anotherAppsPlayerEndsIt() {
        assertFalse(swiped.next("youtube", playing = true).hides("spotify"))
    }
}
