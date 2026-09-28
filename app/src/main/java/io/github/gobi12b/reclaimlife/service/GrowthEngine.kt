package io.github.gobi12b.reclaimlife.service

import io.github.gobi12b.reclaimlife.ReclaimLifeApp
import io.github.gobi12b.reclaimlife.data.Baseline
import io.github.gobi12b.reclaimlife.data.DailyRecord
import io.github.gobi12b.reclaimlife.data.DayOutcome
import io.github.gobi12b.reclaimlife.data.GROWTH_CLOSE_WINDOW_DAYS
import io.github.gobi12b.reclaimlife.data.GrowthDay
import io.github.gobi12b.reclaimlife.data.GrowthInputs
import io.github.gobi12b.reclaimlife.data.LimitChange
import io.github.gobi12b.reclaimlife.data.LimitMode
import io.github.gobi12b.reclaimlife.data.LimitRaise
import io.github.gobi12b.reclaimlife.data.PauseEntry
import io.github.gobi12b.reclaimlife.data.RestReason
import io.github.gobi12b.reclaimlife.data.SWAPS_THAT_GROW_PER_DAY
import io.github.gobi12b.reclaimlife.data.TreeState
import io.github.gobi12b.reclaimlife.data.currentStreak
import io.github.gobi12b.reclaimlife.data.dateKeyOf
import io.github.gobi12b.reclaimlife.data.dayStartOfKey
import io.github.gobi12b.reclaimlife.data.effectiveDayHistory
import io.github.gobi12b.reclaimlife.data.evaluateGrowthDay
import io.github.gobi12b.reclaimlife.data.growthTotal
import io.github.gobi12b.reclaimlife.data.legacyFloorStage
import io.github.gobi12b.reclaimlife.data.limitRaiseOn
import io.github.gobi12b.reclaimlife.data.localDayStart
import io.github.gobi12b.reclaimlife.data.loweredDailyFrom
import io.github.gobi12b.reclaimlife.data.modeAt
import io.github.gobi12b.reclaimlife.data.nextReclaimDayStart
import io.github.gobi12b.reclaimlife.data.pausedMsOn
import io.github.gobi12b.reclaimlife.data.reclaimDayKey
import io.github.gobi12b.reclaimlife.data.reclaimDayStart
import io.github.gobi12b.reclaimlife.data.restOfTodayOn
import io.github.gobi12b.reclaimlife.data.savedHistory
import io.github.gobi12b.reclaimlife.data.savedOnDay
import io.github.gobi12b.reclaimlife.data.savedToday
import io.github.gobi12b.reclaimlife.data.todayRest
import io.github.gobi12b.reclaimlife.data.treeNameInline
import io.github.gobi12b.reclaimlife.data.treeState
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Local midnight to midnight for a `yyyy-MM-dd` key — the growth day, the same as the reel limit's. */
private fun localDayOf(dateKey: String): LongRange? {
    val start = dayStartOfKey(dateKey, 0) ?: return null
    return start..nextReclaimDayStart(start)
}

private fun keyOf(ms: Long): String = dateKeyOf(Calendar.getInstance().apply { timeInMillis = ms })

/** Noon the day after [dateKey]: where the streak chip stood that evening, counting [dateKey] itself. */
private fun streakThrough(history: Map<String, DayOutcome>, dateKey: String): Int {
    val noon = dayStartOfKey(dateKey, 12) ?: return 0
    return currentStreak(history, nextReclaimDayStart(noon))
}

/** Everything a closed day's evaluation reads, loaded once per sync. */
private class GrowthSources(
    val effective: Map<String, DayOutcome>,
    val records: Map<String, DailyRecord>,
    val baselines: Map<String, Baseline>,
    val pauseLog: List<PauseEntry>,
    val changes: List<LimitChange>,
    val swaps: Map<String, Int>,
    val mode: LimitMode,
    val dayStartHour: Int
)

private fun GrowthSources.evaluate(key: String, nowMs: Long, backfill: Boolean): GrowthDay? {
    val day = localDayOf(key) ?: return null
    val record = records[key]
    val recordStart = dayStartOfKey(key, dayStartHour)
    val saved = if (record != null && recordStart != null) {
        savedOnDay(record, recordStart, baselines).takeIf { it.isNotEmpty() }?.values?.sum()
    } else {
        null
    }
    val changes = if (backfill) emptyList() else changes
    return evaluateGrowthDay(
        GrowthInputs(
            dateKey = key,
            outcome = effective[key],
            record = record,
            savedMs = saved,
            raise = limitRaiseOn(changes, day.first, day.last, modeAt(changes, day.last, mode)),
            loweredDailyFrom = loweredDailyFrom(changes, day.first, day.last),
            restOfToday = restOfTodayOn(pauseLog, day.first, day.last, nowMs),
            pausedMs = pausedMsOn(pauseLog, day.first, day.last, nowMs),
            swaps = if (backfill) 0 else swaps[key] ?: 0,
            streakThroughDay = streakThrough(effective, key),
            backfill = backfill
        )
    )
}

