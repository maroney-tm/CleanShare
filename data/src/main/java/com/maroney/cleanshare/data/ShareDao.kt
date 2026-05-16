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
