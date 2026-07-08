package com.maroney.cleanshare.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.maroney.cleanshare.CleanShareApplication
import com.maroney.cleanshare.data.IngestionStatus
import com.maroney.cleanshare.data.OfflineVideoRecord
import com.maroney.cleanshare.data.ShareRecordWithMetadata
import com.maroney.cleanshare.data.ShareRepository
import com.maroney.cleanshare.data.metadata.MetadataWorkScheduler
import com.maroney.cleanshare.media.OfflineVideoRepository
import com.maroney.cleanshare.media.VideoPlaybackManager
import com.maroney.cleanshare.sync.CleanShareSyncClient
import com.maroney.cleanshare.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

/** The id — and, if ready, the playable video URL — of the entry to swipe to when moving to
 * the previous/next video in the list order the detail screen was opened with. Both null when
 * there's no such neighbor with a video. */
data class VideoSwipeTargets(
    val previousId: Long?,
    val previousVideoUrl: String?,
    val nextId: Long?,
    val nextVideoUrl: String?,
)

class DetailViewModel(
    private val id: Long,
    private val orderedIds: List<Long>,
    private val repository: ShareRepository,
    private val workScheduler: MetadataWorkScheduler,
    private val syncManager: SyncManager,
    private val offlineVideoRepository: OfflineVideoRepository,
    private val syncClient: CleanShareSyncClient,
    private val videoPlaybackManager: VideoPlaybackManager,
) : ViewModel() {

    /** This entry's position in [orderedIds], or -1 if it's not part of that list (e.g. no
     * swipe-navigation context was supplied, such as when opened from a widget deep link). */
    private val currentIndex: Int = orderedIds.indexOf(id)

    private val _uiState = MutableStateFlow<ShareRecordWithMetadata?>(null)
    val uiState: StateFlow<ShareRecordWithMetadata?> = _uiState.asStateFlow()

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    private val _deleted = MutableSharedFlow<Unit>()
    val deleted: SharedFlow<Unit> = _deleted.asSharedFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val offlineVideo: StateFlow<OfflineVideoRecord?> = offlineVideoRepository.observeById(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Shared upstream so tagVocabulary/suggestedTags don't each open their own
    // getAll() combine() subscription just to read tags off every entry.
    private val allRecords: StateFlow<List<ShareRecordWithMetadata>> = repository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Full tag vocabulary across all entries, for autocomplete while typing a new tag. */
    val tagVocabulary: StateFlow<List<String>> = allRecords
        .map { allTags(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The most-used tags across all entries, for one-tap quick tagging. */
    val suggestedTags: StateFlow<List<String>> = allRecords
        .map { topTags(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The nearest entries with a playable video on either side of this one, walking
     * [orderedIds] — the list order the detail screen was opened with — and skipping over any
     * entries whose video isn't ready yet. */
    val videoSwipeTargets: StateFlow<VideoSwipeTargets> = allRecords
        .map { records -> computeVideoSwipeTargets(records) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VideoSwipeTargets(null, null, null, null))

    private fun computeVideoSwipeTargets(records: List<ShareRecordWithMetadata>): VideoSwipeTargets {
        if (currentIndex == -1) return VideoSwipeTargets(null, null, null, null)
        val recordsById = records.associateBy { it.record.id }
        val previous = orderedIds.subList(0, currentIndex).asReversed()
            .firstNotNullOfOrNull { candidateId -> recordsById[candidateId]?.takeIf { it.hasVideo } }
        val next = orderedIds.subList(currentIndex + 1, orderedIds.size)
            .firstNotNullOfOrNull { candidateId -> recordsById[candidateId]?.takeIf { it.hasVideo } }
        return VideoSwipeTargets(
            previousId = previous?.record?.id,
            previousVideoUrl = previous?.let(::resolveVideoUrl),
            nextId = next?.record?.id,
            nextVideoUrl = next?.let(::resolveVideoUrl),
        )
    }

    private val ShareRecordWithMetadata.hasVideo: Boolean
        get() = ingestion?.status == IngestionStatus.COMPLETE

    /** Mirrors the videoUrl computation in DetailScreen, minus the offline-copy preference —
     * prefetching only ever needs the streamed URL, since an entry with an offline copy will
     * already play instantly without it. */
    private fun resolveVideoUrl(record: ShareRecordWithMetadata): String? {
        if (!record.hasVideo) return null
        return syncClient.effectiveBaseUrl()?.let { "$it/records/${record.record.syncId}/media" }
    }

    private var debounceJob: Job? = null
    private var pendingSave = false
    private var notesInitialized = false

    // Tracks what's currently registered with videoPlaybackManager's preload manager, so a
    // changed or vacated neighbor (e.g. a swipe target's ingestion finishing mid-visit shifts
    // videoSwipeTargets) gets un-registered instead of leaking a stale preload registration for
    // the lifetime of the (process-wide, longer-lived-than-this-ViewModel) preload manager.
    private var registeredPreviousPreloadUrl: String? = null
    private var registeredNextPreloadUrl: String? = null

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
        // Register the neighboring videos for background preloading as early as possible —
        // ideally well before the user ever swipes — so landing on one hands the shared player
        // an already-buffered source instead of starting cold. Ranked by position in
        // orderedIds, the same ordering swipe navigation itself uses.
        if (currentIndex != -1) videoPlaybackManager.setCurrentIndex(currentIndex)
        viewModelScope.launch {
            videoSwipeTargets.collect { targets ->
                if (registeredPreviousPreloadUrl != targets.previousVideoUrl) {
                    registeredPreviousPreloadUrl?.let(videoPlaybackManager::clearPreload)
                    registeredPreviousPreloadUrl = targets.previousVideoUrl
                }
                if (registeredNextPreloadUrl != targets.nextVideoUrl) {
                    registeredNextPreloadUrl?.let(videoPlaybackManager::clearPreload)
                    registeredNextPreloadUrl = targets.nextVideoUrl
                }
                targets.previousVideoUrl?.let { url ->
                    videoPlaybackManager.preload(url, orderedIds.indexOf(targets.previousId))
                }
                targets.nextVideoUrl?.let { url ->
                    videoPlaybackManager.preload(url, orderedIds.indexOf(targets.nextId))
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
            offlineVideoRepository.removeOffline(id)
            repository.deleteById(id)
            _deleted.emit(Unit)
        }
    }

    fun saveOffline(videoUrl: String) = offlineVideoRepository.saveOffline(id, videoUrl)

    fun removeOffline() = offlineVideoRepository.removeOffline(id)

    fun addTag(tag: String) {
        val record = _uiState.value?.record ?: return
        val trimmed = tag.trim()
        if (trimmed.isEmpty()) return
        if (record.tags.any { it.equals(trimmed, ignoreCase = true) }) return
        viewModelScope.launch { repository.updateTags(id, record.tags + trimmed) }
    }

    fun removeTag(tag: String) {
        val record = _uiState.value?.record ?: return
        viewModelScope.launch {
            repository.updateTags(id, record.tags.filterNot { it.equals(tag, ignoreCase = true) })
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
        registeredPreviousPreloadUrl?.let(videoPlaybackManager::clearPreload)
        registeredNextPreloadUrl?.let(videoPlaybackManager::clearPreload)
        if (pendingSave) {
            debounceJob?.cancel()
            val notesSnapshot = _notes.value
            CoroutineScope(Dispatchers.IO).launch {
                repository.updateNotes(id, notesSnapshot)
            }
        }
    }

    companion object {
        fun factory(id: Long, orderedIds: List<Long> = emptyList()): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    val app = extras[APPLICATION_KEY] as CleanShareApplication
                    return DetailViewModel(
                        id,
                        orderedIds,
                        app.shareRepository,
                        app.workScheduler,
                        app.syncManager,
                        app.offlineVideoRepository,
                        app.syncClient,
                        app.videoPlaybackManager,
                    ) as T
                }
            }
    }
}
