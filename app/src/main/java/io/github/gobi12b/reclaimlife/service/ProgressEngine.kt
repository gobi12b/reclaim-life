package io.github.gobi12b.reclaimlife.service

import io.github.gobi12b.reclaimlife.ReclaimLifeApp
import io.github.gobi12b.reclaimlife.data.AppStretch
import io.github.gobi12b.reclaimlife.data.BASELINE_HISTORY_DAYS
import io.github.gobi12b.reclaimlife.data.Baseline
import io.github.gobi12b.reclaimlife.data.DAY_MS
import io.github.gobi12b.reclaimlife.data.DailyRecord
import io.github.gobi12b.reclaimlife.data.HomeInsight
import io.github.gobi12b.reclaimlife.data.MONTH_MIN_DAYS
import io.github.gobi12b.reclaimlife.data.SavedHistory
import io.github.gobi12b.reclaimlife.data.TodaySaved
import io.github.gobi12b.reclaimlife.data.TreeState
import io.github.gobi12b.reclaimlife.data.quietDays
import io.github.gobi12b.reclaimlife.data.USAGE_WINDOW_MS
import io.github.gobi12b.reclaimlife.data.YEAR_MIN_DAY
import io.github.gobi12b.reclaimlife.data.baselineFromHistory
import io.github.gobi12b.reclaimlife.data.chooseInsight
import io.github.gobi12b.reclaimlife.data.clipTo
import io.github.gobi12b.reclaimlife.data.closeDay
import io.github.gobi12b.reclaimlife.data.dayNumberSince
import io.github.gobi12b.reclaimlife.data.merged
import io.github.gobi12b.reclaimlife.data.monthProjectionMs
import io.github.gobi12b.reclaimlife.data.nextReclaimDayStart
import io.github.gobi12b.reclaimlife.data.pausedIntervals
import io.github.gobi12b.reclaimlife.data.reclaimDayKey
import io.github.gobi12b.reclaimlife.data.reclaimDayStart
import io.github.gobi12b.reclaimlife.data.refineBaseline
import io.github.gobi12b.reclaimlife.data.savedHistory
import io.github.gobi12b.reclaimlife.data.savedToday
import io.github.gobi12b.reclaimlife.data.uncoveredMs
import io.github.gobi12b.reclaimlife.data.wakingWindow
import io.github.gobi12b.reclaimlife.data.yearProjectionMs
import io.github.gobi12b.reclaimlife.ui.common.appLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Everything Home, the widget and the pause's calm screen show about time won back, from one
 * calculation — so the numbers agree wherever they appear.
 */
data class Progress(
    val nowMs: Long,
    val dayStartMs: Long,
    /** Saved figures exist: at least one tracked app has a baseline. */
    val hasBaseline: Boolean,
    val today: TodaySaved,
    val history: SavedHistory,
    /** Day number since the earliest baseline; day 1 is the day it was taken. */
    val dayNumber: Int,
    /** When a provisional baseline settles, or null once every baseline is locked. */
    val provisionalUntilMs: Long?,
    /** Waking time so far today with tracking off (service off, phone off). */
    val trackingOffTodayMs: Long,
    /** A pause ran (or is running) today, so some time wasn't tracked. */
    val pausedToday: Boolean,
    /** Minutes come from Android's full record, not ReclaimLife's own timing. */
    val usageFromSystem: Boolean,
    val last24hByApp: Map<String, Long>,
    /** The 24 hours before that, all tracked apps together — for "18 min less than the day before". */
    val previous24hMs: Long,
    val insight: HomeInsight,
    /** Your tree. The widget and the pause's calm screen ignore it. */
    val tree: TreeState,
    /** Days with the counter running and no reels, never closed out: within, for the week strip and streak. */
    val quietDays: Set<String>
) {
    val last24hMs: Long get() = last24hByApp.values.sum()
    /** "≈ 21 h a month at this pace", from the 7-day average — null before [MONTH_MIN_DAYS] days. */
    val monthMs: Long? get() = if (history.daysWithData >= MONTH_MIN_DAYS) monthProjectionMs(history.averageLast7Ms) else null
    /** Only from day [YEAR_MIN_DAY]. */
    val yearMs: Long? get() = if (dayNumber >= YEAR_MIN_DAY && monthMs != null) yearProjectionMs(history.averageLast7Ms) else null
    /** Closed days plus today so far. */
    val sinceStartedMs: Long get() = history.sinceStartedMs + today.totalMs
}

/** Closing days and taking baselines both read then write; one at a time keeps them from doubling up. */
private val syncLock = Mutex()

/**
 * Brings stored progress up to date: closes finished ReclaimLife days into daily usage and takes
 * or refines baselines. Cheap when there's nothing to do; safe to call from any screen.
 */
suspend fun syncProgressData(app: ReclaimLifeApp, nowMs: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
    syncLock.withLock {
        app.dailyUsageRepository.startRecordingIfNeeded(nowMs)
        recordClosedDays(app, nowMs)
        ensureBaselines(app, nowMs)
        syncGrowth(app, nowMs)
    }
}

