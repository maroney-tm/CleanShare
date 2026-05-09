package com.maroney.cleanshare.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LinkMetadataDao {

    @Upsert
    suspend fun upsert(metadata: LinkMetadata)

    @Query("SELECT * FROM link_metadata")
    fun observeAll(): Flow<List<LinkMetadata>>

    @Query("""
        SELECT id FROM share_history
        WHERE id NOT IN (SELECT shareRecordId FROM link_metadata)
    """)
    suspend fun getPendingIds(): List<Long>

    @Query("DELETE FROM link_metadata")
    suspend fun deleteAll()
}
