package com.maroney.cleanshare.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineVideoDao {

    @Upsert
    suspend fun upsert(record: OfflineVideoRecord)

    @Query("SELECT * FROM offline_video WHERE shareRecordId = :shareRecordId")
    fun observeById(shareRecordId: Long): Flow<OfflineVideoRecord?>

    @Query("SELECT * FROM offline_video WHERE shareRecordId = :shareRecordId")
    suspend fun getByIdOnce(shareRecordId: Long): OfflineVideoRecord?

    @Query("SELECT * FROM offline_video")
    fun observeAll(): Flow<List<OfflineVideoRecord>>

    @Query("DELETE FROM offline_video WHERE shareRecordId = :shareRecordId")
    suspend fun deleteByShareRecordId(shareRecordId: Long)
}
