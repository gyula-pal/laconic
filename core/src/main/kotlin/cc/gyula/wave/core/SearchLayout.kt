package cc.gyula.wave.core

/** The sections of search results, in the order they are listed. [word] is the lower-case header. */
enum class Part(val word: String, val cap: Int) {
    APPS("apps", 5),
    CONTACTS("contacts", 3),
    EVENTS("events", 3),
}

/**
 * Which sections to list: those with something to show ([has]: hits, or a line asking for permission), and
 * only those the [scope] aims at. Always in the order of [Part].
 */
fun partsShown(scope: Scope, has: Set<Part>): List<Part> = Part.entries.filter {
    it in has && (scope == Scope.ALL || scope == it.scope)
}

private val Part.scope: Scope get() = when (this) {
    Part.APPS -> Scope.APPS
    Part.CONTACTS -> Scope.CONTACTS
    Part.EVENTS -> Scope.EVENTS
}

/** Headers only help when there is more than one section to tell apart. */
fun showHeaders(parts: List<Part>): Boolean = parts.size >= 2

/** The word shown faint at the end of the field for a narrowed [scope]; null for ALL. */
fun scopeWord(scope: Scope): String? = when (scope) {
    Scope.ALL -> null
    Scope.APPS -> "apps"
    Scope.CONTACTS -> "contacts"
    Scope.EVENTS -> "events"
    Scope.WEB -> "web"
}

/** How many hits a capped section hides behind its "+N more" row; 0 once expanded. */
fun hiddenCount(total: Int, expanded: Boolean, cap: Int): Int = if (expanded) 0 else (total - cap).coerceAtLeast(0)

/** Asking for a source's permission is worth a line from this many typed characters. */
const val MIN_ALLOW_QUERY = 2
