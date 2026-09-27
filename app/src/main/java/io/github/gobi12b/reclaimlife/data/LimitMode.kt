package io.github.gobi12b.reclaimlife.data

/**
 * Which limits are enforced. The daily count is always kept (Home and history show it); in
 * [HOURLY] mode it just never blocks.
 */
enum class LimitMode(val label: String) {
    DAILY("Daily"),
    HOURLY("Hourly"),
    BOTH("Both");

    val usesDaily: Boolean get() = this != HOURLY
    val usesHourly: Boolean get() = this != DAILY

    /**
     * Whether switching to [to] stops enforcing a limit that's enforced now. That's a loosening,
     * gated like raising a limit: confirmed, and not allowed while paused.
     */
    fun loosensTo(to: LimitMode): Boolean =
        (usesDaily && !to.usesDaily) || (usesHourly && !to.usesHourly)

    companion object {
        fun fromStored(value: String?): LimitMode = entries.firstOrNull { it.name == value } ?: DAILY
    }
}

/**
 * Breaks that reopen reels at the hourly limit a day can use and still count as within — the same
 * three a day as the daily limit's extras.
 */
const val HOURLY_BREAKS_WITHIN_LIMIT = 3

/**
 * How a finished day is scored for the history strip and streak. Daily (and both) score the daily
 * count against the limit plus extras. Hourly-only has no daily number to score; like extras,
 * breaks that reopen reels are earned, so a day is within up to [HOURLY_BREAKS_WITHIN_LIMIT] of them.
 */
fun dayWithinLimit(mode: LimitMode, count: Int, dailyLimit: Int, extraAllowance: Int, hourlyBreaks: Int): Boolean =
    if (mode == LimitMode.HOURLY) hourlyBreaks <= HOURLY_BREAKS_WITHIN_LIMIT else count <= dailyLimit + extraAllowance
