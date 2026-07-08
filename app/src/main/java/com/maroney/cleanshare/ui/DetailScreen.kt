package com.maroney.cleanshare.ui

import android.content.ClipData
import android.content.Intent
import androidx.compose.animation.animateBounds
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import com.maroney.cleanshare.domain.UrlSanitizer
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
    val tagVocabulary by vm.tagVocabulary.collectAsStateWithLifecycle()
    val suggestedTags by vm.suggestedTags.collectAsStateWithLifecycle()
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
    // The server re-hosts a local copy of the thumbnail during ingestion (see
    // Ingester.downloadThumbnail) rather than us linking directly to the platform's CDN,
    // whose signed URLs expire after a few weeks. Available as soon as ingestion's metadata
    // phase completes — well before videoUrl, which needs the (much slower) video download
    // to finish — so this isn't gated on COMPLETE the way videoUrl is.
    // ingestion.thumbnailReady is folded in purely as a cache-busting version tag — see the
    // matching comment in HistoryItem.MediaIngestionRow for why that's needed (and why it
    // can't just be thumbnailUrl). Uses lastKnownBaseUrl (not effectiveBaseUrl) so this still
    // resolves while offline and Coil's disk cache (which has no TTL) keeps serving it — unlike
    // videoUrl above, which deliberately requires a live, health-checked connection.
    val thumbnailUrl = remember(item.record.syncId, item.ingestion?.thumbnailReady) {
        val ingestion = item.ingestion
        if (ingestion != null) {
            app.syncClient.lastKnownBaseUrl()?.let {
                "$it/records/${item.record.syncId}/thumbnail?v=${ingestion.thumbnailReady}"
            }
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
        thumbnailUrl = thumbnailUrl,
        offlineVideo = offlineVideo,
        tags = item.record.tags,
        tagVocabulary = tagVocabulary,
        suggestedTags = suggestedTags,
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
        onAddTag = vm::addTag,
        onRemoveTag = vm::removeTag,
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
    thumbnailUrl: String?,
    offlineVideo: OfflineVideoRecord?,
    tags: List<String>,
    tagVocabulary: List<String>,
    suggestedTags: List<String>,
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
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onNotesChanged: (String) -> Unit,
    onNotesFocusLost: () -> Unit,
) {
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete this entry?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirmation = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }

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
                    IconButton(onClick = { showOverflowMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Open Link") },
                            onClick = { showOverflowMenu = false; onOpen() },
                        )
                        DropdownMenuItem(
                            text = { Text("Copy Link") },
                            onClick = { showOverflowMenu = false; onCopy() },
                        )
                        if (videoUrl != null) {
                            when (offlineVideo?.status) {
                                null, OfflineVideoStatus.FAILED -> DropdownMenuItem(
                                    text = { Text("Save Offline") },
                                    onClick = { showOverflowMenu = false; onSaveOffline() },
                                )
                                OfflineVideoStatus.DOWNLOADING -> DropdownMenuItem(
                                    text = { Text("Saving Offline…") },
                                    onClick = {},
                                    enabled = false,
                                )
                                OfflineVideoStatus.COMPLETE -> DropdownMenuItem(
                                    text = { Text("Remove Offline Copy (${formatBytes(offlineVideo.fileSizeBytes)})") },
                                    onClick = { showOverflowMenu = false; onRemoveOffline() },
                                )
                            }
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = { showOverflowMenu = false; showDeleteConfirmation = true },
                        )
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
                handler.DetailSection(urlMetadata, item.ingestion, videoUrl, thumbnailUrl)
            } else {
                HeaderSection(item)
            }

            if (item.record.cleanedText != item.record.originalText) {
                val removedParams = remember(item.record.originalText, item.record.cleanedText) {
                    UrlSanitizer.removedQueryParams(item.record.originalText, item.record.cleanedText)
                }
                val removedColor = MaterialTheme.colorScheme.error
                val annotatedUrl = remember(item.record.originalText, removedParams, removedColor) {
                    buildOriginalUrlAnnotatedString(item.record.originalText, removedParams, removedColor)
                }
                Spacer(Modifier.height(Spacing.md))
                Text(
                    text = annotatedUrl,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(Spacing.sm),
                )
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

            SectionLabel("Tags")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                // Quick-tap suggestions (most-used tags) not yet applied to this entry share
                // the row with the entry's own tags — tapping one adds it and it immediately
                // re-renders as a normal (solid) chip alongside the rest.
                val untappedSuggestions = suggestedTags.filter { suggestion ->
                    tags.none { it.equals(suggestion, ignoreCase = true) }
                }
                if (tags.isNotEmpty() || untappedSuggestions.isNotEmpty()) {
                    // Keying each pill by its tag text (rather than by added/suggested
                    // status) and animating within a shared LookaheadScope lets a pill
                    // glide to its new spot — instead of popping there — when the list
                    // reflows or a suggestion is tapped and turns into an applied tag.
                    LookaheadScope {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            tags.forEach { tag ->
                                key(tag) {
                                    TagPill(
                                        text = tag,
                                        added = true,
                                        onClick = { onRemoveTag(tag) },
                                        modifier = Modifier.animateBounds(this@LookaheadScope),
                                    )
                                }
                            }
                            untappedSuggestions.forEach { tag ->
                                key(tag) {
                                    TagPill(
                                        text = tag,
                                        added = false,
                                        onClick = { onAddTag(tag) },
                                        modifier = Modifier.animateBounds(this@LookaheadScope),
                                    )
                                }
                            }
                        }
                    }
                }

                var newTagText by remember { mutableStateOf("") }
                val autocompleteMatches = remember(newTagText, tagVocabulary, tags) {
                    if (newTagText.isBlank()) {
                        emptyList()
                    } else {
                        tagVocabulary
                            .filter { candidate -> tags.none { it.equals(candidate, ignoreCase = true) } }
                            .filter { it.contains(newTagText, ignoreCase = true) }
                            .sortedBy { !it.startsWith(newTagText, ignoreCase = true) }
                            .take(5)
                    }
                }
                OutlinedTextField(
                    value = newTagText,
                    onValueChange = { newTagText = it },
                    placeholder = { Text("Add tag…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onAddTag(newTagText); newTagText = "" }),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (autocompleteMatches.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        autocompleteMatches.forEach { match ->
                            Text(
                                text = match,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAddTag(match); newTagText = "" }
                                    .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.md))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                // The save/remove-offline action itself now lives in the toolbar overflow
                // menu, but a failure still needs to be visible without opening it.
                if (videoUrl != null && offlineVideo?.status == OfflineVideoStatus.FAILED) {
                    Text(
                        text = "Couldn't save offline: ${offlineVideo.errorMessage ?: "unknown error"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
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

private val TagPillShape = RoundedCornerShape(Radius.md)

/** A tag pill that is either applied to this entry (solid fill, tapping removes it) or a
 * quick-tap suggestion not yet applied (dashed outline, tapping adds it). Both styles are
 * rendered by this single composable — rather than by two separate ones — so that a pill
 * keeps its identity when [added] flips: the caller pairs this with [key] and
 * [androidx.compose.animation.animateBounds] to glide the pill to its new spot instead of
 * having it disappear from one place and pop up in another. */
@Composable
private fun TagPill(
    text: String,
    added: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val addedContentColor = MaterialTheme.colorScheme.onSecondaryContainer
    val suggestedColor = MaterialTheme.colorScheme.outline
    val contentColor = if (added) addedContentColor else suggestedColor
    val decoration = if (added) {
        Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
    } else {
        Modifier.drawBehind {
            // A plain Canvas stroke doesn't get the 1-physical-pixel auto-substitution
            // Modifier.border(Dp.Hairline, ...) applies elsewhere in this app, so this
            // spells out the same hairline weight explicitly.
            drawRoundRect(
                color = suggestedColor,
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f), 0f),
                ),
                cornerRadius = CornerRadius(Radius.md.toPx(), Radius.md.toPx()),
            )
        }
    }
    Row(
        modifier = modifier
            .height(InputChipDefaults.Height)
            .clip(TagPillShape)
            .then(decoration)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = contentColor)
        if (added) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove tag $text",
                tint = contentColor,
                modifier = Modifier.size(InputChipDefaults.IconSize),
            )
        }
    }
}

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

