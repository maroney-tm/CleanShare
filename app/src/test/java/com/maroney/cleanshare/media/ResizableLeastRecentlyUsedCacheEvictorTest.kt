package com.maroney.cleanshare.media

import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.ContentMetadataMutations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Minimal fake standing in for SimpleCache: tracks total bytes and lets the evictor remove
 * spans, without touching the filesystem or a real cache database. */
private class FakeCache : Cache {
    val spans = mutableListOf<CacheSpan>()

    override fun getCacheSpace(): Long = spans.sumOf { it.length }

    override fun removeSpan(span: CacheSpan) {
        spans.remove(span)
    }

    override fun getUid(): Long = 0
    override fun release() {}
    override fun addListener(key: String, listener: Cache.Listener) = sortedSetOf<CacheSpan>()
    override fun removeListener(key: String, listener: Cache.Listener) {}
    override fun getCachedSpans(key: String) = sortedSetOf<CacheSpan>()
    override fun getKeys(): Set<String> = emptySet()
    override fun startReadWrite(key: String, position: Long, length: Long): CacheSpan = throw NotImplementedError()
    override fun startReadWriteNonBlocking(key: String, position: Long, length: Long): CacheSpan? = null
    override fun startFile(key: String, position: Long, length: Long): File = throw NotImplementedError()
    override fun commitFile(file: File, length: Long) {}
    override fun releaseHoleSpan(span: CacheSpan) {}
    override fun removeResource(key: String) {}
    override fun isCached(key: String, position: Long, length: Long): Boolean = false
    override fun getCachedLength(key: String, position: Long, length: Long): Long = 0
    override fun getCachedBytes(key: String, position: Long, length: Long): Long = 0
    override fun applyContentMetadataMutations(key: String, mutations: ContentMetadataMutations) {}
    override fun getContentMetadata(key: String): ContentMetadata = throw NotImplementedError()
}

private fun span(key: String, length: Long, lastTouchTimestamp: Long): CacheSpan =
    CacheSpan(key, 0L, length, lastTouchTimestamp, File("/fake/$key"))

class ResizableLeastRecentlyUsedCacheEvictorTest {

    @Test
    fun `evicts oldest span once over the limit`() {
        val cache = FakeCache()
        val evictor = ResizableLeastRecentlyUsedCacheEvictor(maxBytes = 100)

        val oldest = span("a", length = 60, lastTouchTimestamp = 1)
        cache.spans.add(oldest)
        evictor.onSpanAdded(cache, oldest)

        val newest = span("b", length = 60, lastTouchTimestamp = 2)
        cache.spans.add(newest)
        evictor.onSpanAdded(cache, newest)

        // 120 bytes cached against a 100-byte limit: the least-recently-touched span (a) must go.
        assertFalse(cache.spans.contains(oldest))
        assertTrue(cache.spans.contains(newest))
    }

    @Test
    fun `raising maxBytes stops further eviction`() {
        val cache = FakeCache()
        val evictor = ResizableLeastRecentlyUsedCacheEvictor(maxBytes = 100)

        val a = span("a", length = 90, lastTouchTimestamp = 1)
        cache.spans.add(a)
        evictor.onSpanAdded(cache, a)
        assertTrue(cache.spans.contains(a))

        evictor.maxBytes = 1_000

        val b = span("b", length = 90, lastTouchTimestamp = 2)
        cache.spans.add(b)
        evictor.onSpanAdded(cache, b)

        // With the higher limit both spans fit — no eviction.
        assertTrue(cache.spans.contains(a))
        assertTrue(cache.spans.contains(b))
        assertEquals(180L, cache.cacheSpace)
    }

    @Test
    fun `lowering maxBytes evicts on the next write`() {
        val cache = FakeCache()
        val evictor = ResizableLeastRecentlyUsedCacheEvictor(maxBytes = 1_000)

        val a = span("a", length = 90, lastTouchTimestamp = 1)
        cache.spans.add(a)
        evictor.onSpanAdded(cache, a)

        evictor.maxBytes = 50
        // Eviction only happens on the next cache write (onStartFile/onSpanAdded), matching
        // media3's own evictor contract — it doesn't proactively scan on a bare setter.
        assertTrue(cache.spans.contains(a))

        evictor.onStartFile(cache, "b", 0L, 10L)
        assertFalse(cache.spans.contains(a))
    }
}
