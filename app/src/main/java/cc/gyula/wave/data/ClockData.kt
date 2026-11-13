package cc.gyula.wave.data

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Instances
import androidx.annotation.RequiresApi
import cc.gyula.wave.core.eventLookAheadEnd
import cc.gyula.wave.core.formatPrice
import cc.gyula.wave.core.priceLabel
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private suspend fun getJson(url: String): JSONObject = withContext(Dispatchers.IO) {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 8_000
    conn.readTimeout = 8_000
    try {
        JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
    } finally {
        conn.disconnect()
    }
}

/** A last-known fix older than this is where the phone was, not where it is. */
private val MAX_FIX_AGE_NANOS = TimeUnit.HOURS.toNanos(2)

/**
 * The phone's approximate (coarse) location: the freshest one Android already has (under two hours old), else a one-off
 * network fix. Throws if there is none, so the widget shows "—°C" rather than some other city's weather.
 */
@SuppressLint("MissingPermission") // only called once ACCESS_COARSE_LOCATION is granted
internal suspend fun coarseLocation(context: Context): Location {
    val lm = context.getSystemService(LocationManager::class.java)
    val known = lm.allProviders
        .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        .filter { SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos <= MAX_FIX_AGE_NANOS }
        .maxByOrNull { it.time }
    if (known != null) return known
    if (Build.VERSION.SDK_INT >= 30 && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) return networkFix(context, lm)
    error("no location")
}

/** A one-off fix from the network provider; Android 11 added the call. */
@RequiresApi(30)
@SuppressLint("MissingPermission")
private suspend fun networkFix(context: Context, lm: LocationManager): Location =
    withTimeout(15_000) {
        suspendCancellableCoroutine { cont ->
            val cancel = CancellationSignal()
            cont.invokeOnCancellation { cancel.cancel() }
            lm.getCurrentLocation(LocationManager.NETWORK_PROVIDER, cancel, context.mainExecutor) { loc ->
                if (loc != null) cont.resume(loc) else cont.resumeWithException(IllegalStateException("no location"))
            }
        }
    }

/** Open-Meteo's current-temperature request; the position is rounded to ~1 km, which is all a temperature needs. */
internal fun weatherUrl(latitude: Double, longitude: Double): String {
    val lat = "%.2f".format(Locale.US, latitude)
    val lon = "%.2f".format(Locale.US, longitude)
    return "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m"
}

/** "12°C" from Open-Meteo's answer; throws if the answer isn't shaped like one. */
internal fun temperatureLabel(response: JSONObject): String {
    val t = response.getJSONObject("current").getDouble("temperature_2m")
    return "${t.roundToInt()}°C"
}

/** Current temperature where the phone is, e.g. "12°C" — Open-Meteo, free, no key. */
suspend fun fetchTemperature(context: Context): String {
    val loc = coarseLocation(context)
    return temperatureLabel(getJson(weatherUrl(loc.latitude, loc.longitude)))
}

internal fun spotPriceUrl(ticker: String) = "https://api.binance.com/api/v3/ticker/price?symbol=${ticker}USDT"
internal fun futuresPriceUrl(ticker: String) = "https://fapi.binance.com/fapi/v1/ticker/price?symbol=${ticker}USDT"
internal fun coinbasePriceUrl(ticker: String) = "https://api.coinbase.com/v2/prices/$ticker-USD/spot"

/** "₿80.7K" / "ETH 2318" from a Binance answer; throws if there is no usable price in it. */
internal fun binancePriceLabel(ticker: String, response: JSONObject): String =
    priceLabel(ticker, formatPrice(response.getString("price").toDouble()))

/** The same from Coinbase's answer, in dollars. */
internal fun coinbasePriceLabel(ticker: String, response: JSONObject): String =
    priceLabel(ticker, formatPrice(response.getJSONObject("data").getString("amount").toDouble()))

/**
 * Last trade of [ticker]/USDT on Binance: "₿80.7K" for Bitcoin (the ₿ is drawn, see BitcoinText),
 * otherwise the short name and price, e.g. "ETH 2318". Coins come from spot; anything spot doesn't
 * list falls back to Binance's futures, which also carry stocks, ETFs and metals (SPY, NVDA, XAU …).
 * Binance turns away US addresses, so when it doesn't answer at all, coins come from Coinbase; stocks then
 * have no source. Throws when nobody lists it, or there is no connection. [get] does the network call, so tests
 * can swap it.
 */
internal suspend fun fetchPrice(ticker: String, get: suspend (String) -> JSONObject): String {
    val binance = attempt { get(spotPriceUrl(ticker)) } ?: attempt { get(futuresPriceUrl(ticker)) }
    if (binance != null) return binancePriceLabel(ticker, binance)
    return coinbasePriceLabel(ticker, get(coinbasePriceUrl(ticker)))
}

/** [block]'s answer, or null if it failed. Unlike runCatching this lets a cancelled coroutine stop, not carry on. */
private suspend fun <T : Any> attempt(block: suspend () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}

suspend fun fetchPrice(ticker: String): String = fetchPrice(ticker, ::getJson)

sealed interface EventState {
    data object None : EventState
    data object Failed : EventState
    data class Next(val title: String, val begin: Long, val end: Long, val id: Long) : EventState
}

/** Next timed (not all-day), not-declined event on a visible calendar, today or tomorrow ([eventLookAheadEnd]). */
suspend fun nextEvent(context: Context): EventState = withContext(Dispatchers.IO) {
    val now = System.currentTimeMillis()
    val end = eventLookAheadEnd(LocalDateTime.now()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val uri = Instances.CONTENT_URI.buildUpon().also {
        ContentUris.appendId(it, now)
        ContentUris.appendId(it, end)
    }.build()
    context.contentResolver.query(
        uri,
        arrayOf(Instances.TITLE, Instances.BEGIN, Instances.END, Instances.EVENT_ID),
        "${Instances.VISIBLE} = 1 AND ${Instances.ALL_DAY} = 0 AND ${Instances.BEGIN} >= ? AND ${Instances.BEGIN} < ? AND " +
            "${Instances.SELF_ATTENDEE_STATUS} != ${Attendees.ATTENDEE_STATUS_DECLINED}",
        arrayOf(now.toString(), end.toString()),
        "${Instances.BEGIN} ASC",
    )?.use { c ->
        // Untitled events are skipped, as in the search agenda: "· 18:00" says nothing. The next titled one shows.
        while (c.moveToNext()) {
            val title = c.getString(0)?.takeIf { it.isNotBlank() } ?: continue
            return@use EventState.Next(title, c.getLong(1), c.getLong(2), c.getLong(3))
        }
        EventState.None
    } ?: EventState.Failed
}
