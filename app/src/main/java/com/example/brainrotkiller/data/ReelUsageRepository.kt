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

/**
 * Tracks how many reels have been counted today, resetting automatically at local midnight.
 * Also tracks the "just a few more" grace allowance shown once someone hits their limit —
 * the allowance and how many times they've asked for it both reset with the daily count.
 */
class ReelUsageRepository(private val context: Context) {

    private object Keys {
        val REEL_COUNT = intPreferencesKey("reel_count")
        val COUNT_DATE = stringPreferencesKey("count_date")
        val EXTRA_ALLOWANCE = intPreferencesKey("extra_allowance")
        val EXTRA_ATTEMPTS = intPreferencesKey("extra_attempts")
        val DAYS_WITHIN_LIMIT = intPreferencesKey("days_within_limit")
        val DAYS_EXCEEDED_LIMIT = intPreferencesKey("days_exceeded_limit")
        val DAY_HISTORY = stringPreferencesKey("day_history")
    }

    /** Lifetime count of past days that ended at or under that day's limit. */
    val daysWithinLimit: Flow<Int> = context.usageDataStore.data.map { it[Keys.DAYS_WITHIN_LIMIT] ?: 0 }

    /** Lifetime count of past days that ended over that day's limit. */
    val daysExceededLimit: Flow<Int> = context.usageDataStore.data.map { it[Keys.DAYS_EXCEEDED_LIMIT] ?: 0 }

    /**
     * Per-date outcome ([DayOutcome]) of closed-out days. Only days closed out since this was
     * added are in it — the lifetime totals above predate it and aren't back-filled.
     */
    val dayHistory: Flow<Map<String, DayOutcome>> =
        context.usageDataStore.data.map { parseDayHistory(it[Keys.DAY_HISTORY]) }

    /**
     * Closes out the previous day into the within/exceeded tally, using [currentLimit] as a stand-in
     * for that day's limit (we don't keep a per-day history of limit changes). Call this once when the
     * app or service wakes up, before anything else reads today's count, so a day with zero app opens
     * doesn't just silently vanish without ever being tallied.
     */
    suspend fun closeOutPreviousDayIfNeeded(currentLimit: Int) {
        context.usageDataStore.edit { prefs ->
            val today = todayKey()
            val storedDate = prefs[Keys.COUNT_DATE]
            if (storedDate != null && storedDate != today) {
                val finalCount = prefs[Keys.REEL_COUNT] ?: 0
                val finalAllowance = prefs[Keys.EXTRA_ALLOWANCE] ?: 0
                val within = finalCount <= currentLimit + finalAllowance
                val key = if (within) Keys.DAYS_WITHIN_LIMIT else Keys.DAYS_EXCEEDED_LIMIT
                prefs[key] = (prefs[key] ?: 0) + 1
                val history = parseDayHistory(prefs[Keys.DAY_HISTORY]).toMutableMap()
                history[storedDate] = if (within) DayOutcome.WITHIN else DayOutcome.OVER
                prefs[Keys.DAY_HISTORY] = serializeDayHistory(history)
            }
            rollDateIfNeeded(prefs, today)
        }
    }

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** Reel count for today. Emits 0 as soon as the stored date rolls over, even without a write. */
    val todayCount: Flow<Int> = context.usageDataStore.data.map { prefs ->
        if (prefs[Keys.COUNT_DATE] == todayKey()) prefs[Keys.REEL_COUNT] ?: 0 else 0
    }

    /** Extra reels granted today on top of the daily limit, via the "just a few more" flow. */
    val todayExtraAllowance: Flow<Int> = context.usageDataStore.data.map { prefs ->
        if (prefs[Keys.COUNT_DATE] == todayKey()) prefs[Keys.EXTRA_ALLOWANCE] ?: 0 else 0
    }

    /** How many times today someone has asked for more after being blocked. */
    val todayExtraAttempts: Flow<Int> = context.usageDataStore.data.map { prefs ->
        if (prefs[Keys.COUNT_DATE] == todayKey()) prefs[Keys.EXTRA_ATTEMPTS] ?: 0 else 0
    }

    /** Adds [amount] reels (usually 1; more if a pager skipped past several at once). */
    suspend fun incrementAndGet(amount: Int = 1): Int {
        var result = 0
        context.usageDataStore.edit { prefs ->
            val today = todayKey()
            val current = if (prefs[Keys.COUNT_DATE] == today) prefs[Keys.REEL_COUNT] ?: 0 else 0
            result = current + amount
            rollDateIfNeeded(prefs, today)
            prefs[Keys.REEL_COUNT] = result
        }
        return result
    }

    /** Grants [amount] extra reels for today and bumps the attempt counter. Returns the new attempt count. */
    suspend fun grantExtraAndGet(amount: Int): Int {
        var attempts = 0
        context.usageDataStore.edit { prefs ->
            val today = todayKey()
            val currentAllowance = if (prefs[Keys.COUNT_DATE] == today) prefs[Keys.EXTRA_ALLOWANCE] ?: 0 else 0
            val currentAttempts = if (prefs[Keys.COUNT_DATE] == today) prefs[Keys.EXTRA_ATTEMPTS] ?: 0 else 0
            attempts = currentAttempts + 1
            rollDateIfNeeded(prefs, today)
            prefs[Keys.EXTRA_ALLOWANCE] = currentAllowance + amount
            prefs[Keys.EXTRA_ATTEMPTS] = attempts
        }
        return attempts
    }

    /** Records an ask for more without granting anything yet (used to advance past the guilt/walk gates). */
    suspend fun recordExtraAttemptAndGet(): Int {
        var attempts = 0
        context.usageDataStore.edit { prefs ->
            val today = todayKey()
            val currentAttempts = if (prefs[Keys.COUNT_DATE] == today) prefs[Keys.EXTRA_ATTEMPTS] ?: 0 else 0
            attempts = currentAttempts + 1
            rollDateIfNeeded(prefs, today)
            prefs[Keys.EXTRA_ATTEMPTS] = attempts
        }
        return attempts
    }

    /** Resets count/allowance/attempts to zero for a new day if the stored date is stale. */
    private fun rollDateIfNeeded(prefs: androidx.datastore.preferences.core.MutablePreferences, today: String) {
        if (prefs[Keys.COUNT_DATE] != today) {
            prefs[Keys.COUNT_DATE] = today
            prefs[Keys.REEL_COUNT] = 0
            prefs[Keys.EXTRA_ALLOWANCE] = 0
            prefs[Keys.EXTRA_ATTEMPTS] = 0
        }
    }
}
