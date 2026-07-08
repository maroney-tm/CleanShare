package com.maroney.cleanshare.sync

import android.content.Context
import com.maroney.cleanshare.data.ContentType
import com.maroney.cleanshare.data.FetchStatus
import com.maroney.cleanshare.data.IngestionDao
import com.maroney.cleanshare.data.IngestionRecord
import com.maroney.cleanshare.data.IngestionStatus
import com.maroney.cleanshare.data.LinkMetadata
import com.maroney.cleanshare.data.LinkMetadataDao
import com.maroney.cleanshare.data.ShareDao
import com.maroney.cleanshare.data.ShareRecord
import com.maroney.cleanshare.data.ShareSource
import com.maroney.cleanshare.data.SyncPusher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber



sealed class ConnectionStatus {
    data object Disconnected : ConnectionStatus()
    data object Searching    : ConnectionStatus()
    data class  Connected(val host: String, val port: Int?) : ConnectionStatus()
}

class SyncManager(
    private val context: Context,
    private val syncClient: CleanShareSyncClient,
    private val configRepo: ServerConfigRepository,
    private val shareDao: ShareDao,
    private val metadataDao: LinkMetadataDao,
    private val ingestionDao: IngestionDao? = null,
) : SyncPusher {
    private val _status = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val connectionStatus: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private var sseListener: SseListener? = null

    // SyncManager is a process-wide singleton, so the SSE listening job's lifetime
    // should be too — not borrowed from whichever caller happens to invoke
    // startListening() (a ViewModel's viewModelScope would cancel the connection the
    // moment that particular screen is torn down, e.g. leaving Settings after first
    // configuring a server).
    private val listenerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ---- Discovery + connection ----

    /**
     * Connects to the manually configured server, verifies with /health, and syncs.
     * Returns true if a live server was found.
     */
    suspend fun resolveAndSync(): Boolean = withContext(Dispatchers.IO) {
        _status.value = ConnectionStatus.Searching
        val config = configRepo.config.first()
        val host = config.manualHost

        if (host == null) {
            _status.value = ConnectionStatus.Disconnected
            syncClient.clear()
            return@withContext false
        }

        syncClient.configure(host, config.port)

        if (!syncClient.health()) {
            syncClient.clear()
            _status.value = ConnectionStatus.Disconnected
            return@withContext false
        }

        configRepo.setLastSeenAt(System.currentTimeMillis())
        _status.value = ConnectionStatus.Connected(host, config.port)
        fullSync()
        true
    }

    // ---- Full pull sync ----

    /**
     * Bidirectional sync: pulls server records locally (LWW) and pushes local records
     * that are absent from the server (e.g. created before sync was configured).
     * Deletions propagate via SSE only; this method never deletes local records.
     */
    suspend fun fullSync() = withContext(Dispatchers.IO) {
        val serverRecords = syncClient.getAllRecords()
        val serverSyncIds = serverRecords.map { it.syncId }.toSet()

        // Pull: bring server records down (LWW on notes/tags/updatedAt; server always wins for ingestion).
        for (sr in serverRecords) {
            val local = shareDao.getBySyncId(sr.syncId)
            if (local == null) {
                val id = shareDao.insert(sr.toShareRecord())
                sr.linkMetadata?.let { metadataDao.upsert(it.toLinkMetadata(id)) }
                sr.ingestion?.let { ingestionDao?.upsert(it.toIngestionRecord(id)) }
            } else {
                if (sr.updatedAt > local.updatedAt) {
                    shareDao.updateNotesAndTagsAndTimestamp(local.id, sr.notes, sr.tags, sr.updatedAt)
                    Timber.d("Pulled record ${sr.syncId} from server (tags=${sr.tags.size})")
                    sr.linkMetadata?.let { metadataDao.upsert(it.toLinkMetadata(local.id)) }
                }
                sr.ingestion?.let { serverIng ->
                    val dao = ingestionDao ?: return@let
                    if (serverIng.title != null || serverIng.thumbnailUrl != null) {
                        dao.upsert(serverIng.toIngestionRecord(local.id))
                    } else {
                        val status = IngestionStatus.entries.firstOrNull { it.name == serverIng.status }
                            ?: IngestionStatus.QUEUED
                        dao.updateStatusOnly(local.id, status, serverIng.errorMessage)
                    }
                }
            }
        }

        // Push: send local records the server doesn't have yet (e.g. created before sync was set up).
        val localRecords = shareDao.getAllOnce()
        for (local in localRecords) {
            if (local.syncId !in serverSyncIds) {
                syncClient.postRecord(local.toSyncRecord())
                val meta = metadataDao.getByShareRecordIdOnce(local.id)
                if (meta != null && meta.fetchStatus == FetchStatus.SUCCESS) {
                    syncClient.putMetadata(local.syncId, meta.toSyncLinkMetadata())
                }
            }
        }
    }

    // ---- SSE ----

    fun startListening() {
        if (!syncClient.isConfigured()) return
        if (sseListener != null) return  // already listening — don't start a second SSE connection
        val baseUrl = syncClient.effectiveBaseUrl() ?: return

        sseListener = SseListener(buildOkHttp()) { type, data ->
            handleSseEvent(type, data)
        }
        sseListener?.start(baseUrl, listenerScope)
    }

    fun stopListening() {
        sseListener?.stop()
        sseListener = null
    }

    private suspend fun handleSseEvent(type: String, data: String) = withContext(Dispatchers.IO) {
        try {
            val obj = org.json.JSONObject(data)
            when (type) {
                "record_created", "record_updated" -> {
                    val sr = parseSyncRecord(obj)
                    val local = shareDao.getBySyncId(sr.syncId)
                    if (local == null) {
                        val id = shareDao.insert(sr.toShareRecord())
                        sr.linkMetadata?.let { metadataDao.upsert(it.toLinkMetadata(id)) }
                    } else if (sr.updatedAt > local.updatedAt) {
                        shareDao.updateNotesAndTagsAndTimestamp(local.id, sr.notes, sr.tags, sr.updatedAt)
                        Timber.d("Applied $type for ${sr.syncId} via SSE (tags=${sr.tags.size})")
                        sr.linkMetadata?.let { metadataDao.upsert(it.toLinkMetadata(local.id)) }
                    }
                }
                "record_metadata_updated" -> {
                    val sr = parseSyncRecord(obj)
                    val local = shareDao.getBySyncId(sr.syncId) ?: return@withContext
                    sr.linkMetadata?.let { metadataDao.upsert(it.toLinkMetadata(local.id)) }
                }
                "record_deleted" -> {
                    val syncId = obj.getString("syncId")
                    val local = shareDao.getBySyncId(syncId) ?: return@withContext
                    metadataDao.deleteByShareRecordId(local.id)
                    shareDao.deleteBySyncId(syncId)
                }
                "ingestion_metadata" -> {
                    val dao = ingestionDao ?: return@withContext
                    val syncId = obj.getString("syncId")
                    val local = shareDao.getBySyncId(syncId) ?: return@withContext
                    val incoming = parseIngestionRecord(local.id, obj)
                    // Status-only broadcasts (QUEUED/EXTRACTING_METADATA, e.g. right after a
                    // manual retry) carry no title/thumbnail. If a row already exists — from
                    // a prior successful download — a plain upsert would null out its
                    // metadata and make the row vanish from the UI until fresh metadata
                    // arrives. Preserve it with a status-only update instead; only overwrite
                    // metadata wholesale when the event actually carries it, or when this is
                    // the first-ever row for this item (so it still appears immediately as
                    // "loading" rather than not appearing at all).
                    val hasMetadata = incoming.title != null || incoming.thumbnailUrl != null
                    if (hasMetadata || dao.getByIdOnce(local.id) == null) {
                        dao.upsert(incoming)
                    } else {
                        dao.updateStatusOnly(local.id, incoming.status, incoming.errorMessage)
                    }
                }
                "ingestion_complete" -> {
                    val dao = ingestionDao ?: return@withContext
                    val syncId = obj.getString("syncId")
                    val local = shareDao.getBySyncId(syncId) ?: return@withContext
                    val videoPath = if (obj.isNull("serverVideoPath")) null else obj.getString("serverVideoPath")
                    dao.markComplete(local.id, videoPath)
                }
                "ingestion_failed" -> {
                    val dao = ingestionDao ?: return@withContext
                    val syncId = obj.getString("syncId")
                    val local = shareDao.getBySyncId(syncId) ?: return@withContext
                    val statusStr = runCatching { obj.getString("status") }.getOrNull() ?: "FAILED"
                    val status = IngestionStatus.entries.firstOrNull { it.name == statusStr } ?: IngestionStatus.FAILED
                    val errorMessage = if (obj.isNull("errorMessage")) null else obj.getString("errorMessage")
                    dao.markFailed(local.id, status, errorMessage)
                }
            }
        } catch (e: Exception) { Timber.w(e, "Malformed SSE event: type=$type") }
    }

    // ---- Push helpers (fire-and-forget, called from ShareRepository) ----

    override suspend fun pushInsert(record: ShareRecord) {
        syncClient.postRecord(record.toSyncRecord())
    }

    override suspend fun pushRecordUpdate(syncId: String, notes: String?, tags: List<String>, updatedAt: Long) {
        syncClient.patchRecord(syncId, notes, tags, updatedAt)
    }

    override suspend fun pushDelete(syncId: String) {
        syncClient.deleteRecord(syncId)
    }

    suspend fun pushMetadata(syncId: String, meta: LinkMetadata) {
        syncClient.putMetadata(syncId, meta.toSyncLinkMetadata())
    }

    /** Manually retries a failed video/social ingestion. The state change (QUEUED → ...)
     * arrives back over SSE, so no local Room update is needed here. */
    suspend fun retryIngestion(syncId: String): Boolean = syncClient.retryIngestion(syncId)

    // ---- Conversion helpers ----

    private fun SyncRecord.toShareRecord() = ShareRecord(
        originalText = originalText,
        cleanedText  = cleanedText,
        sharedAt     = sharedAt,
        updatedAt    = updatedAt,
        notes        = notes,
        tags         = tags,
        syncId       = syncId,
        source       = runCatching { ShareSource.valueOf(source) }.getOrDefault(ShareSource.MOBILE),
    )

    private fun ShareRecord.toSyncRecord() = SyncRecord(
        syncId       = syncId,
        originalText = originalText,
        cleanedText  = cleanedText,
        sharedAt     = sharedAt,
        updatedAt    = updatedAt,
        notes        = notes,
        tags         = tags,
        source       = source.name,
        linkMetadata = null,
    )

    private fun SyncLinkMetadata.toLinkMetadata(shareRecordId: Long) = LinkMetadata(
        shareRecordId  = shareRecordId,
        title          = title,
        thumbnailUrl   = thumbnailUrl,
        description    = description,
        articleSnippet = articleSnippet,
        contentType    = runCatching { ContentType.valueOf(contentType) }.getOrDefault(ContentType.UNKNOWN),
        fetchStatus    = runCatching { FetchStatus.valueOf(fetchStatus) }.getOrDefault(FetchStatus.FAILED),
    )

    private fun SyncIngestionRecord.toIngestionRecord(shareRecordId: Long) = IngestionRecord(
        shareRecordId = shareRecordId,
        status        = IngestionStatus.entries.firstOrNull { it.name == status } ?: IngestionStatus.QUEUED,
        errorMessage  = errorMessage,
        title         = title,
        uploader      = uploader,
        uploaderUrl   = uploaderUrl,
        description   = description,
        thumbnailUrl  = thumbnailUrl,
        uploadDate    = uploadDate,
        duration      = duration,
        viewCount     = viewCount,
        likeCount     = likeCount,
        tags          = tags,
        mediaType     = mediaType,
        thumbnailReady = thumbnailReady,
    )

    private fun LinkMetadata.toSyncLinkMetadata() = SyncLinkMetadata(
        title          = title,
        thumbnailUrl   = thumbnailUrl,
        description    = description,
        articleSnippet = articleSnippet,
        contentType    = contentType.name,
        fetchStatus    = fetchStatus.name,
    )

    private fun parseSyncRecord(obj: org.json.JSONObject): SyncRecord {
        val meta = if (!obj.isNull("linkMetadata")) {
            val m = obj.getJSONObject("linkMetadata")
            SyncLinkMetadata(
                title          = if (m.isNull("title")) null else m.getString("title"),
                thumbnailUrl   = if (m.isNull("thumbnailUrl")) null else m.getString("thumbnailUrl"),
                description    = if (m.isNull("description")) null else m.getString("description"),
                articleSnippet = if (m.isNull("articleSnippet")) null else m.getString("articleSnippet"),
                contentType    = m.getString("contentType"),
                fetchStatus    = m.getString("fetchStatus"),
            )
        } else null
        val tags = if (!obj.has("tags") || obj.isNull("tags")) {
            emptyList()
        } else {
            val arr = obj.getJSONArray("tags")
            (0 until arr.length()).map { arr.getString(it) }
        }
        return SyncRecord(
            syncId       = obj.getString("syncId"),
            originalText = obj.getString("originalText"),
            cleanedText  = obj.getString("cleanedText"),
            sharedAt     = obj.getLong("sharedAt"),
            updatedAt    = obj.getLong("updatedAt"),
            notes        = if (obj.isNull("notes")) null else obj.getString("notes"),
            tags         = tags,
            source       = obj.getString("source"),
            linkMetadata = meta,
        )
    }

    private fun buildOkHttp() = okhttp3.OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(0, java.util.concurrent.TimeUnit.SECONDS) // no read timeout for SSE
        .build()

    private fun parseIngestionRecord(shareRecordId: Long, obj: org.json.JSONObject): IngestionRecord {
        val statusStr = runCatching { obj.getString("status") }.getOrNull() ?: "QUEUED"
        val status = IngestionStatus.entries.firstOrNull { it.name == statusStr } ?: IngestionStatus.QUEUED
        return IngestionRecord(
            shareRecordId = shareRecordId,
            status        = status,
            errorMessage  = obj.optString("errorMessage").takeIf { it.isNotEmpty() },
            title         = obj.optString("title").takeIf { it.isNotEmpty() },
            uploader      = obj.optString("uploader").takeIf { it.isNotEmpty() },
            uploaderUrl   = obj.optString("uploaderUrl").takeIf { it.isNotEmpty() },
            description   = obj.optString("description").takeIf { it.isNotEmpty() },
            thumbnailUrl  = obj.optString("thumbnailUrl").takeIf { it.isNotEmpty() },
            uploadDate    = obj.optString("uploadDate").takeIf { it.isNotEmpty() },
            duration      = obj.optInt("duration").takeIf { it > 0 },
            viewCount     = obj.optLong("viewCount").takeIf { it > 0 },
            likeCount     = obj.optLong("likeCount").takeIf { it > 0 },
            tags          = obj.optString("tags").takeIf { it.isNotEmpty() },
            mediaType     = obj.optString("mediaType").takeIf { it.isNotEmpty() },
            serverVideoPath = obj.optString("serverVideoPath").takeIf { it.isNotEmpty() },
            thumbnailReady  = obj.optBoolean("thumbnailReady", false),
        )
    }
}
