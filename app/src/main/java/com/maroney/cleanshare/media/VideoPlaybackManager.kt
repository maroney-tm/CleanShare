package com.maroney.cleanshare.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import androidx.media3.exoplayer.source.preload.TargetPreloadStatusControl

// Preload enough of a neighboring video to eliminate the cold-start gap without fully
// downloading videos the user may never actually swipe to.
private const val PRELOAD_DURATION_MS = 3_000L

/**
 * Owns a single, process-wide [ExoPlayer] instance reused across every video the user swipes
 * to/from in [com.maroney.cleanshare.domain.VideoPlayerDialog], rather than that dialog
 * constructing (and tearing down) a fresh player per entry. Player construction has real
 * overhead of its own — decoder pool, audio track, internal playback thread setup — on top of
 * whatever it takes to fetch the media itself, and that overhead was being paid on every swipe.
 *
 * Paired with a [DefaultPreloadManager] that prepares the neighboring videos' [MediaSource]s
 * ahead of time (see [preload]), sharing the same cache-backed data source as the player itself
 * (via [VideoCacheManager]) — so by the time the user actually lands on one, [play] hands the
 * already-buffered source straight to the shared player instead of starting cold.
 */
class VideoPlaybackManager(context: Context, videoCacheManager: VideoCacheManager) {

    private val appContext = context.applicationContext

    private val audioManager = appContext.getSystemService(AudioManager::class.java)

    // Held for as long as *any* video dialog is showing, across however many swipes happen in
    // one session — requested once in play() and abandoned once in stop(), rather than
    // re-requested/abandoned on every dialog mount/dispose. That per-dialog churn was the
    // actual cause of swiped-to videos silently not playing: the outgoing dialog's still-active
    // focus request could receive a spurious AUDIOFOCUS_LOSS_TRANSIENT the instant the
    // incoming dialog requested its own (both requests belong to this same app/player), and its
    // listener would pause the shared player moments after the new video had just started.
    private var audioFocusRequest: AudioFocusRequest? = null

    // Local (already-downloaded) files must never go through the cache-backed factory below —
    // it would either fail (its upstream only understands http/https) or, worse, succeed and
    // write the whole local file straight into the LRU-bounded streaming cache. This is a
    // deliberately separate, uncached factory used only for those.
    private val localMediaSourceFactory = DefaultMediaSourceFactory(appContext)

    private val targetPreloadStatusControl =
        TargetPreloadStatusControl<Int, DefaultPreloadManager.PreloadStatus> {
            DefaultPreloadManager.PreloadStatus.specifiedRangeLoaded(PRELOAD_DURATION_MS)
        }

    private val preloadManagerBuilder =
        DefaultPreloadManager.Builder(appContext, targetPreloadStatusControl)
            .setDataSourceFactory(videoCacheManager.cacheDataSourceFactory())

    private val preloadManager = preloadManagerBuilder.build()

    /** The single shared player — built from the same [preloadManagerBuilder] as the preload
     * manager itself, so it uses the identical renderer/track-selector/data-source
     * configuration as preloading does, which is what lets a preloaded [MediaSource] be handed
     * straight to it with no re-setup. */
    val player: ExoPlayer = preloadManagerBuilder.buildExoPlayer()

    /** Registers [url]'s video for background preloading, prioritized by [rankingIndex] — the
     * entry's position in the same ordered list swipe navigation uses, so "closer" neighbors
     * (by that ordering) are preloaded first. Safe to call repeatedly; re-registering just
     * updates the ranking. */
    fun preload(url: String, rankingIndex: Int) {
        preloadManager.add(MediaItem.fromUri(url), rankingIndex)
        preloadManager.invalidate()
    }

    /** Stops preloading [url] — call when it's no longer a swipe neighbor (the user moved on,
     * or this ViewModel is being cleared) so the preload manager doesn't keep working on media
     * nobody's about to watch. */
    fun clearPreload(url: String) {
        preloadManager.remove(MediaItem.fromUri(url))
    }

    /** Tells the preload manager which ranking index is "current," so it can prioritize
     * preloading by distance from it. */
    fun setCurrentIndex(index: Int) {
        preloadManager.setCurrentPlayingIndex(index)
    }

    /** Starts playing [url] on the shared [player]. Deliberately always builds a fresh
     * [MediaSource] via [MediaItem] rather than handing the player whatever [preload] may have
     * already produced — [BasePreloadManager.remove] releases the [MediaSource] it's tracking
     * as part of detaching it, so handing that same (now-released) instance to the player right
     * after would leave it holding a torn-down source. The player still starts up fast for a
     * preloaded neighbor because it reads through the same cache-backed data source [preload]
     * already warmed; it just builds its own (live) source rather than reusing the manager's. */
    fun play(url: String, isRemote: Boolean) {
        requestAudioFocusIfNeeded()
        val mediaItem = MediaItem.fromUri(url)
        if (isRemote) {
            // No longer anything to preload once it's actually playing.
            preloadManager.remove(mediaItem)
            player.setMediaItem(mediaItem)
        } else {
            player.setMediaSource(localMediaSourceFactory.createMediaSource(mediaItem))
        }
        player.prepare()
        player.playWhenReady = true
    }

    /** Pauses the shared player and releases audio focus — call when the video dialog is
     * dismissed without swiping to another entry, since nothing else is about to take over
     * playback. */
    fun stop() {
        player.pause()
        audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        audioFocusRequest = null
    }

    // Requesting *transient* (not permanent) focus tells other media apps this is a brief
    // interruption they should pause for and resume from — rather than a full stop — once we
    // abandon the request in stop().
    private fun requestAudioFocusIfNeeded() {
        if (audioFocusRequest != null) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build(),
            )
            .setOnAudioFocusChangeListener { focusChange ->
                if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                    focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                ) {
                    player.pause()
                }
            }
            .build()
        audioManager?.requestAudioFocus(request)
        audioFocusRequest = request
    }
}
