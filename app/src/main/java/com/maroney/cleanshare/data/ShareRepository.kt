package com.maroney.cleanshare.data

import com.maroney.cleanshare.data.metadata.MetadataWorkScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ShareRepository(
    private val shareDao: ShareDao,
    private val metadataDao: LinkMetadataDao,
    private val workScheduler: MetadataWorkScheduler,
) {

    fun getAll(): Flow<List<ShareRecordWithMetadata>> =
        combine(shareDao.getAll(), metadataDao.observeAll()) { records, metadataList ->
            val byId = metadataList.associateBy { it.shareRecordId }
            records.map { ShareRecordWithMetadata(it, byId[it.id]) }
        }

    suspend fun insert(record: ShareRecord) {
        val id = shareDao.insert(record)
        val url = record.cleanedText
            .split("\\s+".toRegex())
            .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
        if (url != null) workScheduler.scheduleFetch(id, url)
    }

    suspend fun deleteAll() {
        shareDao.deleteAll()
        metadataDao.deleteAll()
    }
}
