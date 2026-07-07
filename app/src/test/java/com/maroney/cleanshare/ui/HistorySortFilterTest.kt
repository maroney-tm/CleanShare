package com.maroney.cleanshare.ui

import com.maroney.cleanshare.data.ShareRecord
import com.maroney.cleanshare.data.ShareRecordWithMetadata
import org.junit.Assert.assertEquals
import org.junit.Test

class HistorySortFilterTest {

    private fun item(id: Long, sharedAt: Long, tags: List<String> = emptyList()) =
        ShareRecordWithMetadata(
            record = ShareRecord(id = id, originalText = "u$id", cleanedText = "u$id", sharedAt = sharedAt, tags = tags),
            metadata = null,
        )

    private val a = item(1, sharedAt = 1_000L, tags = listOf("video"))
    private val b = item(2, sharedAt = 2_000L, tags = listOf("recipe"))
    private val c = item(3, sharedAt = 3_000L, tags = listOf("video", "recipe"))
    private val d = item(4, sharedAt = 4_000L)

    private val all = listOf(a, b, c, d)

    @Test
    fun `newest first sorts by sharedAt descending`() {
        val result = applyHistorySortAndFilter(all, SortOption.NEWEST_FIRST, emptySet())
        assertEquals(listOf(4L, 3L, 2L, 1L), result.map { it.record.id })
    }

    @Test
    fun `oldest first sorts by sharedAt ascending`() {
        val result = applyHistorySortAndFilter(all, SortOption.OLDEST_FIRST, emptySet())
        assertEquals(listOf(1L, 2L, 3L, 4L), result.map { it.record.id })
    }

    @Test
    fun `empty tag filter returns everything`() {
        val result = applyHistorySortAndFilter(all, SortOption.NEWEST_FIRST, emptySet())
        assertEquals(4, result.size)
    }

    @Test
    fun `single tag filter matches only entries with that tag`() {
        val result = applyHistorySortAndFilter(all, SortOption.NEWEST_FIRST, setOf("recipe"))
        assertEquals(listOf(3L, 2L), result.map { it.record.id })
    }

    @Test
    fun `multiple selected tags match ANY of them (OR semantics)`() {
        val result = applyHistorySortAndFilter(all, SortOption.OLDEST_FIRST, setOf("video", "recipe"))
        assertEquals(listOf(1L, 2L, 3L), result.map { it.record.id })
    }

    @Test
    fun `entries with no tags never match a non-empty filter`() {
        val result = applyHistorySortAndFilter(all, SortOption.NEWEST_FIRST, setOf("video", "recipe"))
        assert(result.none { it.record.id == 4L })
    }
}
