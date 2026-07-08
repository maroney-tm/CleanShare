package com.maroney.cleanshare.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import com.maroney.cleanshare.CleanShareApplication
import com.maroney.cleanshare.media.PoolRole
import com.maroney.cleanshare.media.VideoPlayerPool
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val DOUBLE_TAP_SKIP_MS = 10_000L

// Double-tap zone split across the screen's width: the left/right 30% skip backward/forward,
// the center 40% toggles play/pause without surfacing the control overlay.
private const val DOUBLE_TAP_LEFT_ZONE_FRACTION = 0.30f
private const val DOUBLE_TAP_RIGHT_ZONE_FRACTION = 0.70f
private const val BUTTON_REWIND_MS = 5_000L
private const val BUTTON_SKIP_MS = 15_000L
private const val FAST_FORWARD_SPEED = 2f
private const val CONTROLS_AUTO_HIDE_MS = 3_000L

// The breakpoint a swipe has to cross — clearly more deliberate than a scrub or an accidental
// brush — before the row starts visually following the drag at all.
private const val SWIPE_NAVIGATE_THRESHOLD_FRACTION = 0.10f

// Crossing the breakpoint above only arms the drag to *follow* the finger — actually moving to
// the previous/next video additionally requires releasing past a third of the screen's width,
// so a drag that's let go partway through springs back instead of committing.
private const val SWIPE_COMMIT_THRESHOLD_FRACTION = 0.20f

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

    /** A second pointer joined and one of the two lifted again, both within touch slop. */
    data object TwoFingerTap : HoldGestureResult
}

/**
 * Distinguishes a stationary tap-and-hold (which should trigger 2x playback) from a swipe that
 * merely starts with a finger going down in the same place, and from a second finger joining
 * for a two-finger tap: waits up to [timeoutMillis] for [pointerId] to either lift or move
 * beyond touch slop, reporting whichever happens first, or [HoldGestureResult.Stationary] if
 * neither happens before the timeout elapses.
 *
 * While waiting, a second pointer touching down is tracked alongside the first. If that second
 * pointer moves beyond touch slop it's dropped (not a tap partner) and the gesture continues to
 * resolve on the first pointer alone. If instead either pointer lifts while both are still
 * within slop of where they went down, that's a two-finger tap — reported regardless of which
 * finger happened to lift first.
 */
