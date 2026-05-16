@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.maroney.cleanshare.sync

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SseListenerTest {

    private val server = MockWebServer()
    private val client = OkHttpClient()

    @Before fun setUp() = server.start()
    @After  fun tearDown() = server.shutdown()

    private fun sseBody(vararg events: Pair<String, String>): MockResponse {
        val buf = Buffer()
        for ((type, data) in events) {
            buf.writeUtf8("event: $type\ndata: $data\n\n")
        }
        return MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "text/event-stream")
            .setBody(buf)
    }

    @Test
    fun `parses record_created event`() = runTest {
        server.enqueue(sseBody("record_created" to """{"syncId":"u1"}"""))

        val received = mutableListOf<Pair<String, String>>()
        val listener = SseListener(client, UnconfinedTestDispatcher(testScheduler)) { type, data ->
            received.add(type to data)
        }
        val baseUrl = "http://${server.hostName}:${server.port}"

        listener.start(baseUrl, this)
        delay(300)
        listener.stop()

        assertEquals(1, received.size)
        assertEquals("record_created", received[0].first)
        assertTrue(received[0].second.contains("u1"))
    }

    @Test
    fun `ignores comment lines`() = runTest {
        val buf = Buffer().apply {
            writeUtf8(": ping\n\n")
            writeUtf8("event: record_deleted\ndata: {\"syncId\":\"u2\"}\n\n")
        }
        server.enqueue(
            MockResponse().setResponseCode(200)
                .addHeader("Content-Type", "text/event-stream")
                .setBody(buf)
        )

        val received = mutableListOf<String>()
        val listener = SseListener(client, UnconfinedTestDispatcher(testScheduler)) { type, _ ->
            received.add(type)
        }
        listener.start("http://${server.hostName}:${server.port}", this)
        delay(300)
        listener.stop()

        assertEquals(listOf("record_deleted"), received)
    }
}
