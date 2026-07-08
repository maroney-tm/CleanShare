package com.maroney.cleanshare.media

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import com.maroney.cleanshare.settings.CachePreferencesRepository
import java.io.File

/**
 * Owns the process-wide disk cache used to opportunistically cache video *streaming* bytes
 * (chunks read during playback), bounded by the user's cache-size setting. This is distinct
 * from — and never used for — videos the user has explicitly saved offline, which are stored
 * in full under [Context.getFilesDir] via [OfflineVideoRepository] and are not subject to
 * eviction here.
 *
 * A [SimpleCache] locks its directory for the process's lifetime, so it's created once and
 * kept as a singleton (via [com.maroney.cleanshare.CleanShareApplication]); the size limit is
 * applied through [ResizableLeastRecentlyUsedCacheEvictor] so changing the setting doesn't
 * require tearing it down.
 */
class VideoCacheManager(context: Context) {

    private val appContext = context.applicationContext

    private val evictor =
        ResizableLeastRecentlyUsedCacheEvictor(CachePreferencesRepository.DEFAULT_CACHE_SIZE_BYTES)

    private val cache: SimpleCache by lazy {
        SimpleCache(
            File(appContext.cacheDir, "video_cache"),
            evictor,
            StandaloneDatabaseProvider(appContext),
        )
    }

    fun setMaxBytes(maxBytes: Long) {
        evictor.maxBytes = maxBytes
    }

    /** Bytes currently occupied by cached streaming data. */
    fun currentCacheUsageBytes(): Long = cache.cacheSpace

    /** Deletes all cached streaming data. Does not touch saved-offline videos. */
    fun clearCache() {
        cache.keys.toList().forEach { key -> cache.removeResource(key) }
    }

    /** A [androidx.media3.datasource.DataSource.Factory] for remote playback only — never
     * pass a local (offline-saved) file URI through this, since a `file://` upstream would
     * still get written straight into the LRU-bounded cache. */
    fun cacheDataSourceFactory(): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
}
