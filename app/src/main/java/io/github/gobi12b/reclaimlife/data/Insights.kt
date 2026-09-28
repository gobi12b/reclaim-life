package io.github.gobi12b.reclaimlife.data

import java.util.Calendar

/** A stretch of an app in front, from Android's usage log or ReclaimLife's own timing. */
data class AppStretch(val packageName: String, val startMs: Long, val endMs: Long)

/** One calendar day of the Insights chart: minutes per app, oldest day first. */
data class DayUsage(val dayStartMs: Long, val msByPackage: Map<String, Long>) {
    val totalMs: Long get() = msByPackage.values.sum()
}

data class UsageInsights(
    /** The last [INSIGHT_DAYS] local days, today last. */
    val days: List<DayUsage>,
    /** Minutes in each hour of the day (0–23), summed over those days — when scrolling happens. */
    val msByHour: List<Long>,
    /** Separate visits: stretches more than [SESSION_GAP_MS] apart count as new opens. */
    val opens: Int,
    /** Whether the numbers come from Android's full record rather than ReclaimLife's own timing. */
    val fromSystem: Boolean
) {
    val totalMs: Long get() = days.sumOf { it.totalMs }
    val averageDayMs: Long get() = if (days.isEmpty()) 0L else totalMs / days.size
    val averageVisitMs: Long get() = if (opens == 0) 0L else totalMs / opens
    /** The hour (0–23) with the most time, or null with no usage at all. */
    val peakHour: Int? get() = msByHour.indices.maxByOrNull { msByHour[it] }?.takeIf { msByHour[it] > 0 }
}

const val INSIGHT_DAYS = 7
/** Coming back within this long (a notification, a quick switch) is the same visit. */
const val SESSION_GAP_MS = 60_000L

/** Local midnight at the start of the day [daysAgo] days before the one containing [nowMs]. */
fun localDayStart(nowMs: Long, daysAgo: Int = 0): Long = Calendar.getInstance().run {
    timeInMillis = nowMs
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
    add(Calendar.DAY_OF_YEAR, -daysAgo)
    timeInMillis
}

/** Splits [stretches] into the last [INSIGHT_DAYS] local days, hours of the day and visits. */
fun buildInsights(stretches: List<AppStretch>, nowMs: Long, fromSystem: Boolean): UsageInsights {
    val dayStarts = (INSIGHT_DAYS - 1 downTo 0).map { localDayStart(nowMs, it) }
    val windowStart = dayStarts.first()
    val perDay = List(INSIGHT_DAYS) { mutableMapOf<String, Long>() }
    val perHour = LongArray(24)
    val calendar = Calendar.getInstance()

    val clipped = stretches.mapNotNull { s ->
        val start = maxOf(s.startMs, windowStart)
        val end = minOf(s.endMs, nowMs)
        if (end > start) s.copy(startMs = start, endMs = end) else null
    }
    for (s in clipped) {
        // Walk the stretch hour by hour so time is credited to the right day and hour even when it
        // spans midnight or several hours.
        var t = s.startMs
        while (t < s.endMs) {
            calendar.timeInMillis = t
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.add(Calendar.HOUR_OF_DAY, 1)
            val chunkEnd = minOf(calendar.timeInMillis, s.endMs)
            val ms = chunkEnd - t
            perHour[hour] += ms
            val day = dayStarts.indexOfLast { it <= t }
            if (day >= 0) perDay[day].merge(s.packageName, ms, Long::plus)
            t = chunkEnd
        }
    }

    var opens = 0
    var lastEnd: Long? = null
    for (s in clipped.sortedBy { it.startMs }) {
        val previous = lastEnd
        if (previous == null || s.startMs - previous > SESSION_GAP_MS) opens++
        lastEnd = maxOf(previous ?: s.endMs, s.endMs)
    }

    return UsageInsights(
        days = dayStarts.mapIndexed { i, start -> DayUsage(start, perDay[i]) },
        msByHour = perHour.toList(),
        opens = opens,
        fromSystem = fromSystem
    )
}

/** A choice made on the open-pause screen. */
data class GateDecision(val timeMs: Long, val skipped: Boolean)

/** How long gate choices are kept — enough for the Insights week. */
const val GATE_DECISION_RETENTION_MS = 8 * USAGE_WINDOW_MS

internal fun parseGateDecisions(raw: String?): List<GateDecision> =
    raw.orEmpty().split(';').mapNotNull { entry ->
        val parts = entry.split(',')
        if (parts.size != 2) return@mapNotNull null
        val time = parts[0].toLongOrNull() ?: return@mapNotNull null
        GateDecision(time, parts[1] == "1")
    }

internal fun serializeGateDecisions(decisions: List<GateDecision>): String =
    decisions.joinToString(";") { "${it.timeMs},${if (it.skipped) 1 else 0}" }
