package cc.gyula.wave.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.SystemClock
import android.provider.CalendarContract
import java.io.FileNotFoundException
import java.io.IOException
import java.time.Duration
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSystemClock

/**
 * Weather and price answers turn into the clock's text, and every failure throws, so the clock keeps
 * showing its last text (or "—°C") instead of something wrong. No test touches the network.
 */
@RunWith(RobolectricTestRunner::class)
class ClockDataTest {
    private fun json(text: String) = JSONObject(text)
    private fun price(ticker: String, get: suspend (String) -> JSONObject) = runBlocking { fetchPrice(ticker, get) }

    private fun weather(t: String) = """{"current":{"time":"2026-10-27T10:00","temperature_2m":$t}}"""

    // --- weather -------------------------------------------------------------------------------

    @Test
    fun temperatureIsRoundedToWholeDegrees() {
        assertEquals("12°C", temperatureLabel(json(weather("11.6"))))
        assertEquals("12°C", temperatureLabel(json(weather("12.4"))))
        assertEquals("-3°C", temperatureLabel(json(weather("-3.2"))))
        assertEquals("0°C", temperatureLabel(json(weather("0.0"))))
    }

    @Test
    fun weatherAnswerWithoutTemperatureThrows() {
        assertThrows(JSONException::class.java) { temperatureLabel(json("""{"current":{"time":"x"}}""")) }
        assertThrows(JSONException::class.java) { temperatureLabel(json("""{"error":true,"reason":"bad"}""")) }
        assertThrows(JSONException::class.java) { temperatureLabel(json(weather("null"))) }
        assertThrows(JSONException::class.java) { temperatureLabel(json(weather("\"warm\""))) }
    }

    @Test
    fun weatherRequestSendsAPositionRoundedToAboutAKilometre() {
        assertEquals(
            "https://api.open-meteo.com/v1/forecast?latitude=47.50&longitude=19.04&current=temperature_2m",
            weatherUrl(47.497912, 19.040235),
        )
        assertEquals(
            "https://api.open-meteo.com/v1/forecast?latitude=-33.87&longitude=-151.21&current=temperature_2m",
            weatherUrl(-33.8688, -151.2093),
        )
    }

    @Test
    fun weatherRequestUsesADecimalPointInEveryLanguage() {
        val before = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        try {
            assertEquals(
                "https://api.open-meteo.com/v1/forecast?latitude=47.50&longitude=19.04&current=temperature_2m",
                weatherUrl(47.497912, 19.040235),
            )
        } finally {
            Locale.setDefault(before)
        }
    }

    // --- price ---------------------------------------------------------------------------------

    @Test
    fun bitcoinShowsAsBitcoinSignAndShortPrice() {
        assertEquals("₿80.7K", price("BTC") { json("""{"symbol":"BTCUSDT","price":"80712.34000000"}""") })
    }

    @Test
    fun otherCoinsShowTickerAndPrice() {
        assertEquals("ETH 2318", price("ETH") { json("""{"symbol":"ETHUSDT","price":"2318.41000000"}""") })
        assertEquals("DOGE 0.163", price("DOGE") { json("""{"symbol":"DOGEUSDT","price":"0.16312000"}""") })
    }

    @Test
    fun spotIsAskedFirstAndFuturesNeverWhenSpotAnswers() {
        val asked = mutableListOf<String>()
        price("ETH") { url -> asked += url; json("""{"price":"2318.41"}""") }
        assertEquals(listOf("https://api.binance.com/api/v3/ticker/price?symbol=ETHUSDT"), asked)
    }

    @Test
    fun tickerSpotDoesNotListFallsBackToFutures() {
        val asked = mutableListOf<String>()
        val label = price("NVDA") { url ->
            asked += url
            if (url.startsWith("https://api.binance.com/")) throw FileNotFoundException("HTTP 400") // spot: Invalid symbol
            json("""{"symbol":"NVDAUSDT","price":"182.46"}""")
        }
        assertEquals("NVDA 182", label)
        assertEquals(
            listOf(
                "https://api.binance.com/api/v3/ticker/price?symbol=NVDAUSDT",
                "https://fapi.binance.com/fapi/v1/ticker/price?symbol=NVDAUSDT",
            ),
            asked,
        )
    }

    @Test
    fun tickerNeitherMarketListsThrowsSoSettingsCanRefuseIt() {
        val asked = mutableListOf<String>()
        assertThrows(IOException::class.java) {
            price("NOPE") { url -> asked += url; throw IOException("HTTP 400") }
        }
        assertEquals(3, asked.size)
    }

