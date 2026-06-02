package com.maroney.cleanshare.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter fun fromContentType(v: ContentType): String = v.name
    @TypeConverter fun toContentType(v: String): ContentType =
        ContentType.entries.firstOrNull { it.name == v } ?: ContentType.UNKNOWN

    @TypeConverter fun fromFetchStatus(v: FetchStatus): String = v.name
    @TypeConverter fun toFetchStatus(v: String): FetchStatus =
        FetchStatus.entries.firstOrNull { it.name == v } ?: FetchStatus.FAILED

    @TypeConverter fun fromShareSource(v: ShareSource): String = v.name
    @TypeConverter fun toShareSource(v: String): ShareSource =
        ShareSource.entries.firstOrNull { it.name == v } ?: ShareSource.MOBILE

    @TypeConverter fun fromIngestionStatus(v: IngestionStatus): String = v.name
    @TypeConverter fun toIngestionStatus(v: String): IngestionStatus =
        IngestionStatus.entries.firstOrNull { it.name == v } ?: IngestionStatus.FAILED
}
