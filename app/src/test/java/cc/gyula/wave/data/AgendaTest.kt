package cc.gyula.wave.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import cc.gyula.wave.core.eventWhen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** A stand-in for the Calendar provider; rows are (event id, title, begin, end, all-day). */
class FakeCalendar : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, args: Array<String>?, sort: String?): Cursor {
        queries += uri
        if (refuse) throw SecurityException("no permission")
        return MatrixCursor(projection ?: emptyArray()).also { c -> rows.forEach { c.addRow(it) } }
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0

    companion object {
        val queries = mutableListOf<Uri>()
        var rows: List<Array<Any?>> = emptyList()
        var refuse = false
    }
}

/** The coming year of events: one line per event, soonest first, nothing without the permission. */
@RunWith(RobolectricTestRunner::class)
class AgendaTest {
    private val zone = ZoneId.of("Europe/Budapest")
    private val resolver get() = RuntimeEnvironment.getApplication().contentResolver
    private val day = 24 * 3_600_000L

    @Before
    fun setUp() {
        FakeCalendar.queries.clear()
        FakeCalendar.rows = emptyList()
        Robolectric.setupContentProvider(FakeCalendar::class.java, CalendarContract.AUTHORITY)
    }

    private fun agenda(granted: Boolean = true) = Agenda(resolver) { granted }
    private fun at(date: String, hour: Int = 0) = LocalDate.parse(date).atStartOfDay(zone).plusHours(hour.toLong()).toInstant().toEpochMilli()
    private val now get() = at("2026-11-12", 15)

    /** How the calendar stores an all-day date: midnight UTC. */
    private fun utc(date: String) = LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun allDayEventsKeepTheCalendarsTimesAndStillReadAsTodayWestOfGreenwich() = runBlocking {
        val newYork = ZoneId.of("America/New_York")
        FakeCalendar.rows = listOf(arrayOf(8L, "Holiday", utc("2026-11-12"), utc("2026-11-13"), 1))
        val nowInNewYork = LocalDate.parse("2026-11-12").atStartOfDay(newYork).plusHours(15).toInstant().toEpochMilli()
        val got = agenda().upcoming(nowInNewYork, newYork).single()
        assertEquals(utc("2026-11-12"), got.begin) // handed back unchanged, so opening it finds the right day
        assertEquals(utc("2026-11-13"), got.end)
        assertEquals("today", eventWhen(got.begin, got.allDay, nowInNewYork, newYork))
    }

    @Test
    fun aRefusedQueryIsEmptyNotACrash() = runBlocking {
        FakeCalendar.refuse = true
        try {
            assertEquals(emptyList<Happening>(), agenda().upcoming(now, zone))
        } finally {
            FakeCalendar.refuse = false
        }
    }

    @Test
    fun withoutPermissionNothingIsAskedAndNothingComesBack() = runBlocking {
        FakeCalendar.rows = listOf(arrayOf(1L, "Dentist", 1L, 2L, 0))
        assertFalse(agenda(granted = false).canRead())
        assertEquals(emptyList<Happening>(), agenda(granted = false).upcoming(now, zone))
        assertTrue(FakeCalendar.queries.isEmpty())
    }

    @Test
    fun theWindowRunsFromTheStartOfTodayFor365Days() = runBlocking {
        agenda().upcoming(now, zone)
        val segments = FakeCalendar.queries.single().pathSegments
        assertEquals(at("2026-11-12").toString(), segments[segments.size - 2])
        assertEquals(at("2027-11-12").toString(), segments.last())
        assertEquals(365L * day, at("2027-11-12") - at("2026-11-12"))
    }

    @Test
    fun aRepeatingEventAppearsOnceAsItsEarliestOccurrenceAndUntitledOnesAreLeftOut() = runBlocking {
        FakeCalendar.rows = listOf(
            arrayOf(5L, "Standup", at("2026-11-19", 9), at("2026-11-19", 10), 0),
            arrayOf(5L, "Standup", at("2026-11-13", 9), at("2026-11-13", 10), 0),
            arrayOf(6L, null, at("2026-11-12", 16), at("2026-11-12", 17), 0),
            arrayOf(7L, "", at("2026-11-12", 18), at("2026-11-12", 19), 0),
            arrayOf(8L, "Holiday", utc("2026-11-12"), utc("2026-11-13"), 1),
        )
        val got = agenda().upcoming(now, zone)
        assertEquals(
            listOf(
                Happening(8, "Holiday", utc("2026-11-12"), utc("2026-11-13"), true),
                Happening(5, "Standup", at("2026-11-13", 9), at("2026-11-13", 10), false),
            ),
            got,
        )
    }

    @Test
    fun noMoreThan2000AreReturned() = runBlocking {
        FakeCalendar.rows = (1..2500).map { arrayOf<Any?>(it.toLong(), "E$it", at("2026-11-13") + it * 60_000L, at("2026-11-13") + it * 60_000L + 1, 0) }
        val got = agenda().upcoming(now, zone)
        assertEquals(2000, got.size)
        assertEquals(1L, got.first().eventId)
        assertEquals(2000L, got.last().eventId)
    }
}
