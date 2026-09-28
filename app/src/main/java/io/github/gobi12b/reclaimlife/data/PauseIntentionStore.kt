package io.github.gobi12b.reclaimlife.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Deliberately outside the backup rules: the intention lives on this phone only, and only for as
// long as its pause.
private val Context.pauseIntentionDataStore by preferencesDataStore(name = "pause_intention")

/**
 * The typed intention of a running Rest of today ("Movie night with friends"), for the Home
 * banner. Deleted when the pause ends; never written to the pause log.
 */
class PauseIntentionStore(private val context: Context) {

    private val key = stringPreferencesKey("intention")

    val intention: Flow<String?> = context.pauseIntentionDataStore.data.map { it[key] }

    suspend fun set(text: String) {
        context.pauseIntentionDataStore.edit { it[key] = text.trim().take(INTENTION_MAX_CHARS) }
    }

    suspend fun clear() {
        context.pauseIntentionDataStore.edit { it.remove(key) }
    }
}
