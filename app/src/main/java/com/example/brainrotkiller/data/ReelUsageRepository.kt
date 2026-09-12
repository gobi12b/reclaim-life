package com.example.brainrotkiller.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.usageDataStore by preferencesDataStore(name = "reel_usage")

/** Tracks how many reels have been counted today, resetting automatically at local midnight. */
class ReelUsageRepository(private val context: Context) {

    private object Keys {
        val REEL_COUNT = intPreferencesKey("reel_count")
        val COUNT_DATE = stringPreferencesKey("count_date")
    }

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** Reel count for today. Emits 0 as soon as the stored date rolls over, even without a write. */
    val todayCount: Flow<Int> = context.usageDataStore.data.map { prefs ->
        if (prefs[Keys.COUNT_DATE] == todayKey()) prefs[Keys.REEL_COUNT] ?: 0 else 0
    }

    suspend fun incrementAndGet(): Int {
        var result = 0
        context.usageDataStore.edit { prefs ->
            val today = todayKey()
            val current = if (prefs[Keys.COUNT_DATE] == today) prefs[Keys.REEL_COUNT] ?: 0 else 0
            result = current + 1
            prefs[Keys.COUNT_DATE] = today
            prefs[Keys.REEL_COUNT] = result
        }
        return result
    }
}
