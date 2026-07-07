package com.maroney.cleanshare.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

// ---- Sync-layer data types (separate from Room entities) ----

data class SyncRecord(
    val syncId: String,
    val originalText: String,
    val cleanedText: String,
    val sharedAt: Long,
    val updatedAt: Long,
    val notes: String?,
    val source: String,
    val linkMetadata: SyncLinkMetadata?,
    val ingestion: SyncIngestionRecord? = null,
)

data class SyncLinkMetadata(
    val title: String?,
    val thumbnailUrl: String?,
    val description: String?,
    val articleSnippet: String?,
    val contentType: String,
    val fetchStatus: String,
)

data class SyncIngestionRecord(
    val status: String,
    val errorMessage: String?,
    val title: String?,
    val uploader: String?,
    val uploaderUrl: String?,
    val description: String?,
    val thumbnailUrl: String?,
    val uploadDate: String?,
    val duration: Int?,
    val viewCount: Long?,
    val likeCount: Long?,
    val tags: String?,
    val mediaType: String?,
    val thumbnailReady: Boolean,
)

// ---- REST client ----

class CleanShareSyncClient(private val okHttpClient: OkHttpClient) {

    @Volatile private var baseUrl: String? = null

    fun configure(host: String, port: Int?) {
        val portSuffix = if (port != null && port != 80) ":$port" else ""
        baseUrl = "http://$host$portSuffix"
    }
    fun clear() { baseUrl = null }
    fun isConfigured(): Boolean = baseUrl != null
    fun effectiveBaseUrl(): String? = baseUrl

    suspend fun health(): Boolean = withContext(Dispatchers.IO) {
        val url = baseUrl ?: return@withContext false
        try {
            Timber.d("Checking health at $url/health")
            okHttpClient.newCall(Request.Builder().url("$url/health").build())
                .execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Timber.e(e, "Health check failed")
            false
        }
    }

