package com.maroney.cleanshare.ui.fakedata

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.maroney.cleanshare.data.ContentType
import com.maroney.cleanshare.data.FetchStatus
import com.maroney.cleanshare.data.IngestionRecord
import com.maroney.cleanshare.data.IngestionStatus
import com.maroney.cleanshare.data.LinkMetadata
import com.maroney.cleanshare.data.ShareRecord
import com.maroney.cleanshare.data.ShareRecordWithMetadata

private val recordWithTracking = ShareRecord(
    id = 1L,
    originalText = "https://developer.android.com/jetpack/compose?utm_source=twitter&utm_campaign=compose",
    cleanedText = "https://developer.android.com/jetpack/compose",
    sharedAt = 1_715_000_000_000L,
)

private val recordClean = ShareRecord(
    id = 2L,
    originalText = "https://developer.android.com/jetpack/compose",
    cleanedText = "https://developer.android.com/jetpack/compose",
    sharedAt = 1_715_000_000_000L,
    tags = listOf("compose", "reading-list"),
)

private val youtubeRecord = ShareRecord(
    id = 3L,
    originalText = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
    cleanedText = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
    sharedAt = 1_715_100_000_000L,
)

private val instagramRecord = ShareRecord(
    id = 4L,
    originalText = "https://www.instagram.com/reel/CxYz123/",
    cleanedText = "https://www.instagram.com/reel/CxYz123/",
    sharedAt = 1_715_200_000_000L,
)

// State 1: pending fetch — no metadata yet
val shimmerItem = ShareRecordWithMetadata(
    record = recordWithTracking,
    metadata = null,
)

// State 2: fetched link with OG thumbnail
val fetchedLinkItem = ShareRecordWithMetadata(
    record = recordWithTracking,
    metadata = LinkMetadata(
        shareRecordId = 1L,
        title = "Jetpack Compose  |  Android Developers",
        thumbnailUrl = "https://developer.android.com/static/images/social/android-developers.png",
        description = "Build better apps faster with Jetpack Compose, Android's modern toolkit for building native UI.",
        articleSnippet = null,
        contentType = ContentType.UNKNOWN,
        fetchStatus = FetchStatus.SUCCESS,
    ),
)

// State 3: fetched link, no thumbnail
val fetchedLinkNoThumbItem = ShareRecordWithMetadata(
    record = recordClean,
    metadata = LinkMetadata(
        shareRecordId = 2L,
        title = "Jetpack Compose  |  Android Developers",
        thumbnailUrl = null,
        description = "Build better apps faster with Jetpack Compose, Android's modern toolkit for building native UI.",
        articleSnippet = "Jetpack Compose is Android's recommended modern toolkit for building native UI.",
        contentType = ContentType.ARTICLE,
        fetchStatus = FetchStatus.SUCCESS,
    ),
)

// State 4: metadata fetch failed
val fallbackItem = ShareRecordWithMetadata(
    record = recordWithTracking,
    metadata = LinkMetadata(
        shareRecordId = 1L,
        title = null,
        thumbnailUrl = null,
        description = null,
        articleSnippet = null,
        contentType = ContentType.UNKNOWN,
        fetchStatus = FetchStatus.FAILED,
    ),
)

// State 5: ingestion downloading (title + uploader known, video in progress)
val ingestionDownloadingItem = ShareRecordWithMetadata(
    record = youtubeRecord,
    metadata = null,
    ingestion = IngestionRecord(
        shareRecordId = 3L,
        status = IngestionStatus.DOWNLOADING,
        title = "Never Gonna Give You Up",
        uploader = "RickAstleyVEVO",
        thumbnailUrl = null,
        duration = 213,
    ),
)

// State 6: ingestion complete
val ingestionCompleteItem = ShareRecordWithMetadata(
    record = instagramRecord,
    metadata = null,
    ingestion = IngestionRecord(
        shareRecordId = 4L,
        status = IngestionStatus.COMPLETE,
        title = "POV: you just discovered this account",
        uploader = "natgeo",
        thumbnailUrl = null,
        duration = 28,
        viewCount = 2_400_000L,
    ),
)

class HistoryItemPreviewProvider : PreviewParameterProvider<ShareRecordWithMetadata> {
    override val values: Sequence<ShareRecordWithMetadata> = sequenceOf(
        ingestionCompleteItem,
        ingestionDownloadingItem,
        fetchedLinkItem,
        fetchedLinkNoThumbItem,
        fallbackItem,
        shimmerItem,
    )
}
