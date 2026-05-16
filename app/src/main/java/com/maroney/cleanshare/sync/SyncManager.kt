package com.maroney.cleanshare.sync

import android.content.Context
import com.maroney.cleanshare.data.ContentType
import com.maroney.cleanshare.data.FetchStatus
import com.maroney.cleanshare.data.LinkMetadata
import com.maroney.cleanshare.data.LinkMetadataDao
import com.maroney.cleanshare.data.ShareDao
import com.maroney.cleanshare.data.ShareRecord
import com.maroney.cleanshare.data.ShareSource
import com.maroney.cleanshare.data.SyncPusher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

sealed class ConnectionStatus {
    data object Disconnected : ConnectionStatus()
    data object Searching    : ConnectionStatus()
    data class  Connected(val host: String, val port: Int) : ConnectionStatus()
}

class SyncManager(
    private val context: Context,
    private val syncClient: CleanShareSyncClient,
    private val configRepo: ServerConfigRepository,
    private val shareDao: ShareDao,
    private val metadataDao: LinkMetadataDao,
) : SyncPusher {
    private val _status = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val connectionStatus: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private var sseListener: SseListener? = null

    // ---- Discovery + connection ----

    /**
     * Resolves the server address (manual override → mDNS → give up),
     * verifies with /health, and configures [syncClient].
     * Returns true if a live server was found.
     */
    suspend fun resolveAndSync(): Boolean = withContext(Dispatchers.IO) {
        _status.value = ConnectionStatus.Searching
        val config = configRepo.config.first()
        val port = config.port

        val host: String? = when {
            config.manualHost != null -> config.manualHost
            config.autoDiscover -> {
                val discovered = NsdDiscoveryHelper(context).discover()
                if (discovered != null) {
                    configRepo.setResolvedHost(discovered.first)
                    discovered.first
                } else null
            }
            else -> config.resolvedHost
        }

        if (host == null) {
            _status.value = ConnectionStatus.Disconnected
            syncClient.clear()
            return@withContext false
        }

        syncClient.configure(host, port)

        if (!syncClient.health()) {
            // Cached host is stale — clear it and give up
            configRepo.setResolvedHost(null)
            syncClient.clear()
            _status.value = ConnectionStatus.Disconnected
            return@withContext false
        }

        _status.value = ConnectionStatus.Connected(host, port)
        fullSync()
        true
    }

    // ---- Full pull sync ----

    /**
     * Fetches all records from the server and upserts them locally (LWW).
     * NOTE: Deletions propagate via SSE only; this method never deletes local records.
     */
    suspend fun fullSync() = withContext(Dispatchers.IO) {
        val serverRecords = syncClient.getAllRecords()
        for (sr in serverRecords) {
            val local = shareDao.getBySyncId(sr.syncId)
            if (local == null) {
                // Record originated on another client (e.g. future TUI) — insert locally.
                val id = shareDao.insert(sr.toShareRecord())
                sr.linkMetadata?.let { metadataDao.upsert(it.toLinkMetadata(id)) }
            } else if (sr.updatedAt > local.updatedAt) {
                shareDao.updateNotesAndTimestamp(local.id, sr.notes, sr.updatedAt)
                sr.linkMetadata?.let { metadataDao.upsert(it.toLinkMetadata(local.id)) }
            }
        }
    }

    // ---- SSE ----

    fun startListening(scope: CoroutineScope) {
        if (!syncClient.isConfigured()) return
        val baseUrl = (_status.value as? ConnectionStatus.Connected)
            ?.let { "http://${it.host}:${it.port}" } ?: return

        sseListener = SseListener(buildOkHttp()) { type, data ->
            handleSseEvent(type, data)
        }
        sseListener?.start(baseUrl, scope)
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
                        shareDao.updateNotesAndTimestamp(local.id, sr.notes, sr.updatedAt)
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
            }
        } catch (_: Exception) { /* malformed event — ignore */ }
    }

    // ---- Push helpers (fire-and-forget, called from ShareRepository) ----

    override suspend fun pushInsert(record: ShareRecord) {
        syncClient.postRecord(record.toSyncRecord())
    }

    override suspend fun pushNoteUpdate(syncId: String, notes: String?, updatedAt: Long) {
        syncClient.patchRecord(syncId, notes, updatedAt)
    }

    override suspend fun pushDelete(syncId: String) {
        syncClient.deleteRecord(syncId)
    }

    suspend fun pushMetadata(syncId: String, meta: LinkMetadata) {
        syncClient.putMetadata(syncId, meta.toSyncLinkMetadata())
    }

    // ---- Conversion helpers ----

    private fun SyncRecord.toShareRecord() = ShareRecord(
        originalText = originalText,
        cleanedText  = cleanedText,
        sharedAt     = sharedAt,
        updatedAt    = updatedAt,
        notes        = notes,
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
        return SyncRecord(
            syncId       = obj.getString("syncId"),
            originalText = obj.getString("originalText"),
            cleanedText  = obj.getString("cleanedText"),
            sharedAt     = obj.getLong("sharedAt"),
            updatedAt    = obj.getLong("updatedAt"),
            notes        = if (obj.isNull("notes")) null else obj.getString("notes"),
            source       = obj.getString("source"),
            linkMetadata = meta,
        )
    }

    private fun buildOkHttp() = okhttp3.OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(0, java.util.concurrent.TimeUnit.SECONDS) // no read timeout for SSE
        .build()
}
