package com.maroney.cleanshare.domain

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import com.maroney.cleanshare.data.IngestionRecord
import com.maroney.cleanshare.data.IngestionStatus
import com.maroney.cleanshare.data.isFailure
import com.maroney.cleanshare.ui.IconSize
import com.maroney.cleanshare.ui.Radius
import com.maroney.cleanshare.ui.Spacing
import com.maroney.cleanshare.ui.theme.LocalColors

enum class YoutubeContentType(val label: String) {
    VIDEO("Video"), SHORT("Short"), PLAYLIST("Playlist"), CHANNEL("Channel"), UNKNOWN("Video")
}

data class YoutubeUrlMetadata(
    val contentType: YoutubeContentType,
    val videoId: String?,
    val channelHandle: String?,
    val playlistId: String?,
) : DomainUrlMetadata()

// YouTube red — intentionally hardcoded, not from the app color system.
private val youtubeRed = Color(0xFFFF0000)

private val youtubeHosts = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be")

class YoutubeDomainHandler : DomainHandler {

    override fun matches(url: String): Boolean {
        val host = extractHost(url) ?: return false
        return host in youtubeHosts
    }

    override fun extractUrlMetadata(url: String): YoutubeUrlMetadata {
        val host = extractHost(url) ?: return YoutubeUrlMetadata(YoutubeContentType.UNKNOWN, null, null, null)
        val segments = extractPathSegments(url)

        if (host == "youtu.be") {
            return YoutubeUrlMetadata(YoutubeContentType.VIDEO, segments.firstOrNull(), null, null)
        }

        return when (segments.firstOrNull()) {
            "watch"    -> YoutubeUrlMetadata(YoutubeContentType.VIDEO,    extractQueryParam(url, "v"), null, null)
            "shorts"   -> YoutubeUrlMetadata(YoutubeContentType.SHORT,    segments.getOrNull(1), null, null)
            "playlist" -> YoutubeUrlMetadata(YoutubeContentType.PLAYLIST, null, null, extractQueryParam(url, "list"))
            "channel"  -> YoutubeUrlMetadata(YoutubeContentType.CHANNEL,  null, segments.getOrNull(1), null)
            else -> {
                val first = segments.firstOrNull()
                if (first?.startsWith("@") == true) {
                    YoutubeUrlMetadata(YoutubeContentType.CHANNEL, null, first, null)
                } else {
                    YoutubeUrlMetadata(YoutubeContentType.UNKNOWN, null, null, null)
                }
            }
        }
    }

    companion object {
        internal fun extractHost(url: String): String? {
            val afterScheme = url.removePrefix("https://").removePrefix("http://")
            if (afterScheme == url) return null
            return afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
                .takeIf { it.isNotEmpty() }
        }

        internal fun extractPathSegments(url: String): List<String> {
            val afterScheme = url.removePrefix("https://").removePrefix("http://")
            val afterHost = afterScheme.substringAfter('/', "")
            val path = afterHost.substringBefore('?').substringBefore('#')
            return path.split('/').filter { it.isNotEmpty() }
        }

        internal fun extractQueryParam(url: String, key: String): String? {
            val query = url.substringAfter('?', "").substringBefore('#')
            return query.split('&').firstOrNull { it.startsWith("$key=") }?.substringAfter('=')
        }
    }

    @Composable
    override fun DetailSection(
        urlMetadata: DomainUrlMetadata,
        ingestion: IngestionRecord?,
        videoUrl: String?,
        thumbnailUrl: String?,
        videoNavigation: VideoNavigation,
    ) {
        val meta = urlMetadata as? YoutubeUrlMetadata ?: return
        when {
            ingestion == null -> UrlOnlyState(meta)
            ingestion.status == IngestionStatus.QUEUED ||
            ingestion.status == IngestionStatus.EXTRACTING_METADATA -> LoadingState()
            else -> FullCard(
                meta,
                ingestion,
                showProgress = ingestion.status == IngestionStatus.DOWNLOADING,
                videoUrl = videoUrl,
                thumbnailUrl = thumbnailUrl,
                videoNavigation = videoNavigation,
            )
        }
    }
}

