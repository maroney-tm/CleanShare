package com.maroney.cleanshare.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

/** Which position in the swipeable video sequence a [PooledPlayer] currently represents. Roles
 * rotate between the pool's fixed slots as the user swipes — the slots (and their underlying
 * players) never get recreated, only relabeled. */
enum class PoolRole { PREVIOUS, CURRENT, NEXT }

/**
 * One slot in [VideoPlayerPool]: a persistent [ExoPlayer] that gets reassigned to whatever video
 * currently needs its [role], rather than being torn down and rebuilt — see [VideoPlayerPool]'s
 * kdoc for why. Rendered via Media3's native Compose `PlayerSurface`
 * (`com.maroney.cleanshare.ui.VideoPlayerOverlay`), not an `AndroidView`-hosted `PlayerView` —
 * see that composable's kdoc for why.
 */
class PooledPlayer internal constructor(context: Context, mediaSourceFactory: DefaultMediaSourceFactory) {
    val player: ExoPlayer = ExoPlayer.Builder(context).setMediaSourceFactory(mediaSourceFactory).build()

    /** The URL currently loaded on [player] (prepared, whether playing or paused-on-standby),
     * or null if nothing's been assigned to this slot yet. */
    var url: String? by mutableStateOf(null)
        internal set

    var role: PoolRole by mutableStateOf(PoolRole.NEXT)
        internal set
}

/**
 * Lets the video overlay move to the previous/next video in whatever list order the detail
 * screen was opened with (the history list's current sort/filter), skipping over entries
 * without a playable video. Carries the actual URLs (not just booleans) because
 * [VideoPlayerPool] needs them to keep the standby slots prepared ahead of time.
 */
data class VideoNavigation(
    val previousVideoUrl: String?,
    val nextVideoUrl: String?,
    val onNavigatePrevious: () -> Unit,
    val onNavigateNext: () -> Unit,
) {
    val hasPrevious: Boolean get() = previousVideoUrl != null
    val hasNext: Boolean get() = nextVideoUrl != null

    companion object {
        val None = VideoNavigation(
            previousVideoUrl = null,
            nextVideoUrl = null,
            onNavigatePrevious = {},
            onNavigateNext = {},
        )
    }
}

/**
 * Owns three persistent [ExoPlayer]s — previous, current, and next — so that swiping between
 * videos is a pure role swap (instant, since the target was already playing on standby with a
 * decoded frame ready) instead of tearing down and rebuilding a single player per video. That
 * per-swipe rebuild is what left a black screen visible on the new entry until its player caught
 * up: even with a warm cache, parsing the container and decoding a first frame takes real (if
 * short) time, and a single-player design pays that cost synchronously on every swipe rather
 * than ahead of time.
 *
 * The pool — and the fullscreen overlay that displays it (see
 * `com.maroney.cleanshare.ui.VideoPlayerOverlay`) — is process-wide and rendered once at the app
 * root, deliberately outside the per-entry navigation/composition lifecycle: reusing a
 * *prepared* player across a swipe only helps if whatever's rendering it also survives the
 * swipe, and a `Dialog` (a distinct Android Window per instance) can't preserve a rendered
 * Surface across being torn down and recreated.
 */
class VideoPlayerPool(context: Context, videoCacheManager: VideoCacheManager) {

    private val appContext = context.applicationContext

    private val audioManager = appContext.getSystemService(AudioManager::class.java)

    // Held for as long as the overlay is open, across however many swipes happen in one
    // session — requested once in open() and abandoned once in close().
    private var audioFocusRequest: AudioFocusRequest? = null

    private val cachedMediaSourceFactory = DefaultMediaSourceFactory(videoCacheManager.cacheDataSourceFactory())

    // Local (already-downloaded) files must never go through the cache-backed factory above —
    // it would either fail (its upstream only understands http/https) or, worse, succeed and
    // write the whole local file straight into the LRU-bounded streaming cache.
    private val localMediaSourceFactory = DefaultMediaSourceFactory(appContext)

    /** The pool's three fixed slots — stable identity for the app's entire lifetime. Only each
     * slot's [PooledPlayer.role] (and, via that, which one is "current") ever changes. */
    val slots: List<PooledPlayer> = listOf(
        PooledPlayer(appContext, cachedMediaSourceFactory).apply { role = PoolRole.PREVIOUS },
        PooledPlayer(appContext, cachedMediaSourceFactory).apply { role = PoolRole.CURRENT },
        PooledPlayer(appContext, cachedMediaSourceFactory).apply { role = PoolRole.NEXT },
    )

    private fun slot(role: PoolRole): PooledPlayer = slots.first { it.role == role }

    /** The player currently showing on screen — read fresh each time, since which slot holds
     * this role changes as the user swipes. */
    val currentPlayer: ExoPlayer get() = slot(PoolRole.CURRENT).player

    var isOpen: Boolean by mutableStateOf(false)
        private set

