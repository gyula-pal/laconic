package cc.gyula.wave.core

import java.text.Collator
import java.text.Normalizer

/** Scroller letter for a label: accents fold to their base letter (Árkád → A); anything not A–Z → '#'. */
fun sectionOf(label: String): Char {
    val first = label.trimStart().firstOrNull() ?: return '#'
    val base = Normalizer.normalize(first.toString(), Normalizer.Form.NFD).first().uppercaseChar()
    return if (base in 'A'..'Z') base else '#'
}

/** '#' first, then A–Z; locale-aware order inside each letter. */
fun <T> sectionComparator(label: (T) -> String): Comparator<T> {
    val collator = Collator.getInstance()
    return compareBy<T> { sectionOf(label(it)) }.thenComparator { a, b -> collator.compare(label(a), label(b)) }
}

// Built once: search folds every app, contact and event on every keystroke.
private val ACCENT_MARKS = Regex("\\p{Mn}+")

/** Lower case with accents stripped, so "arkad" finds "Árkád". */
fun fold(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD).replace(ACCENT_MARKS, "").lowercase()

/**
 * How well [label] matches a typed [query]: 0 = the label starts with it, 1 = a later word does,
 * 2 = it appears anywhere, null = no match.
 */
fun matchRank(label: String, query: String): Int? {
    val q = fold(query.trim())
    if (q.isEmpty()) return null
    val l = fold(label)
    return when {
        l.startsWith(q) -> 0
        l.split(' ', '-', '.', ':', '(').any { it.startsWith(q) } -> 1
        q in l -> 2
        else -> null
    }
}

private val HOST = Regex("""(?i)^(https?://)?(([a-z0-9-]+\.)+[a-z]{2,24})(:\d{1,5})?([/?#]\S*)?$""")
private val IP = Regex("""(?i)^(https?://)?(\d{1,3}(\.\d{1,3}){3})(:\d{1,5})?([/?#]\S*)?$""")

/**
 * A typed web address as a URL to open: "github.com" → "https://github.com". A bare IP address gets
 * http://, since that's what a box on the home network usually speaks. Null for anything else.
 */
fun webAddress(query: String): String? {
    val q = query.trim()
    if (q.any(Char::isWhitespace)) return null
    val hasScheme = q.startsWith("http://", true) || q.startsWith("https://", true)
    return when {
        HOST.matches(q) -> if (hasScheme) q else "https://$q"
        IP.matches(q) -> if (hasScheme) q else "http://$q"
        else -> null
    }
}
