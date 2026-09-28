package io.github.gobi12b.reclaimlife.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Its own file: up to 400 days of per-app rows, rewritten once a day, kept apart from reel_usage
// (rewritten on every reel) so a count never has to rewrite a year of history.
private val Context.dailyUsageDataStore by preferencesDataStore(name = "daily_usage")

/** Closed ReclaimLife days ([DailyRecord]), for month, year and "since you started". */
class DailyUsageRepository(private val context: Context) {

    private object Keys {
        val DAILY_USAGE = stringPreferencesKey("daily_usage")
        /** When tracking with this data model began: the recorder never closes days before it. */
        val RECORDING_SINCE_MS = longPreferencesKey("recording_since_ms")
    }

    val records: Flow<Map<String, DailyRecord>> =
        context.dailyUsageDataStore.data.map { parseDailyUsage(it[Keys.DAILY_USAGE]) }

    val recordingSinceMs: Flow<Long?> = context.dailyUsageDataStore.data.map { it[Keys.RECORDING_SINCE_MS] }

    /** Sets the recording start once; later calls keep the first. */
    suspend fun startRecordingIfNeeded(nowMs: Long) {
        context.dailyUsageDataStore.edit { if (it[Keys.RECORDING_SINCE_MS] == null) it[Keys.RECORDING_SINCE_MS] = nowMs }
    }

    /** Adds [newRecords]; a day already recorded is left as it was. */
    suspend fun addRecords(newRecords: Collection<DailyRecord>) {
        if (newRecords.isEmpty()) return
        context.dailyUsageDataStore.edit { prefs ->
            val existing = parseDailyUsage(prefs[Keys.DAILY_USAGE])
            val merged = existing + newRecords.filter { it.dateKey !in existing }.associateBy { it.dateKey }
            prefs[Keys.DAILY_USAGE] = serializeDailyUsage(merged)
        }
    }
}