    var currentUrl: String? by mutableStateOf(null)
        private set

    var navigation: VideoNavigation by mutableStateOf(VideoNavigation.None)
        private set

    /** Opens the overlay for [url] — the cold-start path, e.g. a thumbnail tap from a Detail
     * screen that isn't already mid-swipe-session. Prepares the current slot if it isn't
     * already showing [url], and (re)prepares the standby slots per [navigation]. */
    fun open(url: String, navigation: VideoNavigation) {
        requestAudioFocusIfNeeded()
        val current = slot(PoolRole.CURRENT)
        if (current.url != url) {
            prepare(current, url, playWhenReady = true)
        } else {
            current.player.playWhenReady = true
        }
        currentUrl = url
        isOpen = true
        this.navigation = navigation
        syncStandbySlots(navigation)
    }

    /** Called reactively by whichever Detail screen is currently topmost, to keep the pool's
     * navigation metadata (and therefore its standby slots) in sync as the user swipes through
     * entries or a neighbor's own data changes — without forcing the overlay open or replaying
     * playback. No-ops if [url] isn't what the pool is actually showing right now (e.g. this
     * Detail screen isn't the active one, or the overlay was dismissed). */
    fun updateCurrentEntry(url: String, navigation: VideoNavigation) {
        if (currentUrl != url) return
        this.navigation = navigation
        syncStandbySlots(navigation)
    }

    /** Rotates roles so the already-playing-on-standby next video becomes current — no
     * prepare/buffer wait, since it's been ready since [syncStandbySlots] warmed it. Returns
     * false (no-op) if there's nothing prepared in the next slot yet. */
    fun advanceToNext(): Boolean {
        val next = slot(PoolRole.NEXT)
        if (next.url == null) return false
        val previous = slot(PoolRole.PREVIOUS)
        val current = slot(PoolRole.CURRENT)
        // The old previous is now two steps away — free it for reuse as the new next once real
        // navigation reports the new current's own next neighbor.
        previous.player.pause()
        previous.url = null
        previous.role = PoolRole.NEXT
        current.player.pause()
        current.role = PoolRole.PREVIOUS
        next.role = PoolRole.CURRENT
        next.player.playWhenReady = true
        currentUrl = next.url
        return true
    }

    /** Mirror of [advanceToNext] for swiping backward. */
    fun advanceToPrevious(): Boolean {
        val previous = slot(PoolRole.PREVIOUS)
        if (previous.url == null) return false
        val next = slot(PoolRole.NEXT)
        val current = slot(PoolRole.CURRENT)
        next.player.pause()
        next.url = null
        next.role = PoolRole.PREVIOUS
        current.player.pause()
        current.role = PoolRole.NEXT
        previous.role = PoolRole.CURRENT
        previous.player.playWhenReady = true
        currentUrl = previous.url
        return true
    }

    /** Pauses the current slot and releases audio focus — call when the overlay is dismissed
     * without swiping to another entry, since nothing else is about to take over playback. The
     * standby slots are left prepared as-is; there's no cost to that beyond the memory they
     * already hold, and it means reopening the same session soon is still instant. */
    fun close() {
        slot(PoolRole.CURRENT).player.pause()
        isOpen = false
        audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        audioFocusRequest = null
    }

    private fun syncStandbySlots(navigation: VideoNavigation) {
        syncSlot(slot(PoolRole.PREVIOUS), navigation.previousVideoUrl)
        syncSlot(slot(PoolRole.NEXT), navigation.nextVideoUrl)
    }

    private fun syncSlot(slot: PooledPlayer, targetUrl: String?) {
        if (targetUrl == null) {
            if (slot.url != null) {
                slot.player.pause()
                slot.url = null
            }
            return
        }
        if (slot.url == targetUrl) return // already prepared for this — leave it alone
        prepare(slot, targetUrl, playWhenReady = false)
    }

    private fun prepare(slot: PooledPlayer, url: String, playWhenReady: Boolean) {
        val isRemote = url.startsWith("http://") || url.startsWith("https://")
        val mediaItem = MediaItem.fromUri(url)
        if (isRemote) {
            slot.player.setMediaItem(mediaItem)
        } else {
            slot.player.setMediaSource(localMediaSourceFactory.createMediaSource(mediaItem))
        }
        slot.player.prepare()
        slot.player.playWhenReady = playWhenReady
        slot.url = url
    }

    // Requesting *transient* (not permanent) focus tells other media apps this is a brief
    // interruption they should pause for and resume from — rather than a full stop — once we
    // abandon the request in close(). Looks up the current slot dynamically (rather than
    // capturing a player reference) so this still pauses the right player even after several
    // swipes have rotated which slot is "current" since the request was made.
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
                    slot(PoolRole.CURRENT).player.pause()
                }
            }
            .build()
        audioManager?.requestAudioFocus(request)
        audioFocusRequest = request
    }
}
