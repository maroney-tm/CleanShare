package com.maroney.cleanshare.data

/**
 * Abstracts sync-push operations so that [ShareRepository] (in the :data module)
 * can fire-and-forget sync calls without a direct dependency on the :app module's
 * SyncManager.
 */
interface SyncPusher {
    suspend fun pushInsert(record: ShareRecord)
    // Carries the record's full current notes+tags snapshot (not a diff) so the server's
    // unconditional-overwrite PATCH never clobbers the field that didn't actually change —
    // see ShareRepository.updateNotes/updateTags, which always read both before pushing.
    suspend fun pushRecordUpdate(syncId: String, notes: String?, tags: List<String>, updatedAt: Long)
    suspend fun pushDelete(syncId: String)
}
