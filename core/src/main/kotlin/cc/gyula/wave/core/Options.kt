package cc.gyula.wave.core

/** What the clock can show. [onBand] items run along the line through the weekday, in this order. */
enum class ClockItem(val label: String, val onBand: Boolean = true) {
    TIME("Time"),
    ALARM("Next alarm"), // shown only while one is set
    WEATHER("Weather"),
    DATE("Date"),
    BTC("Price"), // any Binance coin; the name predates the ticker choice and is kept for saved settings
    EVENT("Next event", onBand = false), // a line under the weekday
    MUSIC("Music controls", onBand = false), // a player under the clock while something plays
}

/** Where the band with time, weather, date and price sits. */
enum class BandPosition(val label: String) {
    ABOVE("above"),
    THROUGH("through"), // a gap cut through the weekday's letters
    BELOW("below"),
}

/** How the band is drawn, across the whole screen width. */
enum class BandLook(val label: String) {
    PLAIN("plain"),
    INVERTED("inverted"), // a white band with the items in black
    LINES("lines"), // a thin line above and below
}

/** What the home screen lists. */
enum class HomeList(val label: String, val order: String) {
    PINNED("pinned", "your order"),
    FREQUENT("most used", "most used first"),
    RECENT("recent", "latest first"),
}

/** How the pinned apps are laid out on home: a list, or icons grouped in boxes. */
enum class HomeLayout(val label: String) {
    LIST("list"),
    BOXES("boxes"),
}

/**
 * How strongly the lines between boxes show: thickness and brightness go together, since a thick dim line or a
 * thin bright one both look wrong. [px] is in whole screen pixels, so every line stays equally crisp.
 */
enum class BoxLines(val label: String, val px: Int, val alpha: Float) {
    FAINT("faint", 1, 0.18f),
    CLEAR("clear", 1, 0.4f),
    BOLD("bold", 2, 0.6f),
}

/** Which edge the letter strip sits on. */
enum class StripSide(val label: String) {
    RIGHT("Right edge"),
    LEFT("Left edge"),
    EITHER("Either edge, hidden"), // a dot on each edge; the letters show on the one you touch
    OFF("Off"), // no letters at all: apps by search; the home list and boxes get the margins back
}

/** The option after [current], wrapping round: settings rows cycle through their choices on each tap. */
inline fun <reified T : Enum<T>> next(current: T): T = enumValues<T>().let { it[(current.ordinal + 1) % it.size] }
