package com.maroney.androidsharesanitizer.data

import kotlinx.coroutines.flow.Flow

/**
 * Single access point for share-history data.
 * Both [ShareActivity] (writes) and [HistoryViewModel] (reads) go through here.
 */
class ShareRepository(private val dao: ShareDao) {

    fun getAll(): Flow<List<ShareRecord>> = dao.getAll()

    suspend fun insert(record: ShareRecord) = dao.insert(record)

    suspend fun deleteAll() = dao.deleteAll()
}
