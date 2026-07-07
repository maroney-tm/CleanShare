package com.maroney.cleanshare.ui

import android.text.format.DateUtils
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.maroney.cleanshare.data.FetchStatus
import com.maroney.cleanshare.data.IngestionStatus
import com.maroney.cleanshare.data.ShareRecordWithMetadata
import com.maroney.cleanshare.data.isFailure
import com.maroney.cleanshare.domain.formatDuration
import com.maroney.cleanshare.ui.fakedata.HistoryItemPreviewProvider
import com.maroney.cleanshare.ui.theme.CleanShareTheme
import com.maroney.cleanshare.ui.theme.LocalColors

// ── Public entry point ──────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryItem(
    item: ShareRecordWithMetadata,
    onNavigate: () -> Unit,
    modifier: Modifier = Modifier,
    onRetryIngestion: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onNavigate),
    ) {
        val ing = item.ingestion
        val meta = item.metadata
        val hasIngestionData = ing != null && (ing.title != null || ing.thumbnailUrl != null)

        when {
            hasIngestionData                              -> MediaIngestionRow(item, onRetryIngestion)
            meta == null                                  -> ShimmerRow()
            meta.fetchStatus == FetchStatus.FAILED        -> FallbackRow(item)
            meta.thumbnailUrl != null                     -> FetchedLinkRow(item)
            else                                          -> FetchedLinkNoThumbRow(item)
        }
    }
}

// ── Shimmer ─────────────────────────────────────────────────────────────────

@Composable
private fun ShimmerRow() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = -1000f,
        targetValue = 2000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_x",
    )
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceVariant,
        ),
        start = Offset(offset, 0f),
        end = Offset(offset + 600f, 0f),
    )

    Row(
        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(IconSize.thumbnail)
                .clip(RoundedCornerShape(Radius.md))
                .background(shimmerBrush),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // @formatter:off
            Box(Modifier.fillMaxWidth(0.80f).height(Spacing.md).clip(RoundedCornerShape(Radius.sm)).background(shimmerBrush))
            Box(Modifier.fillMaxWidth(1.00f).height(Spacing.sm).clip(RoundedCornerShape(Radius.sm)).background(shimmerBrush))
            Box(Modifier.fillMaxWidth(0.65f).height(Spacing.sm).clip(RoundedCornerShape(Radius.sm)).background(shimmerBrush))
            Box(Modifier.fillMaxWidth(0.20f).height(Spacing.sm).clip(RoundedCornerShape(Radius.sm)).background(shimmerBrush))
            // @formatter:on
        }
    }
}

// ── Tier 1: ingested media (YouTube / Instagram / TikTok) ───────────────────

@Composable
private fun MediaIngestionRow(item: ShareRecordWithMetadata, onRetryIngestion: () -> Unit) {
    val ing = item.ingestion!!
    val thumbUrl = ing.thumbnailUrl ?: item.metadata?.thumbnailUrl
    Row(
        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Thumbnail — duration badge overlaid when complete
        Box(
            modifier = Modifier
                .size(IconSize.thumbnail)
                .clip(RoundedCornerShape(Radius.md))
                .border(Dp.Hairline, LocalColors.current.layout.divider, RoundedCornerShape(Radius.md))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (thumbUrl != null) {
                AsyncImage(
                    model = thumbUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
            val duration = ing.duration
            if (ing.status == IngestionStatus.COMPLETE && duration != null) {
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
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            val title = ing.title ?: item.metadata?.title
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (ing.uploader != null) {
                Text(
                    text = "@${ing.uploader}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when (ing.status) {
                IngestionStatus.QUEUED -> Text(
                    text = "Queued…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                IngestionStatus.EXTRACTING_METADATA -> Text(
                    text = "Fetching details…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                IngestionStatus.DOWNLOADING -> Text(
                    text = "Downloading…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                else -> if (ing.status.isFailure) {
                    Text(
                        text = "Download failed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Text(
                text = formatAge(item.record.sharedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TagChipsRow(item.record.tags)
        }

        if (ing.status.isFailure) {
            IconButton(onClick = onRetryIngestion) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Retry download",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Tier 2a: fetched link with OG thumbnail ──────────────────────────────────

@Composable
private fun FetchedLinkRow(item: ShareRecordWithMetadata) {
    val metadata = item.metadata!!
    val domain = remember(item.record.cleanedText) { extractDomain(item.record.cleanedText) }
    Row(
        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = metadata.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(IconSize.thumbnail)
                .clip(RoundedCornerShape(Radius.md))
                .border(Dp.Hairline, LocalColors.current.layout.divider, RoundedCornerShape(Radius.md)),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            metadata.title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (domain != null) {
                Text(
                    text = domain,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = formatAge(item.record.sharedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TagChipsRow(item.record.tags)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Tier 2b: fetched link, no thumbnail — favicon placeholder ────────────────

@Composable
private fun FetchedLinkNoThumbRow(item: ShareRecordWithMetadata) {
    val metadata = item.metadata!!
    val domain = remember(item.record.cleanedText) { extractDomain(item.record.cleanedText) }
    val faviconUrl = remember(domain) {
        domain?.let { "https://www.google.com/s2/favicons?sz=64&domain=$it" }
    }
    Row(
        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(IconSize.thumbnail)
                .clip(RoundedCornerShape(Radius.md))
                .border(Dp.Hairline, LocalColors.current.layout.divider, RoundedCornerShape(Radius.md)),
            contentAlignment = Alignment.Center,
        ) {
            if (faviconUrl != null) {
                AsyncImage(
                    model = faviconUrl,
                    contentDescription = null,
                    modifier = Modifier.size(IconSize.favicon),
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            metadata.title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (domain != null) {
                Text(
                    text = domain,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = formatAge(item.record.sharedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TagChipsRow(item.record.tags)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Tier 3: metadata fetch failed ────────────────────────────────────────────

@Composable
private fun FallbackRow(item: ShareRecordWithMetadata) {
    val domain = remember(item.record.cleanedText) { extractDomain(item.record.cleanedText) }
    val faviconUrl = remember(domain) {
        domain?.let { "https://www.google.com/s2/favicons?sz=64&domain=$it" }
    }
    Row(
        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(IconSize.favicon)
                .clip(RoundedCornerShape(Radius.md))
                .border(Dp.Hairline, LocalColors.current.layout.divider, RoundedCornerShape(Radius.md)),
            contentAlignment = Alignment.Center,
        ) {
            if (faviconUrl != null) AsyncImage(model = faviconUrl, contentDescription = null)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = domain ?: item.record.cleanedText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatAge(item.record.sharedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TagChipsRow(item.record.tags)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TagChipsRow(tags: List<String>) {
    if (tags.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        tags.forEach { tag ->
            SuggestionChip(
                onClick = {},
                label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun extractDomain(url: String): String? =
    runCatching { url.toUri().host }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?.removePrefix("www.")

internal fun formatAge(timestamp: Long): String {
    val delta = System.currentTimeMillis() - timestamp
    if (delta < 60_000L) return "Just now"
    return DateUtils.getRelativeTimeSpanString(
        timestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
}

// ── Previews ─────────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "HistoryItem", widthDp = 380)
@Composable
private fun HistoryItemPreview(
    @PreviewParameter(HistoryItemPreviewProvider::class)
    item: ShareRecordWithMetadata,
) {
    CleanShareTheme {
        HistoryItem(
            item = item,
            onNavigate = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
