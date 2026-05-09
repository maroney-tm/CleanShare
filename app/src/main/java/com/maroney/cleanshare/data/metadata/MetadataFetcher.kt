package com.maroney.cleanshare.data.metadata

import com.maroney.cleanshare.data.ContentType
import com.maroney.cleanshare.data.FetchStatus
import com.maroney.cleanshare.data.LinkMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class MetadataFetcher(private val okHttpClient: OkHttpClient) {

    suspend fun fetch(url: String): LinkMetadata? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return@withContext null
            }
            val body = response.body.use { it.string() }
            parse(body, url)
        } catch (_: Exception) {
            null
        }
    }

    private fun parse(html: String, baseUrl: String): LinkMetadata {
        val doc = Jsoup.parse(html, baseUrl)

        val title = doc.ogContent("og:title")
        val thumbnailUrl = doc.ogContent("og:image")
        val description = doc.ogContent("og:description")
        val ogType = doc.ogContent("og:type") ?: ""

        val contentType = when {
            ogType.startsWith("video") -> ContentType.VIDEO
            ogType == "article" || doc.selectFirst("article") != null -> ContentType.ARTICLE
            else -> ContentType.UNKNOWN
        }

        val articleSnippet = if (contentType == ContentType.ARTICLE) extractSnippet(doc) else null

        return LinkMetadata(
            shareRecordId = 0L,   // caller must set this before upserting
            title = title,
            thumbnailUrl = thumbnailUrl,
            description = description,
            articleSnippet = articleSnippet,
            contentType = contentType,
            fetchStatus = FetchStatus.SUCCESS,
        )
    }

    private fun Document.ogContent(property: String): String? =
        selectFirst("meta[property=$property]")
            ?.attr("content")
            ?.takeIf { it.isNotBlank() }

    private fun extractSnippet(doc: Document): String? {
        val source = doc.selectFirst("article") ?: doc
        return source.select("p")
            .take(3)
            .joinToString(" ") { it.text() }
            .take(300)
            .takeIf { it.isNotBlank() }
    }
}