private suspend fun AwaitPointerEventScope.awaitHoldGesture(
    pointerId: PointerId,
    downPosition: Offset,
    timeoutMillis: Long,
): HoldGestureResult {
    val touchSlop = viewConfiguration.touchSlop
    var secondPointerId: PointerId? = null
    var secondDownPosition = Offset.Zero
    val outcome = withTimeoutOrNull<HoldGestureResult>(timeoutMillis) {
        while (true) {
            val event = awaitPointerEvent()

            val trackedSecondId = secondPointerId
            if (trackedSecondId != null) {
                val second = event.changes.firstOrNull { it.id == trackedSecondId }
                if (second != null) {
                    if ((second.position - secondDownPosition).getDistance() > touchSlop) {
                        secondPointerId = null
                    } else if (!second.pressed) {
                        return@withTimeoutOrNull HoldGestureResult.TwoFingerTap
                    }
                }
            } else {
                val newSecond = event.changes.firstOrNull { it.id != pointerId && it.pressed && !it.previousPressed }
                if (newSecond != null) {
                    secondPointerId = newSecond.id
                    secondDownPosition = newSecond.position
                }
            }

            val change = event.changes.firstOrNull { it.id == pointerId } ?: continue
            if (!change.pressed) {
                return@withTimeoutOrNull if (secondPointerId != null) {
                    HoldGestureResult.TwoFingerTap
                } else {
                    HoldGestureResult.Released
                }
            }
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

private fun basePositionPx(role: PoolRole, widthPx: Int): Float = when (role) {
    PoolRole.PREVIOUS -> -widthPx.toFloat()
    PoolRole.CURRENT -> 0f
    PoolRole.NEXT -> widthPx.toFloat()
}

/**
 * Full-screen video player, rendered once at the app root (see `MainActivity`) rather than as a
 * `Dialog` owned by whichever entry's detail screen is on top — see [VideoPlayerPool]'s kdoc for
 * why: reusing a *prepared* standby player across a swipe only helps if whatever's rendering it
 * also survives the swipe, which a per-entry `Dialog` (a distinct Android Window each time)
 * can't do. This composable is always present in composition; [VideoPlayerPool.isOpen] controls
 * whether it's actually visible/sized, not whether it exists.
 *
 * Renders each of the pool's three [VideoPlayerPool.slots] with Media3's native Compose
 * `PlayerSurface`, in `SURFACE_TYPE_TEXTURE_VIEW` mode — not an `AndroidView`-hosted classic
 * `PlayerView`, whose `SurfaceView`-backed Surface rendered independently of Compose's own
 * layout/draw passes and never actually painted a frame (audio played; the screen stayed black).
 * `TextureView` composites as an ordinary part of the view hierarchy instead of punching its own
 * window-level hole, which is also what makes having three of them alive at once — two fully
 * off-screen — visually safe.
 *
 * Lays out the three slots side by side — previous, current, next, based on each slot's current
 * [PoolRole] — inside a row that follows horizontal drags, so swiping doesn't just animate the
 * outgoing video away but visibly slides the (already-prepared, already-rendering) incoming one
 * into view.
 *
 * Otherwise unchanged from before: tap-and-hold anywhere for 2x playback, double-tapping the
 * left/right 30% to skip 10 seconds backward/forward, double-tapping the center 40% (or
 * two-finger tapping anywhere) to toggle play/pause without opening the control overlay, and a
 * custom control surface (rewind 5s, play/pause, skip 15s, loop toggle, scrub slider).
 */
@Composable
fun VideoPlayerOverlay(pool: VideoPlayerPool) {
    val context = LocalContext.current
    val app = context.applicationContext as CleanShareApplication

    BackHandler(enabled = pool.isOpen) { pool.close() }

    val view = LocalView.current
    LaunchedEffect(pool.isOpen) {
        // Playing a video is an active-watching session for as long as the overlay is up —
        // don't let the screen dim out from under it.
        view.keepScreenOn = pool.isOpen
    }

    val currentPlayer = pool.currentPlayer

    var isPlaying by remember { mutableStateOf(currentPlayer.isPlaying) }
    DisposableEffect(currentPlayer) {
        isPlaying = currentPlayer.isPlaying
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        currentPlayer.addListener(listener)
        onDispose { currentPlayer.removeListener(listener) }
    }

    var loopEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(currentPlayer) {
        val defaultLoop = app.playbackPreferencesRepository.loopVideosByDefault.first()
        loopEnabled = defaultLoop
        currentPlayer.repeatMode = if (defaultLoop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(currentPlayer) {
        while (isActive) {
            val duration = currentPlayer.duration
            progress = if (duration > 0) {
                (currentPlayer.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
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

    var screenWidthPx by remember { mutableIntStateOf(0) }

    // Horizontal offset for the whole previous/current/next row below — pinned at 0 (no visual
    // effect) until a drag crosses SWIPE_NAVIGATE_THRESHOLD_FRACTION, then a spring catches it
    // up to the finger and it tracks 1:1 from there. See the HoldGestureResult.Swiped branch.
    // Animatable calls are launched on this scope rather than made directly from the gesture
    // handler below because pointerInput's AwaitPointerEventScope is a restricted coroutine
    // scope that can't suspend on arbitrary calls like Animatable.animateTo.
    val dragOffsetX = remember { Animatable(0f) }
    val swipeAnimationScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .onSizeChanged { screenWidthPx = it.width }
            .then(if (pool.isOpen) Modifier.fillMaxSize() else Modifier.size(0.dp))
            .background(if (pool.isOpen) Color.Black else Color.Transparent),
    ) {
        // The pool's three players never get created or torn down here — only repositioned,
        // based on each slot's current role. That's what keeps a neighbor's already-decoded
        // frame intact across a swipe instead of losing it to a fresh Surface.
        pool.slots.forEach { slot ->
            // PlayerSurface (unlike the old PlayerView) doesn't letterbox on its own — it just
            // renders at whatever size its Modifier gives it, so a bare fillMaxSize() stretched
            // every video to the screen's aspect ratio. resizeWithContentScale + the video's
            // actual reported size (from PresentationState, updated live as playback starts)
            // is Media3's own replacement for PlayerView's resizeMode: it already fills the
            // available space and centers the result, so it takes the place of fillMaxSize()
            // here rather than adding to it.
            val presentationState = rememberPresentationState(slot.player)
            PlayerSurface(
                player = slot.player,
                surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                modifier = Modifier
                    .resizeWithContentScale(ContentScale.Fit, presentationState.videoSizeDp)
                    .offset {
                        IntOffset((basePositionPx(slot.role, screenWidthPx) + dragOffsetX.value).roundToInt(), 0)
                    },
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(pool) {
                    val doubleTapTimeoutMillis = viewConfiguration.doubleTapTimeoutMillis
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        when (awaitHoldGesture(down.id, down.position, FAST_FORWARD_HOLD_TIMEOUT_MS)) {
                            HoldGestureResult.Stationary -> {
                                val player = pool.currentPlayer
                                isFastForwarding = true
                                player.setPlaybackSpeed(FAST_FORWARD_SPEED)
                                waitForUpOrCancellation()
                                player.setPlaybackSpeed(1f)
                                isFastForwarding = false
                            }
                            HoldGestureResult.Swiped -> {
                                // Finger moved before the hold timeout — a swipe, not a tap or
                                // a hold. Don't toggle controls, seek, or fast-forward; but a
                                // sufficiently horizontal one moves to the previous/next video,
                                // sliding the (already-prepared) neighbor into view with it
                                // rather than just snapping once the finger lifts.
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
                                    // At either end of the list, there's nothing to swipe to in
                                    // that direction — don't let the row follow the drag at all
                                    // rather than following it only to spring back once released.
                                    val canNavigate = if (dx < 0) pool.navigation.hasNext else pool.navigation.hasPrevious
                                    if (!armed && canNavigate && abs(dx) > abs(dy) && abs(dx) >= followThreshold) {
                                        // Crossing the breakpoint arms the swipe — catch the row
                                        // up to the finger with a spring instead of popping it
                                        // straight there, since it hasn't moved at all yet.
                                        armed = true
                                        val armedAtOffset = dx
                                        swipeAnimationScope.launch {
                                            dragOffsetX.animateTo(armedAtOffset, spring())
                                        }
                                    } else if (armed) {
                                        val trackedOffset = dx
                                        swipeAnimationScope.launch { dragOffsetX.snapTo(trackedOffset) }
                                    }
                                    if (!change.pressed) break
                                }
                                // Following the drag (above) is a much lower bar than actually
                                // committing to the next/previous video: released short of the
                                // halfway point, or with nothing left in that direction, the row
                                // springs back to center instead of navigating, even if armed.
                                val canCommit = if (dx < 0) pool.navigation.hasNext else pool.navigation.hasPrevious
                                if (armed && canCommit && abs(dx) >= commitThreshold) {
                                    val exitTarget = if (dx < 0) -size.width.toFloat() else size.width.toFloat()
                                    val goToNext = dx < 0
                                    swipeAnimationScope.launch {
                                        dragOffsetX.animateTo(exitTarget, spring())
                                        // Rotating roles is instant — the target was already
                                        // playing on standby — so this and the reset below
                                        // happen in the same frame, with no visible jump.
                                        val advanced = if (goToNext) pool.advanceToNext() else pool.advanceToPrevious()
                                        dragOffsetX.snapTo(0f)
                                        if (advanced) {
                                            // Real navigation catches the back stack up to
                                            // match, so dismissing later lands on the right
                                            // entry — doesn't gate anything visual on this.
                                            if (goToNext) pool.navigation.onNavigateNext() else pool.navigation.onNavigatePrevious()
                                        }
                                    }
                                } else {
                                    swipeAnimationScope.launch { dragOffsetX.animateTo(0f, spring()) }
                                }
                            }
                            HoldGestureResult.Released -> {
                                val secondTapOffset = awaitSecondTap(doubleTapTimeoutMillis)
                                val player = pool.currentPlayer
                                if (secondTapOffset != null) {
                                    val leftZoneEnd = size.width * DOUBLE_TAP_LEFT_ZONE_FRACTION
                                    val rightZoneStart = size.width * DOUBLE_TAP_RIGHT_ZONE_FRACTION
                                    when {
                                        secondTapOffset.x < leftZoneEnd -> {
                                            player.seekTo(
                                                (player.currentPosition - DOUBLE_TAP_SKIP_MS).coerceAtLeast(0L),
                                            )
                                        }
                                        secondTapOffset.x >= rightZoneStart -> {
                                            val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                                            player.seekTo(
                                                (player.currentPosition + DOUBLE_TAP_SKIP_MS).coerceAtMost(duration),
                                            )
                                        }
                                        // Center 40%: toggle play/pause — deliberately skips
                                        // the controlsVisible toggle below so a center
                                        // double-tap doesn't also flash the control overlay
                                        // open.
                                        else -> if (player.isPlaying) player.pause() else player.play()
                                    }
                                } else {
                                    controlsVisible = !controlsVisible
                                }
                            }
                            HoldGestureResult.TwoFingerTap -> {
                                // Two fingers tapping together toggles play/pause from anywhere
                                // on screen — mirrors the center-zone double-tap but doesn't
                                // require aiming for the middle 40%, and likewise leaves
                                // controlsVisible untouched.
                                val player = pool.currentPlayer
                                if (player.isPlaying) player.pause() else player.play()
                            }
                        }
                    }
                },
        )

        if (pool.isOpen && isFastForwarding) {
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

        if (pool.isOpen && controlsVisible) {
            // A real Slider (not a hand-rolled drag detector) so tap-to-seek and drag-to-scrub
            // are both correct out of the box.
            Slider(
                value = if (isScrubbing) scrubProgress else progress,
                onValueChange = {
                    isScrubbing = true
                    scrubProgress = it
                },
                onValueChangeFinished = {
                    val duration = currentPlayer.duration.takeIf { it > 0 } ?: 0L
                    currentPlayer.seekTo((scrubProgress * duration).toLong())
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
        } else if (pool.isOpen) {
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

        if (pool.isOpen && controlsVisible) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        currentPlayer.seekTo((currentPlayer.currentPosition - BUTTON_REWIND_MS).coerceAtLeast(0L))
                    },
                ) {
                    Icon(
                        Icons.Filled.Replay5,
                        contentDescription = "Rewind 5 seconds",
                        tint = Color.White,
                        modifier = Modifier.size(IconSize.favicon),
                    )
                }
                IconButton(onClick = { if (isPlaying) currentPlayer.pause() else currentPlayer.play() }) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(IconSize.thumbnail),
                    )
                }
                IconButton(
                    onClick = {
                        val duration = currentPlayer.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                        currentPlayer.seekTo((currentPlayer.currentPosition + BUTTON_SKIP_MS).coerceAtMost(duration))
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
                        currentPlayer.repeatMode = if (loopEnabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
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
