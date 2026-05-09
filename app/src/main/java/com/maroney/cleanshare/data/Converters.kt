package com.maroney.cleanshare.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter fun fromContentType(v: ContentType): String = v.name
    @TypeConverter fun toContentType(v: String): ContentType =
        ContentType.entries.firstOrNull { it.name == v } ?: ContentType.UNKNOWN
    @TypeConverter fun fromFetchStatus(v: FetchStatus): String = v.name
    @TypeConverter fun toFetchStatus(v: String): FetchStatus =
        FetchStatus.entries.firstOrNull { it.name == v } ?: FetchStatus.FAILED
}
