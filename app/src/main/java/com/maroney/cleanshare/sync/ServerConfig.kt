package com.maroney.cleanshare.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ServerConfig(
    val manualHost: String? = null,
    val port: Int? = null,
)

private val Context.serverConfigDataStore by preferencesDataStore(name = "server_config")

class ServerConfigRepository(private val context: Context) {

    companion object {
        private val KEY_MANUAL_HOST = stringPreferencesKey("manual_host")
        private val KEY_PORT        = intPreferencesKey("port")
    }

    val config: Flow<ServerConfig> = context.serverConfigDataStore.data.map { prefs ->
        ServerConfig(
            manualHost = prefs[KEY_MANUAL_HOST],
            port       = prefs[KEY_PORT],
        )
    }

    suspend fun setManualHost(host: String?) {
        context.serverConfigDataStore.edit { prefs ->
            if (host == null) prefs.remove(KEY_MANUAL_HOST) else prefs[KEY_MANUAL_HOST] = host
        }
    }

    suspend fun setPort(port: Int?) {
        context.serverConfigDataStore.edit { prefs ->
            if (port == null) prefs.remove(KEY_PORT) else prefs[KEY_PORT] = port
        }
    }
}
