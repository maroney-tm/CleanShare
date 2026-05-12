package com.maroney.cleanshare.data.metadata

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.maroney.cleanshare.data.ContentType
import com.maroney.cleanshare.data.FetchStatus
import com.maroney.cleanshare.data.LinkMetadata
import com.maroney.cleanshare.data.LinkMetadataDao
import com.maroney.cleanshare.widget.RecentSharesWidget

class FetchMetadataWorker(
    context: Context,
    params: WorkerParameters,
    private val fetcher: MetadataFetcher,
    private val dao: LinkMetadataDao,
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_SHARE_RECORD_ID = "share_record_id"
        const val KEY_URL = "url"
    }

    override suspend fun doWork(): Result {
        val shareRecordId = inputData.getLong(KEY_SHARE_RECORD_ID, -1L)
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        if (shareRecordId == -1L) return Result.failure()

        val fetched = fetcher.fetch(url)
        val metadata = fetched?.copy(shareRecordId = shareRecordId)
            ?: LinkMetadata(
                shareRecordId = shareRecordId,
                title = null,
                thumbnailUrl = null,
                description = null,
                articleSnippet = null,
                contentType = ContentType.UNKNOWN,
                fetchStatus = FetchStatus.FAILED,
            )
        dao.upsert(metadata)
        RecentSharesWidget().updateAll(applicationContext)
        return Result.success()
    }
}
