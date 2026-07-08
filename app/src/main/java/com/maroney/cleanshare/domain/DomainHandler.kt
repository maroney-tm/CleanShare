package com.maroney.cleanshare.domain

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.RepeatOneOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.maroney.cleanshare.CleanShareApplication
import com.maroney.cleanshare.data.IngestionRecord
import com.maroney.cleanshare.ui.IconSize
import com.maroney.cleanshare.ui.Radius
import com.maroney.cleanshare.ui.Spacing
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A platform-specific handler that can match a URL, extract structured metadata
 * from its path, and render a rich detail section in the UI.
 *
 * Pure URL parsing only — no network calls.
 */
interface DomainHandler {
    fun matches(url: String): Boolean
    fun extractUrlMetadata(url: String): DomainUrlMetadata

    @Composable
    fun DetailSection(
        urlMetadata: DomainUrlMetadata,
        ingestion: IngestionRecord?,
        videoUrl: String?,
        thumbnailUrl: String?,
        videoNavigation: VideoNavigation,
    )
}

/** Marker sealed class for per-platform URL metadata derived from path parsing. */
sealed class DomainUrlMetadata

/**
 * Lets [VideoPlayerDialog] move to the previous/next video in whatever list order the detail
 * screen was opened with (the history list's current sort/filter), skipping over entries
 * without a playable video.
 *
 * @param autoPlay Whether the fullscreen player should open immediately rather than waiting for
 * a thumbnail tap — set when this entry was reached by swiping from another video, so playback
 * continues across the swipe instead of dropping back to the plain detail view.
 */
data class VideoNavigation(
    val hasPrevious: Boolean,
    val hasNext: Boolean,
    val onNavigatePrevious: () -> Unit,
    val onNavigateNext: () -> Unit,
    val autoPlay: Boolean,
) {
    companion object {
        val None = VideoNavigation(
            hasPrevious = false,
            hasNext = false,
            onNavigatePrevious = {},
            onNavigateNext = {},
            autoPlay = false,
        )
    }
}

class DomainHandlerRegistry(private val handlers: List<DomainHandler>) {
    fun findHandler(url: String): DomainHandler? = handlers.firstOrNull { it.matches(url) }
}

private const val DOUBLE_TAP_SKIP_MS = 10_000L
private const val BUTTON_REWIND_MS = 5_000L
private const val BUTTON_SKIP_MS = 15_000L
private const val FAST_FORWARD_SPEED = 2f
private const val CONTROLS_AUTO_HIDE_MS = 3_000L

// The breakpoint a swipe has to cross — clearly more deliberate than a scrub or an accidental
// brush — before the player starts visually following the drag at all.
private const val SWIPE_NAVIGATE_THRESHOLD_FRACTION = 0.10f

// Crossing the breakpoint above only arms the drag to *follow* the finger — actually moving to
// the previous/next video additionally requires releasing past a third of the screen's width,
// so a drag that's let go partway through springs back instead of committing.
private const val SWIPE_COMMIT_THRESHOLD_FRACTION = 0.33f

// Well past the platform's default long-press timeout (~500ms) so an ordinary tap-and-hold —
// e.g. steadying a finger on the screen — doesn't accidentally kick off 2x playback.
private const val FAST_FORWARD_HOLD_TIMEOUT_MS = 750L

private sealed interface HoldGestureResult {
    /** Held in place for [FAST_FORWARD_HOLD_TIMEOUT_MS] without lifting or moving. */
    data object Stationary : HoldGestureResult

    /** Lifted before the hold timeout elapsed — a tap (or double-tap) candidate. */
    data object Released : HoldGestureResult

    /** Moved past touch slop before the hold timeout elapsed — a swipe, not a hold. */
    data object Swiped : HoldGestureResult
}

/**
 * Distinguishes a stationary tap-and-hold (which should trigger 2x playback) from a swipe that
 * merely starts with a finger going down in the same place: waits up to [timeoutMillis] for
 * [pointerId] to either lift or move beyond touch slop, reporting whichever happens first, or
 * [HoldGestureResult.Stationary] if neither happens before the timeout elapses.
 */
private suspend fun AwaitPointerEventScope.awaitHoldGesture(
    pointerId: PointerId,
    downPosition: Offset,
    timeoutMillis: Long,
): HoldGestureResult {
    val touchSlop = viewConfiguration.touchSlop
    val outcome = withTimeoutOrNull<HoldGestureResult>(timeoutMillis) {
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == pointerId } ?: continue
            if (!change.pressed) return@withTimeoutOrNull HoldGestureResult.Released
            if ((change.position - downPosition).getDistance() > touchSlop) {
                return@withTimeoutOrNull HoldGestureResult.Swiped
            }
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable: while(true) only exits via the returns above")
    }
    return outcome ?: HoldGestureResult.Stationary
}

/**
 * Waits up to [timeoutMillis] for a second pointer to go down and back up, promoting a tap into
 * a double-tap. Returns the second tap's position, or null if none arrived in time.
 */
