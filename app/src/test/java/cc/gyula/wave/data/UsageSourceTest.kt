package cc.gyula.wave.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.os.Process
import cc.gyula.wave.core.HomeList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowUsageStatsManager

/** The used-apps lists come from Android's usage stats, and say so (null) when the user hasn't allowed that. */
@RunWith(RobolectricTestRunner::class)
class UsageSourceTest {
    private val app = RuntimeEnvironment.getApplication()
    private val usage = shadowOf(app.getSystemService(UsageStatsManager::class.java))
    private val source = UsageSource(app)

    @Before
    fun setUp() {
        allow(true)
    }

    private fun allow(yes: Boolean) = shadowOf(app.getSystemService(AppOpsManager::class.java)).setMode(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        app.packageName,
        if (yes) AppOpsManager.MODE_ALLOWED else AppOpsManager.MODE_IGNORED,
    )

    private fun open(pkg: String, minutesAgo: Int, type: Int = UsageEvents.Event.ACTIVITY_RESUMED) =
        usage.addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage(pkg)
                .setTimeStamp(System.currentTimeMillis() - minutesAgo * 60_000L)
                .setEventType(type)
                .build(),
        )

    @Test
    fun withoutUsageAccessThereIsNoListAtAll() {
        allow(false)
        open("a", 5)
        assertFalse(source.hasAccess())
        assertNull(source.used(HomeList.RECENT))
        assertNull(source.used(HomeList.FREQUENT))
    }

    @Test
    fun recentListsMostRecentlyOpenedFirst() {
        open("a", 30)
        open("b", 20)
        open("c", 10)
        open("a", 5)
        assertTrue(source.hasAccess())
        assertEquals(listOf("a", "c", "b"), source.used(HomeList.RECENT))
    }

    @Test
    fun frequentListsMostOpenedFirst() {
        open("a", 40)
        open("b", 30)
        open("a", 20)
        open("c", 10)
        open("a", 5)
        assertEquals("a", source.used(HomeList.FREQUENT)!!.first())
    }

    @Test
    fun onlyAnAppComingToTheFrontCountsAsOpeningIt() {
        open("a", 30, UsageEvents.Event.ACTIVITY_PAUSED)
        open("b", 20)
        open("a", 10, UsageEvents.Event.ACTIVITY_STOPPED)
        assertEquals(listOf("b"), source.used(HomeList.RECENT))
    }

    // The newer way to register an activity needs a whole fake package; for one ResolveInfo this is simpler.
    @Suppress("DEPRECATION")
    private fun installHome(pkg: String, priority: Int) = shadowOf(app.packageManager).addResolveInfoForIntent(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
        ResolveInfo().apply {
            activityInfo = ActivityInfo().apply { packageName = pkg }
            this.priority = priority
        },
    )

    @Test
    fun installedHomeScreensAreNotAppsYouOpen() {
        installHome("old.launcher", priority = 0)
        open("old.launcher", 30)
        open("a", 20)
        assertEquals(listOf("a"), source.used(HomeList.RECENT))
    }

    @Test
    fun settingsStandInHomeUsedWhileThePhoneStartsStaysAnApp() {
        installHome("com.android.settings", priority = -1000)
        open("com.android.settings", 30)
        open("a", 20)
        assertEquals(listOf("a", "com.android.settings"), source.used(HomeList.RECENT))
    }
}

/** Usage access can be taken away between the check and the query; that reads as "no access". */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [RevokedUsageStats::class])
class UsageAccessRevokedTest {
    private val app = RuntimeEnvironment.getApplication()

    @Test
    fun aQueryRefusedAfterTheCheckPassedIsNoAccessNotACrash() {
        shadowOf(app.getSystemService(AppOpsManager::class.java)).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            app.packageName,
            AppOpsManager.MODE_ALLOWED,
        )
        assertNull(UsageSource(app).used(HomeList.RECENT))
    }
}

/** Android's usage stats as they are once the user has switched the access off mid-way. */
@Implements(UsageStatsManager::class)
class RevokedUsageStats {
    @Implementation
    fun queryEvents(beginTime: Long, endTime: Long): UsageEvents = throw SecurityException("usage access revoked")
}
