package io.github.gobi12b.reclaimlife.data

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
        /** Comma-separated epoch millis of reels counted in the last hour, for the hourly limit. */
        val RECENT_REEL_TIMES = stringPreferencesKey("recent_reel_times")
        /** Breaks today that reopened reels at the hourly limit — how hourly-only days are scored. */
        val HOURLY_BREAKS = intPreferencesKey("hourly_breaks")
        /** Time each target app was in front over the last 24 hours — see [UsageSpan]. */
        val APP_USAGE_SPANS = stringPreferencesKey("app_usage_spans")
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
     * Closes out the previous day into the within/exceeded tally, using [currentLimit] and [mode] as
     * stand-ins for that day's (we don't keep a per-day history of setting changes); see [dayWithinLimit]. Call this once when the
     * app or service wakes up, before anything else reads today's count, so a day with zero app opens
     * doesn't just silently vanish without ever being tallied.
     */
    suspend fun closeOutPreviousDayIfNeeded(currentLimit: Int, mode: LimitMode) {
        context.usageDataStore.edit { prefs ->
            val today = todayKey()
            val storedDate = prefs[Keys.COUNT_DATE]
            if (storedDate != null && storedDate != today) {
                val finalCount = prefs[Keys.REEL_COUNT] ?: 0
                val finalAllowance = prefs[Keys.EXTRA_ALLOWANCE] ?: 0
                val hourlyBreaks = prefs[Keys.HOURLY_BREAKS] ?: 0
                val within = dayWithinLimit(mode, finalCount, currentLimit, finalAllowance, hourlyBreaks)
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

    /**
     * Timestamps of reels counted in roughly the last hour (see [reelsInWindow] for the exact
     * window). Deliberately not reset at midnight — an hour that straddles it is still one hour.
     */
    val recentReelTimes: Flow<List<Long>> =
        context.usageDataStore.data.map { parseReelTimes(it[Keys.RECENT_REEL_TIMES]) }

    /** Spans of time Instagram/YouTube were in front, trimmed to roughly the last 24 hours. */
    val appUsageSpans: Flow<List<UsageSpan>> =
        context.usageDataStore.data.map { parseUsageSpans(it[Keys.APP_USAGE_SPANS]) }

    /** Records that [packageName] was in front from [startMs] to [endMs]. */
    suspend fun addAppUsage(packageName: String, startMs: Long, endMs: Long) {
        context.usageDataStore.edit { prefs ->
            val spans = addUsageSpan(
                parseUsageSpans(prefs[Keys.APP_USAGE_SPANS]),
                UsageSpan(packageName, startMs, endMs),
                System.currentTimeMillis()
            )
            prefs[Keys.APP_USAGE_SPANS] = serializeUsageSpans(spans)
        }
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
            val now = System.currentTimeMillis()
            val recent = reelsInWindow(parseReelTimes(prefs[Keys.RECENT_REEL_TIMES]), now) + List(amount) { now }
            // Only the newest ones can ever matter against the limit; this bounds the stored string.
            prefs[Keys.RECENT_REEL_TIMES] = serializeReelTimes(recent.takeLast(MAX_STORED_REEL_TIMES))
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

    /**
     * Starts the hourly window afresh — the reward for finishing a 2-minute swap at the hourly
     * limit. Today's count (and so the daily limit) is untouched; the break is tallied for scoring.
     */
    suspend fun clearHourlyWindow() {
        context.usageDataStore.edit { prefs ->
            rollDateIfNeeded(prefs, todayKey())
            prefs.remove(Keys.RECENT_REEL_TIMES)
            prefs[Keys.HOURLY_BREAKS] = (prefs[Keys.HOURLY_BREAKS] ?: 0) + 1
        }
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
            prefs[Keys.HOURLY_BREAKS] = 0
        }
    }

    private companion object {
        const val MAX_STORED_REEL_TIMES = MAX_HOURLY_REEL_LIMIT + 50
    }
}
