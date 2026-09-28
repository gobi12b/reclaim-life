package io.github.gobi12b.reclaimlife.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Time won back, per app a, on a day d: saved = max(0, B_a × tracked waking share − counted minutes).
 * Never negative — a day over baseline is 0 saved, and the insight card carries that news. Hours
 * when tracking was off or paused are left out of both sides.
 */
fun savedMs(baselineMinutesPerDay: Double, trackedWakingMs: Long, countedMs: Long): Long {
    val expected = baselineMinutesPerDay * 60_000.0 * trackedWakingMs / WAKING_WINDOW_MS
    return (expected - countedMs).roundToLong().coerceAtLeast(0L)
}

/** Whether [baseline] applies to the day starting at [dayStartMs] (the day it was taken counts). */
private fun Baseline.appliesTo(dayStartMs: Long): Boolean = anchorMs < nextReclaimDayStart(dayStartMs)

data class TodaySaved(
    val totalMs: Long,
    val byApp: Map<String, Long>,
    /** Tracked time in the waking window so far — h_elapsed in the formula. */
    val trackedWakingMs: Long,
    /** Still before the waking window: the card shows yesterday's final figure instead. */
    val beforeWaking: Boolean
)

/**
 * Saved so far today. The baseline is scaled to the waking hours that have passed *and were
 * tracked*, so 09:00 doesn't read as "saved 90 min" and an hour with Accessibility off or a pause
 * running never inflates it. [stretchesByApp] is each app's time in front today.
 */
fun savedToday(
    baselines: Map<String, Baseline>,
    stretchesByApp: Map<String, List<LongRange>>,
    untracked: List<LongRange>,
    dayStartMs: Long,
    nowMs: Long
): TodaySaved {
    val waking = wakingWindow(dayStartMs)
    val beforeWaking = nowMs <= waking.first
    val elapsed = waking.first..minOf(nowMs, waking.last)
    val trackedWaking = if (beforeWaking) 0L else uncoveredMs(elapsed, untracked)
    val day = dayStartMs..nowMs
    val byApp = baselines.filter { it.value.appliesTo(dayStartMs) }.mapValues { (pkg, baseline) ->
        val counted = stretchMsOutside(stretchesByApp[pkg].orEmpty(), day, untracked)
        savedMs(baseline.minutesPerDay, trackedWaking, counted)
    }
    return TodaySaved(byApp.values.sum(), byApp, trackedWaking, beforeWaking)
}

/** Saved on a closed day, per app with a baseline by then. Empty for a day with no data. */
fun savedOnDay(record: DailyRecord, dayStartMs: Long, baselines: Map<String, Baseline>): Map<String, Long> {
    if (!record.hasData) return emptyMap()
    val trackedMs = record.trackedWakingMinutes * 60_000L
    return baselines.filter { it.value.appliesTo(dayStartMs) }.mapValues { (pkg, baseline) ->
        savedMs(baseline.minutesPerDay, trackedMs, (record.apps[pkg]?.countedMinutes ?: 0) * 60_000L)
    }
}

/** A closed day's record from its raw measurements. */
fun closeDay(
    dateKey: String,
    dayStartMs: Long,
    dayEndMs: Long,
    packages: Collection<String>,
    stretchesByApp: Map<String, List<LongRange>>,
    reelsByApp: Map<String, Int>,
    untracked: List<LongRange>
): DailyRecord {
    val day = dayStartMs..dayEndMs
    val apps = (packages + reelsByApp.keys.filter { it != EARLIER_TODAY_KEY }).distinct().associateWith { pkg ->
        val stretches = stretchesByApp[pkg].orEmpty()
        AppDay(
            minutes = (stretchMsOutside(stretches, day, emptyList()) / 60_000L).toInt(),
            reels = reelsByApp[pkg] ?: 0,
            countedMinutes = (stretchMsOutside(stretches, day, untracked) / 60_000L).toInt()
        )
    }
    val tracked = (uncoveredMs(wakingWindow(dayStartMs), untracked) / 60_000L).toInt()
    return DailyRecord(dateKey, apps, tracked)
}