    @Test
    fun binanceRefusingAUsAddressFallsBackToCoinbase() {
        val asked = mutableListOf<String>()
        val label = price("BTC") { url ->
            asked += url
            if ("binance.com/" in url) throw IOException("HTTP 451") // "Service unavailable from a restricted location"
            json("""{"data":{"amount":"80604.415","base":"BTC","currency":"USD"}}""")
        }
        assertEquals("₿80.6K", label)
        assertEquals("https://api.coinbase.com/v2/prices/BTC-USD/spot", asked.last())
    }

    @Test
    fun coinbaseIsNotAskedWhenBinanceAnswers() {
        val asked = mutableListOf<String>()
        price("ETH") { url -> asked += url; json("""{"symbol":"ETHUSDT","price":"2318.2"}""") }
        assertEquals(listOf("https://api.binance.com/api/v3/ticker/price?symbol=ETHUSDT"), asked)
    }

    @Test
    fun cancellationIsNotMistakenForAMarketThatDoesNotListTheTicker() {
        val asked = mutableListOf<String>()
        assertThrows(CancellationException::class.java) {
            price("ETH") { url -> asked += url; throw CancellationException("screen left") }
        }
        assertEquals(1, asked.size) // it stopped at once, rather than going on to futures and Coinbase
    }

    @Test
    fun noConnectionThrowsInsteadOfShowingAPrice() {
        assertThrows(IOException::class.java) { price("BTC") { throw java.net.UnknownHostException("offline") } }
    }

    @Test
    fun answerWithoutAUsablePriceThrows() {
        assertThrows(JSONException::class.java) { price("BTC") { json("""{"code":-1121,"msg":"Invalid symbol."}""") } }
        assertThrows(NumberFormatException::class.java) { price("BTC") { json("""{"price":"soon"}""") } }
    }

    // --- next event ----------------------------------------------------------------------------

    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun setUpCalendar() {
        FakeClockCalendar.rows = emptyList()
        Robolectric.setupContentProvider(FakeClockCalendar::class.java, CalendarContract.AUTHORITY)
    }

    @Test
    fun untitledEventsAreSkippedSoTheNextTitledOneIsShown() {
        FakeClockCalendar.rows = listOf(
            arrayOf(null, 100L, 200L, 1L),
            arrayOf("  ", 150L, 250L, 2L),
            arrayOf("Dentist", 300L, 400L, 3L),
        )
        val next = runBlocking { nextEvent(context) } as EventState.Next
        assertEquals("Dentist", next.title)
        assertEquals(3L, next.id)
    }

    @Test
    fun onlyUntitledEventsMeansNoEvent() {
        FakeClockCalendar.rows = listOf(arrayOf(null, 100L, 200L, 1L))
        assertEquals(EventState.None, runBlocking { nextEvent(context) })
    }

    // --- last known location -------------------------------------------------------------------

    private val locations = shadowOf(context.getSystemService(LocationManager::class.java))

    /** A network fix taken [hours] ago on the phone's own clock, which is how its age is told. */
    private fun fixAged(hours: Long): Location {
        ShadowSystemClock.advanceBy(Duration.ofDays(4)) // so "72 hours ago" is not before the phone started
        return Location(LocationManager.NETWORK_PROVIDER).apply {
            latitude = 47.5
            longitude = 19.0
            time = System.currentTimeMillis() // a wall clock can be set to anything: must not decide the age
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos() - TimeUnit.HOURS.toNanos(hours)
        }
    }

    @Test
    fun aRecentLastKnownFixIsUsed() {
        locations.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        locations.simulateLocation(fixAged(1))
        assertEquals(47.5, runBlocking { coarseLocation(context) }.latitude, 0.0)
    }

    @Test
    fun aLastKnownFixFromDaysAgoIsNotTheTemperatureForHere() {
        locations.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        locations.simulateLocation(fixAged(72))
        locations.setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
        assertThrows(IllegalStateException::class.java) { runBlocking { coarseLocation(context) } }
    }
}

/** A stand-in for the Calendar provider as the clock asks: rows are (title, begin, end, event id). */
class FakeClockCalendar : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, args: Array<String>?, sort: String?): Cursor =
        MatrixCursor(projection ?: emptyArray()).also { c -> rows.forEach { c.addRow(it) } }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0

    companion object {
        var rows: List<Array<Any?>> = emptyList()
    }
}
