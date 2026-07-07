package com.maroney.cleanshare.ui

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.maroney.cleanshare.CleanShareApplication
import com.maroney.cleanshare.data.FetchStatus
import com.maroney.cleanshare.data.IngestionStatus
import com.maroney.cleanshare.data.OfflineVideoRecord
import com.maroney.cleanshare.data.OfflineVideoStatus
import com.maroney.cleanshare.data.ShareRecordWithMetadata
import com.maroney.cleanshare.data.isFailure
import com.maroney.cleanshare.domain.DomainHandler
import com.maroney.cleanshare.domain.DomainUrlMetadata
import com.maroney.cleanshare.ui.fakedata.HistoryItemPreviewProvider
import com.maroney.cleanshare.ui.theme.CleanShareTheme
import com.maroney.cleanshare.ui.theme.LocalColors
import kotlinx.coroutines.launch
import timber.log.Timber

// ── Stateful entry point (ViewModel-driven) ──────────────────────────────────

@Composable
fun DetailScreen(
    id: Long,
    onNavigateBack: () -> Unit,
) {
    val vm: DetailViewModel = viewModel(key = id.toString(), factory = DetailViewModel.factory(id))
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val isRefreshing by vm.isRefreshing.collectAsStateWithLifecycle()
    val offlineVideo by vm.offlineVideo.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        vm.deleted.collect { onNavigateBack() }
    }

    val item = uiState ?: return

    val app = LocalContext.current.applicationContext as CleanShareApplication
    val url = item.record.cleanedText
    val handler = remember(url) { app.domainHandlerRegistry.findHandler(url) }
    val urlMetadata = remember(url, handler) { handler?.extractUrlMetadata(url) }
    val videoUrl = remember(item.record.syncId, item.ingestion?.status) {
        if (item.ingestion?.status == IngestionStatus.COMPLETE) {
            app.syncClient.effectiveBaseUrl()?.let { "$it/records/${item.record.syncId}/media" }
        } else null
    }
    // Prefer the fully-downloaded local copy when one has been saved offline, so playback
    // works without a connection and doesn't touch the (separately bounded) streaming cache.
    val playableVideoUrl = remember(videoUrl, offlineVideo) {
        offlineVideo?.takeIf { it.status == OfflineVideoStatus.COMPLETE }?.localFilePath ?: videoUrl
    }

    DetailContent(
        item = item,
        notes = notes,
        handler = handler,
        urlMetadata = urlMetadata,
        videoUrl = playableVideoUrl,
        offlineVideo = offlineVideo,
        isRefreshing = isRefreshing,
        onRefresh = vm::refresh,
        onNavigateBack = onNavigateBack,
        onShare = {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, item.record.cleanedText)
            }
            context.startActivity(Intent.createChooser(intent, null))
        },
        onCopy = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            scope.launch {
                clipboard.setClipEntry(ClipData.newPlainText("link", item.record.cleanedText).toClipEntry())
            }
        },
        onOpen = {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, item.record.cleanedText.toUri()))
            } catch (e: Exception) { Timber.e(e, "Failed to open URL in browser") }
        },
        onDelete = { vm.deleteItem() },
        onRetryFetch = { vm.retryMetadataFetch() },
        onRetryIngestion = { vm.retryIngestion() },
        onSaveOffline = { videoUrl?.let(vm::saveOffline) },
        onRemoveOffline = { vm.removeOffline() },
        onNotesChanged = vm::onNotesChanged,
        onNotesFocusLost = vm::onNotesFocusLost,
    )
}

