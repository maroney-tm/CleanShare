package com.maroney.cleanshare.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class IngestionStatus { QUEUED, EXTRACTING_METADATA, DOWNLOADING, COMPLETE, FAILED }

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
)