    suspend fun getAllRecords(): List<SyncRecord> = withContext(Dispatchers.IO) {
        val url = baseUrl ?: return@withContext emptyList()
        try {
            okHttpClient.newCall(Request.Builder().url("$url/records").build())
                .execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext emptyList()
                    parseRecordList(resp.body.string())
                }
        } catch (e: Exception) { Timber.e(e, "Failed to fetch records"); emptyList() }
    }

    suspend fun postRecord(record: SyncRecord): Boolean = withContext(Dispatchers.IO) {
        val url = baseUrl ?: return@withContext false
        try {
            val body = buildRecordJson(record).toRequestBody(JSON_MT)
            okHttpClient.newCall(Request.Builder().url("$url/records").post(body).build())
                .execute().use { it.isSuccessful }
        } catch (e: Exception) { Timber.e(e, "Failed to post record"); false }
    }

    suspend fun patchRecord(syncId: String, notes: String?, updatedAt: Long): Boolean =
        withContext(Dispatchers.IO) {
            val url = baseUrl ?: return@withContext false
            try {
                val json = JSONObject().apply {
                    if (notes != null) put("notes", notes) else put("notes", JSONObject.NULL)
                    put("updatedAt", updatedAt)
                }.toString().toRequestBody(JSON_MT)
                okHttpClient.newCall(
                    Request.Builder().url("$url/records/$syncId").patch(json).build()
                ).execute().use { it.isSuccessful }
            } catch (e: Exception) { Timber.e(e, "Failed to patch record"); false }
        }

    suspend fun deleteRecord(syncId: String): Boolean = withContext(Dispatchers.IO) {
        val url = baseUrl ?: return@withContext false
        try {
            okHttpClient.newCall(
                Request.Builder().url("$url/records/$syncId").delete().build()
            ).execute().use { it.isSuccessful }
        } catch (e: Exception) { Timber.e(e, "Failed to delete record"); false }
    }

    suspend fun putMetadata(syncId: String, meta: SyncLinkMetadata): Boolean =
        withContext(Dispatchers.IO) {
            val url = baseUrl ?: return@withContext false
            try {
                val json = buildMetadataJson(meta).toRequestBody(JSON_MT)
                okHttpClient.newCall(
                    Request.Builder().url("$url/records/$syncId/metadata").put(json).build()
                ).execute().use { it.isSuccessful }
            } catch (e: Exception) { Timber.e(e, "Failed to put metadata"); false }
        }

    suspend fun retryIngestion(syncId: String): Boolean = withContext(Dispatchers.IO) {
        val url = baseUrl ?: return@withContext false
        try {
            val body = "{}".toRequestBody(JSON_MT)
            okHttpClient.newCall(
                Request.Builder().url("$url/records/$syncId/retry").post(body).build()
            ).execute().use { it.isSuccessful }
        } catch (e: Exception) { Timber.e(e, "Failed to retry ingestion"); false }
    }

    // ---- JSON helpers ----

    private fun parseRecordList(json: String): List<SyncRecord> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { parseRecord(arr.getJSONObject(it)) }
    }

    private fun parseRecord(obj: JSONObject): SyncRecord {
        val meta = if (!obj.isNull("linkMetadata"))
            parseMetadata(obj.getJSONObject("linkMetadata")) else null
        val ingestion = if (!obj.isNull("ingestion") && obj.has("ingestion"))
            parseIngestion(obj.getJSONObject("ingestion")) else null
        return SyncRecord(
            syncId       = obj.getString("syncId"),
            originalText = obj.getString("originalText"),
            cleanedText  = obj.getString("cleanedText"),
            sharedAt     = obj.getLong("sharedAt"),
            updatedAt    = obj.getLong("updatedAt"),
            notes        = if (obj.isNull("notes")) null else obj.getString("notes"),
            source       = obj.getString("source"),
            linkMetadata = meta,
            ingestion    = ingestion,
        )
    }

    private fun parseIngestion(obj: JSONObject) = SyncIngestionRecord(
        status       = obj.getString("status"),
        errorMessage = obj.nullableString("errorMessage"),
        title        = obj.nullableString("title"),
        uploader     = obj.nullableString("uploader"),
        uploaderUrl  = obj.nullableString("uploaderUrl"),
        description  = obj.nullableString("description"),
        thumbnailUrl = obj.nullableString("thumbnailUrl"),
        uploadDate   = obj.nullableString("uploadDate"),
        duration     = if (!obj.has("duration") || obj.isNull("duration")) null else obj.getInt("duration"),
        viewCount    = if (!obj.has("viewCount") || obj.isNull("viewCount")) null else obj.getLong("viewCount"),
        likeCount    = if (!obj.has("likeCount") || obj.isNull("likeCount")) null else obj.getLong("likeCount"),
        tags         = obj.nullableString("tags"),
        mediaType    = obj.nullableString("mediaType"),
        thumbnailReady = obj.optBoolean("thumbnailReady", false),
    )

    private fun parseMetadata(obj: JSONObject) = SyncLinkMetadata(
        title          = obj.nullableString("title"),
        thumbnailUrl   = obj.nullableString("thumbnailUrl"),
        description    = obj.nullableString("description"),
        articleSnippet = obj.nullableString("articleSnippet"),
        contentType    = obj.getString("contentType"),
        fetchStatus    = obj.getString("fetchStatus"),
    )

    private fun buildRecordJson(r: SyncRecord) = JSONObject().apply {
        put("syncId",       r.syncId)
        put("originalText", r.originalText)
        put("cleanedText",  r.cleanedText)
        put("sharedAt",     r.sharedAt)
        put("updatedAt",    r.updatedAt)
        if (r.notes != null) put("notes", r.notes) else put("notes", JSONObject.NULL)
        put("source",       r.source)
    }.toString()

    private fun buildMetadataJson(m: SyncLinkMetadata) = JSONObject().apply {
        putNullable("title",          m.title)
        putNullable("thumbnailUrl",   m.thumbnailUrl)
        putNullable("description",    m.description)
        putNullable("articleSnippet", m.articleSnippet)
        put("contentType",  m.contentType)
        put("fetchStatus",  m.fetchStatus)
    }.toString()

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else getString(key)

    private fun JSONObject.putNullable(key: String, value: String?) {
        if (value != null) put(key, value) else put(key, JSONObject.NULL)
    }

    companion object {
        private val JSON_MT = "application/json".toMediaType()
    }
}
