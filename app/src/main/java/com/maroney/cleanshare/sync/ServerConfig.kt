package com.maroney.cleanshare.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ServerConfig(
    val manualHost: String? = null,
    val port: Int? = null,
    val lastSeenAt: Long? = null,
)

private val Context.serverConfigDataStore by preferencesDataStore(name = "server_config")

class ServerConfigRepository(private val context: Context) {

    companion object {
        private val KEY_MANUAL_HOST  = stringPreferencesKey("manual_host")
        private val KEY_PORT         = intPreferencesKey("port")
        private val KEY_LAST_SEEN_AT = longPreferencesKey("last_seen_at")
    }

    val config: Flow<ServerConfig> = context.serverConfigDataStore.data.map { prefs ->
        ServerConfig(
            manualHost = prefs[KEY_MANUAL_HOST],
            port       = prefs[KEY_PORT],
            lastSeenAt = prefs[KEY_LAST_SEEN_AT],
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

    suspend fun setLastSeenAt(ts: Long) {
        context.serverConfigDataStore.edit { prefs ->
            prefs[KEY_LAST_SEEN_AT] = ts
        }
    }
}
