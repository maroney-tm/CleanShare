package com.maroney.cleanshare.ui

import android.content.ClipData
import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.maroney.cleanshare.data.FetchStatus
import com.maroney.cleanshare.data.ShareRecordWithMetadata
import kotlinx.coroutines.launch

// ── Public entry point ──────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryItem(
    item: ShareRecordWithMetadata,
    onRetryFetch: (shareRecordId: Long, url: String) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val cleanedUrl = item.record.cleanedText

    val onOpen: () -> Unit = {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, cleanedUrl.toUri())) }
        catch (_: Exception) {}
    }
    val onCopy: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            clipboard.setClipEntry(ClipData.newPlainText("link", cleanedUrl).toClipEntry())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onCopy),
    ) {
        when {
            item.metadata == null -> ShimmerRow()
            item.metadata.fetchStatus == FetchStatus.FAILED -> FallbackRow(item, onCopy, onOpen, onRetryFetch)
            item.metadata.thumbnailUrl != null -> LayoutA(item, onCopy, onOpen)
            else -> LayoutC(item, onCopy, onOpen)
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
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(shimmerBrush),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.fillMaxWidth(0.80f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(shimmerBrush))
            Box(Modifier.fillMaxWidth(1.00f).height(11.dp).clip(RoundedCornerShape(4.dp)).background(shimmerBrush))
            Box(Modifier.fillMaxWidth(0.65f).height(11.dp).clip(RoundedCornerShape(4.dp)).background(shimmerBrush))
            Box(Modifier.fillMaxWidth(0.55f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(shimmerBrush))
        }
    }
}

// ── Layout A: leading 64×64 thumbnail ────────────────────────────────────────

@Composable
private fun LayoutA(
    item: ShareRecordWithMetadata,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
) {
    val metadata = item.metadata!!
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AsyncImage(
            model = metadata.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            metadata.title?.let {
                Text(it, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            metadata.description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            UrlLines(item)
        }
        OverflowMenu(onCopy = onCopy, onOpen = onOpen, onRetry = null)
    }
}

// ── Layout C: 32×32 favicon, no thumbnail ────────────────────────────────────

@Composable
private fun LayoutC(
    item: ShareRecordWithMetadata,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
) {
    val metadata = item.metadata!!
    val faviconUrl = remember(item.record.cleanedText) {
        runCatching { item.record.cleanedText.toUri().host }
            .getOrNull()?.takeIf { it.isNotBlank() }
            ?.let { "https://www.google.com/s2/favicons?sz=64&domain=$it" }
    }
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            if (faviconUrl != null) AsyncImage(model = faviconUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            metadata.title?.let {
                Text(it, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val snippet = metadata.articleSnippet ?: metadata.description
            snippet?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            UrlLines(item)
        }
        OverflowMenu(onCopy = onCopy, onOpen = onOpen, onRetry = null)
    }
}

// ── Fallback: fetch failed ────────────────────────────────────────────────────

@Composable
private fun FallbackRow(
    item: ShareRecordWithMetadata,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
    onRetryFetch: (shareRecordId: Long, url: String) -> Unit,
) {
    val faviconUrl = remember(item.record.cleanedText) {
        runCatching { item.record.cleanedText.toUri().host }
            .getOrNull()?.takeIf { it.isNotBlank() }
            ?.let { "https://www.google.com/s2/favicons?sz=64&domain=$it" }
    }
    val onRetry: () -> Unit = {
        onRetryFetch(item.record.id, item.record.cleanedText)
    }
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            if (faviconUrl != null) AsyncImage(model = faviconUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            UrlLines(item)
            Text(
                text = formatAge(item.record.sharedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OverflowMenu(onCopy = onCopy, onOpen = onOpen, onRetry = onRetry)
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
    if (item.record.originalText != item.record.cleanedText) {
        Text(
            text = item.record.originalText,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OverflowMenu(
    onCopy: () -> Unit,
    onOpen: () -> Unit,
    onRetry: (() -> Unit)?,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More options")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (onRetry != null) {
                DropdownMenuItem(
                    text = { Text("Retry metadata fetch") },
                    onClick = { onRetry(); expanded = false },
                )
            }
            DropdownMenuItem(
                text = { Text("Copy link") },
                onClick = { onCopy(); expanded = false },
            )
            DropdownMenuItem(
                text = { Text("Open link") },
                onClick = { onOpen(); expanded = false },
            )
        }
    }
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