/** Time tracking was off, up to [nowMs]. A service that's down right now counts as off since it was last alive. */
private suspend fun offIntervals(app: ReclaimLifeApp, nowMs: Long): List<LongRange> {
    val usage = app.reelUsageRepository
    val off = usage.trackingOffIntervals.first().map { it.first..minOf(it.last, nowMs) }.toMutableList()
    val lastAlive = usage.serviceLastAliveMs.first()
    if (lastAlive != null && !isReelBlockerServiceRunning(app)) off += lastAlive..nowMs
    return off.merged()
}

/** Time tracking was off or paused — left out of both baseline and saved. */
internal suspend fun untrackedIntervals(app: ReclaimLifeApp, nowMs: Long): List<LongRange> =
    (offIntervals(app, nowMs) + pausedIntervals(app.reelUsageRepository.pauseLog.first(), nowMs)).merged()

internal fun List<AppStretch>.byApp(): Map<String, List<LongRange>> =
    groupBy { it.packageName }.mapValues { (_, list) -> list.map { it.startMs..it.endMs } }

private suspend fun recordClosedDays(app: ReclaimLifeApp, nowMs: Long) {
    val since = app.dailyUsageRepository.recordingSinceMs.first() ?: return
    val hour = app.settingsRepository.dayStartHour.first()
    val todayStart = reclaimDayStart(nowMs, hour)
    val existing = app.dailyUsageRepository.records.first()
    // Raw spans only go back about a week, so older gaps stay as "no data".
    val firstStart = maxOf(reclaimDayStart(since, hour), reclaimDayStart(nowMs, hour, daysAgo = 7))
    val days = generateSequence(firstStart) { nextReclaimDayStart(it) }
        .takeWhile { it < todayStart }
        .filter { reclaimDayKey(it) !in existing }
        .toList()
    if (days.isEmpty()) return

    val packages = app.settingsRepository.trackedApps.first().map { it.packageName }.toSet()
    val (stretches, _) = appStretches(app, packages, app.reelUsageRepository.appUsageSpans.first(), days.first(), todayStart)
    val byApp = stretches.byApp()
    val untracked = untrackedIntervals(app, nowMs)
    val reelsByDay = app.reelUsageRepository.reelsByDay.first()
    app.dailyUsageRepository.addRecords(days.map { start ->
        val key = reclaimDayKey(start)
        // Reels are counted per midnight day; they're filed under the ReclaimLife day of the same date.
        closeDay(key, start, nextReclaimDayStart(start), packages, byApp, reelsByDay[key].orEmpty(), untracked)
    })
}

/**
 * Baselines for tracked apps from Android's record of the [BASELINE_HISTORY_DAYS] days before now,
 * or as many as the phone keeps. Null without Usage access or with under 3 days.
 */
private fun readBaselines(app: ReclaimLifeApp, packages: Set<String>, nowMs: Long): Map<String, Baseline>? {
    if (packages.isEmpty()) return emptyMap()
    val earliest = earliestUsageEventMs(app, nowMs - BASELINE_HISTORY_DAYS * DAY_MS, nowMs) ?: return null
    val historyDays = ((nowMs - earliest) / DAY_MS).toInt().coerceAtMost(BASELINE_HISTORY_DAYS)
    val stretches = systemStretches(app, packages, nowMs - historyDays * DAY_MS, nowMs) ?: return null
    val msByApp = stretches.groupBy { it.packageName }.mapValues { (_, l) -> l.sumOf { it.endMs - it.startMs } }
    return packages.mapNotNull { pkg ->
        baselineFromHistory(msByApp[pkg] ?: 0L, historyDays, nowMs)?.let { pkg to it }
    }.toMap()
}

private suspend fun ensureBaselines(app: ReclaimLifeApp, nowMs: Long) {
    val settings = app.settingsRepository
    val current = settings.baselines.first()
    val missing = settings.trackedApps.first().map { it.packageName }.filter { it !in current }.toSet()
    if (missing.isNotEmpty() && hasUsageAccess(app)) {
        readBaselines(app, missing, nowMs)?.let { settings.addBaselines(it) }
    }

    val provisional = settings.baselines.first().filterValues { it.isProvisional }
    if (provisional.isEmpty()) return
    val hour = settings.dayStartHour.first()
    val records = app.dailyUsageRepository.records.first()
    val refined = provisional.mapValues { (pkg, baseline) ->
        val anchorDay = reclaimDayKey(reclaimDayStart(baseline.anchorMs, hour))
        // Only whole days after the anchor: the anchor day itself was partly before ReclaimLife.
        refineBaseline(baseline, records.values.filter { it.dateKey > anchorDay }, pkg, nowMs)
    }
    if (refined != provisional) settings.setBaselines(settings.baselines.first() + refined)
}

