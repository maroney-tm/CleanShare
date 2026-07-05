package com.maroney.cleanshare

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.maroney.cleanshare.BuildConfig
import com.maroney.cleanshare.data.ShareDatabase
import com.maroney.cleanshare.data.metadata.AppWorkerFactory
import com.maroney.cleanshare.data.metadata.MetadataFetcher
import com.maroney.cleanshare.data.metadata.MetadataWorkScheduler
import com.maroney.cleanshare.data.ShareRepository
import com.maroney.cleanshare.domain.DomainHandlerRegistry
import com.maroney.cleanshare.domain.InstagramDomainHandler
import com.maroney.cleanshare.domain.YoutubeDomainHandler
import com.maroney.cleanshare.sync.CleanShareSyncClient
import com.maroney.cleanshare.sync.ServerConfigRepository
import com.maroney.cleanshare.sync.SyncManager
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import timber.log.Timber
import java.util.concurrent.TimeUnit

class CleanShareApplication : Application(), Configuration.Provider {

    // Long-lived, process-scoped: SSE listening must outlive any single screen's
    // ViewModel, otherwise navigating between screens (which disposes off-stack
    // composables and their lifecycles) tears down the app's one SSE connection.
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())

        // SSE lifecycle is tied to the whole app's foreground/background state, not to
        // whichever screen happens to be on top — so it survives navigating between
        // History and Detail. Also re-syncs on every foreground to catch up on anything
        // missed while backgrounded (SSE events are not queued/replayed by the server).
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                applicationScope.launch {
                    syncManager.resolveAndSync()
                    syncManager.startListening()
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                syncManager.stopListening()
            }
        })
    }

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
        SyncManager(
            context      = this,
            syncClient   = syncClient,
            configRepo   = serverConfigRepository,
            shareDao     = database.shareDao(),
            metadataDao  = database.linkMetadataDao(),
            ingestionDao = database.ingestionDao(),
        )
    }

    val shareRepository by lazy {
        ShareRepository(
            shareDao     = database.shareDao(),
            metadataDao  = database.linkMetadataDao(),
            workScheduler = workScheduler,
            syncPusher   = syncManager,
            ingestionDao = database.ingestionDao(),
        )
    }

    val domainHandlerRegistry by lazy {
        DomainHandlerRegistry(listOf(InstagramDomainHandler(), YoutubeDomainHandler()))
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(AppWorkerFactory(metadataFetcher, database.linkMetadataDao(), syncClient))
            .build()
}
