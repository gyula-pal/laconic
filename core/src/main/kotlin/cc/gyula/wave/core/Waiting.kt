package cc.gyula.wave.core

/**
 * Which apps have a notification waiting, kept up from single posts and removals so the listener needn't
 * re-read every notification on each change. Remembers only a key and a package name per notification,
 * never its content. Every call returns the packages waiting now.
 */
class Waiting {
    private val byKey = HashMap<String, String>()

    /** A notification came or changed; one that is ongoing or can't be dismissed isn't waiting for anyone. */
    fun posted(key: String, pkg: String, waiting: Boolean): Set<String> {
        if (waiting) byKey[key] = pkg else byKey.remove(key)
        return packages()
    }

    fun removed(key: String): Set<String> {
        byKey.remove(key)
        return packages()
    }

    /** Start over from a full list of (key, package, waiting). */
    fun reset(all: List<Triple<String, String, Boolean>>): Set<String> {
        byKey.clear()
        for ((key, pkg, waiting) in all) if (waiting) byKey[key] = pkg
        return packages()
    }

    fun clear(): Set<String> = reset(emptyList())

    private fun packages(): Set<String> = byKey.values.toSet()
}