/** Renders [original] verbatim, with each query-string token in [removed] (the params
 * [UrlSanitizer.clean] would strip) colored [removedColor] via a span, so a single URL
 * block can show both the original link and what cleaning it would remove. */
internal fun buildOriginalUrlAnnotatedString(
    original: String,
    removed: Set<String>,
    removedColor: Color,
): AnnotatedString = buildAnnotatedString {
    val hashIdx = original.indexOf('#')
    val fragment = if (hashIdx >= 0) original.substring(hashIdx) else ""
    val beforeFragment = if (hashIdx >= 0) original.substring(0, hashIdx) else original
    val queryIdx = beforeFragment.indexOf('?')
    if (queryIdx < 0) {
        append(original)
        return@buildAnnotatedString
    }

    append(beforeFragment.substring(0, queryIdx + 1))
    beforeFragment.substring(queryIdx + 1).split('&').forEachIndexed { index, token ->
        if (index > 0) append("&")
        if (token in removed) {
            withStyle(SpanStyle(color = removedColor)) { append(token) }
        } else {
            append(token)
        }
    }
    append(fragment)
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
            thumbnailUrl = null,
            offlineVideo = null,
            tags = listOf("compose", "reading-list"),
            tagVocabulary = listOf("compose", "kotlin", "reading-list", "video"),
            suggestedTags = listOf("compose", "kotlin", "video"),
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
            onAddTag = {},
            onRemoveTag = {},
            onNotesChanged = {},
            onNotesFocusLost = {},
        )
    }
}
