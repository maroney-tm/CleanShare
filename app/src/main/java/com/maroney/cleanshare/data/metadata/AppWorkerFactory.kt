package com.maroney.cleanshare.data.metadata

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.maroney.cleanshare.data.LinkMetadataDao

class AppWorkerFactory(
    private val fetcher: MetadataFetcher,
    private val dao: LinkMetadataDao,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        FetchMetadataWorker::class.java.name ->
            FetchMetadataWorker(appContext, workerParameters, fetcher, dao)
        else -> null
    }
}
