package cc.gyula.wave.data

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import cc.gyula.wave.core.WidgetSize

/** Laconic's id with Android's widget service; any fixed number works, it only has to stay the same. */
const val WIDGET_HOST_ID = 0x57415645

/** Request code for a widget's own setup screen; the result comes back through MainActivity. */
const val REQUEST_CONFIGURE_WIDGET = 0x5741

/**
 * Android's widget host, which keeps every widget view it made, and so the activity each was made in, until
 * [clearViews]. Android hides that behind `protected`; this only opens it up.
 */
open class WidgetHost(context: Context) : AppWidgetHost(context, WIDGET_HOST_ID) {
    public override fun clearViews() = super.clearViews()
}

/** The widgets on Laconic's pages: Android's widget host, and where each one sits and how big it is. */
class WidgetStore(
    context: Context,
    private val settings: SettingsStore,
    val host: WidgetHost = WidgetHost(context),
) {
    private val manager = AppWidgetManager.getInstance(context)
    private val userManager = context.getSystemService(UserManager::class.java)

    val pages = settings.widgetPages.flow
    val heights = settings.widgetHeights.flow
    val widths = settings.widgetWidths.flow

    /** A widget picked but still in its own setup screen: (page, id). */
    var pending: Pair<Int, Int>? = null

    /** Gives back ids handed out for a widget that never got placed (the picker left mid-way). */
    fun releaseUnplaced() {
        val placed = settings.widgetPages.value.pages.flatten().toSet()
        host.appWidgetIds.filter { it !in placed }.forEach(host::deleteAppWidgetId)
    }

    /** Every widget on the phone, personal and work, for the picker. */
    fun providers(): List<AppWidgetProviderInfo> = userManager.userProfiles.flatMap { manager.getInstalledProvidersForProfile(it) }

    /** Android 15's preview of a widget, rendered by its app; null if the app doesn't provide one. */
    @RequiresApi(35)
    fun generatedPreview(info: AppWidgetProviderInfo): RemoteViews? =
        manager.getWidgetPreview(info.provider, info.profile, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)

    /** True when Laconic may show [info] in [id] straight away; otherwise Android has to ask the user first. */
    fun bind(id: Int, info: AppWidgetProviderInfo): Boolean = manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)

    fun info(id: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(id)

    /**
     * Lets go of every widget view; called when the activity they were made in is destroyed. The store outlives
     * it (dark mode, font size, language or wallpaper colours make a new one), and views on pages not shown
     * again would keep the old activity alive. The widgets stay placed; their pages make new views when shown.
     */
    fun dropViews() = host.clearViews()

    fun add(page: Int, id: Int) {
        val next = settings.widgetPages.value.withWidget(page, id) ?: return host.deleteAppWidgetId(id)
        settings.widgetPages.set(next)
    }

    fun remove(id: Int) {
        forget(id)
        settings.widgetPages.set(settings.widgetPages.value.without(id))
    }

    /** Called from the activity when a widget's setup screen closes. */
    fun configured(ok: Boolean) {
        val (page, id) = pending ?: return
        pending = null
        if (ok) add(page, id) else host.deleteAppWidgetId(id)
    }

    /** [left] pages on the left of home and [right] on its right; a page taken away takes its widgets with it. */
    fun setPageCounts(left: Int, right: Int) {
        val (next, removed) = settings.widgetPages.value.resized(left, right)
        removed.forEach(::forget)
        settings.widgetPages.set(next)
    }

    fun setHeight(id: Int, dp: Int?) = settings.widgetHeights.set(settings.widgetHeights.value.with(id, dp))
    fun setWidth(id: Int, columns: Int?) = settings.widgetWidths.set(settings.widgetWidths.value.with(id, columns))

    private fun forget(id: Int) {
        host.deleteAppWidgetId(id)
        setHeight(id, null)
        setWidth(id, null)
    }

    private fun Map<Int, Int>.with(id: Int, value: Int?) = if (value == null) this - id else this + (id to value)
}

/** A widget's own size wishes, with the fields that only exist on newer Android left at 0 below that. */
fun AppWidgetProviderInfo.size(): WidgetSize {
    val s = Build.VERSION.SDK_INT >= 31
    return WidgetSize(
        minWidth = minWidth,
        minHeight = minHeight,
        minResizeWidth = minResizeWidth,
        minResizeHeight = minResizeHeight,
        maxResizeWidth = if (s) maxResizeWidth else 0,
        maxResizeHeight = if (s) maxResizeHeight else 0,
        targetCellWidth = if (s) targetCellWidth else 0,
        targetCellHeight = if (s) targetCellHeight else 0,
        resizesSideways = resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0,
        resizesVertically = resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0,
    )
}

/** True when a widget's setup screen may be skipped (Android 12+). */
fun AppWidgetProviderInfo.configurationOptional(): Boolean =
    Build.VERSION.SDK_INT >= 31 && widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
