package com.maroney.cleanshare.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ShareDao {

    @Query("SELECT * FROM share_history ORDER BY sharedAt DESC")
    fun getAll(): Flow<List<ShareRecord>>

    @Query("SELECT * FROM share_history WHERE id = :id")
    fun getById(id: Long): Flow<ShareRecord?>

    @Insert
    suspend fun insert(record: ShareRecord): Long

    // Original — kept for existing tests.
    @Query("UPDATE share_history SET notes = :notes WHERE id = :id")
    suspend fun updateNotes(id: Long, notes: String?)

    // New — bumps updatedAt for LWW tracking.
    @Query("UPDATE share_history SET notes = :notes, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateNotesAndTimestamp(id: Long, notes: String?, updatedAt: Long)

    // Original — kept for existing tests.
    @Query("UPDATE share_history SET tags = :tags WHERE id = :id")
    suspend fun updateTags(id: Long, tags: List<String>)

    // Used by ShareRepository.updateTags — bumps updatedAt for LWW tracking, same as
    // updateNotesAndTimestamp.
    @Query("UPDATE share_history SET tags = :tags, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateTagsAndTimestamp(id: Long, tags: List<String>, updatedAt: Long)

    // Used by SyncManager when pulling a record from the server — notes and tags arrive
    // together in one payload, so both are applied atomically rather than risking a second
    // write racing a concurrent local edit between them.
    @Query("UPDATE share_history SET notes = :notes, tags = :tags, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateNotesAndTagsAndTimestamp(id: Long, notes: String?, tags: List<String>, updatedAt: Long)

    @Query("SELECT * FROM share_history WHERE id = :id")
    suspend fun getByIdOnce(id: Long): ShareRecord?

    @Query("DELETE FROM share_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM share_history")
    suspend fun deleteAll()

    // Sync queries
    @Query("SELECT * FROM share_history WHERE sync_id = :syncId")
    suspend fun getBySyncId(syncId: String): ShareRecord?

    @Query("DELETE FROM share_history WHERE sync_id = :syncId")
    suspend fun deleteBySyncId(syncId: String)

    @Query("SELECT sync_id FROM share_history WHERE id = :id")
    suspend fun getSyncIdById(id: Long): String?

    @Query("SELECT * FROM share_history")
    suspend fun getAllOnce(): List<ShareRecord>
}
