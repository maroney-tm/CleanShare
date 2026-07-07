package com.maroney.cleanshare.domain

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.maroney.cleanshare.CleanShareApplication
import com.maroney.cleanshare.data.IngestionRecord
import com.maroney.cleanshare.ui.IconSize
import com.maroney.cleanshare.ui.Radius
import com.maroney.cleanshare.ui.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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

private const val DOUBLE_TAP_SKIP_MS = 10_000L
private const val BUTTON_REWIND_MS = 5_000L
private const val BUTTON_SKIP_MS = 15_000L
private const val FAST_FORWARD_SPEED = 2f
private const val CONTROLS_AUTO_HIDE_MS = 3_000L

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
internal fun VideoPlayerDialog(videoUrl: String, onDismiss: () -> Unit) {
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
                modifier = Modifier.fillMaxSize(),
            )

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
                                    player.seekTo((player.currentPosition - DOUBLE_TAP_SKIP_MS).coerceAtLeast(0L))
                                } else {
                                    player.seekTo((player.currentPosition + DOUBLE_TAP_SKIP_MS).coerceAtMost(duration))
                                }
                            },
                            onTap = { controlsVisible = !controlsVisible },
                        )
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
