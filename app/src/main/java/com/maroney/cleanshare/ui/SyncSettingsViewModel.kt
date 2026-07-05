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

    fun setManualHost(raw: String) {
        viewModelScope.launch {
            val trimmed = raw.trim()
            if (trimmed.isBlank()) {
                configRepo.setManualHost(null)
                configRepo.setPort(null)
                return@launch
            }
            // Accept "ip:port" notation (e.g. "192.168.1.60:8765").
            val colonIdx = trimmed.lastIndexOf(':')
            val parsedPort = if (colonIdx > 0) trimmed.substring(colonIdx + 1).toIntOrNull() else null
            val host = if (parsedPort != null) trimmed.substring(0, colonIdx) else trimmed
            configRepo.setManualHost(host.ifBlank { null })
            configRepo.setPort(parsedPort)
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            syncManager.resolveAndSync()
            // Covers first-time setup: if this is the first successful connection this
            // session, SSE hasn't started yet (nothing else triggers it once the app is
            // already foregrounded). No-op if already listening.
            syncManager.startListening()
        }
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
