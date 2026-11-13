package cc.gyula.wave.core

import java.math.BigDecimal
import java.math.MathContext
import java.util.Locale

/** "₿80.7K" for Bitcoin (the ₿ is drawn on screen), otherwise the ticker and price, e.g. "ETH 2318". */
fun priceLabel(ticker: String, value: String) = if (ticker == "BTC") "₿$value" else "$ticker $value"

/** Short enough for the clock band: 124K, 80.7K, 4025, 182, 2.41, 0.163. */
fun formatPrice(p: Double): String = when {
    p >= 100_000 -> "%.0fK".format(Locale.US, p / 1000)
    p >= 10_000 -> "%.1fK".format(Locale.US, p / 1000)
    p >= 100 -> "%.0f".format(Locale.US, p)
    p >= 1 -> "%.2f".format(Locale.US, p)
    else -> BigDecimal(p).round(MathContext(3)).stripTrailingZeros().toPlainString()
}
