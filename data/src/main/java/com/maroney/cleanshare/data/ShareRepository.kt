package com.maroney.cleanshare.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ShareRepository(
    private val shareDao: ShareDao,
    private val metadataDao: LinkMetadataDao,
    private val workScheduler: WorkScheduler,
    private val syncPusher: SyncPusher? = null,
    private val ingestionDao: IngestionDao? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    fun getAll(): Flow<List<ShareRecordWithMetadata>> {
        val ing = ingestionDao
        return if (ing != null) {
            combine(shareDao.getAll(), metadataDao.observeAll(), ing.observeAll()) { records, metadataList, ingestions ->
                val metaById = metadataList.associateBy { it.shareRecordId }
                val ingestionById = ingestions.associateBy { it.shareRecordId }
                records.map { ShareRecordWithMetadata(it, metaById[it.id], ingestionById[it.id]) }
            }
        } else {
            combine(shareDao.getAll(), metadataDao.observeAll()) { records, metadataList ->
                val byId = metadataList.associateBy { it.shareRecordId }
                records.map { ShareRecordWithMetadata(it, byId[it.id]) }
            }
        }
    }

    fun getById(id: Long): Flow<ShareRecordWithMetadata?> {
        val ing = ingestionDao
        return if (ing != null) {
            combine(shareDao.getById(id), metadataDao.getById(id), ing.observeById(id)) { record, metadata, ingestion ->
                record?.let { ShareRecordWithMetadata(it, metadata, ingestion) }
            }
        } else {
            combine(shareDao.getById(id), metadataDao.getById(id)) { record, metadata ->
                record?.let { ShareRecordWithMetadata(it, metadata) }
            }
        }
    }

    suspend fun insert(record: ShareRecord) {
        val id = shareDao.insert(record)
        val url = record.cleanedText
            .split("\\s+".toRegex())
            .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
        if (url != null) workScheduler.scheduleFetch(id, url, record.syncId)
        scope.launch { syncPusher?.pushInsert(record) }
    }

    suspend fun updateNotes(id: Long, notes: String?) {
        val now = System.currentTimeMillis()
        shareDao.updateNotesAndTimestamp(id, notes, now)
        val record = shareDao.getByIdOnce(id) ?: return
        scope.launch { syncPusher?.pushRecordUpdate(record.syncId, record.notes, record.tags, now) }
    }

    suspend fun updateTags(id: Long, tags: List<String>) {
        val now = System.currentTimeMillis()
        shareDao.updateTagsAndTimestamp(id, tags, now)
        val record = shareDao.getByIdOnce(id) ?: return
        scope.launch { syncPusher?.pushRecordUpdate(record.syncId, record.notes, record.tags, now) }
    }

    suspend fun deleteById(id: Long) {
        val syncId = shareDao.getSyncIdById(id)
        metadataDao.deleteByShareRecordId(id)
        ingestionDao?.deleteByShareRecordId(id)
        shareDao.deleteById(id)
        if (syncId != null) scope.launch { syncPusher?.pushDelete(syncId) }
    }

    // Note: deleteAll() does not push to the sync server — bulk-delete propagation
    // is out of scope for MVP. Cleared records will reappear on the next fullSync()
    // if the server still has them.
    suspend fun deleteAll() {
        shareDao.deleteAll()
        metadataDao.deleteAll()
        ingestionDao?.deleteAll()
    }
}