data class SavedHistory(
    /** Real, not projected: saved minutes summed over every closed day since the baseline. */
    val sinceStartedMs: Long,
    /** S̄₇: the average saved per day over the last 7 closed days that have data. */
    val averageLast7Ms: Long,
    /** Closed days with data since the baseline — month needs 3, see [monthProjectionMs]. */
    val daysWithData: Int,
    /** The last closed day's figure, for "Yesterday you saved 38 min" before the waking window. */
    val yesterdayMs: Long?
)

/** Summarises [records] (closed days before the day starting at [todayStartMs]) against [baselines]. */
fun savedHistory(
    records: Map<String, DailyRecord>,
    baselines: Map<String, Baseline>,
    dayStartHour: Int,
    todayStartMs: Long
): SavedHistory {
    if (baselines.isEmpty()) return SavedHistory(0L, 0L, 0, null)
    val todayKey = reclaimDayKey(todayStartMs)
    val yesterdayKey = reclaimDayKey(reclaimDayStart(todayStartMs, dayStartHour, daysAgo = 1))
    val weekAgoKey = reclaimDayKey(reclaimDayStart(todayStartMs, dayStartHour, daysAgo = 7))
    val closed = records.values
        .filter { it.dateKey < todayKey && it.hasData }
        .mapNotNull { record ->
            val start = dayStartOfKey(record.dateKey, dayStartHour) ?: return@mapNotNull null
            val perApp = savedOnDay(record, start, baselines)
            if (perApp.isEmpty()) null else record.dateKey to perApp.values.sum()
        }
    val week = closed.filter { it.first >= weekAgoKey }
    return SavedHistory(
        sinceStartedMs = closed.sumOf { it.second },
        averageLast7Ms = if (week.isEmpty()) 0L else week.sumOf { it.second } / week.size,
        daysWithData = closed.size,
        yesterdayMs = closed.firstOrNull { it.first == yesterdayKey }?.second
    )
}

/** Closed days needed before a monthly estimate is shown. */
const val MONTH_MIN_DAYS = 3
/** A year projected from 3 days is hype; it appears from this day on. */
const val YEAR_MIN_DAY = 14

fun monthProjectionMs(averageDayMs: Long): Long = averageDayMs * 30
fun yearProjectionMs(averageDayMs: Long): Long = averageDayMs * 365

/** Which day since the baseline [todayStartMs] is: the day the baseline was taken is day 1. */
fun dayNumberSince(anchorMs: Long, dayStartHour: Int, todayStartMs: Long): Int {
    val anchorDay = reclaimDayStart(anchorMs, dayStartHour)
    return ((todayStartMs - anchorDay + DAY_MS / 2) / DAY_MS).toInt() + 1
}

/** Start of the ReclaimLife day keyed [dateKey], or null for a malformed key. */
fun dayStartOfKey(dateKey: String, dayStartHour: Int): Long? {
    val date = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateKey) }.getOrNull() ?: return null
    return Calendar.getInstance().run {
        time = date
        set(Calendar.HOUR_OF_DAY, dayStartHour)
        timeInMillis
    }
}

/**
 * One rounding rule for every percentage shown: below 20 the exact whole number ("15%"); from 20
 * rounded to the nearest 5 with "about" ("about 20%" for 22).
 */
fun formatPercent(percent: Double): String {
    val whole = percent.roundToInt()
    return if (whole < 20) "$whole%" else "about ${(whole + 2) / 5 * 5}%"
}

/** "42 min", "6 h 10 min", "10 full days" — saved time never shows seconds or minus signs. */
fun formatSaved(ms: Long): String {
    val minutes = ms.coerceAtLeast(0L) / 60_000L
    return when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0L -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}

/** "≈ 10 full days" for the year line; shorter spans read as [formatProjection] does. */
fun formatYearProjection(ms: Long): String {
    val text = formatProjection(ms)
    return if (text.endsWith(" days")) text.removeSuffix(" days") + " full days" else text
}
