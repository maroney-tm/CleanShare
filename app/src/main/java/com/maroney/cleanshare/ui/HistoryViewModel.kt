package com.maroney.cleanshare.ui

import android.text.format.DateUtils
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.maroney.cleanshare.CleanShareApplication
import com.maroney.cleanshare.data.ShareRecordWithMetadata
import com.maroney.cleanshare.data.ShareRepository
import com.maroney.cleanshare.data.metadata.MetadataWorkScheduler
import com.maroney.cleanshare.sync.ConnectionStatus
import com.maroney.cleanshare.sync.ServerConfig
import com.maroney.cleanshare.sync.ServerConfigRepository
import com.maroney.cleanshare.sync.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

fun computeOfflineBannerTimestamp(
    status: ConnectionStatus,
    config: ServerConfig,
): Long? {
    if (status != ConnectionStatus.Disconnected) return null
    if (config.manualHost == null) return null
    return config.lastSeenAt
}

class HistoryViewModel(
    private val repository: ShareRepository,
    private val workScheduler: MetadataWorkScheduler,
    private val syncManager: SyncManager,
    private val configRepo: ServerConfigRepository,
) : ViewModel() {

    val history: StateFlow<List<ShareRecordWithMetadata>> = repository.getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val offlineBannerText: StateFlow<String?> =
        combine(syncManager.connectionStatus, configRepo.config) { status, config ->
            val ts = computeOfflineBannerTimestamp(status, config) ?: return@combine null
            val relative = DateUtils.getRelativeTimeSpanString(
                ts,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
            )
            "Server offline · last seen $relative"
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val snapshot = repository.getAll().first()
            workScheduler.schedulePendingFetches(snapshot)
        }
    }

    // SSE listening is owned by CleanShareApplication (tied to the whole app's
    // foreground/background state via ProcessLifecycleOwner), not this screen —
    // otherwise navigating to Detail would tear down the app's one SSE connection.

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            try {
                syncManager.fullSync()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun deleteItem(id: Long) {
        viewModelScope.launch { repository.deleteById(id) }
    }

    fun clearHistory() {
        viewModelScope.launch { repository.deleteAll() }
    }

    fun retryFetch(shareRecordId: Long, url: String, syncId: String) {
        workScheduler.retryFetch(shareRecordId, url, syncId)
    }

    fun retryIngestion(syncId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!syncManager.retryIngestion(syncId)) {
                Timber.w("Retry ingestion request failed for $syncId")
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[APPLICATION_KEY] as CleanShareApplication
                return HistoryViewModel(
                    app.shareRepository,
                    app.workScheduler,
                    app.syncManager,
                    app.serverConfigRepository,
                ) as T
            }
        }
    }
}
