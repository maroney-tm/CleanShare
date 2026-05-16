package com.maroney.cleanshare

import android.app.Application
import androidx.work.Configuration
import com.maroney.cleanshare.data.ShareDatabase
import com.maroney.cleanshare.data.metadata.AppWorkerFactory
import com.maroney.cleanshare.data.metadata.MetadataFetcher
import com.maroney.cleanshare.data.metadata.MetadataWorkScheduler
import com.maroney.cleanshare.data.ShareRepository
import com.maroney.cleanshare.sync.CleanShareSyncClient
import com.maroney.cleanshare.sync.ServerConfigRepository
import com.maroney.cleanshare.sync.SyncManager
import androidx.work.WorkManager
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class CleanShareApplication : Application(), Configuration.Provider {

    val database by lazy { ShareDatabase.getInstance(this) }

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .callTimeout(10, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                        .build()
                )
            }
            .build()
    }

    val metadataFetcher by lazy { MetadataFetcher(okHttpClient) }

    val workScheduler by lazy {
        MetadataWorkScheduler(WorkManager.getInstance(this))
    }

    val serverConfigRepository by lazy { ServerConfigRepository(this) }

    val syncClient by lazy { CleanShareSyncClient(okHttpClient) }

    val syncManager by lazy {
        SyncManager(this, syncClient, serverConfigRepository, database.shareDao(), database.linkMetadataDao())
    }

    val shareRepository by lazy {
        ShareRepository(database.shareDao(), database.linkMetadataDao(), workScheduler, syncManager)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(AppWorkerFactory(metadataFetcher, database.linkMetadataDao()))
            .build()
}
