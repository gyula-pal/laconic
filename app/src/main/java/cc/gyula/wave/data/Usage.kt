package cc.gyula.wave.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import cc.gyula.wave.core.AppOpen
import cc.gyula.wave.core.HomeList
import cc.gyula.wave.core.USAGE_DAYS
import cc.gyula.wave.core.rankUsage

/** Which apps you open, from Android's usage stats; needs the user to allow usage access. */
class UsageSource(private val context: Context) {
    fun hasAccess(): Boolean = context.getSystemService(AppOpsManager::class.java)
        .checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) ==
        AppOpsManager.MODE_ALLOWED

    /**
     * Packages for [mode], best first, over the last [USAGE_DAYS] days. Null without usage access, which includes
     * access taken away between the check and the query.
     */
    fun used(mode: HomeList): List<String>? {
        if (!hasAccess()) return null
        val now = System.currentTimeMillis()
        val events = try {
            context.getSystemService(UsageStatsManager::class.java).queryEvents(now - USAGE_DAYS * DAY_MS, now)
        } catch (e: SecurityException) {
            null
        } ?: return null
        val opens = sequence {
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) yield(AppOpen(event.packageName, event.timeStamp))
            }
        }
        return rankUsage(opens, mode, homeScreens())
    }

    /** Installed home screens. Settings' stand-in home, used only while the phone starts, has a negative priority. */
    private fun homeScreens(): Set<String> =
        context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .filter { it.priority >= 0 }.map { it.activityInfo.packageName }.toSet()

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}
