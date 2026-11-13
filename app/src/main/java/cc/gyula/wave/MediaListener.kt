package cc.gyula.wave

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import cc.gyula.wave.core.Waiting
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Android only lets an app see other apps' media sessions while it holds notification access, so this
 * listener exists to be switched on in Settings. Of the notifications it is sent it keeps only which apps
 * have one waiting, for the dots on the home screen; never their content.
 *
 * It stays at cc.gyula.wave.MediaListener: Android keeps the notification-access grant under this class
 * name, so moving it would quietly switch access off on the next update.
 */
class MediaListener : NotificationListenerService() {
    // Read in full only on connect; after that each post and removal carries what is needed.
    private val waiting = Waiting()

    override fun onListenerConnected() {
        notified.value = try {
            waiting.reset(activeNotifications.orEmpty().map { Triple(it.key, it.packageName, it.waits()) })
        } catch (e: SecurityException) { // access switched off mid-way
            waiting.clear()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        notified.value = waiting.posted(sbn.key, sbn.packageName, sbn.waits())
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        notified.value = waiting.removed(sbn.key)
    }

    override fun onListenerDisconnected() { notified.value = waiting.clear() }

    // Ongoing ones (music, navigation, a running timer) aren't waiting for anyone.
    private fun StatusBarNotification.waits() = isClearable && !isOngoing

    companion object {
        /** Packages with a notification waiting. */
        val notified = MutableStateFlow<Set<String>>(emptySet())
    }
}