/**
 * What "Reset baseline" would set: a fresh read of the last 14 days for every tracked app. Null
 * without Usage access or enough history. Applying it is [ReclaimLifeApp.settingsRepository]'s job.
 */
suspend fun proposeBaselineReset(app: ReclaimLifeApp, nowMs: Long = System.currentTimeMillis()): Map<String, Baseline>? =
    withContext(Dispatchers.IO) {
        val packages = app.settingsRepository.trackedApps.first().map { it.packageName }.toSet()
        readBaselines(app, packages, nowMs)?.takeIf { it.isNotEmpty() }
    }

/** Brings data up to date, then works out [Progress] for now. Runs off the main thread. */
suspend fun computeProgress(app: ReclaimLifeApp, nowMs: Long = System.currentTimeMillis()): Progress = withContext(Dispatchers.IO) {
    syncProgressData(app, nowMs)
    val settings = app.settingsRepository
    val hour = settings.dayStartHour.first()
    val todayStart = reclaimDayStart(nowMs, hour)
    val tracked = settings.trackedApps.first().map { it.packageName }.toSet()
    val baselines = settings.baselines.first()
    val active = baselines.filterKeys { it in tracked }
    val records = app.dailyUsageRepository.records.first()

    val since = minOf(todayStart, nowMs - 2 * USAGE_WINDOW_MS)
    val (stretches, fromSystem) = appStretches(app, tracked, app.reelUsageRepository.appUsageSpans.first(), since, nowMs)
    val byApp = stretches.byApp()
    val untracked = untrackedIntervals(app, nowMs)
    val today = savedToday(active, byApp, untracked, todayStart, nowMs)
    val history = savedHistory(records, baselines, hour, todayStart)

    val waking = wakingWindow(todayStart)
    val elapsed = waking.first..minOf(nowMs, waking.last)
    val paused = pausedIntervals(app.reelUsageRepository.pauseLog.first(), nowMs)
    val trackingOff = if (elapsed.last > elapsed.first) elapsed.last - elapsed.first - uncoveredMs(elapsed, offIntervals(app, nowMs)) else 0L

    val last24 = (nowMs - USAGE_WINDOW_MS)..nowMs
    val previous24 = (nowMs - 2 * USAGE_WINDOW_MS)..(nowMs - USAGE_WINDOW_MS)
    fun msIn(window: LongRange, ranges: List<LongRange>) = ranges.mapNotNull { it.clipTo(window) }.merged().sumOf { it.last - it.first }

    Progress(
        nowMs = nowMs,
        dayStartMs = todayStart,
        hasBaseline = active.isNotEmpty(),
        today = today,
        history = history,
        dayNumber = baselines.values.minOfOrNull { it.anchorMs }?.let { dayNumberSince(it, hour, todayStart) } ?: 0,
        provisionalUntilMs = active.values.filter { it.isProvisional }.maxOfOrNull { it.provisionalUntilMs },
        trackingOffTodayMs = trackingOff,
        pausedToday = paused.any { it.clipTo(todayStart..nowMs) != null },
        usageFromSystem = fromSystem,
        last24hByApp = tracked.associateWith { msIn(last24, byApp[it].orEmpty()) },
        previous24hMs = tracked.sumOf { msIn(previous24, byApp[it].orEmpty()) },
        insight = todaysInsight(app, records, active, tracked, todayStart, hour),
        tree = buildTreeState(app, nowMs, hasBaseline = active.isNotEmpty()),
        quietDays = quietDays(app.reelUsageRepository.dayHistory.first(), records)
    )
}

/** Today's insight, chosen once per ReclaimLife day and then kept, so it never flickers. */
private suspend fun todaysInsight(
    app: ReclaimLifeApp,
    records: Map<String, DailyRecord>,
    baselines: Map<String, Baseline>,
    tracked: Set<String>,
    todayStart: Long,
    hour: Int
): HomeInsight {
    val settings = app.settingsRepository
    val todayKey = reclaimDayKey(todayStart)
    settings.cachedInsight.first()?.takeIf { it.dateKey == todayKey }?.let { return it.insight }

    fun daysBetween(fromAgo: Int, toAgo: Int) = (fromAgo..toAgo)
        .mapNotNull { records[reclaimDayKey(reclaimDayStart(todayStart, hour, it))] }
        .filter { it.hasData }
    val gateEnabled = settings.gateEnabled.first()
    val gateOff = settings.gateDisabledApps.first()
    val limitMode = settings.limitMode.first()
    val insight = chooseInsight(
        hasSavedFigures = baselines.isNotEmpty(),
        last7 = daysBetween(1, 7),
        prev7 = daysBetween(8, 14),
        baselineMinutes = baselines.mapValues { it.value.minutesPerDay },
        gateOn = { pkg -> pkg !in tracked || (gateEnabled && pkg !in gateOff) },
        usesDailyLimit = limitMode.usesDaily,
        nickname = settings.nickname.first(),
        appLabel = { appLabel(app, it) }
    )
    settings.cacheInsight(todayKey, insight)
    return insight
}
