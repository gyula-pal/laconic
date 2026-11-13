package cc.gyula.wave.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.scale
import cc.gyula.wave.MediaListener
import cc.gyula.wave.core.PauseClock
import kotlin.math.roundToInt
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

fun hasMediaAccess(context: Context): Boolean =
    context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

/** The Settings page where the user turns on Laconic's notification access (straight to its toggle on 11+). */
fun mediaAccessIntent(context: Context): Intent =
    if (Build.VERSION.SDK_INT >= 30) {
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
            Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
            ComponentName(context, MediaListener::class.java).flattenToString(),
        )
    } else {
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

class NowPlaying(
    val title: String,
    val artist: String,
    val art: ImageBitmap?,
    val playing: Boolean,
    /** elapsedRealtime of the last play/pause, so a long-paused player can drop off the home screen. */
    val since: Long,
    val controller: MediaController,
) {
    /**
     * Whether the home panel would look and behave the same for [other]: what it shows, plus which session its
     * buttons talk to. The cover counts only as present or not; comparing its pixels would be slow and pointless.
     */
    fun sameOnScreen(other: NowPlaying): Boolean =
        controller.sessionToken == other.controller.sessionToken &&
            title == other.title && artist == other.artist && playing == other.playing &&
            since == other.since && (art != null) == (other.art != null)
}

/** Shared by every collector: it must outlive the home view, which comes and goes. */
private val pauseClock = PauseClock()

/**
 * What's playing: the first playing session, else the first paused one, else null. Emits on every
 * play/pause/track change while collected; nothing is registered once collection stops.
 */
fun nowPlaying(context: Context, artPx: Int): Flow<NowPlaying?> = callbackFlow {
    val sessions = context.getSystemService(MediaSessionManager::class.java)
    val listener = ComponentName(context, MediaListener::class.java)
    val handler = Handler(Looper.getMainLooper())
    var controllers = emptyList<MediaController>()
    // Album art is decoded from the session's metadata only when the track changes, or while a track still has none.
    var artKey: String? = null
    var art: ImageBitmap? = null
    // What the collector last got; every media callback comes through here, most of them with nothing new to say.
    var sent = false
    var last: NowPlaying? = null

    fun emit(next: NowPlaying?) {
        if (sent && (last?.let { l -> next != null && l.sameOnScreen(next) } ?: (next == null))) return
        sent = true
        last = next
        trySend(next)
    }

    fun publish() {
        val c = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PAUSED }
        val meta = c?.metadata
        if (c == null || meta == null) {
            emit(null)
            return
        }
        val title = meta.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty()
        val key = "${c.packageName}|$title|$artist"
        // Some players set the title first and the cover a moment later: keep looking until it shows up.
        if (key != artKey || art == null) {
            artKey = key
            art = (meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON))
                ?.let { shrink(it, artPx).asImageBitmap() }
        }
        val state = c.playbackState!!
        val stamp = state.lastPositionUpdateTime.takeIf { it > 0 } ?: SystemClock.elapsedRealtime()
        val since = pauseClock.since(c.packageName, state.state == PlaybackState.STATE_PLAYING, state.position, stamp)
        emit(
            NowPlaying(
                title = title,
                artist = artist,
                art = art,
                playing = state.state == PlaybackState.STATE_PLAYING,
                since = since,
                controller = c,
            )
        )
    }

    val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
    }

    fun attach(list: List<MediaController>?) {
        controllers.forEach { it.unregisterCallback(callback) }
        controllers = list.orEmpty()
        controllers.forEach { it.registerCallback(callback, handler) }
        publish()
    }

    val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener(::attach)
    try {
        sessions.addOnActiveSessionsChangedListener(sessionsChanged, listener, handler)
        attach(sessions.getActiveSessions(listener))
    } catch (e: SecurityException) {
        emit(null) // access was switched off
    }
    awaitClose {
        runCatching { sessions.removeOnActiveSessionsChangedListener(sessionsChanged) }
        controllers.forEach { it.unregisterCallback(callback) }
    }
}

/** Scales [src] so its shorter side is [px]; a 1000 px cover would otherwise sit in memory for an 80 dp tile. */
private fun shrink(src: Bitmap, px: Int): Bitmap {
    val scale = px.toFloat() / minOf(src.width, src.height)
    if (scale >= 1f) return src
    return src.scale((src.width * scale).roundToInt(), (src.height * scale).roundToInt())
}
