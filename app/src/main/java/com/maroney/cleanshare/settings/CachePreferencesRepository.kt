package com.maroney.cleanshare.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.cachePreferencesDataStore by preferencesDataStore(name = "cache_preferences")

/** Preset sizes offered in Settings for the on-disk video streaming cache. */
enum class CacheSizeOption(val bytes: Long, val label: String) {
    MB_250(250L * 1024 * 1024, "250 MB"),
    MB_500(500L * 1024 * 1024, "500 MB"),
    GB_1(1024L * 1024 * 1024, "1 GB"),
    GB_2(2L * 1024L * 1024 * 1024, "2 GB"),
    GB_5(5L * 1024L * 1024 * 1024, "5 GB"),
}

/** Controls the size limit of the video streaming cache (separate from saved-offline videos,
 * which are not subject to this limit — see [com.maroney.cleanshare.media.OfflineVideoRepository]). */
class CachePreferencesRepository(private val context: Context) {

    companion object {
        private val KEY_CACHE_SIZE_BYTES = longPreferencesKey("video_cache_size_bytes")
        val DEFAULT_CACHE_SIZE_BYTES = CacheSizeOption.GB_1.bytes
    }

    val cacheSizeLimitBytes: Flow<Long> = context.cachePreferencesDataStore.data.map { prefs ->
        prefs[KEY_CACHE_SIZE_BYTES] ?: DEFAULT_CACHE_SIZE_BYTES
    }

    suspend fun setCacheSizeLimitBytes(bytes: Long) {
        context.cachePreferencesDataStore.edit { prefs ->
            prefs[KEY_CACHE_SIZE_BYTES] = bytes
        }
    }
}
