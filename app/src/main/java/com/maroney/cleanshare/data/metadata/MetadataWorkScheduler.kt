package com.maroney.cleanshare.data.metadata

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.maroney.cleanshare.data.ShareRecordWithMetadata

class MetadataWorkScheduler(private val workManager: WorkManager) {

    fun scheduleFetch(shareRecordId: Long, url: String) {
        enqueue(shareRecordId, url, ExistingWorkPolicy.KEEP)
    }

    fun schedulePendingFetches(records: List<ShareRecordWithMetadata>) {
        records
            .filter { it.metadata == null }
            .forEach { item ->
                val url = extractUrl(item.record.cleanedText) ?: return@forEach
                scheduleFetch(item.record.id, url)
            }
    }

    fun retryFetch(shareRecordId: Long, url: String) {
        enqueue(shareRecordId, url, ExistingWorkPolicy.REPLACE)
    }

    private fun enqueue(shareRecordId: Long, url: String, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<FetchMetadataWorker>()
            .setInputData(workDataOf(
                FetchMetadataWorker.KEY_SHARE_RECORD_ID to shareRecordId,
                FetchMetadataWorker.KEY_URL to url,
            ))
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .build()
        workManager.enqueueUniqueWork("metadata_$shareRecordId", policy, request)
    }

    private fun extractUrl(text: String): String? =
        text.split("\\s+".toRegex())
            .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
}
