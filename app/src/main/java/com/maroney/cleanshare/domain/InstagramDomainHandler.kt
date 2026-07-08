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
import androidx.compose.ui.graphics.Brush
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

enum class InstagramContentType(val label: String) {
    POST("Post"), REEL("Reel"), PROFILE("Profile"), STORY("Story"), TV("TV"), UNKNOWN("Post")
}

data class InstagramUrlMetadata(
    val contentType: InstagramContentType,
    val shortcode: String?,
    val username: String?,
) : DomainUrlMetadata()

// Instagram brand gradient — intentionally hardcoded, not from the app color system.
private val instagramGradient = Brush.linearGradient(
    colors = listOf(Color(0xFFE1306C), Color(0xFFF77737), Color(0xFFFFDC80)),
)

class InstagramDomainHandler : DomainHandler {

    override fun matches(url: String): Boolean {
        val host = extractHost(url) ?: return false
        return host == "instagram.com" || host == "www.instagram.com"
    }

    override fun extractUrlMetadata(url: String): InstagramUrlMetadata {
        val segments = extractPathSegments(url)
        return when (segments.firstOrNull()) {
            "p"       -> InstagramUrlMetadata(InstagramContentType.POST,    segments.getOrNull(1), null)
            "reel"    -> InstagramUrlMetadata(InstagramContentType.REEL,    segments.getOrNull(1), null)
            "tv"      -> InstagramUrlMetadata(InstagramContentType.TV,      segments.getOrNull(1), null)
            "stories" -> InstagramUrlMetadata(InstagramContentType.STORY,   null, segments.getOrNull(1))
            else      -> InstagramUrlMetadata(InstagramContentType.PROFILE, null, segments.firstOrNull())
        }
    }

    companion object {
        // Pure string helpers — no Android imports so these work in JVM unit tests.
        internal fun extractHost(url: String): String? {
            val afterScheme = url.removePrefix("https://").removePrefix("http://")
            if (afterScheme == url) return null // no recognised scheme
            return afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
                .takeIf { it.isNotEmpty() }
        }

        internal fun extractPathSegments(url: String): List<String> {
            val afterScheme = url.removePrefix("https://").removePrefix("http://")
            val afterHost = afterScheme.substringAfter('/', "")
            val path = afterHost.substringBefore('?').substringBefore('#')
            return path.split('/').filter { it.isNotEmpty() }
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
        val meta = urlMetadata as? InstagramUrlMetadata ?: return
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
private fun UrlOnlyState(meta: InstagramUrlMetadata) {
    Row(
        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InstagramAvatar(size = IconSize.favicon)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            ContentTypeBadge(meta.contentType)
            val detail = meta.shortcode ?: meta.username
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
                .clip(CircleShape)
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
    meta: InstagramUrlMetadata,
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
        // Header: avatar + uploader + content type + age
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InstagramAvatar(size = IconSize.favicon)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                val handle = ingestion.uploader?.let { "@$it" } ?: meta.username?.let { "@$it" }
                if (handle != null) {
                    Text(handle, style = MaterialTheme.typography.titleSmall)
                }
                ContentTypeBadge(meta.contentType)
            }
        }

        // Caption
        val caption = ingestion.description
        if (!caption.isNullOrBlank()) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
            )
        }

        // Thumbnail with play button overlay when video is available
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
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Spacing.xs),
                ) {
                    ContentTypeBadge(meta.contentType)
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

        // Structured fields: shortcode + hashtags
        val shortcode = meta.shortcode
        val hashtags = parseHashtags(ingestion.tags)
        if (shortcode != null || hashtags.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.sm))
            if (shortcode != null) {
                Text(
                    text = shortcode,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
        }

        if (ingestion.status.isFailure) {
            Spacer(Modifier.height(Spacing.xs))
            val msg = ingestion.errorMessage ?: "Download failed"
            Text(
                text = msg,
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
private fun InstagramAvatar(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(instagramGradient),
    )
}

@Composable
private fun ContentTypeBadge(contentType: InstagramContentType) {
    Text(
        text = contentType.label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Color(0xFFE1306C),
    )
}

private fun parseHashtags(tags: String?): List<String> {
    if (tags.isNullOrBlank()) return emptyList()
    return try {
        val arr = org.json.JSONArray(tags)
        (0 until arr.length()).map { "#${arr.getString(it)}" }
    } catch (_: Exception) { emptyList() }
}
