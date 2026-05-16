package com.maroney.cleanshare.data.metadata

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.maroney.cleanshare.data.ContentType
import com.maroney.cleanshare.data.FetchStatus
import com.maroney.cleanshare.data.LinkMetadata
import com.maroney.cleanshare.data.LinkMetadataDao
import com.maroney.cleanshare.sync.CleanShareSyncClient
import com.maroney.cleanshare.sync.SyncLinkMetadata
import com.maroney.cleanshare.widget.RecentSharesWidget

class FetchMetadataWorker(
    context: Context,
    params: WorkerParameters,
    private val fetcher: MetadataFetcher,
    private val dao: LinkMetadataDao,
    private val syncClient: CleanShareSyncClient?,
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_SHARE_RECORD_ID = "share_record_id"
        const val KEY_URL             = "url"
        const val KEY_SYNC_ID         = "sync_id"
    }

    override suspend fun doWork(): Result {
        val shareRecordId = inputData.getLong(KEY_SHARE_RECORD_ID, -1L)
        val url    = inputData.getString(KEY_URL)    ?: return Result.failure()
        val syncId = inputData.getString(KEY_SYNC_ID) ?: ""
        if (shareRecordId == -1L) return Result.failure()

        val fetched = fetcher.fetch(url)
        val metadata = fetched?.copy(shareRecordId = shareRecordId)
            ?: LinkMetadata(
                shareRecordId  = shareRecordId,
                title          = null,
                thumbnailUrl   = null,
                description    = null,
                articleSnippet = null,
                contentType    = ContentType.UNKNOWN,
                fetchStatus    = FetchStatus.FAILED,
            )
        dao.upsert(metadata)

        // Push metadata to sync server if we have a syncId and the fetch succeeded.
        if (syncId.isNotEmpty() && metadata.fetchStatus == FetchStatus.SUCCESS) {
            syncClient?.putMetadata(syncId, metadata.toSyncLinkMetadata())
        }

        RecentSharesWidget().updateAll(applicationContext)
        return Result.success()
    }

    private fun LinkMetadata.toSyncLinkMetadata() = SyncLinkMetadata(
        title          = title,
        thumbnailUrl   = thumbnailUrl,
        description    = description,
        articleSnippet = articleSnippet,
        contentType    = contentType.name,
        fetchStatus    = fetchStatus.name,
    )
}
