package cc.gyula.wave.data

import android.content.SharedPreferences
import androidx.core.content.edit
import cc.gyula.wave.core.BandLook
import cc.gyula.wave.core.BandPosition
import cc.gyula.wave.core.ClockItem
import cc.gyula.wave.core.CornerAction
import cc.gyula.wave.core.BoxLines
import cc.gyula.wave.core.HomeLayout
import cc.gyula.wave.core.HomeList
import cc.gyula.wave.core.StripSide
import cc.gyula.wave.core.WidgetPages
import cc.gyula.wave.core.formatCorner
import cc.gyula.wave.core.parseCorner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** One saved setting: its value to watch, and a setter that also saves it. */
class Setting<T>(initial: T, private val save: (T) -> Unit) {
    private val state = MutableStateFlow(initial)
    val flow: StateFlow<T> = state.asStateFlow()
    val value: T get() = state.value

    fun set(value: T) {
        state.value = value
        save(value)
    }
}

/**
 * Everything Laconic remembers, in one SharedPreferences file. The keys are a contract with installed copies:
 * renaming one would quietly reset that setting on update, so old formats are read and migrated instead.
 */
class SettingsStore(private val prefs: SharedPreferences) {
    private fun bool(key: String, default: Boolean) =
        Setting(prefs.getBoolean(key, default)) { prefs.edit { putBoolean(key, it) } }

    private fun string(key: String, default: String) =
        Setting(prefs.getString(key, default)!!) { prefs.edit { putString(key, it) } }

    private fun optionalColor(key: String) = Setting(prefs.takeIf { it.contains(key) }?.getInt(key, 0)) {
        prefs.edit { if (it == null) remove(key) else putInt(key, it) }
    }

    private inline fun <reified T : Enum<T>> enum(key: String, default: T) = Setting(
        prefs.getString(key, null)?.let { name -> enumValues<T>().find { it.name == name } } ?: default,
    ) { prefs.edit { putString(key, it.name) } }

    /** Pinned apps, by [cc.gyula.wave.data.AppEntry.key], in their order on home. */
    val pins = Setting(prefs.getString(KEY_PINS, "")!!.split('\n').filter { it.isNotEmpty() }) {
        prefs.edit { putString(KEY_PINS, it.joinToString("\n")) }
    }

    val clockItems = Setting(
        prefs.getString(KEY_CLOCK, null)?.split(',')?.mapNotNull { name -> ClockItem.entries.find { it.name == name } }?.toSet()
            ?: DEFAULT_CLOCK,
    ) { prefs.edit { putString(KEY_CLOCK, it.joinToString(",") { item -> item.name }) } }

    val bandPosition = enum(KEY_BAND_POSITION, BandPosition.THROUGH)
    val bandLook = enum(KEY_BAND_LOOK, BandLook.PLAIN)

    fun toggleClockItem(item: ClockItem) = clockItems.set(clockItems.value.let { if (item in it) it - item else it + item })

    /** The coin or stock on the band, priced in USDT. */
    val ticker = string(KEY_TICKER, "BTC")

    /** Album art in the player is greyscale like the icons unless this is on. */
    val colorArt = bool(KEY_COLOR_ART, false)
    val animations = bool(KEY_ANIMATIONS, true)

    /** Pulling home down opens search when on, Android's notification shade when off. */
    val pullSearch = bool(KEY_PULL_SEARCH, false)

    /** Search also looks through contacts. */
    val searchContacts = bool(KEY_SEARCH_CONTACTS, true)

    /** Search also looks through the coming year of calendar events. */
    val searchEvents = bool(KEY_SEARCH_EVENTS, true)

    val homeList = enum(KEY_HOME_LIST, HomeList.PINNED)
    val homeLayout = enum(KEY_HOME_LAYOUT, HomeLayout.LIST)
    val boxLines = enum(KEY_BOX_LINES, BoxLines.FAINT)

    /** Which box each pin sits in, as the tiling tree; null until boxes are first changed. See [cc.gyula.wave.core.reconciled]. */
    val boxes = Setting(boxesFromJson(prefs.getString(KEY_BOXES, null))) {
        prefs.edit { if (it == null) remove(KEY_BOXES) else putString(KEY_BOXES, boxesToJson(it)) }
    }

    /** Home's text and icon colour as ARGB; null = white. */
    val ink = optionalColor(KEY_INK)

    /** A solid colour drawn behind home as ARGB; null = the wallpaper shows. */
    val background = optionalColor(KEY_BACKGROUND)

    /** The home list A–Z instead of in its own order. */
    val homeAz = bool(KEY_HOME_AZ, false)
    val showIcons = bool(KEY_ICONS, true)

    /**
     * What the buttons in the bottom corners do; flashlight and camera, as on the lock screen, until chosen.
     * 0.7 had one on/off switch for both: saved off, both corners start off. It is read only until these are saved.
     */
    val leftCorner = corner(KEY_CORNER_LEFT, CornerAction.Torch)
    val rightCorner = corner(KEY_CORNER_RIGHT, CornerAction.Camera)

    /** A dot by apps with notifications waiting; needs notification access. */
    val dots = bool(KEY_DOTS, true)

    /** Apps left out of the letter lists and the used-apps lists; search still finds them. */
    val hidden = Setting(prefs.getStringSet(KEY_HIDDEN, emptySet())!!.toSet()) {
        prefs.edit { putStringSet(KEY_HIDDEN, it) }
    }

