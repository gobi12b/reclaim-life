package io.github.gobi12b.reclaimlife.data

/**
 * The hourly limit is a rolling 60-minute window, not a clock hour — otherwise 30 at 10:59 and
 * 30 more at 11:00 would be 60 in two minutes. Each counted reel's timestamp is kept for an hour;
 * the reels "this hour" are the ones younger than that.
 */
const val HOURLY_WINDOW_MS = 60 * 60_000L

/** Timestamps (epoch millis) of reels counted within the hour before [nowMs], oldest first. */
fun reelsInWindow(times: List<Long>, nowMs: Long): List<Long> =
    times.filter { it > nowMs - HOURLY_WINDOW_MS && it <= nowMs }.sorted()

/**
 * When the window drops back under [limit] — the moment enough of the oldest reels age out that
 * one more is allowed. Null if it's already under (or [limit] is off, i.e. ≤ 0).
 */
fun hourlyUnblockAt(times: List<Long>, limit: Int, nowMs: Long): Long? {
    if (limit <= 0) return null
    val window = reelsInWindow(times, nowMs)
    if (window.size < limit) return null
    // To get back to limit - 1 reels, the oldest (size - limit + 1) must age out.
    return window[window.size - limit] + HOURLY_WINDOW_MS
}

internal fun parseReelTimes(raw: String?): List<Long> =
    raw.orEmpty().split(',').mapNotNull { it.toLongOrNull() }

internal fun serializeReelTimes(times: List<Long>): String = times.joinToString(",")
