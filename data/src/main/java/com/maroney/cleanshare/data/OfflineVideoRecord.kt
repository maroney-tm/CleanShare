package com.maroney.cleanshare.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class OfflineVideoStatus { DOWNLOADING, COMPLETE, FAILED }

/** Tracks a video the user explicitly saved for offline viewing — a full local copy kept
 * outside the app's streaming cache, so it is never subject to the cache's size limit or LRU
 * eviction. */
@Entity(tableName = "offline_video")
data class OfflineVideoRecord(
    @PrimaryKey val shareRecordId: Long,
    val status: OfflineVideoStatus,
    val localFilePath: String? = null,
    val fileSizeBytes: Long = 0,
    val savedAt: Long = System.currentTimeMillis(),
    val errorMessage: String? = null,
)
