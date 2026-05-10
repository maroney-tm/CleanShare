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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val repository: ShareRepository,
    private val workScheduler: MetadataWorkScheduler,
) : ViewModel() {

    val history: StateFlow<List<ShareRecordWithMetadata>> = repository.getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    init {
        // Schedule fetches for any records missing metadata — covers existing
        // rows on first launch after upgrade and workers that didn't finish.
        viewModelScope.launch(Dispatchers.IO) {
            val snapshot = repository.getAll().first()
            workScheduler.schedulePendingFetches(snapshot)
        }
    }

    fun deleteItem(id: Long) {
        viewModelScope.launch { repository.deleteById(id) }
    }

    fun clearHistory() {
        viewModelScope.launch { repository.deleteAll() }
    }

    fun retryFetch(shareRecordId: Long, url: String) {
        workScheduler.retryFetch(shareRecordId, url)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[APPLICATION_KEY] as CleanShareApplication
                return HistoryViewModel(app.shareRepository, app.workScheduler) as T
            }
        }
    }
}
