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
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    fun getAll(): Flow<List<ShareRecordWithMetadata>> =
        combine(shareDao.getAll(), metadataDao.observeAll()) { records, metadataList ->
            val byId = metadataList.associateBy { it.shareRecordId }
            records.map { ShareRecordWithMetadata(it, byId[it.id]) }
        }

    fun getById(id: Long): Flow<ShareRecordWithMetadata?> =
        combine(shareDao.getById(id), metadataDao.getById(id)) { record, metadata ->
            record?.let { ShareRecordWithMetadata(it, metadata) }
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
        val syncId = shareDao.getSyncIdById(id) ?: return
        scope.launch { syncPusher?.pushNoteUpdate(syncId, notes, now) }
    }

    suspend fun deleteById(id: Long) {
        val syncId = shareDao.getSyncIdById(id)
        metadataDao.deleteByShareRecordId(id)
        shareDao.deleteById(id)
        if (syncId != null) scope.launch { syncPusher?.pushDelete(syncId) }
    }

    suspend fun deleteAll() {
        shareDao.deleteAll()
        metadataDao.deleteAll()
    }
}
