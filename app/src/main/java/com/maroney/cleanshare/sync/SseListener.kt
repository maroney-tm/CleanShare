package com.maroney.cleanshare.sync

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Opens a persistent GET /events connection and delivers (eventType, dataJson)
 * pairs to [onEvent]. Call [start] to begin streaming; [stop] to cancel.
 *
 * SSE format expected:
 *   event: record_created
 *   data: {...}
 *   (blank line)
 *
 * The [ioDispatcher] parameter exists to allow tests to inject a test dispatcher.
 */
class SseListener(
    private val okHttpClient: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onEvent: suspend (type: String, data: String) -> Unit,
) {
    private var job: Job? = null

    fun start(baseUrl: String, scope: CoroutineScope) {
        job = scope.launch(ioDispatcher) {
            val request = Request.Builder().url("$baseUrl/events").build()
            try {
                okHttpClient.newCall(request).execute().use { response ->
                    val source = response.body?.source() ?: return@launch
                    var eventType = ""
                    var dataLine  = ""
                    while (isActive && !source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        when {
                            line.startsWith("event: ") -> eventType = line.removePrefix("event: ")
                            line.startsWith("data: ")  -> dataLine  = line.removePrefix("data: ")
                            line.isEmpty() && eventType.isNotEmpty() -> {
                                onEvent(eventType, dataLine)
                                eventType = ""
                                dataLine  = ""
                            }
                            // Lines starting with ':' are SSE comments — ignore.
                        }
                    }
                }
            } catch (_: Exception) {
                // Connection dropped — caller (SyncManager) can restart on next resume.
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
