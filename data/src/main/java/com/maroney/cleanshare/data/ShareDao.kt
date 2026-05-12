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

    @Query("UPDATE share_history SET notes = :notes WHERE id = :id")
    suspend fun updateNotes(id: Long, notes: String?)

    @Query("DELETE FROM share_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM share_history")
    suspend fun deleteAll()
}
