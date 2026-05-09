package com.maroney.cleanshare.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LinkMetadataDao {

    @Upsert
    suspend fun upsert(metadata: LinkMetadata)

    @Query("SELECT * FROM link_metadata ORDER BY shareRecordId DESC")
    fun observeAll(): Flow<List<LinkMetadata>>

    @Query("""
        SELECT s.id FROM share_history s
        LEFT JOIN link_metadata m ON s.id = m.shareRecordId
        WHERE m.shareRecordId IS NULL
    """)
    suspend fun getPendingIds(): List<Long>

    @Query("DELETE FROM link_metadata")
    suspend fun deleteAll()
}