@Composable
private fun UrlOnlyState(meta: YoutubeUrlMetadata) {
    Row(
        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YoutubeAvatar(size = IconSize.favicon)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            ContentTypeBadge(meta.contentType)
            val detail = meta.channelHandle ?: meta.videoId ?: meta.playlistId
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LoadingState() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.3f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "shimmer_alpha",
    )
    Row(
        modifier = Modifier
            .padding(horizontal = Spacing.md, vertical = Spacing.md)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(IconSize.favicon)
                .clip(RoundedCornerShape(Radius.sm))
                .background(LocalColors.current.layout.divider.copy(alpha = alpha)),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Box(
                modifier = Modifier
                    .width(Spacing.lg + Spacing.lg)
                    .height(Spacing.sm + Spacing.xs)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(LocalColors.current.layout.divider.copy(alpha = alpha)),
            )
            Box(
                modifier = Modifier
                    .width(Spacing.lg)
                    .height(Spacing.sm)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(LocalColors.current.layout.divider.copy(alpha = alpha)),
            )
        }
    }
    Text(
        text = "Fetching details…",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.md),
    )
}

@Composable
private fun FullCard(
    meta: YoutubeUrlMetadata,
    ingestion: IngestionRecord,
    showProgress: Boolean,
    videoUrl: String?,
    thumbnailUrl: String?,
    videoNavigation: VideoNavigation,
) {
    var showPlayer by remember { mutableStateOf(videoNavigation.autoPlay) }

    if (showPlayer && videoUrl != null) {
        VideoPlayerDialog(videoUrl = videoUrl, onDismiss = { showPlayer = false }, videoNavigation = videoNavigation)
    }

    Column(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md)) {
        // Header: avatar + channel + content type
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YoutubeAvatar(size = IconSize.favicon)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                val channel = ingestion.uploader?.let { "@$it" } ?: meta.channelHandle
                if (channel != null) {
                    Text(channel, style = MaterialTheme.typography.titleSmall)
                }
                ContentTypeBadge(meta.contentType)
            }
        }

        // Title
        val title = ingestion.title
        if (!title.isNullOrBlank()) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
            )
        }

        // Thumbnail with play overlay
        val thumbUrl = thumbnailUrl
        if (!thumbUrl.isNullOrBlank()) {
            Spacer(Modifier.height(Spacing.sm))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.78f)
                    .clip(RoundedCornerShape(Radius.md))
                    .border(Dp.Hairline, LocalColors.current.layout.divider, RoundedCornerShape(Radius.md))
                    .then(if (videoUrl != null) Modifier.clickable { showPlayer = true } else Modifier),
            ) {
                AsyncImage(
                    model = thumbUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
                // Duration badge bottom-right
                val duration = ingestion.duration
                if (duration != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(Spacing.xs)
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(Color.Black.copy(alpha = 0.8f))
                            .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
                    ) {
                        Text(
                            text = formatDuration(duration),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                    }
                }
                if (videoUrl != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(IconSize.thumbnail)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "Play video",
                            tint = Color.White,
                            modifier = Modifier.size(IconSize.favicon),
                        )
                    }
                }
            }
        }

        // Stats row: view count · like count
        val viewCount = ingestion.viewCount
        val likeCount = ingestion.likeCount
        if (viewCount != null || likeCount != null) {
            Spacer(Modifier.height(Spacing.xs))
            val parts = buildList {
                viewCount?.let { add("${formatCount(it)} views") }
                likeCount?.let { add("${formatCount(it)} likes") }
            }
            Text(
                text = parts.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Description snippet
        val desc = ingestion.description
        if (!desc.isNullOrBlank()) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
            )
        }

        // Tags
        val hashtags = parseHashtags(ingestion.tags)
        if (hashtags.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                hashtags.take(5).forEach { tag ->
                    SuggestionChip(
                        onClick = {},
                        label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    )
                }
            }
        }

        if (ingestion.status.isFailure) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = ingestion.errorMessage ?: "Download failed",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (showProgress) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun YoutubeAvatar(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(Radius.sm))
            .background(youtubeRed),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.6f),
        )
    }
}

@Composable
private fun ContentTypeBadge(contentType: YoutubeContentType) {
    Text(
        text = contentType.label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = youtubeRed,
    )
}

internal fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

internal fun formatCount(count: Long): String = when {
    count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
    count >= 1_000     -> "%.1fK".format(count / 1_000.0)
    else               -> count.toString()
}

private fun parseHashtags(tags: String?): List<String> {
    if (tags.isNullOrBlank()) return emptyList()
    return try {
        val arr = org.json.JSONArray(tags)
        (0 until arr.length()).map { "#${arr.getString(it)}" }
    } catch (_: Exception) { emptyList() }
}
