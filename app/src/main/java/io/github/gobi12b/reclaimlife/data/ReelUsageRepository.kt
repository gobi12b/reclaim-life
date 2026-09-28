package io.github.gobi12b.reclaimlife.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.text.SimpleDateFormat
import java.util.Calendar
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
        /** Legacy combined count; only read to migrate it into [REEL_COUNT_BY_APP]. */
        val REEL_COUNT = intPreferencesKey("reel_count")
        /** Today's reels per app — see [parseReelCounts]. */
        val REEL_COUNT_BY_APP = stringPreferencesKey("reel_count_by_app")
        /** Per-app reels of the last few closed reel days, picked up by the daily-usage recorder. */
        val REELS_BY_DAY = stringPreferencesKey("reels_by_day")
        /** Every pause, for 90 days — see [PauseEntry]. */
        val PAUSE_LOG = stringPreferencesKey("pause_log")
        /** Closed stretches when tracking was off (service switched off, phone off). */
        val OFF_INTERVALS = stringPreferencesKey("tracking_off_intervals")
        /** Set while the service is switched off; closed into [OFF_INTERVALS] when it's back. */
        val OFF_SINCE_MS = longPreferencesKey("tracking_off_since_ms")
        /** The service's last sign of life, and the boot it was in, to notice the phone was off. */
        val LAST_ALIVE_MS = longPreferencesKey("service_last_alive_ms")
        val LAST_BOOT_MS = longPreferencesKey("service_last_boot_ms")
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
        /** Skip/continue choices on the open-pause screen, for Insights. */
        val GATE_DECISIONS = stringPreferencesKey("gate_decisions")
        /** Finished 2-minute swaps per local day, for two weeks — see [parseSwapsByDay]. */
        val SWAPS_BY_DAY = stringPreferencesKey("swaps_by_day")
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
     * stand-ins for that day's (see [dayWithinLimit]). Call this once when the app or service wakes
     * up, before anything else reads today's count, so a day with zero app opens doesn't just
     * silently vanish without ever being tallied. The first reel of a new day does the same.
     */
    suspend fun closeOutPreviousDayIfNeeded(currentLimit: Int, mode: LimitMode) {
        context.usageDataStore.edit { prefs -> rollDateIfNeeded(prefs, todayKey(), currentLimit, mode) }
    }

    /** Tallies the stored day's outcome before it's reset, so the streak, week and tree never miss it. */
    private fun recordOutcome(prefs: MutablePreferences, storedDate: String, limit: Int, mode: LimitMode) {
        val finalCount = totalReels(countsOf(prefs))
        val finalAllowance = prefs[Keys.EXTRA_ALLOWANCE] ?: 0
        val hourlyBreaks = prefs[Keys.HOURLY_BREAKS] ?: 0
        val within = dayWithinLimit(mode, finalCount, limit, finalAllowance, hourlyBreaks)
        val history = parseDayHistory(prefs[Keys.DAY_HISTORY]).toMutableMap()
        // Recorded once: a date already in the history keeps its outcome and isn't tallied again.
        if (storedDate in history) return
        val key = if (within) Keys.DAYS_WITHIN_LIMIT else Keys.DAYS_EXCEEDED_LIMIT
        prefs[key] = (prefs[key] ?: 0) + 1
        history[storedDate] = if (within) DayOutcome.WITHIN else DayOutcome.OVER
        prefs[Keys.DAY_HISTORY] = serializeDayHistory(history)
    }

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun countsOf(prefs: Preferences): Map<String, Int> =
        reelCountsFromStored(prefs[Keys.REEL_COUNT_BY_APP], prefs[Keys.REEL_COUNT])

    /**
     * Today's reels per app (plus "Earlier today" on upgrade day — see [EARLIER_TODAY_KEY]). Empty
     * as soon as the stored date rolls over, even without a write.
     */
    val todayCountsByApp: Flow<Map<String, Int>> = context.usageDataStore.data.map { prefs ->
        if (prefs[Keys.COUNT_DATE] == todayKey()) countsOf(prefs) else emptyMap()
    }

    /** Reel count for today: the sum over apps, which is what the daily limit is on. */
    val todayCount: Flow<Int> = todayCountsByApp.map { totalReels(it) }

    /** Per-app reels of recently closed reel days (`yyyy-MM-dd` → app → reels). */
    val reelsByDay: Flow<Map<String, Map<String, Int>>> =
        context.usageDataStore.data.map { parseReelsByDay(it[Keys.REELS_BY_DAY]) }

    /** Extra reels granted today on top of the daily limit, via the "just a few more" flow. */
    val todayExtraAllowance: Flow<Int> = context.usageDataStore.data.map { prefs ->
        if (prefs[Keys.COUNT_DATE] == todayKey()) prefs[Keys.EXTRA_ALLOWANCE] ?: 0 else 0
    }

    /** Breaks today that reopened reels at the hourly limit — how an hourly-only day is scored. */
    val todayHourlyBreaks: Flow<Int> = context.usageDataStore.data.map { prefs ->
        if (prefs[Keys.COUNT_DATE] == todayKey()) prefs[Keys.HOURLY_BREAKS] ?: 0 else 0
    }

    /** 2-minute swaps finished per local day, for two weeks — each grows the tree a little. */
    val swapsByDay: Flow<Map<String, Int>> = context.usageDataStore.data.map { parseSwapsByDay(it[Keys.SWAPS_BY_DAY]) }

    /** A swap ran to the end and its finish button was tapped (not skipped or closed). */
    suspend fun recordSwapCompleted(nowMs: Long) {
        context.usageDataStore.edit { prefs ->
            val key = dateKeyOf(Calendar.getInstance().apply { timeInMillis = nowMs })
            val keepFrom = dateKeyOf(Calendar.getInstance().apply { timeInMillis = nowMs - GROWTH_LOG_RETENTION_MS })
            val swaps = parseSwapsByDay(prefs[Keys.SWAPS_BY_DAY]).toMutableMap()
            swaps.merge(key, 1, Int::plus)
            prefs[Keys.SWAPS_BY_DAY] = serializeSwapsByDay(swaps, keepFrom)
        }
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

    /** Every pause in the last 90 days, oldest first. */
    val pauseLog: Flow<List<PauseEntry>> = context.usageDataStore.data.map { parsePauseLog(it[Keys.PAUSE_LOG]) }

    suspend fun addPause(entry: PauseEntry) {
        context.usageDataStore.edit { prefs ->
            val now = System.currentTimeMillis()
            prefs[Keys.PAUSE_LOG] = serializePauseLog(parsePauseLog(prefs[Keys.PAUSE_LOG]) + entry, now)
        }
    }

    /** Drops a pause that never started (Rest of today cancelled during its delayed start). */
    suspend fun removePauseStartingAt(startMs: Long) {
        context.usageDataStore.edit { prefs ->
            val now = System.currentTimeMillis()
            prefs[Keys.PAUSE_LOG] = serializePauseLog(parsePauseLog(prefs[Keys.PAUSE_LOG]).filterNot { it.startMs == startMs }, now)
        }
    }

    /** Ends any running pause at [nowMs] in the log (Resume now). Its budget isn't refunded. */
    suspend fun endPausesAt(nowMs: Long) {
        context.usageDataStore.edit { prefs ->
            val log = parsePauseLog(prefs[Keys.PAUSE_LOG]).map {
                if (it.startMs <= nowMs && it.endMs > nowMs) it.copy(endMs = nowMs) else it
            }
            prefs[Keys.PAUSE_LOG] = serializePauseLog(log, nowMs)
        }
    }

    /** Stretches tracking was off, including one still open (the service is switched off now). */
    val trackingOffIntervals: Flow<List<LongRange>> = context.usageDataStore.data.map { prefs ->
        val closed = parseOffIntervals(prefs[Keys.OFF_INTERVALS])
        val openSince = prefs[Keys.OFF_SINCE_MS]
        if (openSince != null) closed + listOf(openSince..Long.MAX_VALUE) else closed
    }

    /** When the service was last known to be running, or null before it ever ran. */
    val serviceLastAliveMs: Flow<Long?> = context.usageDataStore.data.map { it[Keys.LAST_ALIVE_MS] }

    /**
     * The service (re)connected. Closes an open "switched off" stretch; after a reboot, the time
     * from its last sign of life to the boot counts as off (the phone was off or restarting).
     */
    suspend fun markServiceConnected(nowMs: Long, bootMs: Long) {
        context.usageDataStore.edit { prefs ->
            val off = parseOffIntervals(prefs[Keys.OFF_INTERVALS]).toMutableList()
            val offSince = prefs[Keys.OFF_SINCE_MS]
            val lastAlive = prefs[Keys.LAST_ALIVE_MS]
            val lastBoot = prefs[Keys.LAST_BOOT_MS]
            if (offSince != null) {
                off += offSince..nowMs
            } else if (lastAlive != null && lastBoot != null && bootMs - lastBoot > BOOT_TOLERANCE_MS) {
                off += lastAlive..minOf(bootMs, nowMs)
            }
            prefs.remove(Keys.OFF_SINCE_MS)
            prefs[Keys.OFF_INTERVALS] = serializeOffIntervals(off, nowMs)
            prefs[Keys.LAST_ALIVE_MS] = nowMs
            prefs[Keys.LAST_BOOT_MS] = bootMs
        }
    }

    suspend fun markServiceAlive(nowMs: Long, bootMs: Long) {
        context.usageDataStore.edit {
            it[Keys.LAST_ALIVE_MS] = nowMs
            it[Keys.LAST_BOOT_MS] = bootMs
        }
    }

    /** The user switched the service off: tracking is off from now until it reconnects. */
    suspend fun markServiceDisconnected(nowMs: Long) {
        context.usageDataStore.edit { if (it[Keys.OFF_SINCE_MS] == null) it[Keys.OFF_SINCE_MS] = nowMs }
    }

    /** Spans of time Instagram/YouTube were in front, kept for about a week (see [USAGE_RETENTION_MS]). */
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

    /** Skip/continue choices on the open-pause screen over roughly the last week. */
    val gateDecisions: Flow<List<GateDecision>> =
        context.usageDataStore.data.map { parseGateDecisions(it[Keys.GATE_DECISIONS]) }

    suspend fun recordGateDecision(skipped: Boolean) {
        context.usageDataStore.edit { prefs ->
            val now = System.currentTimeMillis()
            val kept = parseGateDecisions(prefs[Keys.GATE_DECISIONS]).filter { it.timeMs > now - GATE_DECISION_RETENTION_MS }
            prefs[Keys.GATE_DECISIONS] = serializeGateDecisions(kept + GateDecision(now, skipped))
        }
    }

    /**
     * Adds [amount] reels in [packageName] (usually 1; more if a pager skipped past several at
     * once). Returns today's new total.
     */
    suspend fun incrementAndGet(packageName: String, amount: Int, dailyLimit: Int, mode: LimitMode): Int {
        var result = 0
        context.usageDataStore.edit { prefs ->
            val today = todayKey()
            rollDateIfNeeded(prefs, today, dailyLimit, mode)
            val counts = countsOf(prefs).toMutableMap()
            counts.merge(packageName, amount, Int::plus)
            result = totalReels(counts)
            prefs[Keys.REEL_COUNT_BY_APP] = serializeReelCounts(counts)
            prefs.remove(Keys.REEL_COUNT)
            val now = System.currentTimeMillis()
            val recent = reelsInWindow(parseReelTimes(prefs[Keys.RECENT_REEL_TIMES]), now) + List(amount) { now }
            // Only the newest ones can ever matter against the limit; this bounds the stored string.
            prefs[Keys.RECENT_REEL_TIMES] = serializeReelTimes(recent.takeLast(MAX_STORED_REEL_TIMES))
        }
        return result
    }

    /** Grants [amount] extra reels for today and bumps the attempt counter. Returns the new attempt count. */
    suspend fun grantExtraAndGet(amount: Int, dailyLimit: Int, mode: LimitMode): Int {
        var attempts = 0
        context.usageDataStore.edit { prefs ->
            val today = todayKey()
            val currentAllowance = if (prefs[Keys.COUNT_DATE] == today) prefs[Keys.EXTRA_ALLOWANCE] ?: 0 else 0
            val currentAttempts = if (prefs[Keys.COUNT_DATE] == today) prefs[Keys.EXTRA_ATTEMPTS] ?: 0 else 0
            attempts = currentAttempts + 1
            rollDateIfNeeded(prefs, today, dailyLimit, mode)
            prefs[Keys.EXTRA_ALLOWANCE] = currentAllowance + amount
            prefs[Keys.EXTRA_ATTEMPTS] = attempts
        }
        return attempts
    }

    /**
     * Starts the hourly window afresh — the reward for finishing a 2-minute swap at the hourly
     * limit. Today's count (and so the daily limit) is untouched; the break is tallied for scoring.
     */
    suspend fun clearHourlyWindow(dailyLimit: Int, mode: LimitMode) {
        context.usageDataStore.edit { prefs ->
            rollDateIfNeeded(prefs, todayKey(), dailyLimit, mode)
            prefs.remove(Keys.RECENT_REEL_TIMES)
            prefs[Keys.HOURLY_BREAKS] = (prefs[Keys.HOURLY_BREAKS] ?: 0) + 1
        }
    }

    /** Records an ask for more without granting anything yet (used to advance past the guilt/walk gates). */
    suspend fun recordExtraAttemptAndGet(dailyLimit: Int, mode: LimitMode): Int {
        var attempts = 0
        context.usageDataStore.edit { prefs ->
            val today = todayKey()
            val currentAttempts = if (prefs[Keys.COUNT_DATE] == today) prefs[Keys.EXTRA_ATTEMPTS] ?: 0 else 0
            attempts = currentAttempts + 1
            rollDateIfNeeded(prefs, today, dailyLimit, mode)
            prefs[Keys.EXTRA_ATTEMPTS] = attempts
        }
        return attempts
    }

    /**
     * Resets count/allowance/attempts to zero for a new day if the stored date is stale, after
     * recording the outgoing day's outcome against [dailyLimit] and [mode] (the current settings stand
     * in for that day's). The closed day's per-app reels are kept briefly in [Keys.REELS_BY_DAY] for
     * the daily-usage record.
     */
    private fun rollDateIfNeeded(prefs: MutablePreferences, today: String, dailyLimit: Int, mode: LimitMode) {
        val storedDate = prefs[Keys.COUNT_DATE]
        if (storedDate != today) {
            if (storedDate != null) recordOutcome(prefs, storedDate, dailyLimit, mode)
            val closed = countsOf(prefs).filterKeys { it != EARLIER_TODAY_KEY }
            if (storedDate != null && closed.isNotEmpty()) {
                val byDay = parseReelsByDay(prefs[Keys.REELS_BY_DAY]).toMutableMap()
                byDay[storedDate] = closed
                prefs[Keys.REELS_BY_DAY] = serializeReelsByDay(byDay)
            }
            prefs[Keys.COUNT_DATE] = today
            prefs.remove(Keys.REEL_COUNT)
            prefs[Keys.REEL_COUNT_BY_APP] = ""
            prefs[Keys.EXTRA_ALLOWANCE] = 0
            prefs[Keys.EXTRA_ATTEMPTS] = 0
            prefs[Keys.HOURLY_BREAKS] = 0
        }
    }

    private companion object {
        const val MAX_STORED_REEL_TIMES = MAX_HOURLY_REEL_LIMIT + 50
        /** Boot time read from two clocks jitters a little; a real reboot moves it by far more. */
        const val BOOT_TOLERANCE_MS = 60_000L
    }
}
