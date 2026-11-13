package cc.gyula.wave.data

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.core.net.toUri

/** Everything Laconic hands over to another app or to Android: alarms, calendar, browser, settings screens. */
class SystemActions(private val context: Context) {
    /** When the next alarm rings, from any alarm app; null if none is set. */
    fun nextAlarm(): Long? = context.getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime

    fun openAlarms() = start(Intent(AlarmClock.ACTION_SHOW_ALARMS))

    fun openEvent(id: Long, begin: Long, end: Long) = start(
        Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
    )

    /** The dialler with [number] typed in, not calling: no CALL_PHONE permission, on purpose. */
    fun dial(number: String) = start(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))

    /** A new text message to [number] in the messaging app. */
    fun message(number: String) = start(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)))

    /** A new email to [address] in the mail app. */
    fun email(address: String) = start(Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", address, null)))

    /** A contact's page in the Contacts app. */
    fun openContact(uri: Uri) = start(Intent(Intent.ACTION_VIEW, uri))

    /**
     * What another app added to a contact ("Voice call", "Message"): that row opened, so the app that wrote it
     * handles it, aimed at [Reach.pkg] when that is known.
     */
    fun openReach(reach: Reach) = start(
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, reach.dataId), reach.mime)
            .also { if (reach.pkg.isNotBlank()) it.setPackage(reach.pkg) },
    )

    /** Laconic's own page in Android's app settings, where a permission refused for good can be switched on. */
    fun openAppDetails() =
        start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))

    /** The phone's own web search; a browser with Google if nothing handles that. */
    fun webSearch(query: String) = context.guarded {
        try {
            context.startActivity(newTask(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)))
        } catch (e: ActivityNotFoundException) {
            context.startActivity(newTask(Intent(Intent.ACTION_VIEW, ("https://www.google.com/search?q=" + Uri.encode(query)).toUri())))
        }
    }

    /** In the default browser, or the app that owns the link (a YouTube address opens YouTube). */
    fun openUrl(url: String) = start(Intent(Intent.ACTION_VIEW, url.toUri()))

    fun copy(text: String) {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Laconic", text))
        // Android 13+ shows its own "Copied" note.
        if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Copied $text", Toast.LENGTH_SHORT).show()
    }

    /**
     * The camera, ready for a photo. With more than one camera app installed, the phone's own one, as the
     * lock screen's button opens it, rather than asking each time; the choice only appears without one.
     */
    fun openCamera() {
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        val own = context.packageManager.queryIntentActivities(intent, 0)
            .firstOrNull { it.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 }
        start(if (own != null) intent.setPackage(own.activityInfo.packageName) else intent)
    }

    fun openWallpaperPicker() = start(Intent(Intent.ACTION_SET_WALLPAPER))

    /** Pulls down the notification shade, as a swipe from the status bar would. Not public API, hence reflection. */
    @SuppressLint("WrongConstant")
    fun openNotifications() {
        try {
            val statusBar = context.getSystemService("statusbar")
            statusBar.javaClass.getMethod("expandNotificationsPanel").invoke(statusBar)
        } catch (e: ReflectiveOperationException) {
            Toast.makeText(context, "Can't open notifications", Toast.LENGTH_SHORT).show()
        }
    }

    fun openUsageAccessSettings() = context.guarded {
        val intent = newTask(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        try {
            // Straight to Laconic's own switch where the phone supports it.
            context.startActivity(Intent(intent).setData(Uri.fromParts("package", context.packageName, null)))
        } catch (e: ActivityNotFoundException) {
            context.startActivity(intent)
        }
    }

    fun openMediaAccessSettings() = context.guarded { context.startActivity(mediaAccessIntent(context)) }

    /** Opens whatever is playing — the session's own screen if it offers one, else the app. */
    fun openPlayer(np: NowPlaying) = context.guarded {
        val activity = np.controller.sessionActivity
        if (activity != null) {
            activity.send()
        } else {
            context.packageManager.getLaunchIntentForPackage(np.controller.packageName)?.let { context.startActivity(newTask(it)) }
        }
    }

    private fun start(intent: Intent) = context.guarded { context.startActivity(newTask(intent)) }

    private fun newTask(intent: Intent) = intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/**
 * Runs [block], which hands over to another app, and says so in a toast if that app is gone or refuses —
 * rather than crashing the home screen, which would leave the phone without one.
 */
internal inline fun Context.guarded(block: () -> Unit) {
    try {
        block()
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, "Can't open that", Toast.LENGTH_SHORT).show()
    } catch (e: SecurityException) {
        Toast.makeText(this, "Can't open that", Toast.LENGTH_SHORT).show()
    } catch (e: IllegalStateException) { // e.g. a shortcut in a locked work profile
        Toast.makeText(this, "Can't open that", Toast.LENGTH_SHORT).show()
    } catch (e: PendingIntent.CanceledException) {
        Toast.makeText(this, "Can't open that", Toast.LENGTH_SHORT).show()
    }
}
