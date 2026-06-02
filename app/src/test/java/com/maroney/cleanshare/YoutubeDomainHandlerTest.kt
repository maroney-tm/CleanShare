package com.maroney.cleanshare

import com.maroney.cleanshare.domain.YoutubeContentType
import com.maroney.cleanshare.domain.YoutubeDomainHandler
import com.maroney.cleanshare.domain.YoutubeUrlMetadata
import com.maroney.cleanshare.domain.formatCount
import com.maroney.cleanshare.domain.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubeDomainHandlerTest {

    private val handler = YoutubeDomainHandler()

    // ---- matches() ----

    @Test fun `matches youtube com`() =
        assertTrue(handler.matches("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))

    @Test fun `matches youtube com without www`() =
        assertTrue(handler.matches("https://youtube.com/watch?v=dQw4w9WgXcQ"))

    @Test fun `matches youtu be short link`() =
        assertTrue(handler.matches("https://youtu.be/dQw4w9WgXcQ"))

    @Test fun `matches mobile youtube`() =
        assertTrue(handler.matches("https://m.youtube.com/watch?v=dQw4w9WgXcQ"))

    @Test fun `does not match instagram`() =
        assertFalse(handler.matches("https://www.instagram.com/reel/ABC123/"))

    @Test fun `does not match empty string`() =
        assertFalse(handler.matches(""))

    @Test fun `does not match non url`() =
        assertFalse(handler.matches("not-a-url"))

    // ---- extractUrlMetadata() ----

    @Test fun `watch url returns VIDEO with videoId`() {
        val meta = handler.extractUrlMetadata("https://www.youtube.com/watch?v=dQw4w9WgXcQ") as YoutubeUrlMetadata
        assertEquals(YoutubeContentType.VIDEO, meta.contentType)
        assertEquals("dQw4w9WgXcQ", meta.videoId)
        assertNull(meta.channelHandle)
        assertNull(meta.playlistId)
    }

    @Test fun `youtu be url returns VIDEO with videoId`() {
        val meta = handler.extractUrlMetadata("https://youtu.be/dQw4w9WgXcQ") as YoutubeUrlMetadata
        assertEquals(YoutubeContentType.VIDEO, meta.contentType)
        assertEquals("dQw4w9WgXcQ", meta.videoId)
    }

    @Test fun `shorts url returns SHORT with videoId`() {
        val meta = handler.extractUrlMetadata("https://www.youtube.com/shorts/AbCdEfGhIjK") as YoutubeUrlMetadata
        assertEquals(YoutubeContentType.SHORT, meta.contentType)
        assertEquals("AbCdEfGhIjK", meta.videoId)
    }

    @Test fun `playlist url returns PLAYLIST with playlistId`() {
        val meta = handler.extractUrlMetadata("https://www.youtube.com/playlist?list=PLxyz123") as YoutubeUrlMetadata
        assertEquals(YoutubeContentType.PLAYLIST, meta.contentType)
        assertNull(meta.videoId)
        assertEquals("PLxyz123", meta.playlistId)
    }

    @Test fun `channel url returns CHANNEL with handle`() {
        val meta = handler.extractUrlMetadata("https://www.youtube.com/channel/UCxyz") as YoutubeUrlMetadata
        assertEquals(YoutubeContentType.CHANNEL, meta.contentType)
        assertEquals("UCxyz", meta.channelHandle)
        assertNull(meta.videoId)
    }

    @Test fun `at-handle url returns CHANNEL with handle`() {
        val meta = handler.extractUrlMetadata("https://www.youtube.com/@RickAstleyYT") as YoutubeUrlMetadata
        assertEquals(YoutubeContentType.CHANNEL, meta.contentType)
        assertEquals("@RickAstleyYT", meta.channelHandle)
        assertNull(meta.videoId)
    }

    @Test fun `watch url with extra params still extracts videoId`() {
        val meta = handler.extractUrlMetadata(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42s&feature=share"
        ) as YoutubeUrlMetadata
        assertEquals("dQw4w9WgXcQ", meta.videoId)
    }

    // ---- formatDuration() ----

    @Test fun `formatDuration under one hour`() = assertEquals("3:45", formatDuration(225))
    @Test fun `formatDuration exactly one minute`() = assertEquals("1:00", formatDuration(60))
    @Test fun `formatDuration zero seconds`() = assertEquals("0:00", formatDuration(0))
    @Test fun `formatDuration over one hour`() = assertEquals("1:23:45", formatDuration(5025))
    @Test fun `formatDuration pads seconds`() = assertEquals("0:09", formatDuration(9))

    // ---- formatCount() ----

    @Test fun `formatCount under thousand`() = assertEquals("999", formatCount(999))
    @Test fun `formatCount thousands`() = assertEquals("1.2K", formatCount(1200))
    @Test fun `formatCount millions`() = assertEquals("1.2M", formatCount(1_200_000))
    @Test fun `formatCount exactly one million`() = assertEquals("1.0M", formatCount(1_000_000))
}