    /** Your own names for apps, by key. */
    val names = Setting(
        prefs.getString(KEY_NAMES, null)?.let { json ->
            runCatching { JSONObject(json).let { o -> o.keys().asSequence().associateWith(o::getString) } }.getOrNull()
        }.orEmpty(),
    ) { prefs.edit { putString(KEY_NAMES, JSONObject(it).toString()) } }

    /** 0.2 saved only a left-handed flag; it carries over. */
    val stripSide = Setting(
        prefs.getString(KEY_STRIP, null)?.let { name -> StripSide.entries.find { it.name == name } }
            ?: if (prefs.getBoolean(KEY_LEFT_HANDED, false)) StripSide.LEFT else StripSide.RIGHT,
    ) { prefs.edit { putString(KEY_STRIP, it.name) } }

    val widgetPages = Setting(loadWidgetPages()) {
        prefs.edit {
            putString(KEY_WIDGETS, it.pages.joinToString(";") { ids -> ids.joinToString(",") }.takeIf { _ -> it.pages.isNotEmpty() })
            putInt(KEY_LEFT_PAGES, it.left)
            remove(KEY_WIDGETS_LEFT)
        }
    }

    private fun corner(key: String, default: CornerAction): Setting<CornerAction> {
        val old = if (prefs.contains(key) || prefs.getBoolean(KEY_CORNERS, true)) default else CornerAction.Off
        return Setting(parseCorner(prefs.getString(key, null), old)) { prefs.edit { putString(key, formatCorner(it)) } }
    }

    init {
        // Corners saved in today's form straight away, so the old on/off switch is read only once.
        if (!prefs.contains(KEY_CORNER_LEFT)) leftCorner.set(leftCorner.value)
        if (!prefs.contains(KEY_CORNER_RIGHT)) rightCorner.set(rightCorner.value)
        // Saved in today's form straight away, so the old one-side flag is read only once.
        if (!prefs.contains(KEY_LEFT_PAGES)) widgetPages.set(widgetPages.value)
    }

    /** Sizes set by resizing, per widget id: height in dp, width in columns. */
    val widgetHeights = intMap(KEY_WIDGET_HEIGHTS)
    val widgetWidths = intMap(KEY_WIDGET_WIDTHS)

    private fun loadWidgetPages(): WidgetPages {
        val pages = prefs.getString(KEY_WIDGETS, null)?.split(';')?.map { page ->
            page.split(',').mapNotNull(String::toIntOrNull)
        }.orEmpty()
        return if (prefs.contains(KEY_LEFT_PAGES)) WidgetPages(pages, prefs.getInt(KEY_LEFT_PAGES, 0))
        else WidgetPages.fromOneSide(pages, onLeft = prefs.getBoolean(KEY_WIDGETS_LEFT, true))
    }

    private fun intMap(key: String) = Setting(
        prefs.getString(key, "")!!.split(',').mapNotNull {
            val (k, v) = it.split(':').takeIf { p -> p.size == 2 } ?: return@mapNotNull null
            (k.toIntOrNull() ?: return@mapNotNull null) to (v.toIntOrNull() ?: return@mapNotNull null)
        }.toMap(),
    ) { prefs.edit { putString(key, it.entries.joinToString(",") { e -> "${e.key}:${e.value}" }) } }

    private companion object {
        const val KEY_PINS = "favorites" // pins were called favourites in the code; the saved key keeps that name
        const val KEY_CLOCK = "clock_items"
        const val KEY_TICKER = "ticker"
        const val KEY_BAND_POSITION = "band_position"
        const val KEY_ANIMATIONS = "animations"
        const val KEY_COLOR_ART = "color_art"
        const val KEY_BAND_LOOK = "band_look"
        const val KEY_LEFT_HANDED = "left_handed" // 0.2; read once, replaced by KEY_STRIP
        const val KEY_STRIP = "strip_side"
        const val KEY_PULL_SEARCH = "pull_search"
        const val KEY_WIDGETS = "widget_pages"
        const val KEY_WIDGETS_LEFT = "widgets_left" // until 0.3.2: every page on the left, or every one on the right
        const val KEY_LEFT_PAGES = "widget_pages_left"
        const val KEY_WIDGET_HEIGHTS = "widget_heights"
        const val KEY_WIDGET_WIDTHS = "widget_widths"
        const val KEY_SEARCH_CONTACTS = "search_contacts"
        const val KEY_SEARCH_EVENTS = "search_events"
        const val KEY_HOME_LIST = "home_list"
        const val KEY_HOME_LAYOUT = "home_layout"
        const val KEY_BOX_LINES = "box_lines"
        const val KEY_BOXES = "boxes"
        const val KEY_HOME_AZ = "home_az"
        const val KEY_ICONS = "icons"
        const val KEY_DOTS = "dots"
        const val KEY_CORNERS = "corner_buttons" // 0.7; read once, replaced by the two below
        const val KEY_CORNER_LEFT = "corner_left"
        const val KEY_CORNER_RIGHT = "corner_right"
        const val KEY_HIDDEN = "hidden"
        const val KEY_NAMES = "names"
        const val KEY_INK = "ink_color"
        const val KEY_BACKGROUND = "background_color"
        val DEFAULT_CLOCK = setOf(ClockItem.WEATHER, ClockItem.DATE, ClockItem.BTC, ClockItem.EVENT, ClockItem.MUSIC)
    }
}

fun Setting<Boolean>.toggle() = set(!value)

/** Moves an option on to its next choice, as the settings rows do on each tap. */
inline fun <reified T : Enum<T>> Setting<T>.cycle() = set(cc.gyula.wave.core.next(value))
