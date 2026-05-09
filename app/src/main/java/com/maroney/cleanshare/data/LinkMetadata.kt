package com.maroney.cleanshare.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ContentType { VIDEO, ARTICLE, UNKNOWN }
enum class FetchStatus { SUCCESS, FAILED }

@Entity(tableName = "link_metadata")
data class LinkMetadata(
    @PrimaryKey val shareRecordId: Long,
    val title: String?,
    val thumbnailUrl: String?,
    val description: String?,
    val articleSnippet: String?,
    val contentType: ContentType,
    val fetchStatus: FetchStatus,
)
