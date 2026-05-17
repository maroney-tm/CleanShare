package com.maroney.cleanshare.sync

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ServerConfig(
    val manualHost: String? = null,
    /** Explicit port override. Null = use scheme default (443 for https, 80 for http). */
    val port: Int? = null,
    val autoDiscover: Boolean = true,
    val resolvedHost: String? = null,
    /** Port saved from the last successful mDNS discovery, so reconnects work without re-discovering. */
    val resolvedPort: Int? = null,
) {
    /** The host to actually connect to: manual override wins, else last mDNS result. */
    val effectiveHost: String?
        get() = manualHost ?: resolvedHost
}

private val Context.serverConfigDataStore by preferencesDataStore(name = "server_config")

class ServerConfigRepository(private val context: Context) {

    companion object {
        private val KEY_MANUAL_HOST    = stringPreferencesKey("manual_host")
        private val KEY_PORT           = intPreferencesKey("port")
        private val KEY_AUTO_DISCOVER  = booleanPreferencesKey("auto_discover")
        private val KEY_RESOLVED_HOST  = stringPreferencesKey("resolved_host")
        private val KEY_RESOLVED_PORT  = intPreferencesKey("resolved_port")
    }

    val config: Flow<ServerConfig> = context.serverConfigDataStore.data.map { prefs ->
        ServerConfig(
            manualHost    = prefs[KEY_MANUAL_HOST],
            port          = prefs[KEY_PORT],          // null when unset → use scheme default
            autoDiscover  = prefs[KEY_AUTO_DISCOVER] ?: true,
            resolvedHost  = prefs[KEY_RESOLVED_HOST],
            resolvedPort  = prefs[KEY_RESOLVED_PORT],
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

    suspend fun setAutoDiscover(enabled: Boolean) {
        context.serverConfigDataStore.edit { it[KEY_AUTO_DISCOVER] = enabled }
    }

    suspend fun setResolvedHost(host: String?) {
        context.serverConfigDataStore.edit { prefs ->
            if (host == null) prefs.remove(KEY_RESOLVED_HOST)
            else prefs[KEY_RESOLVED_HOST] = host
        }
    }

    suspend fun setResolvedPort(port: Int?) {
        context.serverConfigDataStore.edit { prefs ->
            if (port == null) prefs.remove(KEY_RESOLVED_PORT)
            else prefs[KEY_RESOLVED_PORT] = port
        }
    }
}
