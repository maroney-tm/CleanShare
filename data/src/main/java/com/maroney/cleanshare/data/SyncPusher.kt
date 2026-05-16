package com.maroney.cleanshare.data

/**
 * Abstracts sync-push operations so that [ShareRepository] (in the :data module)
 * can fire-and-forget sync calls without a direct dependency on the :app module's
 * SyncManager.
 */
interface SyncPusher {
    suspend fun pushInsert(record: ShareRecord)
    suspend fun pushNoteUpdate(syncId: String, notes: String?, updatedAt: Long)
    suspend fun pushDelete(syncId: String)
}