private suspend fun AwaitPointerEventScope.awaitSecondTap(timeoutMillis: Long): Offset? =
    withTimeoutOrNull(timeoutMillis) {
        val secondDown = awaitFirstDown()
        waitForUpOrCancellation()
        secondDown.position
    }

/**
 * Full-screen video player used by domain handlers' thumbnail-tap-to-play. Shared across
 * handlers since playback isn't platform-specific.
 *
 * Uses a fully custom control surface (PlayerView's own controller is disabled) so we can
 * offer exactly: rewind 5s, play/pause, skip 15s, and a loop toggle — plus a minimal
 * always-visible progress line pinned to the bottom of the screen.
 *
 * Also supports: tap-and-hold anywhere to play at 2x speed (reverting to normal speed on
 * release), and double-tapping the left/right half of the screen to skip 10 seconds
 * backward/forward. Built on Compose's own tap/long-press/double-tap gesture detector so a
 * plain tap (to reveal/hide the control row) is never confused with these.
 */
@Composable
internal fun VideoPlayerDialog(videoUrl: String, onDismiss: () -> Unit, videoNavigation: VideoNavigation) {
    val context = LocalContext.current
    val player = remember(videoUrl) {
        // Saved-offline videos are already fully downloaded local files (absolute paths, no
        // "http(s)" prefix) — play those directly rather than routing them through the
        // streaming cache, which would needlessly duplicate them into the LRU-bounded cache.
        val isRemote = videoUrl.startsWith("http://") || videoUrl.startsWith("https://")
        val builder = ExoPlayer.Builder(context)
        if (isRemote) {
            val app = context.applicationContext as CleanShareApplication
            builder.setMediaSourceFactory(DefaultMediaSourceFactory(app.videoCacheManager.cacheDataSourceFactory()))
        }
        builder.build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    // Playing a video is an active-watching session for as long as this dialog is up —
    // don't let the screen dim out from under it.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Requesting *transient* (not permanent) focus tells other media apps this is a brief
    // interruption they should pause for and resume from — rather than a full stop — once we
    // abandon the request as the dialog closes.
    DisposableEffect(Unit) {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
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
        audioManager?.requestAudioFocus(focusRequest)
        onDispose { audioManager?.abandonAudioFocusRequest(focusRequest) }
    }

    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    var loopEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(player) {
        val app = context.applicationContext as CleanShareApplication
        val defaultLoop = app.playbackPreferencesRepository.loopVideosByDefault.first()
        loopEnabled = defaultLoop
        player.repeatMode = if (defaultLoop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(player) {
        while (isActive) {
            val duration = player.duration
            progress = if (duration > 0) {
                (player.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
            } else {
                0f
            }
            delay(200)
        }
    }

    var controlsVisible by remember { mutableStateOf(false) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(controlsVisible, isScrubbing) {
        // Don't auto-hide out from under an in-progress drag on the seek bar.
        if (controlsVisible && !isScrubbing) {
            delay(CONTROLS_AUTO_HIDE_MS)
            controlsVisible = false
        }
    }

    var isFastForwarding by remember { mutableStateOf(false) }

    // Horizontal offset for the previous/next-video swipe below — pinned at 0 (no visual
    // effect) until the drag crosses SWIPE_NAVIGATE_THRESHOLD_FRACTION, then a spring catches
    // it up to the finger and it tracks 1:1 from there. See the HoldGestureResult.Swiped branch.
    // Animatable calls are launched on this scope rather than made directly from the gesture
    // handler below because pointerInput's AwaitPointerEventScope is a restricted coroutine
    // scope that can't suspend on arbitrary calls like Animatable.animateTo.
    val playerOffsetX = remember { Animatable(0f) }
    val swipeAnimationScope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(playerOffsetX.value.roundToInt(), 0) },
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(player) {
                        val doubleTapTimeoutMillis = viewConfiguration.doubleTapTimeoutMillis
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            when (awaitHoldGesture(down.id, down.position, FAST_FORWARD_HOLD_TIMEOUT_MS)) {
                                HoldGestureResult.Stationary -> {
                                    isFastForwarding = true
                                    player.setPlaybackSpeed(FAST_FORWARD_SPEED)
                                    waitForUpOrCancellation()
                                    player.setPlaybackSpeed(1f)
                                    isFastForwarding = false
                                }
                                HoldGestureResult.Swiped -> {
                                    // Finger moved before the hold timeout — a swipe, not a tap
                                    // or a hold. Don't toggle controls, seek, or fast-forward;
                                    // but a sufficiently horizontal one moves to the previous/
                                    // next video, animating the player with it rather than
                                    // just snapping once the finger lifts.
                                    val followThreshold = size.width * SWIPE_NAVIGATE_THRESHOLD_FRACTION
                                    val commitThreshold = size.width * SWIPE_COMMIT_THRESHOLD_FRACTION
                                    var armed = false
                                    var dx = 0f
                                    var dy = 0f
                                    while (true) {
                                        val change = awaitPointerEvent().changes
                                            .firstOrNull { it.id == down.id } ?: continue
                                        dx = change.position.x - down.position.x
                                        dy = change.position.y - down.position.y
                                        // At either end of the list, there's nothing to swipe
                                        // to in that direction — don't let the player follow
                                        // the drag at all rather than following it only to
                                        // spring back once released.
                                        val canNavigate = if (dx < 0) videoNavigation.hasNext else videoNavigation.hasPrevious
                                        if (!armed && canNavigate && abs(dx) > abs(dy) && abs(dx) >= followThreshold) {
                                            // Crossing the breakpoint arms the swipe — catch the
                                            // player up to the finger with a spring instead of
                                            // popping it straight there, since it hasn't moved
                                            // at all up to this point.
                                            armed = true
                                            val armedAtOffset = dx
                                            swipeAnimationScope.launch {
                                                playerOffsetX.animateTo(armedAtOffset, spring())
                                            }
                                        } else if (armed) {
                                            val trackedOffset = dx
                                            swipeAnimationScope.launch { playerOffsetX.snapTo(trackedOffset) }
                                        }
                                        if (!change.pressed) break
                                    }
                                    // Following the drag (above) is a much lower bar than
                                    // actually committing to the next/previous video: released
                                    // short of the halfway point, or with nothing left in that
                                    // direction, the player springs back to center instead of
                                    // navigating, even if it was armed.
                                    val canCommit = if (dx < 0) videoNavigation.hasNext else videoNavigation.hasPrevious
                                    if (armed && canCommit && abs(dx) >= commitThreshold) {
                                        val exitTarget = if (dx < 0) -size.width.toFloat() else size.width.toFloat()
                                        val goToNext = dx < 0
                                        swipeAnimationScope.launch {
                                            playerOffsetX.animateTo(exitTarget, spring())
                                            if (goToNext) videoNavigation.onNavigateNext() else videoNavigation.onNavigatePrevious()
                                        }
                                    } else {
                                        swipeAnimationScope.launch { playerOffsetX.animateTo(0f, spring()) }
                                    }
                                }
                                HoldGestureResult.Released -> {
                                    val secondTapOffset = awaitSecondTap(doubleTapTimeoutMillis)
                                    if (secondTapOffset != null) {
                                        val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                                        if (secondTapOffset.x < size.width / 2f) {
                                            player.seekTo(
                                                (player.currentPosition - DOUBLE_TAP_SKIP_MS).coerceAtLeast(0L),
                                            )
                                        } else {
                                            player.seekTo(
                                                (player.currentPosition + DOUBLE_TAP_SKIP_MS).coerceAtMost(duration),
                                            )
                                        }
                                    } else {
                                        controlsVisible = !controlsVisible
                                    }
                                }
                            }
                        }
                    },
            )

            if (isFastForwarding) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = Spacing.lg)
                        .clip(RoundedCornerShape(Radius.md))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                ) {
                    Text(
                        text = "${FAST_FORWARD_SPEED.toInt()}x",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            if (controlsVisible) {
                // A real Slider (not a hand-rolled drag detector) so tap-to-seek and
                // drag-to-scrub are both correct out of the box.
                Slider(
                    value = if (isScrubbing) scrubProgress else progress,
                    onValueChange = {
                        isScrubbing = true
                        scrubProgress = it
                    },
                    onValueChangeFinished = {
                        val duration = player.duration.takeIf { it > 0 } ?: 0L
                        player.seekTo((scrubProgress * duration).toLong())
                        isScrubbing = false
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md),
                )
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(Spacing.xs)
                        .background(Color.White.copy(alpha = 0.25f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(Color.White),
                    )
                }
            }

            if (controlsVisible) {
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            player.seekTo((player.currentPosition - BUTTON_REWIND_MS).coerceAtLeast(0L))
                        },
                    ) {
                        Icon(
                            Icons.Filled.Replay5,
                            contentDescription = "Rewind 5 seconds",
                            tint = Color.White,
                            modifier = Modifier.size(IconSize.favicon),
                        )
                    }
                    IconButton(onClick = { if (isPlaying) player.pause() else player.play() }) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(IconSize.thumbnail),
                        )
                    }
                    IconButton(
                        onClick = {
                            val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                            player.seekTo((player.currentPosition + BUTTON_SKIP_MS).coerceAtMost(duration))
                        },
                    ) {
                        Icon(
                            Icons.Filled.FastForward,
                            contentDescription = "Skip 15 seconds",
                            tint = Color.White,
                            modifier = Modifier.size(IconSize.favicon),
                        )
                    }
                    IconButton(
                        onClick = {
                            loopEnabled = !loopEnabled
                            player.repeatMode = if (loopEnabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                        },
                    ) {
                        Icon(
                            if (loopEnabled) Icons.Filled.RepeatOneOn else Icons.Filled.RepeatOne,
                            contentDescription = "Toggle loop",
                            tint = Color.White,
                            modifier = Modifier.size(IconSize.favicon),
                        )
                    }
                }
            }
        }
    }
}
