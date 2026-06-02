package com.maroney.cleanshare.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface IngestionDao {

    @Upsert
    suspend fun upsert(record: IngestionRecord)

    @Query("SELECT * FROM ingestion_record WHERE shareRecordId = :shareRecordId")
    fun observeById(shareRecordId: Long): Flow<IngestionRecord?>

    @Query("SELECT * FROM ingestion_record")
    fun observeAll(): Flow<List<IngestionRecord>>

    // Used for ingestion_complete SSE — only updates status + serverVideoPath so
    // metadata fields from ingestion_metadata are preserved.
    @Query("UPDATE ingestion_record SET status = 'COMPLETE', serverVideoPath = :serverVideoPath WHERE shareRecordId = :shareRecordId")
    suspend fun markComplete(shareRecordId: Long, serverVideoPath: String?)

    // Used for ingestion_failed SSE — only updates status + errorMessage.
    @Query("UPDATE ingestion_record SET status = 'FAILED', errorMessage = :errorMessage WHERE shareRecordId = :shareRecordId")
    suspend fun markFailed(shareRecordId: Long, errorMessage: String?)

    @Query("DELETE FROM ingestion_record WHERE shareRecordId = :shareRecordId")
    suspend fun deleteByShareRecordId(shareRecordId: Long)

    @Query("DELETE FROM ingestion_record")
    suspend fun deleteAll()
}
