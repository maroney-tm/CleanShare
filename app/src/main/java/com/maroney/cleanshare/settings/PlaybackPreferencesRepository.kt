package com.maroney.cleanshare.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.playbackPreferencesDataStore by preferencesDataStore(name = "playback_preferences")

/** App-wide video playback preferences, independent of any single share/video. */
class PlaybackPreferencesRepository(private val context: Context) {

    companion object {
        private val KEY_LOOP_VIDEOS_BY_DEFAULT = booleanPreferencesKey("loop_videos_by_default")
    }

    val loopVideosByDefault: Flow<Boolean> = context.playbackPreferencesDataStore.data.map { prefs ->
        prefs[KEY_LOOP_VIDEOS_BY_DEFAULT] ?: false
    }

    suspend fun setLoopVideosByDefault(enabled: Boolean) {
        context.playbackPreferencesDataStore.edit { prefs ->
            prefs[KEY_LOOP_VIDEOS_BY_DEFAULT] = enabled
        }
    }
}