private suspend fun loadSources(app: ReclaimLifeApp): GrowthSources {
    val settings = app.settingsRepository
    val records = app.dailyUsageRepository.records.first()
    return GrowthSources(
        effective = effectiveDayHistory(app.reelUsageRepository.dayHistory.first(), records),
        records = records,
        baselines = settings.baselines.first(),
        pauseLog = app.reelUsageRepository.pauseLog.first(),
        changes = settings.limitChanges.first(),
        swaps = app.reelUsageRepository.swapsByDay.first(),
        mode = settings.limitMode.first(),
        dayStartHour = settings.dayStartHour.first()
    )
}

/**
 * Brings the tree's ledger up to date: backfills once for upgrades, then writes each closed day
 * not yet in it. A day closes once its ReclaimLife day has (04:00 the next morning by default),
 * so growth lands overnight. Called at the end of [syncProgressData], under its lock.
 */
internal suspend fun syncGrowth(app: ReclaimLifeApp, nowMs: Long) {
    val settings = app.settingsRepository
    val usage = app.reelUsageRepository
    val daily = app.dailyUsageRepository
    usage.closeOutPreviousDayIfNeeded(settings.dailyReelLimit.first(), settings.limitMode.first())
    val sources = loadSources(app)
    // The last day whose ReclaimLife day has ended: yesterday after the day start hour, else the day before.
    val lastCloseable = reclaimDayKey(reclaimDayStart(nowMs, sources.dayStartHour, daysAgo = 1))

    if (!daily.growthBackfilled.first()) backfill(app, sources, lastCloseable, nowMs)
    settings.raiseTreeBestStreak(currentStreak(sources.effective, nowMs))

    val ledger = daily.growthLedger.first()
    val since = daily.recordingSinceMs.first()
    val firstEligible = listOfNotNull(usage.dayHistory.first().keys.minOrNull(), since?.let { keyOf(it) }).minOrNull() ?: return
    val windowStart = keyOf(localDayStart(nowMs, GROWTH_CLOSE_WINDOW_DAYS))
    val startKey = maxOf(firstEligible, windowStart)
    val start = dayStartOfKey(startKey, 0) ?: return
    val keys = generateSequence(start) { nextReclaimDayStart(it) }
        .map { keyOf(it) }
        .takeWhile { it <= lastCloseable }
        .filter { it !in ledger }
        .toList()
    if (keys.isEmpty()) return
    daily.addGrowth(keys.mapNotNull { sources.evaluate(it, nowMs, backfill = false) })
}

/**
 * The one-time upgrade: every day in the history is evaluated (no limit or swap logs existed), days
 * counted before per-day history get 10 points each, and the old plant's stage is a floor —
 * nobody sees a smaller plant after updating.
 */
private suspend fun backfill(app: ReclaimLifeApp, sources: GrowthSources, lastCloseable: String, nowMs: Long) {
    val rows = sources.effective.keys.filter { it <= lastCloseable }.sorted().mapNotNull { sources.evaluate(it, nowMs, backfill = true) }
    val within = sources.effective.values.count { it == DayOutcome.WITHIN }
    val lifetime = (app.reelUsageRepository.daysWithinLimit.first() - within).coerceAtLeast(0) * 10L
    val floor = legacyFloorStage(sinceStartedMs(app, sources, nowMs)).thresholdPoints
    val total = growthTotal(rows.associateBy { it.dateKey }, lifetime)
    val grant = lifetime + (floor - total).coerceAtLeast(0L)
    app.dailyUsageRepository.addGrowth(rows, carriedGrant = grant, markBackfilled = true)
    rows.minOfOrNull { it.dateKey }?.let { first ->
        dayStartOfKey(first, sources.dayStartHour)?.let { app.settingsRepository.setTreePlantedAtIfUnset(it) }
    }
}

