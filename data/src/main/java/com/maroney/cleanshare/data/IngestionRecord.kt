package com.maroney.cleanshare.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class IngestionStatus { QUEUED, EXTRACTING_METADATA, DOWNLOADING, COMPLETE, FAILED, FAILED_PERMANENT }

val IngestionStatus.isFailure: Boolean
    get() = this == IngestionStatus.FAILED || this == IngestionStatus.FAILED_PERMANENT

@Entity(tableName = "ingestion_record")
data class IngestionRecord(
    @PrimaryKey val shareRecordId: Long,
    val status: IngestionStatus,
    val errorMessage: String? = null,
    val title: String? = null,
    val uploader: String? = null,
    val uploaderUrl: String? = null,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val uploadDate: String? = null,
    val duration: Int? = null,
    val viewCount: Long? = null,
    val likeCount: Long? = null,
    val tags: String? = null,
    val mediaType: String? = null,
    val serverVideoPath: String? = null,
    // Whether the server currently has a local copy of the thumbnail to serve — distinct
    // from thumbnailUrl being non-null, since thumbnailUrl often doesn't change even once
    // a local copy becomes available (e.g. YouTube's thumbnail URLs are stable, not
    // signed/expiring like Instagram's). The UI keys its image request off this flipping
    // true so Coil actually retries a thumbnail that previously 404'd.
    val thumbnailReady: Boolean = false,
)
