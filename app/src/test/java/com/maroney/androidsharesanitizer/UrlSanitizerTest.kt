package com.maroney.androidsharesanitizer

import com.maroney.androidsharesanitizer.domain.UrlSanitizer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [UrlSanitizer.clean].
 *
 * Cases 1-12 are mandated by the spec (§T3 test matrix).
 * Cases 13-15 are additional cases covering repeated keys,
 * URL-encoded values, and multiple HubSpot params.
 */
class UrlSanitizerTest {

    // ── Spec test matrix (cases 1–12) ────────────────────────────────────────

    @Test
    fun `1 - strips si, preserves t on youtu-be`() {
        assertEquals(
            "https://youtu.be/abc123?t=45",
            UrlSanitizer.clean("https://youtu.be/abc123?si=xyz&t=45")
        )
    }

    @Test
    fun `2 - strips feature, preserves v and t on youtube watch`() {
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=10",
            UrlSanitizer.clean(
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ&feature=share&t=10"
            )
        )
    }

    @Test
    fun `3 - strips igshid, drops trailing question mark`() {
        assertEquals(
            "https://www.instagram.com/reel/CxYz/",
            UrlSanitizer.clean("https://www.instagram.com/reel/CxYz/?igshid=abc123")
        )
    }

    @Test
    fun `4 - strips utm_source and utm_medium, preserves id`() {
        assertEquals(
            "https://example.com/page?id=42",
            UrlSanitizer.clean(
                "https://example.com/page?utm_source=newsletter&utm_medium=email&id=42"
            )
        )
    }

    @Test
    fun `5 - utm keys are matched case-insensitively`() {
        assertEquals(
            "https://example.com/page?id=42",
            UrlSanitizer.clean("https://example.com/page?UTM_SOURCE=NL&id=42")
        )
    }

    @Test
    fun `6 - strips fbclid, result has no query string`() {
        assertEquals(
            "https://example.com/page",
            UrlSanitizer.clean("https://example.com/page?fbclid=xyz")
        )
    }

    @Test
    fun `7 - URL with no query params is returned unchanged`() {
        assertEquals(
            "https://example.com/page",
            UrlSanitizer.clean("https://example.com/page")
        )
    }

    @Test
    fun `8 - non-URL string is returned unchanged`() {
        assertEquals("not a url", UrlSanitizer.clean("not a url"))
    }

    @Test
    fun `9 - empty string is returned unchanged`() {
        assertEquals("", UrlSanitizer.clean(""))
    }

    @Test
    fun `10 - fragment is preserved when tracking param is stripped`() {
        assertEquals(
            "https://example.com/p#section-2",
            UrlSanitizer.clean("https://example.com/p?utm_source=x#section-2")
        )
    }

    @Test
    fun `11 - strips utm_source in the middle, preserves surrounding params`() {
        assertEquals(
            "https://example.com/p?a=1&b=2",
            UrlSanitizer.clean("https://example.com/p?a=1&utm_source=x&b=2")
        )
    }

    @Test
    fun `12 - port is preserved, si stripped, other param kept`() {
        assertEquals(
            "https://example.com:8443/p?q=ok",
            UrlSanitizer.clean("https://example.com:8443/p?si=x&q=ok")
        )
    }

    // ── Additional cases (13–15) ──────────────────────────────────────────────

    @Test
    fun `13 - repeated non-tracking keys are all preserved`() {
        // ?a=1&a=2 must both survive; only utm_source is stripped
        assertEquals(
            "https://example.com/p?a=1&a=2",
            UrlSanitizer.clean("https://example.com/p?a=1&a=2&utm_source=x")
        )
    }

    @Test
    fun `14 - URL-encoded values are preserved verbatim`() {
        // si is stripped; q with encoded value is kept unchanged
        assertEquals(
            "https://example.com/p?q=test%3D1",
            UrlSanitizer.clean("https://example.com/p?si=hello%20world&q=test%3D1")
        )
    }

    @Test
    fun `15 - multiple hubspot params stripped, lone content param preserved`() {
        assertEquals(
            "https://example.com/p?content=hello",
            UrlSanitizer.clean(
                "https://example.com/p?__hstc=123&__hssc=456&content=hello"
            )
        )
    }
}
