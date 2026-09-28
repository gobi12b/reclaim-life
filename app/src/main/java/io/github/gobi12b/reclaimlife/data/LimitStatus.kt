package io.github.gobi12b.reclaimlife.data

/**
 * Where today's limit stands, as the Limits row, the Limits screen, the widget and the block
 * screen all show it — one calculation so "left" is the same number everywhere.
 */
data class LimitStatus(
    /** Reels left against the limit that leads: the day's (with extras), or the hour's in hourly-only mode. */
    val left: Int,
    val used: Int,
    val total: Int,
    val hourlyOnly: Boolean,
    val thisHour: Int,
    val hourlyLimit: Int,
    /** When the hourly limit lifts, or null when it isn't blocking. */
    val hourlyUnblockAtMs: Long?,
    val dailyReached: Boolean
) {
    val blocked: Boolean get() = dailyReached || hourlyUnblockAtMs != null

    /** Near the limit (10% or less left) or blocked: the Limits row moves up, right under Alerts. */
    val nearLimit: Boolean get() = blocked || left * 10 <= total
}

fun limitStatus(
    mode: LimitMode,
    dailyLimit: Int,
    extraAllowance: Int,
    todayCount: Int,
    hourlyLimit: Int,
    recentReelTimes: List<Long>,
    nowMs: Long
): LimitStatus {
    val thisHour = reelsInWindow(recentReelTimes, nowMs).size
    val unblockAt = if (mode.usesHourly) hourlyUnblockAt(recentReelTimes, hourlyLimit, nowMs) else null
    val effective = dailyLimit + extraAllowance
    return if (mode.usesDaily) {
        LimitStatus(
            left = (effective - todayCount).coerceAtLeast(0),
            used = todayCount,
            total = effective,
            hourlyOnly = false,
            thisHour = thisHour,
            hourlyLimit = hourlyLimit,
            hourlyUnblockAtMs = unblockAt,
            dailyReached = todayCount >= effective
        )
    } else {
        LimitStatus(
            left = (hourlyLimit - thisHour).coerceAtLeast(0),
            used = thisHour,
            total = hourlyLimit,
            hourlyOnly = true,
            thisHour = thisHour,
            hourlyLimit = hourlyLimit,
            hourlyUnblockAtMs = unblockAt,
            dailyReached = false
        )
    }
}
