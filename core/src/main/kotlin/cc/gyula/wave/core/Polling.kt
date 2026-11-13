package cc.gyula.wave.core

/** After a failed fetch, try again this soon: home opened with no signal should not show "—" for long. */
const val RETRY_FAILED_MS = 60_000L

/**
 * A poller's last answer: [value] fetched for [key] (e.g. a ticker) at [at], in uptime milliseconds.
 * [ok] is false when the fetch failed and [value] is the poller's "failed" text.
 */
data class Polled<T>(val key: Any?, val value: T, val at: Long, val ok: Boolean)

/** What to show for [key]: nothing until a fetch for it has answered, never another key's value. */
fun <T> Polled<T>?.valueFor(key: Any?): T? = this?.takeIf { it.key == key }?.value

/**
 * How long a poller waits at [now] before fetching for [key]: not at all the first time or for a new key,
 * else until [everyMs] after the last fetch, or [retryMs] after it if that one failed.
 */
fun fetchWait(last: Polled<*>?, key: Any?, now: Long, everyMs: Long, retryMs: Long = RETRY_FAILED_MS): Long {
    if (last == null || last.key != key) return 0L
    return (last.at + (if (last.ok) everyMs else retryMs) - now).coerceAtLeast(0L)
}
