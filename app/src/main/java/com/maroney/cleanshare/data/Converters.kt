package com.maroney.cleanshare.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter fun fromContentType(v: ContentType): String = v.name
    @TypeConverter fun toContentType(v: String): ContentType = ContentType.valueOf(v)
    @TypeConverter fun fromFetchStatus(v: FetchStatus): String = v.name
    @TypeConverter fun toFetchStatus(v: String): FetchStatus = FetchStatus.valueOf(v)
}
