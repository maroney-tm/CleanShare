package com.maroney.cleanshare.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.maroney.cleanshare.CleanShareApplication
import com.maroney.cleanshare.sync.ConnectionStatus
import com.maroney.cleanshare.sync.ServerConfig
import com.maroney.cleanshare.sync.ServerConfigRepository
import com.maroney.cleanshare.sync.SyncManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SyncSettingsViewModel(
    private val configRepo: ServerConfigRepository,
    private val syncManager: SyncManager,
) : ViewModel() {

    val config: StateFlow<ServerConfig> = configRepo.config
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServerConfig())

    val connectionStatus: StateFlow<ConnectionStatus> = syncManager.connectionStatus

    fun setAutoDiscover(enabled: Boolean) {
        viewModelScope.launch { configRepo.setAutoDiscover(enabled) }
    }

    fun setManualHost(raw: String) {
        viewModelScope.launch {
            val cleaned = raw.trim().ifBlank { null }
            configRepo.setManualHost(cleaned)
        }
    }

    fun testConnection() {
        viewModelScope.launch { syncManager.resolveAndSync() }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[APPLICATION_KEY] as CleanShareApplication
                return SyncSettingsViewModel(app.serverConfigRepository, app.syncManager) as T
            }
        }
    }
}
