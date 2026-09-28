package io.github.gobi12b.reclaimlife.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
        /** Your tree's closed days — see [parseGrowthLedger]. Append-only; trimmed rows go to [GROWTH_CARRIED]. */
        val GROWTH_LEDGER = stringPreferencesKey("growth_ledger")
        val GROWTH_CARRIED = longPreferencesKey("growth_carried")
        /** 1 once the upgrade backfill has run. */
        val GROWTH_BACKFILLED = intPreferencesKey("growth_backfilled")
    }

    val growthLedger: Flow<Map<String, GrowthDay>> = context.dailyUsageDataStore.data.map { parseGrowthLedger(it[Keys.GROWTH_LEDGER]) }

    val growthCarried: Flow<Long> = context.dailyUsageDataStore.data.map { it[Keys.GROWTH_CARRIED] ?: 0L }

    val growthBackfilled: Flow<Boolean> = context.dailyUsageDataStore.data.map { (it[Keys.GROWTH_BACKFILLED] ?: 0) == 1 }

    /**
     * Adds closed days to the ledger (a day already there is kept as it was), trims to the newest
     * [GROWTH_LEDGER_KEEP] and carries the dropped points, all in one edit so the total never dips.
     * [carriedGrant] and [markBackfilled] are the one-time upgrade backfill, applied only once.
     */
    suspend fun addGrowth(rows: Collection<GrowthDay>, carriedGrant: Long = 0L, markBackfilled: Boolean = false) {
        context.dailyUsageDataStore.edit { prefs ->
            val backfilled = (prefs[Keys.GROWTH_BACKFILLED] ?: 0) == 1
            if (markBackfilled && backfilled) return@edit
            val (kept, dropped) = trimLedger(addGrowthDays(parseGrowthLedger(prefs[Keys.GROWTH_LEDGER]), rows))
            prefs[Keys.GROWTH_LEDGER] = serializeGrowthLedger(kept)
            prefs[Keys.GROWTH_CARRIED] = (prefs[Keys.GROWTH_CARRIED] ?: 0L) + dropped + carriedGrant.coerceAtLeast(0L)
            if (markBackfilled) prefs[Keys.GROWTH_BACKFILLED] = 1
        }
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
