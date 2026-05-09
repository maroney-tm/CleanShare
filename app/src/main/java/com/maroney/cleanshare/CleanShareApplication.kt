package com.maroney.cleanshare

import android.app.Application
import androidx.work.Configuration
import com.maroney.cleanshare.data.ShareDatabase
import com.maroney.cleanshare.data.metadata.AppWorkerFactory
import com.maroney.cleanshare.data.metadata.MetadataFetcher
import com.maroney.cleanshare.data.metadata.MetadataWorkScheduler
import com.maroney.cleanshare.data.ShareRepository
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class CleanShareApplication : Application(), Configuration.Provider {

    val database by lazy { ShareDatabase.getInstance(this) }

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    val metadataFetcher by lazy { MetadataFetcher(okHttpClient) }

    val workScheduler by lazy {
        MetadataWorkScheduler(androidx.work.WorkManager.getInstance(this))
    }

    val shareRepository by lazy {
        ShareRepository(database.shareDao(), database.linkMetadataDao(), workScheduler)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(AppWorkerFactory(metadataFetcher, database.linkMetadataDao()))
            .build()
}