/** The old plant's measure: time won back since the baseline, closed days plus today so far. */
private suspend fun sinceStartedMs(app: ReclaimLifeApp, sources: GrowthSources, nowMs: Long): Long {
    if (sources.baselines.isEmpty()) return 0L
    val hour = sources.dayStartHour
    val todayStart = reclaimDayStart(nowMs, hour)
    val tracked = app.settingsRepository.trackedApps.first().map { it.packageName }.toSet()
    val (stretches, _) = appStretches(app, tracked, app.reelUsageRepository.appUsageSpans.first(), todayStart, nowMs)
    val today = savedToday(sources.baselines.filterKeys { it in tracked }, stretches.byApp(), untrackedIntervals(app, nowMs), todayStart, nowMs)
    return savedHistory(sources.records, sources.baselines, hour, todayStart).sinceStartedMs + today.totalMs
}

/** Today's state for the tree, as the hero, the sheet, the confirms and the swap line read it. */
internal class TreeToday(val name: String, val rest: RestReason?, val raise: LimitRaise?, val swapsToday: Int, val pausedTodayMs: Long)

internal suspend fun treeToday(app: ReclaimLifeApp, nowMs: Long): TreeToday {
    val settings = app.settingsRepository
    val usage = app.reelUsageRepository
    val todayStart = localDayStart(nowMs)
    val todayEnd = nextReclaimDayStart(todayStart)
    val mode = settings.limitMode.first()
    val changes = settings.limitChanges.first()
    val log = usage.pauseLog.first()
    val raise = limitRaiseOn(changes, todayStart, nowMs, mode)
    val pausedToday = pausedMsOn(log, todayStart, todayEnd, nowMs)
    val pausedFrom = settings.pausedFromMs.first()
    val pausedUntil = settings.pausedUntilMs.first()
    val rest = todayRest(
        mode = mode,
        todayCount = usage.todayCount.first(),
        dailyLimit = settings.dailyReelLimit.first(),
        extras = usage.todayExtraAllowance.first(),
        hourlyBreaks = usage.todayHourlyBreaks.first(),
        loweredDailyFrom = loweredDailyFrom(changes, todayStart, nowMs),
        raise = raise,
        restOfTodayStarted = restOfTodayOn(log, todayStart, todayEnd, nowMs),
        pausedTodayMs = pausedToday,
        pausedNow = pausedFrom <= nowMs && nowMs < pausedUntil,
        trackingOn = isReelBlockerServiceRunning(app)
    )
    return TreeToday(settings.treeName.first(), rest, raise, usage.swapsByDay.first()[keyOf(nowMs)] ?: 0, pausedToday)
}

/** The tree for [computeProgress]: the stored ledger plus today's state. Reads only; [syncGrowth] writes. */
internal suspend fun buildTreeState(app: ReclaimLifeApp, nowMs: Long, hasBaseline: Boolean): TreeState {
    val today = treeToday(app, nowMs)
    val records = app.dailyUsageRepository.records.first()
    val effective = effectiveDayHistory(app.reelUsageRepository.dayHistory.first(), records)
    val streak = currentStreak(effective, nowMs)
    return treeState(
        name = today.name,
        rows = app.dailyUsageRepository.growthLedger.first(),
        carried = app.dailyUsageRepository.growthCarried.first(),
        todayRest = today.rest,
        raise = today.raise,
        streak = streak,
        todayKey = keyOf(nowMs),
        localHour = Calendar.getInstance().apply { timeInMillis = nowMs }.get(Calendar.HOUR_OF_DAY),
        plantedAtMs = app.settingsRepository.treePlantedAtMs.first(),
        swapsByDay = app.reelUsageRepository.swapsByDay.first(),
        hasBaseline = hasBaseline,
        // Read-only here, so today's streak counts even before the next sync stores it.
        bestStreak = maxOf(app.settingsRepository.treeBestStreak.first(), streak)
    )
}

/** "That swap helps Fern grow." — only while today is growing and the swap still counts. */
suspend fun swapGrowthLine(app: ReclaimLifeApp, nowMs: Long = System.currentTimeMillis()): String? = withContext(Dispatchers.IO) {
    val today = treeToday(app, nowMs)
    if (today.rest == null && today.swapsToday < SWAPS_THAT_GROW_PER_DAY) "That swap helps ${treeNameInline(today.name)} grow." else null
}

/** What the confirms and pause sheet need: the name, today's rest and today's paused time. */
data class TreeTodaySummary(val name: String, val rest: RestReason?, val pausedTodayMs: Long)

suspend fun treeTodaySummary(app: ReclaimLifeApp, nowMs: Long = System.currentTimeMillis()): TreeTodaySummary = withContext(Dispatchers.IO) {
    val today = treeToday(app, nowMs)
    TreeTodaySummary(today.name, today.rest, today.pausedTodayMs)
}
