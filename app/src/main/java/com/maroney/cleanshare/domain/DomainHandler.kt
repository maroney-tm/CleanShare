package com.maroney.cleanshare.domain

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.maroney.cleanshare.data.IngestionRecord
import com.maroney.cleanshare.ui.Radius
import com.maroney.cleanshare.ui.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

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
    fun DetailSection(urlMetadata: DomainUrlMetadata, ingestion: IngestionRecord?, videoUrl: String?)
}

/** Marker sealed class for per-platform URL metadata derived from path parsing. */
sealed class DomainUrlMetadata

class DomainHandlerRegistry(private val handlers: List<DomainHandler>) {
    fun findHandler(url: String): DomainHandler? = handlers.firstOrNull { it.matches(url) }
}

private const val SKIP_MS = 10_000L
private const val FAST_FORWARD_SPEED = 2f

/**
 * Full-screen video player used by domain handlers' thumbnail-tap-to-play. Shared across
 * handlers since playback isn't platform-specific.
 *
 * Shows a minimal playback progress line pinned to the bottom of the screen, spanning the
 * full width, whenever PlayerView's own tap-to-reveal controls (which have their own seek
 * bar) are hidden — avoids showing two overlapping progress bars at once.
 *
 * Also supports, while controls are hidden: tap-and-hold anywhere to play at 2x speed
 * (reverting to normal speed on release), and double-tapping the left/right half of the
 * screen to skip 10 seconds backward/forward. Built on Compose's own tap/long-press/
 * double-tap gesture detector so a plain tap (to reveal controls) is never confused with
 * these — unlike a hand-rolled gesture, [detectTapGestures] handles that disambiguation.
 */
@Composable
internal fun VideoPlayerDialog(videoUrl: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val player = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
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
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    var isFastForwarding by remember { mutableStateOf(false) }

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
                        useController = true
                        setControllerVisibilityListener(
                            PlayerView.ControllerVisibilityListener { visibility ->
                                controlsVisible = visibility == View.VISIBLE
                            },
                        )
                    }.also { playerView = it }
                },
                modifier = Modifier.fillMaxSize(),
            )

            // Active only while controls are hidden, so native button taps and seek-bar
            // dragging inside the visible controller UI are left untouched.
            if (!controlsVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(player) {
                            detectTapGestures(
                                onPress = {
                                    tryAwaitRelease()
                                    if (isFastForwarding) {
                                        player.setPlaybackSpeed(1f)
                                        isFastForwarding = false
                                    }
                                },
                                onLongPress = {
                                    isFastForwarding = true
                                    player.setPlaybackSpeed(FAST_FORWARD_SPEED)
                                },
                                onDoubleTap = { offset ->
                                    val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                                    if (offset.x < size.width / 2f) {
                                        player.seekTo((player.currentPosition - SKIP_MS).coerceAtLeast(0L))
                                    } else {
                                        player.seekTo((player.currentPosition + SKIP_MS).coerceAtMost(duration))
                                    }
                                },
                                onTap = { playerView?.showController() },
                            )
                        },
                )
            }

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

            if (!controlsVisible) {
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
        }
    }
}
