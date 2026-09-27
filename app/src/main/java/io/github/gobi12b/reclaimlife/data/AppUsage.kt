package io.github.gobi12b.reclaimlife.data

/**
 * Time spent with a target app in front, kept as spans for a rolling 24 hours. Measured by the
 * accessibility service itself (it already checks which app is in front), so showing "minutes in
 * the last 24 hours" needs no Usage Access permission.
 */
const val USAGE_WINDOW_MS = 24 * 60 * 60_000L

/** Spans this close together are stored as one, which keeps the stored string short. */
private const val USAGE_MERGE_GAP_MS = 2_000L

data class UsageSpan(val packageName: String, val startMs: Long, val endMs: Long)

/** Milliseconds [packageName] was in front during the 24 hours before [nowMs]. */
fun usageMsInWindow(spans: List<UsageSpan>, packageName: String, nowMs: Long): Long {
    val windowStart = nowMs - USAGE_WINDOW_MS
    return spans.filter { it.packageName == packageName }.sumOf { span ->
        (minOf(span.endMs, nowMs) - maxOf(span.startMs, windowStart)).coerceAtLeast(0L)
    }
}

/** Adds [span] (merging it into the last one when they touch) and drops spans older than the window. */
fun addUsageSpan(spans: List<UsageSpan>, span: UsageSpan, nowMs: Long): List<UsageSpan> {
    if (span.endMs <= span.startMs) return spans
    val kept = spans.filter { it.endMs > nowMs - USAGE_WINDOW_MS }
    val last = kept.lastOrNull()
    return if (last != null && last.packageName == span.packageName && span.startMs - last.endMs <= USAGE_MERGE_GAP_MS) {
        kept.dropLast(1) + last.copy(endMs = maxOf(last.endMs, span.endMs))
    } else {
        kept + span
    }
}

/** "under a minute", "1 min", "42 min", "2 h 05 min". */
fun formatUsage(ms: Long): String {
    val minutes = ms.coerceAtLeast(0L) / 60_000L
    return when {
        minutes < 1 -> "under a minute"
        minutes < 60 -> "$minutes min"
        else -> "%d h %02d min".format(minutes / 60, minutes % 60)
    }
}

/** A projected total, rounded to the unit that reads best: "40 min", "36 h", "18 days". */
fun formatProjection(ms: Long): String {
    val minutes = ms.coerceAtLeast(0L) / 60_000L
    return when {
        minutes < 60 -> "$minutes min"
        minutes < 48 * 60 -> "${(minutes + 30) / 60} h"
        else -> "${(minutes + 12 * 60) / (24 * 60)} days"
    }
}

internal fun parseUsageSpans(raw: String?): List<UsageSpan> =
    raw.orEmpty().split(';').mapNotNull { entry ->
        val parts = entry.split(',')
        if (parts.size != 3) return@mapNotNull null
        val start = parts[1].toLongOrNull() ?: return@mapNotNull null
        val end = parts[2].toLongOrNull() ?: return@mapNotNull null
        UsageSpan(parts[0], start, end)
    }

internal fun serializeUsageSpans(spans: List<UsageSpan>): String =
    spans.joinToString(";") { "${it.packageName},${it.startMs},${it.endMs}" }

/** One of an app's activities coming to the front ([resumed]) or leaving it, from Android's usage log. */
data class ForegroundEvent(val timeMs: Long, val resumed: Boolean)

/**
 * Time an app was in front between [windowStartMs] and [nowMs], from its resume/pause events in
 * time order. Events may start before the window, so a stretch already running at its start is
 * clipped rather than lost; one still running at the end counts up to [nowMs].
 */
fun foregroundMsFromEvents(events: List<ForegroundEvent>, windowStartMs: Long, nowMs: Long): Long {
    var total = 0L
    var since: Long? = null
    fun close(endMs: Long) {
        val start = since ?: return
        total += (minOf(endMs, nowMs) - maxOf(start, windowStartMs)).coerceAtLeast(0L)
        since = null
    }
    for (event in events.sortedBy { it.timeMs }) {
        if (event.resumed) {
            if (since == null) since = event.timeMs
        } else {
            close(event.timeMs)
        }
    }
    close(nowMs)
    return total
}
