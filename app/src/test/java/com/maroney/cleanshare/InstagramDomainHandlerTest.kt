package com.maroney.cleanshare

import com.maroney.cleanshare.domain.InstagramContentType
import com.maroney.cleanshare.domain.InstagramDomainHandler
import com.maroney.cleanshare.domain.InstagramUrlMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramDomainHandlerTest {

    private val handler = InstagramDomainHandler()

    // ---- matches() ----

    @Test
    fun `matches instagram com`() {
        assertTrue(handler.matches("https://www.instagram.com/reel/ABC123/"))
    }

    @Test
    fun `matches instagram com without www`() {
        assertTrue(handler.matches("https://instagram.com/p/ABC123/"))
    }

    @Test
    fun `does not match youtube`() {
        assertFalse(handler.matches("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun `does not match empty string`() {
        assertFalse(handler.matches(""))
    }

    @Test
    fun `does not match non url`() {
        assertFalse(handler.matches("not-a-url"))
    }

    // ---- extractUrlMetadata() ----

    @Test
    fun `reel url returns REEL with shortcode`() {
        val meta = handler.extractUrlMetadata("https://www.instagram.com/reel/CxYz123/")
        meta as InstagramUrlMetadata
        assertEquals(InstagramContentType.REEL, meta.contentType)
        assertEquals("CxYz123", meta.shortcode)
        assertNull(meta.username)
    }

    @Test
    fun `post url returns POST with shortcode`() {
        val meta = handler.extractUrlMetadata("https://www.instagram.com/p/AbC123/") as InstagramUrlMetadata
        assertEquals(InstagramContentType.POST, meta.contentType)
        assertEquals("AbC123", meta.shortcode)
        assertNull(meta.username)
    }

    @Test
    fun `tv url returns TV with shortcode`() {
        val meta = handler.extractUrlMetadata("https://www.instagram.com/tv/TvXyz/") as InstagramUrlMetadata
        assertEquals(InstagramContentType.TV, meta.contentType)
        assertEquals("TvXyz", meta.shortcode)
    }

    @Test
    fun `stories url returns STORY with username`() {
        val meta = handler.extractUrlMetadata("https://www.instagram.com/stories/natgeo/") as InstagramUrlMetadata
        assertEquals(InstagramContentType.STORY, meta.contentType)
        assertNull(meta.shortcode)
        assertEquals("natgeo", meta.username)
    }

    @Test
    fun `profile url returns PROFILE with username`() {
        val meta = handler.extractUrlMetadata("https://www.instagram.com/natgeo/") as InstagramUrlMetadata
        assertEquals(InstagramContentType.PROFILE, meta.contentType)
        assertNull(meta.shortcode)
        assertEquals("natgeo", meta.username)
    }
}
