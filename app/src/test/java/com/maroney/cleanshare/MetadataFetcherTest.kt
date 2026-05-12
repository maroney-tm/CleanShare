package com.maroney.cleanshare

import com.maroney.cleanshare.data.ContentType
import com.maroney.cleanshare.data.FetchStatus
import com.maroney.cleanshare.data.metadata.MetadataFetcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MetadataFetcherTest {

    private val server = MockWebServer()
    private val fetcher = MetadataFetcher(OkHttpClient())

    @Before fun setUp() { server.start() }
    @After fun tearDown() { server.shutdown() }

    private fun enqueue(body: String, code: Int = 200) {
        server.enqueue(
            MockResponse()
                .setResponseCode(code)
                .addHeader("Content-Type", "text/html; charset=utf-8")
                .setBody(body)
        )
    }

    @Test fun `parses og tags for video`() = runTest {
        enqueue("""
            <html><head>
            <meta property="og:title" content="My Video"/>
            <meta property="og:image" content="https://example.com/thumb.jpg"/>
            <meta property="og:description" content="A great video"/>
            <meta property="og:type" content="video.other"/>
            </head><body></body></html>
        """.trimIndent())

        val result = fetcher.fetch(server.url("/").toString())

        assertNotNull(result)
        assertEquals("My Video", result!!.title)
        assertEquals("https://example.com/thumb.jpg", result.thumbnailUrl)
        assertEquals("A great video", result.description)
        assertEquals(ContentType.VIDEO, result.contentType)
        assertNull(result.articleSnippet)
        assertEquals(FetchStatus.SUCCESS, result.fetchStatus)
    }

    @Test fun `parses article type and extracts snippet`() = runTest {
        enqueue("""
            <html><head>
            <meta property="og:title" content="Article Title"/>
            <meta property="og:type" content="article"/>
            </head><body>
            <article><p>First paragraph of article text.</p><p>Second paragraph.</p></article>
            </body></html>
        """.trimIndent())

        val result = fetcher.fetch(server.url("/").toString())

        assertNotNull(result)
        assertEquals(ContentType.ARTICLE, result!!.contentType)
        val snippet = result.articleSnippet
        assertNotNull(snippet)
        assertTrue(snippet!!.contains("First paragraph"))
        assertTrue(snippet.length <= 300)
    }

    @Test fun `detects article from article tag when og type is absent`() = runTest {
        enqueue("""
            <html><head><title>No OG</title></head>
            <body><article><p>Body text here.</p></article></body></html>
        """.trimIndent())

        val result = fetcher.fetch(server.url("/").toString())

        assertNotNull(result)
        assertEquals(ContentType.ARTICLE, result!!.contentType)
        assertNotNull(result.articleSnippet)
    }

    @Test fun `falls back to p tags when no article element`() = runTest {
        enqueue("""
            <html><head><meta property="og:type" content="article"/></head>
            <body><p>Lead paragraph text.</p><p>More content.</p></body></html>
        """.trimIndent())

        val result = fetcher.fetch(server.url("/").toString())

        assertNotNull(result)
        assertNotNull(result!!.articleSnippet)
        assertTrue(result.articleSnippet!!.contains("Lead paragraph"))
    }

    @Test fun `returns null on non-200 response`() = runTest {
        enqueue("", 404)
        assertNull(fetcher.fetch(server.url("/").toString()))
    }

    @Test fun `returns null on network error`() = runTest {
        server.shutdown()
        assertNull(fetcher.fetch("http://localhost:1/bad"))
    }

    @Test fun `handles missing og tags - returns result with null fields`() = runTest {
        enqueue("<html><head><title>Plain</title></head><body><p>Text</p></body></html>")
        val result = fetcher.fetch(server.url("/").toString())
        assertNotNull(result)
        assertNull(result!!.title)
        assertNull(result.thumbnailUrl)
        assertEquals(ContentType.UNKNOWN, result.contentType)
    }

    @Test fun `snippet is capped at 300 chars`() = runTest {
        val longText = "word ".repeat(200)
        enqueue("""
            <html><head><meta property="og:type" content="article"/></head>
            <body><article><p>$longText</p></article></body></html>
        """.trimIndent())

        val result = fetcher.fetch(server.url("/").toString())
        assertNotNull(result!!.articleSnippet)
        assertTrue(result.articleSnippet!!.length <= 300)
    }
}
