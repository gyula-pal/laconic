package cc.gyula.wave.core

/**
 * The [items] matching [query] by any of their [names] (an app's own and the one you gave it), best match
 * first; equally good matches keep their order, which is alphabetical.
 */
fun <T> searchResults(items: List<T>, query: String, names: (T) -> List<String>): List<T> =
    items.mapNotNull { item -> names(item).mapNotNull { matchRank(it, query) }.minOrNull()?.let { it to item } }
        .sortedBy { it.first }
        .map { it.second }
