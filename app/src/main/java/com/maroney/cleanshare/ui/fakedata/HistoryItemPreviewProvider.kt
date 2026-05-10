package com.maroney.cleanshare.ui.fakedata

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.maroney.cleanshare.data.ContentType
import com.maroney.cleanshare.data.FetchStatus
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
)

// State 1: pending fetch — no metadata row yet
val shimmerItem = ShareRecordWithMetadata(
    record = recordWithTracking,
    metadata = null,
)

// State 2: Layout A — success with thumbnail
val layoutAItem = ShareRecordWithMetadata(
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

// State 3: Layout C — success, no thumbnail, has article snippet
val layoutCItem = ShareRecordWithMetadata(
    record = recordClean,
    metadata = LinkMetadata(
        shareRecordId = 2L,
        title = "Jetpack Compose  |  Android Developers",
        thumbnailUrl = null,
        description = "Build better apps faster with Jetpack Compose, Android's modern toolkit for building native UI.",
        articleSnippet = "Jetpack Compose is Android's recommended modern toolkit for building native UI. It simplifies and accelerates UI development on Android. Compose uses a declarative API, which means that all you need to do is describe your UI.",
        contentType = ContentType.ARTICLE,
        fetchStatus = FetchStatus.SUCCESS,
    ),
)

// State 4: Fallback — fetch failed, retry available
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

class HistoryItemPreviewProvider : PreviewParameterProvider<ShareRecordWithMetadata> {
    override val values: Sequence<ShareRecordWithMetadata> = sequenceOf(
        shimmerItem,
        layoutAItem,
        layoutCItem,
        fallbackItem,
    )
}
