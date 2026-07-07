package com.maroney.cleanshare.media

import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import java.util.TreeSet

/**
 * Evicts least-recently-used spans once the cache exceeds [maxBytes], same policy as
 * media3's own `LeastRecentlyUsedCacheEvictor` — except that class is `final` and takes its
 * limit as a constructor-only value, so it can't track a user-adjustable settings value without
 * tearing down and recreating the whole (locked, singleton-per-process) [androidx.media3.datasource.cache.SimpleCache].
 * [maxBytes] here can be updated directly and takes effect on the next span write.
 */
class ResizableLeastRecentlyUsedCacheEvictor(@Volatile var maxBytes: Long) : CacheEvictor {

    // Ordered by lastTouchTimestamp (ties broken by CacheSpan's own comparison) so the
    // least-recently-used span is always first.
    private val leastRecentlyUsed = TreeSet<CacheSpan> { a, b ->
        val byTime = a.lastTouchTimestamp.compareTo(b.lastTouchTimestamp)
        if (byTime != 0) byTime else a.compareTo(b)
    }

    override fun requiresCacheSpanTouches(): Boolean = true

    override fun onCacheInitialized() {}

    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        evictUntilBelowLimit(cache, length)
    }

    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        leastRecentlyUsed.add(span)
        evictUntilBelowLimit(cache, 0)
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        leastRecentlyUsed.remove(span)
    }

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        leastRecentlyUsed.remove(oldSpan)
        leastRecentlyUsed.add(newSpan)
    }

    private fun evictUntilBelowLimit(cache: Cache, requiredFreeSpace: Long) {
        while (cache.cacheSpace + requiredFreeSpace > maxBytes && !leastRecentlyUsed.isEmpty()) {
            cache.removeSpan(leastRecentlyUsed.first())
        }
    }
}
