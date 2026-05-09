package com.maroney.cleanshare.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ShareDao {

    /** All records, newest first. Emits a new list whenever the table changes. */
    @Query("SELECT * FROM share_history ORDER BY sharedAt DESC")
    fun getAll(): Flow<List<ShareRecord>>

    @Insert
    suspend fun insert(record: ShareRecord): Long

    @Query("DELETE FROM share_history")
    suspend fun deleteAll()
}
