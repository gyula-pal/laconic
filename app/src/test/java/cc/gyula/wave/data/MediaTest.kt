package cc.gyula.wave.data

import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowMediaSessionManager

/** What the home screen's player is told, from the media sessions other apps have open. */
@RunWith(RobolectricTestRunner::class)
class MediaTest {
    private val app = RuntimeEnvironment.getApplication()
    private val sessions = shadowOf(app.getSystemService(MediaSessionManager::class.java))
    private val seen = mutableListOf<NowPlaying?>()
    private var job: Job? = null

    @Before
    fun setUp() {
        ShadowMediaSessionManager.reset()
    }

    @After
    fun tearDown() {
        job?.cancel()
    }

    private fun session(
        pkg: String,
        state: Int?,
        title: String? = "Song",
        artist: String? = "Band",
        albumArtist: String? = null,
        art: Bitmap? = null,
    ): MediaController {
        val mediaSession = MediaSession(app, pkg)
        val c = MediaController(app, mediaSession.sessionToken)
        shadowOf(c).setPackageName(pkg)
        state?.let { shadowOf(c).setPlaybackState(PlaybackState.Builder().setState(it, 1_000, 1f).build()) }
        if (title != null) shadowOf(c).setMetadata(metadata(title, artist, albumArtist, art))
        sessions.addController(c)
        return c
    }

