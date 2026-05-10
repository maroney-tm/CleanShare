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

    @Query("DELETE FROM link_metadata WHERE shareRecordId = :shareRecordId")
    suspend fun deleteByShareRecordId(shareRecordId: Long)

    @Query("DELETE FROM link_metadata")
    suspend fun deleteAll()
}
