package com.maroney.cleanshare.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ShareRepository(
    private val shareDao: ShareDao,
    private val metadataDao: LinkMetadataDao,
    private val workScheduler: WorkScheduler,
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
        if (url != null) workScheduler.scheduleFetch(id, url)
    }

    suspend fun updateNotes(id: Long, notes: String?) {
        shareDao.updateNotes(id, notes)
    }

    suspend fun deleteById(id: Long) {
        metadataDao.deleteByShareRecordId(id)
        shareDao.deleteById(id)
    }

    suspend fun deleteAll() {
        shareDao.deleteAll()
        metadataDao.deleteAll()
    }
}
