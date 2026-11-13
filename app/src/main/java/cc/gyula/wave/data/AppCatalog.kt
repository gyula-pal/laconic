package cc.gyula.wave.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.DisplayMetrics
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import cc.gyula.wave.core.sectionComparator
import cc.gyula.wave.core.sectionOf
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One launchable app, personal or work profile. */
class AppEntry(
    /** Stable across restarts: component and profile serial number. Pins, names and hidden apps use it. */
    val key: String,
    val label: String, // the user's name for it, if renamed
    val originalLabel: String,
    val component: ComponentName,
    val user: UserHandle,
    val info: LauncherActivityInfo,
) {
    val section: Char = sectionOf(label)
    val packageName: String get() = component.packageName
    val isPersonal: Boolean get() = user == Process.myUserHandle()
}

/**
 * The installed apps, kept current as apps come and go, with their icons and shortcuts. Opening apps goes
 * through here too: Android's LauncherApps does both, and only for the default home app.
 */
class AppCatalog(
    private val context: Context,
    private val names: StateFlow<Map<String, String>>,
    private val scope: CoroutineScope,
    // How an app's icon is fetched; a parameter so tests can make it fail the way a removed app does.
    private val loadIcon: (LauncherActivityInfo, Int) -> Drawable = { info, density -> info.getIcon(density) },
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val iconPx = (40 * context.resources.displayMetrics.density).roundToInt()

    // Icons are rendered when a row first shows them and kept up to ~8 MB. Rendering all ~200 at
    // start-up is what made the launcher peak around 270-400 MB PSS for its first seconds.
    private val iconCache = object : LruCache<String, ImageBitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())

    /** Every app, A–Z by section, under the names you gave them. */
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackageAdded(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackageChanged(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackagesAvailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) =
            packageNames.forEach(::changed)
        override fun onPackagesUnavailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) =
            packageNames.forEach(::changed)
    }

    private var loadJob: Job? = null

    // A load never suspends, so cancelling it does not stop it. Taking turns means a newer reload waits for an
    // older one, then reads the apps and names afresh: the last one to run is always the newest.
    private val loadTurn = Mutex()

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        scope.launch { names.collect { reload() } } // the first value loads the list
    }

    fun close() = launcherApps.unregisterCallback(callback)

    private fun changed(packageName: String) {
        iconCache.snapshot().keys.filter { it.startsWith("$packageName/") }.forEach { iconCache.remove(it) }
        reload()
    }

    private fun reload() {
        loadJob?.cancel()
        loadJob = scope.launch(Dispatchers.Default) { loadTurn.withLock { _apps.value = load(names.value) } }
    }

    private fun load(names: Map<String, String>): List<AppEntry> =
        launcherApps.profiles
            .flatMap { user -> launcherApps.getActivityList(null, user) }
            .filter { it.componentName.packageName != context.packageName }
            .map { info ->
                // Serial number, not the user id: it is what stays stable for a profile across reboots.
                val key = "${info.componentName.flattenToString()}#${userManager.getSerialNumberForUser(info.user)}"
                val label = info.label.toString().trim()
                AppEntry(key, names[key] ?: label, label, info.componentName, info.user, info)
            }
            .sortedWith(sectionComparator { it.label })

    fun cachedIcon(entry: AppEntry): ImageBitmap? = iconCache.get(entry.key)

    /**
     * The app's icon. Never throws for a bad app: one removed or updated while its row is on screen gets a
     * blank square of the same size, which is not cached, so the next ask can still get the real one.
     */
    suspend fun icon(entry: AppEntry): ImageBitmap = iconCache.get(entry.key)
        ?: withContext(Dispatchers.Default) { renderOrNull(entry.info) }?.also { iconCache.put(entry.key, it) }
        ?: createBitmap(iconPx, iconPx).asImageBitmap()

    private fun renderOrNull(info: LauncherActivityInfo): ImageBitmap? = try {
        renderIcon(info)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null // the app is gone or mid-update; a row without an icon beats a dead home screen
    }

    /**
     * Android 13+ themed (monochrome) glyph in white, for a calm one-colour list.
     * Apps that ship no monochrome layer fall back to a greyscale copy of their normal icon.
     */
    private fun renderIcon(info: LauncherActivityInfo): ImageBitmap {
        // xhdpi is plenty for a 40dp glyph. The device default (0) decodes xxxhdpi layers, ~4x the pixels,
        // which spiked the launcher to ~400 MB PSS for a few seconds while ~200 icons loaded.
        val icon = loadIcon(info, DisplayMetrics.DENSITY_XHIGH)
        val mono = if (Build.VERSION.SDK_INT >= 33) (icon as? AdaptiveIconDrawable)?.monochrome else null
        val bitmap = createBitmap(iconPx, iconPx)
        if (mono != null) {
            // Adaptive layers are 108dp with a 72dp safe zone: stretch so the safe zone fills the bitmap.
            val bleed = iconPx * 3 / 10
            mono.mutate().apply {
                setTint(android.graphics.Color.WHITE)
                setBounds(-bleed, -bleed, iconPx + bleed, iconPx + bleed)
                draw(Canvas(bitmap))
            }
        } else {
            val inset = iconPx / 8
            icon.mutate().apply {
                colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
                setBounds(inset, inset, iconPx - inset, iconPx - inset)
                draw(Canvas(bitmap))
            }
        }
        return bitmap.asImageBitmap()
    }

    fun launch(entry: AppEntry) = context.guarded {
        launcherApps.startMainActivity(entry.component, entry.user, null, null)
    }

    /**
     * The app's shortcuts (Maps → Home, Work …): its fixed ones first, then the ones it adds itself,
     * at most five like the stock launcher. Only the default home app may read these.
     */
    fun shortcuts(entry: AppEntry): List<ShortcutInfo> = try {
        if (!launcherApps.hasShortcutHostPermission()) emptyList() else launcherApps.getShortcuts(
            LauncherApps.ShortcutQuery()
                .setPackage(entry.packageName)
                .setActivity(entry.component)
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC),
            entry.user,
        ).orEmpty().sortedWith(compareBy({ !it.isDeclaredInManifest }, { it.rank })).take(5)
    } catch (e: SecurityException) {
        emptyList()
    } catch (e: IllegalStateException) { // work profile locked
        emptyList()
    }

    fun startShortcut(shortcut: ShortcutInfo) = context.guarded { launcherApps.startShortcut(shortcut, null, null) }

    fun openAppInfo(entry: AppEntry) = context.guarded {
        launcherApps.startAppDetailsActivity(entry.component, entry.user, null, null)
    }

    fun uninstall(entry: AppEntry) = context.guarded {
        context.startActivity(
            Intent(Intent.ACTION_DELETE, Uri.fromParts("package", entry.packageName, null))
                .putExtra(Intent.EXTRA_USER, entry.user)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
