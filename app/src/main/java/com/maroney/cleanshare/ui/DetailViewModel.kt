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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class DetailViewModel(
    private val id: Long,
    private val repository: ShareRepository,
    private val workScheduler: MetadataWorkScheduler,
    private val syncManager: SyncManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ShareRecordWithMetadata?>(null)
    val uiState: StateFlow<ShareRecordWithMetadata?> = _uiState.asStateFlow()

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    private val _deleted = MutableSharedFlow<Unit>()
    val deleted: SharedFlow<Unit> = _deleted.asSharedFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var debounceJob: Job? = null
    private var pendingSave = false
    private var notesInitialized = false

    init {
        viewModelScope.launch {
            repository.getById(id).collect { item ->
                _uiState.value = item
                if (!notesInitialized && item != null) {
                    _notes.value = item.record.notes ?: ""
                    notesInitialized = true
                }
            }
        }
    }

    fun onNotesChanged(text: String) {
        _notes.value = text
        pendingSave = true
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(500)
            repository.updateNotes(id, text)
            pendingSave = false
        }
    }

    fun onNotesFocusLost() {
        if (!pendingSave) return
        debounceJob?.cancel()
        debounceJob = null
        pendingSave = false
        viewModelScope.launch { repository.updateNotes(id, _notes.value) }
    }

    fun deleteItem() {
        viewModelScope.launch {
            repository.deleteById(id)
            _deleted.emit(Unit)
        }
    }

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

    fun retryMetadataFetch() {
        val record = _uiState.value?.record ?: return
        workScheduler.retryFetch(record.id, record.cleanedText, record.syncId)
    }

    fun retryIngestion() {
        val record = _uiState.value?.record ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (!syncManager.retryIngestion(record.syncId)) {
                Timber.w("Retry ingestion request failed for ${record.syncId}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (pendingSave) {
            debounceJob?.cancel()
            val notesSnapshot = _notes.value
            CoroutineScope(Dispatchers.IO).launch {
                repository.updateNotes(id, notesSnapshot)
            }
        }
    }

    companion object {
        fun factory(id: Long): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[APPLICATION_KEY] as CleanShareApplication
                return DetailViewModel(id, app.shareRepository, app.workScheduler, app.syncManager) as T
            }
        }
    }
}
