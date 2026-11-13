package cc.gyula.wave.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val WORD_BREAKS = charArrayOf(' ', '-', '.', ':', '(', '_', '/')

/**
 * How well [label] fits a typed [query], 0..1 (1 = best); null = no fit. Case and accents are ignored.
 * Bands, best first: starts with it 1.00, a later word starts with it 0.95, initials ("gm" → Google Maps)
 * 0.92, appears anywhere or each typed word starts a word in order ("goo maps") 0.90, letters in order inside one
 * word 0.85 (spread over two names, "anya" in "Tihanyi Ármin", is chance, not a match), a small typo 0.80.
 */
fun fit(label: String, query: String): Float? {
    val q = fold(query.trim())
    if (q.isEmpty()) return null
    val l = fold(label)
    val words = l.split(*WORD_BREAKS).filter { it.isNotEmpty() }
    return when {
        l.startsWith(q) -> 1f
        words.any { it.startsWith(q) } -> 0.95f
        q.length >= 2 && initials(label).startsWith(q) -> 0.92f
        q in l -> 0.90f
        wordsInOrder(words, q.split(*WORD_BREAKS).filter { it.isNotEmpty() }) -> 0.90f
        q.length >= 3 && words.any { lettersInOrder(it, q) } -> 0.85f
        q.length >= 4 && typo(l, words, q) -> 0.80f
        else -> null
    }
}

// Built once: initials() runs for every label on every keystroke.
private val CAMEL_HUMP = Regex("(?<=\\p{Ll})(?=\\p{Lu})")

/** First letters of the words from the start, folded; "YouTube Music" → "ytm" (a capital inside a word starts a new one). */
private fun initials(label: String): String =
    label.split(*WORD_BREAKS).filter { it.isNotEmpty() }
        .flatMap { it.split(CAMEL_HUMP) }
        .joinToString("") { fold(it.take(1)) }

/** Two or more typed words, each the start of a later word of the label than the one before. */
private fun wordsInOrder(words: List<String>, typed: List<String>): Boolean {
    if (typed.size < 2) return false
    var next = 0
    for (w in words) if (next < typed.size && w.startsWith(typed[next])) next++
    return next == typed.size
}

/** The [q] letters appear in order in one word [l], covering no more than twice their count. */
private fun lettersInOrder(l: String, q: String): Boolean {
    for (start in l.indices) {
        if (l[start] != q[0]) continue
        var at = start
        for (c in q) {
            at = l.indexOf(c, at)
            if (at < 0) return false // later starts can't do better
            at++
        }
        if (at - start <= 2 * q.length) return true
    }
    return false
}

/**
 * [q] is within a typo or two of the start of [l] or of one of its [words]: one for 4–6 letters, two from 7.
 * A dropped letter never gets here: the rest are still in order, so the letters-in-order band has it.
 */
private fun typo(l: String, words: List<String>, q: String): Boolean {
    val limit = if (q.length >= 7) 2 else 1
    return (words + l).any { w -> editDistance(w.take(q.length), q) <= limit }
}

/** Substitutions, insertions, deletions and adjacent swaps each cost 1. */
internal fun editDistance(a: String, b: String): Int {
    val d = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) d[i][0] = i
    for (j in 0..b.length) d[0][j] = j
    for (i in 1..a.length) for (j in 1..b.length) {
        val cost = if (a[i - 1] == b[j - 1]) 0 else 1
        d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
        if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
    }
    return d[a.length][b.length]
}

/** How much a thing's use (0..1) can add to its fit: enough to reorder close matches, never to lift a typo over a prefix. */
const val USE_WEIGHT = 0.1f

/**
 * [items] that fit [query] by any of their [names], best first. Score = best fit + [USE_WEIGHT] × [use] (0..1),
 * so how often you use something reorders close matches but never lifts a typo over a real prefix. Ties keep input order.
 */
fun <T> ranked(items: List<T>, query: String, names: (T) -> List<String>, use: (T) -> Float = { 0f }): List<T> =
    items.mapNotNull { item ->
        names(item).mapNotNull { fit(it, query) }.maxOrNull()?.let { item to it + USE_WEIGHT * use(item).coerceIn(0f, 1f) }
    }.sortedByDescending { it.second }.map { it.first }

/** Where a typed line is aimed: a one-letter prefix and a space narrows search ("c anna" = contacts only). */
enum class Scope { ALL, APPS, CONTACTS, EVENTS, WEB }

/** A typed line split into where it is aimed and what to look for. */
data class Query(val scope: Scope, val text: String)

/** "a " apps, "c " contacts, "e " events, "w " web, any case, only at the very start; text after it trimmed. Anything else: ALL. */
fun parseQuery(raw: String): Query {
    val scope = if (raw.length >= 2 && raw[1] == ' ') {
        when (raw[0].lowercaseChar()) {
            'a' -> Scope.APPS
            'c' -> Scope.CONTACTS
            'e' -> Scope.EVENTS
            'w' -> Scope.WEB
            else -> null
        }
    } else null
    return if (scope == null) Query(Scope.ALL, raw.trim()) else Query(scope, raw.substring(2).trim())
}

/** What Enter opens. */
enum class Target { ADDRESS, APP, CONTACT, EVENT, SUM, WEB, NONE }

/**
 * What Enter opens, in this order: the web if the scope is WEB; a typed address; the top app, contact, event
 * (each only with a hit and when the scope is ALL or that one); a sum; the web for any other text; else nothing.
 */
fun enterTarget(query: Query, hasApp: Boolean, hasContact: Boolean, hasEvent: Boolean): Target {
    val all = query.scope == Scope.ALL
    return when {
        query.scope == Scope.WEB && query.text.isNotBlank() -> Target.WEB
        all && webAddress(query.text) != null -> Target.ADDRESS
        hasApp && (all || query.scope == Scope.APPS) -> Target.APP
        hasContact && (all || query.scope == Scope.CONTACTS) -> Target.CONTACT
        hasEvent && (all || query.scope == Scope.EVENTS) -> Target.EVENT
        all && calculate(query.text) != null -> Target.SUM
        query.text.isNotBlank() -> Target.WEB
        else -> Target.NONE
    }
}

/** Contacts and events are only looked up from this many typed characters (one letter would list half the phone book). */
const val MIN_PEOPLE_QUERY = 2

/** The rows a capped section shows: the first [cap] unless expanded. */
fun <T> shown(items: List<T>, expanded: Boolean, cap: Int): List<T> = if (expanded) items else items.take(cap)

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val DAY = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/**
 * When an event happens, short, for a search row. [now] and [begin] are epoch millis, [zone] the phone's zone.
 * Today "18:00", tomorrow "tomorrow 18:00", within the next 6 days "Fri 18:00", later "14 Nov 18:00", another
 * year "14 Nov 2027"; all-day events drop the time ("today", "tomorrow", "Fri"). All-day events are stored
 * at UTC midnight, so their date is read in UTC, not [zone].
 */
fun eventWhen(begin: Long, allDay: Boolean, now: Long, zone: ZoneId): String {
    val today: LocalDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val start = Instant.ofEpochMilli(begin).atZone(if (allDay) ZoneOffset.UTC else zone)
    val time = if (allDay) "" else " " + start.format(CLOCK)
    val days = ChronoUnit.DAYS.between(today, start.toLocalDate())
    return when {
        days == 0L -> if (allDay) "today" else time.trim()
        days == 1L -> "tomorrow$time"
        days in 2..6 -> start.format(DAY) + time
        start.year != today.year -> start.format(DAY_MONTH_YEAR) + time
        else -> start.format(DAY_MONTH) + time
    }
}
