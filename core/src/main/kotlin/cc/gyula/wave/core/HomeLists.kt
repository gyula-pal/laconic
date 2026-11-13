package cc.gyula.wave.core

/** How many apps the most used / recent home lists show. */
const val USED_COUNT = 8

/** How far back the most used / recent lists look. */
const val USAGE_DAYS = 14

/** One time an app came to the front (Android's ACTIVITY_RESUMED event). */
class AppOpen(val pkg: String, val time: Long)

/**
 * Packages for [mode], best first: opened most often (each switch to an app counts once, however many of
 * its screens follow), or most recently. Home screens in [homes] are left out: an old launcher counts
 * every return home, and isn't an app you open.
 */
fun rankUsage(opens: Sequence<AppOpen>, mode: HomeList, homes: Set<String>): List<String> {
    val opened = HashMap<String, Int>()
    val last = HashMap<String, Long>()
    var previous: String? = null
    for (open in opens) {
        if (open.pkg != previous) opened[open.pkg] = (opened[open.pkg] ?: 0) + 1
        last[open.pkg] = open.time
        previous = open.pkg
    }
    return when (mode) {
        HomeList.PINNED -> emptyList()
        HomeList.FREQUENT -> opened.entries.sortedByDescending { it.value }.map { it.key }
        HomeList.RECENT -> last.entries.sortedByDescending { it.value }.map { it.key }
    }.filter { it !in homes }
}

/**
 * The apps home lists: the [pins], or the first [count] of [used] packages found among [visible] apps,
 * one row per app (its personal-profile copy when it is also in the work profile); A–Z when [az].
 */
fun <T> homeApps(
    mode: HomeList,
    az: Boolean,
    pins: List<T>,
    visible: List<T>,
    used: List<String>?,
    packageOf: (T) -> String,
    isPersonal: (T) -> Boolean,
    label: (T) -> String,
    count: Int = USED_COUNT,
): List<T> {
    val list = if (mode == HomeList.PINNED) pins else {
        val byPackage = visible.groupBy(packageOf).mapValues { (_, apps) -> apps.firstOrNull(isPersonal) ?: apps.first() }
        used.orEmpty().mapNotNull(byPackage::get).take(count)
    }
    return if (az) list.sortedWith(sectionComparator(label)) else list
}

/** Pins in a new [order]; pins of apps not installed right now keep their place at the end. */
fun pinsReordered(pins: List<String>, order: List<String>): List<String> = order + pins.filter { it !in order }

/** [key] pinned at the end, or unpinned. */
fun pinsToggled(pins: List<String>, key: String): List<String> = if (key in pins) pins - key else pins + key

/** [names] with [key] called [name]; a blank name, or the app's [original] one, removes it. */
fun renamed(names: Map<String, String>, key: String, original: String, name: String): Map<String, String> {
    val clean = name.trim()
    return if (clean.isEmpty() || clean == original) names - key else names + (key to clean)
}
