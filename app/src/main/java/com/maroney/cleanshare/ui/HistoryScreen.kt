package com.maroney.cleanshare.ui

import android.content.ClipData
import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.maroney.cleanshare.data.ShareRecord
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel) {
    val history by viewModel.history.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Clean Share") },
                actions = {
                    if (history.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearHistory() }) {
                            Text("Clear all")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (history.isEmpty()) {
            EmptyState(modifier = Modifier.padding(innerPadding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(history, key = { it.id }) { record ->
                    HistoryItem(record)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No shares yet.\nShare a link from any app to get started.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryItem(record: ShareRecord) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboard.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Derive the Google favicon URL from the cleaned text's host.
    // runCatching guards against malformed URIs (plain text shares, etc.).
    val faviconUrl: String? = remember(record.cleanedText) {
        runCatching { record.cleanedText.toUri().host }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { host -> "https://www.google.com/s2/favicons?sz=64&domain=$host" }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // combinedClickable before padding → ripple covers full row width.
            .combinedClickable(
                onClick = {
                    // Tap → open the cleaned URL in the default browser / handler.
                    // try-catch: cleanedText may not be a valid URI (plain text share).
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, record.cleanedText.toUri())
                        )
                    } catch (_: Exception) { /* no handler or bad URI — ignore */
                    }
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch {
                        clipboardManager.setClipEntry(
                            ClipData.newPlainText("link", record.cleanedText).toClipEntry()
                        )
                    }
                },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Favicon slot — always 32 dp wide so text columns stay aligned.
        // Invisible when the host can't be parsed; Coil handles errors silently.
        // Lifecycle: AsyncImage uses the composition's coroutine scope;
        // the in-flight request is canceled when this composable leaves the screen.
        Box(
            modifier = Modifier
                .size(32.dp)
                .align(Alignment.Top),
            contentAlignment = Alignment.Center,
        ) {
            if (faviconUrl != null) {
                AsyncImage(
                    model = faviconUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // Cleaned URL is the headline — what the user actually shared
            Text(
                text = record.cleanedText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            // Original URL shown only when tracking params were actually stripped
            if (record.originalText != record.cleanedText) {
                Text(
                    text = record.originalText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = formatAge(record.sharedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Human-readable relative timestamp.
 *
 * - < 60 s   → "Just now"  (DateUtils gives "0 minutes ago" without this guard)
 * - minutes  → "2 min. ago"
 * - hours    → "1 hr. ago"
 * - days     → "Yesterday", "3 days ago"
 * - ≥ ~1 wk → absolute date, e.g. "Apr 28"  (DateUtils switches automatically)
 *
 * Negative delta (clock skew) is treated as "Just now".
 */
private fun formatAge(timestamp: Long): String {
    val delta = System.currentTimeMillis() - timestamp
    if (delta < 60_000L) return "Just now"
    return DateUtils.getRelativeTimeSpanString(
        timestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
}
