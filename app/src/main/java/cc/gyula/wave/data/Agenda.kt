package cc.gyula.wave.data

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Instances
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One upcoming occurrence of an event, with the calendar's own times: an all-day event's [begin] is midnight UTC
 * (core's eventWhen reads it so, and the calendar app expects it back when the event is opened).
 */
data class Happening(val eventId: Long, val title: String, val begin: Long, val end: Long, val allDay: Boolean)

/**
 * The calendar's coming year, read when searching. Needs READ_CALENDAR, which [granted] reports; without it
 * the answer is empty rather than a SecurityException.
 */
class Agenda(private val resolver: ContentResolver, private val granted: () -> Boolean) {
    fun canRead(): Boolean = granted()

    /**
     * Events on visible calendars, not declined, from the start of today ([zone]) to 365 days on. A repeating
     * event appears once, as its next occurrence. Soonest first, at most 2000; untitled events are left out.
     */
    suspend fun upcoming(now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): List<Happening> {
        if (!granted()) return emptyList()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val from = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(WINDOW_DAYS).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()
        return withContext(Dispatchers.IO) {
            val first = LinkedHashMap<Long, Happening>()
            try {
                resolver.query(
                    uri,
                    arrayOf(Instances.EVENT_ID, Instances.TITLE, Instances.BEGIN, Instances.END, Instances.ALL_DAY),
                    "${Instances.VISIBLE} = 1 AND ${Instances.SELF_ATTENDEE_STATUS} != ${Attendees.ATTENDEE_STATUS_DECLINED}",
                    null,
                    "${Instances.BEGIN} ASC",
                )?.use { c ->
                    while (c.moveToNext()) {
                        val title = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                        val allDay = c.getInt(4) != 0
                        val h = Happening(c.getLong(0), title, c.getLong(2), c.getLong(3), allDay)
                        val seen = first[h.eventId]
                        if (seen == null || h.begin < seen.begin) first[h.eventId] = h
                    }
                }
            } catch (e: SecurityException) {
                // Permission taken away since granted() was asked: nothing to show, not a crash.
                return@withContext emptyList()
            }
            first.values.sortedBy { it.begin }.take(LIMIT)
        }
    }

    private companion object {
        const val WINDOW_DAYS = 365L
        const val LIMIT = 2000
    }
}
