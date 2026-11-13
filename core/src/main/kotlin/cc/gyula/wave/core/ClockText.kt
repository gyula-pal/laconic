package cc.gyula.wave.core

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "TUE": the weekday in capitals, without the full stop some languages add. */
fun weekday(t: LocalDateTime, locale: Locale = Locale.getDefault()): String =
    t.format(DateTimeFormatter.ofPattern("EEE", locale)).uppercase().trimEnd('.')

/** "27 OCT". */
fun dateText(t: LocalDateTime, locale: Locale = Locale.getDefault()): String =
    t.format(DateTimeFormatter.ofPattern("d MMM", locale)).uppercase().trimEnd('.')

/** "6:30" or "18:30", following the phone's 12/24-hour setting. */
fun timeFormat(is24Hour: Boolean): DateTimeFormatter = DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm")

/** When the next alarm rings: within the next day just the time, else with its weekday, "WED 6:30". */
fun alarmText(ring: LocalDateTime, now: LocalDateTime, time: DateTimeFormatter, locale: Locale = Locale.getDefault()): String =
    (if (ring.isBefore(now.plusDays(1))) "" else weekday(ring, locale) + " ") + ring.format(time)

/**
 * The event line looks this far ahead: to the end of tomorrow. A Thursday event shows from Wednesday on,
 * so on a Tuesday with nothing on Tuesday or Wednesday the line stays empty.
 */
fun eventLookAheadEnd(now: LocalDateTime): LocalDateTime = now.toLocalDate().plusDays(2).atStartOfDay()

/** "DENTIST · 18:00", with the weekday when it isn't today: "DENTIST · WED 18:00". */
fun eventText(
    title: String,
    begin: LocalDateTime,
    now: LocalDateTime,
    time: DateTimeFormatter,
    locale: Locale = Locale.getDefault(),
): String {
    val day = if (begin.toLocalDate() == now.toLocalDate()) "" else weekday(begin, locale) + " "
    return "${title.uppercase()} · $day${begin.format(time)}"
}
