package com.maroney.androidsharesanitizer.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.maroney.androidsharesanitizer.data.ShareDatabase
import com.maroney.androidsharesanitizer.data.ShareRecord
import com.maroney.androidsharesanitizer.data.ShareRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(private val repository: ShareRepository) : ViewModel() {

    val history: StateFlow<List<ShareRecord>> = repository.getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun clearHistory() {
        viewModelScope.launch { repository.deleteAll() }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                // APPLICATION_KEY is provided by the Compose viewModel() infrastructure
                // when the activity or composable has an Application in its extras.
                val app = extras[APPLICATION_KEY]
                    ?: error("HistoryViewModel.Factory requires an Application context")
                val db = ShareDatabase.getInstance(app)
                return HistoryViewModel(ShareRepository(db.shareDao())) as T
            }
        }

        /** Fallback factory for callers that only have a plain [Context]. */
        fun factoryFrom(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val db = ShareDatabase.getInstance(context.applicationContext)
                    return HistoryViewModel(ShareRepository(db.shareDao())) as T
                }
            }
    }
}
