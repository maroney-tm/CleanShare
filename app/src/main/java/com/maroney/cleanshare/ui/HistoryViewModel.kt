package com.maroney.cleanshare.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.maroney.cleanshare.CleanShareApplication
import com.maroney.cleanshare.data.ShareRecordWithMetadata
import com.maroney.cleanshare.data.ShareRepository
import com.maroney.cleanshare.data.metadata.MetadataWorkScheduler
import com.maroney.cleanshare.sync.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val repository: ShareRepository,
    private val workScheduler: MetadataWorkScheduler,
    private val syncManager: SyncManager,
) : ViewModel() {

    val history: StateFlow<List<ShareRecordWithMetadata>> = repository.getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val snapshot = repository.getAll().first()
            workScheduler.schedulePendingFetches(snapshot)
            syncManager.resolveAndSync()
            syncManager.startListening(viewModelScope)  // safe — guarded against double-start above
        }
    }

    /** Called from HistoryScreen when the lifecycle moves to ON_START. */
    fun onStart() {
        syncManager.startListening(viewModelScope)
    }

    /** Called from HistoryScreen when the lifecycle moves to ON_STOP. */
    fun onStop() {
        syncManager.stopListening()
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

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[APPLICATION_KEY] as CleanShareApplication
                return HistoryViewModel(app.shareRepository, app.workScheduler, app.syncManager) as T
            }
        }
    }
}
