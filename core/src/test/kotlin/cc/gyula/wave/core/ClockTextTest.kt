package cc.gyula.wave.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.util.Locale

class ClockTextTest {
    private val now = LocalDateTime.of(2026, 10, 27, 9, 0) // a Tuesday
    private val h24 = timeFormat(true)

    @Test
    fun weekdayAndDateInCapitals() {
        assertEquals("TUE", weekday(now, Locale.ENGLISH))
        assertEquals("27 OCT", dateText(now, Locale.ENGLISH))
        // German abbreviations end in a full stop, which would look like a typo on the band.
        assertEquals("DI", weekday(now, Locale.GERMAN))
    }

    @Test
    fun alarmWithinADayShowsJustTheTime() {
        assertEquals("06:10", alarmText(now.plusHours(21).withMinute(10), now, h24, Locale.ENGLISH))
        assertEquals("WED 09:30", alarmText(now.plusDays(1).withMinute(30), now, h24, Locale.ENGLISH))
    }

    @Test
    fun eventShowsItsWeekdayUnlessToday() {
        assertEquals("DENTIST · 17:00", eventText("Dentist", now.withHour(17), now, h24, Locale.ENGLISH))
        assertEquals("DENTIST · WED 08:15", eventText("Dentist", now.plusDays(1).withHour(8).withMinute(15), now, h24, Locale.ENGLISH))
    }

    @Test
    fun eventLineLooksAheadToTheEndOfTomorrow() {
        // Tuesday morning and Tuesday just before midnight: both stop at Thursday 00:00.
        val thursday = LocalDateTime.of(2026, 10, 29, 0, 0)
        assertEquals(thursday, eventLookAheadEnd(now))
        assertEquals(thursday, eventLookAheadEnd(now.withHour(23).withMinute(59)))
        // From Wednesday, Thursday's events come into view.
        assertEquals(thursday.plusDays(1), eventLookAheadEnd(now.plusDays(1).withHour(0)))
    }

    @Test
    fun twelveHourClock() {
        assertEquals("5:00", now.withHour(17).format(timeFormat(false)))
    }
}
