package com.maroney.cleanshare.sync

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CleanShareSyncClientTest {

    private val server = MockWebServer()
    private lateinit var client: CleanShareSyncClient

    @Before
    fun setUp() {
        server.start()
        client = CleanShareSyncClient(OkHttpClient())
        client.configure(server.hostName, server.port)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `health returns true on 200`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        assertTrue(client.health())
    }

    @Test
    fun `health returns false on 500`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        assertFalse(client.health())
    }

    @Test
    fun `health returns false when not configured`() = runTest {
        val unconfigured = CleanShareSyncClient(OkHttpClient())
        assertFalse(unconfigured.health())
    }

    @Test
    fun `clear resets effectiveBaseUrl but preserves lastKnownBaseUrl`() {
        assertNotNull(client.effectiveBaseUrl())
        val configured = client.lastKnownBaseUrl()
        assertNotNull(configured)

        client.clear()

        assertNull("clear() should disable live sync operations", client.effectiveBaseUrl())
        assertFalse(client.isConfigured())
        assertEquals(
            "lastKnownBaseUrl should survive clear() so cached thumbnails can still resolve offline",
            configured,
            client.lastKnownBaseUrl(),
        )
    }

    @Test
    fun `lastKnownBaseUrl is null before the client is ever configured`() {
        val unconfigured = CleanShareSyncClient(OkHttpClient())
        assertNull(unconfigured.lastKnownBaseUrl())
    }

    @Test
    fun `getAllRecords parses list`() = runTest {
        val body = """[
            {"syncId":"u1","originalText":"o","cleanedText":"c","sharedAt":1000,"updatedAt":1000,
             "notes":null,"source":"MOBILE","linkMetadata":null}
        ]"""
        server.enqueue(MockResponse().setBody(body).setResponseCode(200))
        val records = client.getAllRecords()
        assertEquals(1, records.size)
        assertEquals("u1", records[0].syncId)
        assertNull(records[0].notes)
        assertNull(records[0].linkMetadata)
    }

    @Test
    fun `getAllRecords returns empty on server error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        assertTrue(client.getAllRecords().isEmpty())
    }

    @Test
    fun `postRecord sends correct body`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(
            """{"syncId":"u1","originalText":"o","cleanedText":"c","sharedAt":1000,"updatedAt":1000,"notes":null,"source":"MOBILE","linkMetadata":null}"""
        ))
        val record = SyncRecord("u1", "o", "c", 1000L, 1000L, null, "MOBILE", null)
        assertTrue(client.postRecord(record))

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/records", req.path)
        val body = JSONObject(req.body.readUtf8())
        assertEquals("u1", body.getString("syncId"))
        assertEquals("MOBILE", body.getString("source"))
    }

    @Test
    fun `patchRecord sends notes and updatedAt`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"syncId":"u1","originalText":"o","cleanedText":"c","sharedAt":1000,"updatedAt":2000,"notes":"hi","source":"MOBILE","linkMetadata":null}"""
        ))
        assertTrue(client.patchRecord("u1", "hi", 2000L))

        val req = server.takeRequest()
        assertEquals("PATCH", req.method)
        assertEquals("/records/u1", req.path)
        val body = JSONObject(req.body.readUtf8())
        assertEquals("hi", body.getString("notes"))
        assertEquals(2000L, body.getLong("updatedAt"))
    }

    @Test
    fun `deleteRecord sends DELETE`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(client.deleteRecord("u1"))
        val req = server.takeRequest()
        assertEquals("DELETE", req.method)
        assertEquals("/records/u1", req.path)
    }

    @Test
    fun `putMetadata sends correct body`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"syncId":"u1","originalText":"o","cleanedText":"c","sharedAt":1000,"updatedAt":1000,"notes":null,"source":"MOBILE","linkMetadata":{"title":"T","thumbnailUrl":null,"description":null,"articleSnippet":null,"contentType":"ARTICLE","fetchStatus":"SUCCESS"}}"""
        ))
        val meta = SyncLinkMetadata(
            title = "T", thumbnailUrl = null, description = null,
            articleSnippet = null, contentType = "ARTICLE", fetchStatus = "SUCCESS"
        )
        assertTrue(client.putMetadata("u1", meta))

        val req = server.takeRequest()
        assertEquals("PUT", req.method)
        assertEquals("/records/u1/metadata", req.path)
        val body = JSONObject(req.body.readUtf8())
        assertEquals("ARTICLE", body.getString("contentType"))
    }
}
