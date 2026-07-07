package com.maroney.cleanshare.ui

import com.maroney.cleanshare.data.ShareRecord
import com.maroney.cleanshare.data.ShareRecordWithMetadata
import org.junit.Assert.assertEquals
import org.junit.Test

class TagStatsTest {

    private fun item(id: Long, tags: List<String>) =
        ShareRecordWithMetadata(
            record = ShareRecord(id = id, originalText = "u$id", cleanedText = "u$id", tags = tags),
            metadata = null,
        )

    @Test
    fun `allTags returns distinct tags sorted alphabetically`() {
        val records = listOf(
            item(1, listOf("video", "funny")),
            item(2, listOf("recipe")),
            item(3, listOf("video")),
        )
        assertEquals(listOf("funny", "recipe", "video"), allTags(records))
    }

    @Test
    fun `allTags returns empty list when no entries have tags`() {
        assertEquals(emptyList<String>(), allTags(listOf(item(1, emptyList()))))
    }

    @Test
    fun `topTags orders by usage count descending`() {
        val records = listOf(
            item(1, listOf("video")),
            item(2, listOf("video")),
            item(3, listOf("video")),
            item(4, listOf("recipe")),
            item(5, listOf("recipe")),
            item(6, listOf("funny")),
        )
        assertEquals(listOf("video", "recipe", "funny"), topTags(records))
    }

    @Test
    fun `topTags breaks ties alphabetically`() {
        val records = listOf(item(1, listOf("zebra")), item(2, listOf("apple")))
        assertEquals(listOf("apple", "zebra"), topTags(records))
    }

    @Test
    fun `topTags respects the limit`() {
        val records = listOf(
            item(1, listOf("a")), item(2, listOf("b")), item(3, listOf("c")),
            item(4, listOf("d")), item(5, listOf("e")), item(6, listOf("f")),
        )
        assertEquals(5, topTags(records, limit = 5).size)
    }
}