// ── Stateless UI (previewable) ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailContent(
    item: ShareRecordWithMetadata,
    notes: String,
    handler: DomainHandler?,
    urlMetadata: DomainUrlMetadata?,
    videoUrl: String?,
    offlineVideo: OfflineVideoRecord?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onNavigateBack: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onRetryFetch: () -> Unit,
    onRetryIngestion: () -> Unit,
    onSaveOffline: () -> Unit,
    onRemoveOffline: () -> Unit,
    onNotesChanged: (String) -> Unit,
    onNotesFocusLost: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Link Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onShare) {
                        Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Share")
                    }
                },
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
            if (handler != null && urlMetadata != null) {
                handler.DetailSection(urlMetadata, item.ingestion, videoUrl)
            } else {
                HeaderSection(item)
            }

            if (item.record.cleanedText != item.record.originalText) {
                SectionLabel("URLs")
                UrlBlock(label = "CLEANED", url = item.record.cleanedText, highlighted = true)
                Spacer(Modifier.height(Spacing.sm))
                UrlBlock(label = "ORIGINAL", url = item.record.originalText, highlighted = false)
            }

            // Description is hidden when a domain handler owns the header area.
            val description = if (handler == null) {
                item.metadata?.description?.takeIf { it.isNotBlank() }
                    ?: item.metadata?.articleSnippet?.takeIf { it.isNotBlank() }
            } else null
            if (description != null) {
                SectionLabel("Description")
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = Spacing.md),
                )
            }

            SectionLabel("Notes")
            OutlinedTextField(
                value = notes,
                onValueChange = onNotesChanged,
                placeholder = { Text("Add notes…") },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md)
                    .onFocusChanged { if (!it.isFocused) onNotesFocusLost() },
            )

            Spacer(Modifier.height(Spacing.md))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                    Text("Open Link")
                }
                OutlinedButton(onClick = onCopy, modifier = Modifier.fillMaxWidth()) {
                    Text("Copy Link")
                }
                if (videoUrl != null) {
                    when (offlineVideo?.status) {
                        null, OfflineVideoStatus.FAILED -> OutlinedButton(
                            onClick = onSaveOffline,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Save Offline")
                        }
                        OfflineVideoStatus.DOWNLOADING -> OutlinedButton(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Saving Offline…")
                        }
                        OfflineVideoStatus.COMPLETE -> OutlinedButton(
                            onClick = onRemoveOffline,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Remove Offline Copy (${formatBytes(offlineVideo.fileSizeBytes)})")
                        }
                    }
                    if (offlineVideo?.status == OfflineVideoStatus.FAILED) {
                        Text(
                            text = "Couldn't save offline: ${offlineVideo.errorMessage ?: "unknown error"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = SolidColor(MaterialTheme.colorScheme.error),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Delete")
                }
                if (item.metadata?.fetchStatus == FetchStatus.FAILED) {
                    OutlinedButton(
                        onClick = onRetryFetch,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Retry Metadata Fetch")
                    }
                }
                if (item.ingestion?.status?.isFailure == true) {
                    OutlinedButton(
                        onClick = onRetryIngestion,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Retry Download")
                    }
                }
            }
            Spacer(Modifier.height(Spacing.lg))
            }
        }
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun HeaderSection(item: ShareRecordWithMetadata) {
    val thumbnailUrl = item.metadata?.thumbnailUrl
    val faviconUrl = remember(item.record.cleanedText) {
        runCatching { item.record.cleanedText.toUri().host }
            .getOrNull()?.takeIf { it.isNotBlank() }
            ?.let { "https://www.google.com/s2/favicons?sz=64&domain=$it" }
    }
    val title = item.metadata?.title

    if (thumbnailUrl == null && faviconUrl == null && title == null) return

    Row(
        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            thumbnailUrl != null -> AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(IconSize.thumbnail)
                    .clip(RoundedCornerShape(Radius.md))
                    .border(Dp.Hairline, LocalColors.current.layout.divider, RoundedCornerShape(Radius.md)),
            )
            faviconUrl != null -> AsyncImage(
                model = faviconUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(IconSize.favicon)
                    .clip(RoundedCornerShape(Radius.md))
                    .border(Dp.Hairline, LocalColors.current.layout.divider, RoundedCornerShape(Radius.md)),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            title?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            }
            Text(
                text = formatAge(item.record.sharedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.md, bottom = Spacing.xs),
    )
}

@Composable
private fun UrlBlock(label: String, url: String, highlighted: Boolean) {
    val containerColor: Color
    val contentColor: Color
    if (highlighted) {
        containerColor = MaterialTheme.colorScheme.primaryContainer
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        containerColor = MaterialTheme.colorScheme.surfaceVariant
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    }

    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = contentColor,
        modifier = Modifier.padding(horizontal = Spacing.md),
    )
    Spacer(Modifier.height(Spacing.xs))
    Text(
        text = url,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = contentColor,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md)
            .clip(RoundedCornerShape(Radius.sm))
            .background(containerColor)
            .padding(Spacing.sm),
    )
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Detail Screen", widthDp = 380)
@Composable
private fun DetailScreenPreview(
    @PreviewParameter(HistoryItemPreviewProvider::class)
    item: ShareRecordWithMetadata,
) {
    CleanShareTheme {
        DetailContent(
            item = item,
            notes = "Added this while researching Compose layouts.",
            handler = null,
            urlMetadata = null,
            videoUrl = null,
            offlineVideo = null,
            isRefreshing = false,
            onRefresh = {},
            onNavigateBack = {},
            onShare = {},
            onCopy = {},
            onOpen = {},
            onDelete = {},
            onRetryFetch = {},
            onRetryIngestion = {},
            onSaveOffline = {},
            onRemoveOffline = {},
            onNotesChanged = {},
            onNotesFocusLost = {},
        )
    }
}