    private fun metadata(title: String, artist: String?, albumArtist: String?, art: Bitmap?) =
        MediaMetadata.Builder().apply {
            putString(MediaMetadata.METADATA_KEY_TITLE, title)
            artist?.let { putString(MediaMetadata.METADATA_KEY_ARTIST, it) }
            albumArtist?.let { putString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST, it) }
            art?.let { putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) }
        }.build()

    /** The shadows hand callbacks to the main thread, as Android does. */
    private fun idleMainLooper() = shadowOf(Looper.getMainLooper()).idle()

    /** Starts collecting; whatever is emitted lands in [seen] straight away. */
    private fun collect(artPx: Int = 64) {
        job = CoroutineScope(Dispatchers.Unconfined).launch { nowPlaying(app, artPx).collect { seen += it } }
    }

    @Test
    fun nothingPlayingEmitsNull() {
        collect()
        assertEquals(listOf<NowPlaying?>(null), seen)
    }

    @Test
    fun playingSessionIsShownWithTitleAndArtist() {
        val c = session("music.app", PlaybackState.STATE_PLAYING)
        collect()
        val np = seen.single()!!
        assertEquals("Song", np.title)
        assertEquals("Band", np.artist)
        assertTrue(np.playing)
        assertSame(c, np.controller)
    }

    @Test
    fun aPlayingSessionBeatsAPausedOneListedBeforeIt() {
        session("paused.app", PlaybackState.STATE_PAUSED, title = "Old")
        session("playing.app", PlaybackState.STATE_PLAYING, title = "New")
        collect()
        assertEquals("New", seen.single()!!.title)
    }

    @Test
    fun withNothingPlayingTheFirstPausedSessionIsShownAsPaused() {
        session("stopped.app", PlaybackState.STATE_STOPPED, title = "Gone")
        session("first.app", PlaybackState.STATE_PAUSED, title = "First")
        session("second.app", PlaybackState.STATE_PAUSED, title = "Second")
        collect()
        val np = seen.single()!!
        assertEquals("First", np.title)
        assertFalse(np.playing)
    }

    @Test
    fun stoppedOrStatelessSessionsAreNotShown() {
        session("stopped.app", PlaybackState.STATE_STOPPED)
        session("idle.app", null)
        collect()
        assertEquals(listOf<NowPlaying?>(null), seen)
    }

    @Test
    fun sessionWithoutMetadataIsNotShown() {
        session("blank.app", PlaybackState.STATE_PLAYING, title = null)
        collect()
        assertEquals(listOf<NowPlaying?>(null), seen)
    }

    @Test
    fun albumArtistStandsInWhenThereIsNoArtist() {
        session("music.app", PlaybackState.STATE_PLAYING, artist = null, albumArtist = "The Album Band")
        collect()
        assertEquals("The Album Band", seen.single()!!.artist)
    }

    @Test
    fun missingArtistShowsAsEmptyNotAsNull() {
        session("music.app", PlaybackState.STATE_PLAYING, artist = null)
        collect()
        assertEquals("", seen.single()!!.artist)
    }

    @Test
    fun coverArtIsShrunkToTheTileSize() {
        session("music.app", PlaybackState.STATE_PLAYING, art = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888))
        collect(artPx = 100)
        val art = seen.single()!!.art
        assertNotNull(art)
        assertEquals(100, art!!.width)
        assertEquals(200, art.height)
    }

    @Test
    fun trackWithoutCoverHasNoArt() {
        session("music.app", PlaybackState.STATE_PLAYING)
        collect()
        assertNull(seen.single()!!.art)
    }

    @Test
    fun pausingAndResumingIsReportedAsItHappens() {
        val c = session("music.app", PlaybackState.STATE_PLAYING)
        collect()
        fun set(state: Int) {
            val s = PlaybackState.Builder().setState(state, 2_000, 1f).build()
            shadowOf(c).setPlaybackState(s)
            shadowOf(c).executeOnPlaybackStateChanged(s)
            idleMainLooper()
        }
        set(PlaybackState.STATE_PAUSED)
        set(PlaybackState.STATE_PLAYING)
        assertEquals(listOf(true, false, true), seen.map { it!!.playing })
    }

    @Test
    fun aNewTrackIsReportedWhenTheMetadataChanges() {
        val c = session("music.app", PlaybackState.STATE_PLAYING, title = "One")
        collect()
        val next = metadata("Two", "Band", null, null)
        shadowOf(c).setMetadata(next)
        shadowOf(c).executeOnMetadataChanged(next)
        idleMainLooper()
        assertEquals(listOf("One", "Two"), seen.map { it!!.title })
    }

    @Test
    fun aCoverThatArrivesAfterTheTitleIsStillPickedUp() {
        val c = session("music.app", PlaybackState.STATE_PLAYING)
        collect()
        assertNull(seen.last()!!.art)
        val withCover = metadata("Song", "Band", null, Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888))
        shadowOf(c).setMetadata(withCover)
        shadowOf(c).executeOnMetadataChanged(withCover)
        idleMainLooper()
        assertNotNull(seen.last()!!.art)
    }

    @Test
    fun anUnchangedStateIsNotPublishedAgain() {
        val c = session("music.app", PlaybackState.STATE_PLAYING)
        collect()
        val same = metadata("Song", "Band", null, null)
        shadowOf(c).executeOnMetadataChanged(same)
        shadowOf(c).executeOnPlaybackStateChanged(c.playbackState)
        idleMainLooper()
        assertEquals(1, seen.size)
    }

    @Test
    fun nothingPlayingIsNotAnnouncedTwice() {
        val c = session("music.app", PlaybackState.STATE_STOPPED)
        collect()
        shadowOf(c).executeOnPlaybackStateChanged(c.playbackState)
        idleMainLooper()
        assertEquals(listOf<NowPlaying?>(null), seen)
    }

    @Test
    fun aSessionAppearingLaterIsPickedUpAndOneEndingEmitsNull() {
        collect()
        session("music.app", PlaybackState.STATE_PLAYING)
        sessions.clearControllers()
        assertEquals(listOf(null, "Song", null), seen.map { it?.title })
    }

    @Test
    fun stoppingCollectionUnregistersEverything() {
        val c = session("music.app", PlaybackState.STATE_PLAYING)
        collect()
        assertEquals(1, shadowOf(c).callbacks.size)
        job!!.cancel()
        assertEquals(0, shadowOf(c).callbacks.size)
    }
}
