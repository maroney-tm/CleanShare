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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.maroney.cleanshare.data.FetchStatus
import com.maroney.cleanshare.data.ShareRecordWithMetadata
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
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onNavigate),
    ) {
        val metadata = item.metadata
        when {
            metadata == null -> ShimmerRow()
            metadata.fetchStatus == FetchStatus.FAILED -> FallbackRow(item)
            metadata.thumbnailUrl != null -> LayoutA(item)
            else -> LayoutC(item)
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
            Box(Modifier.fillMaxWidth(0.55f).height(Spacing.sm).clip(RoundedCornerShape(Radius.sm)).background(shimmerBrush))
            Box(Modifier.fillMaxWidth(0.20f).height(Spacing.sm).clip(RoundedCornerShape(Radius.sm)).background(shimmerBrush))
            // @formatter:on
        }
    }
}

// ── Layout A: leading 64×64 thumbnail ────────────────────────────────────────

@Composable
private fun LayoutA(item: ShareRecordWithMetadata) {
    val metadata = item.metadata!!
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
                    it,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            metadata.description?.let {
                Text(
                    it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            UrlLines(item)
            Text(
                text = formatAge(item.record.sharedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Layout C: 32×32 favicon, no thumbnail ────────────────────────────────────

@Composable
private fun LayoutC(item: ShareRecordWithMetadata) {
    val metadata = item.metadata!!
    val faviconUrl = remember(item.record.cleanedText) {
        runCatching { item.record.cleanedText.toUri().host }
            .getOrNull()?.takeIf { it.isNotBlank() }
            ?.let { "https://www.google.com/s2/favicons?sz=64&domain=$it" }
    }
    Row(
        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(IconSize.thumbnail)
                .clip(RoundedCornerShape(Radius.md))
                .border(Dp.Hairline, LocalColors.current.layout.divider, RoundedCornerShape(Radius.md)),
            contentAlignment = Alignment.Center,
        ) {
            if (faviconUrl != null) AsyncImage(model = faviconUrl, contentDescription = null)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            metadata.title?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val snippet = metadata.articleSnippet ?: metadata.description
            snippet?.let {
                Text(
                    it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            UrlLines(item)
            Text(
                text = formatAge(item.record.sharedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Fallback: fetch failed ────────────────────────────────────────────────────

@Composable
private fun FallbackRow(item: ShareRecordWithMetadata) {
    val faviconUrl = remember(item.record.cleanedText) {
        runCatching { item.record.cleanedText.toUri().host }
            .getOrNull()?.takeIf { it.isNotBlank() }
            ?.let { "https://www.google.com/s2/favicons?sz=64&domain=$it" }
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
            UrlLines(item)
            Text(
                text = formatAge(item.record.sharedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Shared sub-composables ────────────────────────────────────────────────────

@Composable
private fun UrlLines(item: ShareRecordWithMetadata) {
    Text(
        text = item.record.cleanedText,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.outline,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

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
