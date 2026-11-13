package cc.gyula.wave.core

/** A paused player drops off the home screen this long after it was paused. */
const val PAUSED_KEEP_MS = 10 * 60_000L

/**
 * When each app's player was paused. Android stamps a session with its last state change, but when
 * Spotify's process restarts — which it does on its own in the background — it brings back its
 * long-paused session stamped as just paused. The same position tells that apart from a real pause.
 */
class PauseClock {
    private val seen = HashMap<String, Pair<Long, Long>>() // app → (position, paused since)

    /** Since when [app] has been in its current state, given Android's [stamp] for it. */
    fun since(app: String, playing: Boolean, position: Long, stamp: Long): Long {
        if (playing) {
            seen.remove(app)
            return stamp
        }
        val known = seen[app]
        if (known != null && known.first == position) return known.second
        seen[app] = position to stamp
        return stamp
    }
}

/** How much longer a player paused [since] stays on the home screen, at [now]; 0 once it should go. */
fun pausedTimeLeft(since: Long, now: Long): Long = (PAUSED_KEEP_MS - (now - since)).coerceAtLeast(0L)

/**
 * A player swiped away: it stays hidden until that app plays again after stopping, or another app's
 * player shows up. Keyed by app, not track: some players change what they report when stopped.
 */
data class Dismissal(val app: String? = null, val settled: Boolean = false) {
    fun hides(app: String): Boolean = this.app == app

    /** After the player changed to [current] (an app, or nothing), [playing] or not. */
    fun next(current: String?, playing: Boolean): Dismissal = when {
        app == null -> this
        current == null || !playing && current == app -> copy(settled = true)
        current != app || settled -> Dismissal()
        else -> this // still playing out the moment it was swiped away
    }
}
