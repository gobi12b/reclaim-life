package io.github.gobi12b.reclaimlife.data

/**
 * The one-sentence read on Home. Increases get the neutral wave and a single constructive offer —
 * no red, no "warning", no guilt. [emoji] always travels with [iconDescription].
 */
enum class InsightKind(val emoji: String, val iconDescription: String) {
    SETTLING("🌱", "Seedling"),
    APP_DRIVEN("🌊", "Heads-up"),
    CREPT_UP("🌊", "Heads-up"),
    HEAVIER("🌊", "Heads-up"),
    CUT_BIG("⭐", "Star"),
    CUT("👍", "Thumbs up"),
    STEADY("⚖️", "Balance")
}

enum class InsightAction { NONE, TURN_ON_GATE, LOWER_LIMIT, START_SWAP }

data class HomeInsight(
    val kind: InsightKind,
    val text: String,
    val action: InsightAction = InsightAction.NONE,
    val actionLabel: String? = null,
    /** The app [InsightAction.TURN_ON_GATE] is for. */
    val actionApp: String? = null
)

/** A change under this many minutes a day reads as holding steady, whatever the percentage. */
const val INSIGHT_MIN_CHANGE_MINUTES = 10.0

/**
 * Picks the insight: rules top to bottom, first match wins. [last7] and [prev7] are closed days
 * with data (newest week first); [baselineMinutes] maps apps to their daily baseline.
 */
fun chooseInsight(
    hasSavedFigures: Boolean,
    last7: List<DailyRecord>,
    prev7: List<DailyRecord>,
    baselineMinutes: Map<String, Double>,
    gateOn: (String) -> Boolean,
    usesDailyLimit: Boolean,
    nickname: String,
    appLabel: (String) -> String
): HomeInsight {
    // 1 — nothing to compare yet.
    if (!hasSavedFigures || last7.size < MONTH_MIN_DAYS) {
        return HomeInsight(InsightKind.SETTLING, "Give it a couple of days, and we'll show you how it's going.")
    }
    val name = nickname.trim()
    fun average(days: List<DailyRecord>, pkg: String? = null): Double =
        days.map { d -> if (pkg == null) d.totalMinutes else d.apps[pkg]?.minutes ?: 0 }.average()

    if (prev7.size >= MONTH_MIN_DAYS) {
        val before = average(prev7)
        val now = average(last7)
        val upMinutes = now - before
        val upPercent = if (before > 0) upMinutes / before * 100 else 0.0
        if (upMinutes >= INSIGHT_MIN_CHANGE_MINUTES && upPercent >= 10) {
            // 2 — one app drives most of the increase, and its Pause before opening is off.
            val apps = (last7 + prev7).flatMap { it.apps.keys }.toSet()
            val driver = apps.maxByOrNull { average(last7, it) - average(prev7, it) }
            if (driver != null && !gateOn(driver) && (average(last7, driver) - average(prev7, driver)) / upMinutes >= 0.7) {
                val label = appLabel(driver)
                return HomeInsight(
                    InsightKind.APP_DRIVEN,
                    "Most of the extra time is $label. Turn on Pause before opening for it?",
                    InsightAction.TURN_ON_GATE,
                    "Turn on for $label",
                    driver
                )
            }
            // 3 — a big jump: a lower limit for a few days.
            if (upPercent >= 30 && usesDailyLimit) {
                return HomeInsight(
                    InsightKind.CREPT_UP,
                    "Scrolling has crept up this week (${formatPercent(upPercent).withPlus()}). Want a lower limit for a few days?",
                    InsightAction.LOWER_LIMIT,
                    "Try 10 fewer reels"
                )
            }
            // 4 — a bit heavier.
            return HomeInsight(
                InsightKind.HEAVIER,
                "This week's been a bit heavier (${formatPercent(upPercent).withPlus()}). No stress — one 2-minute swap can reset the day.",
                InsightAction.START_SWAP,
                "Start a swap"
            )
        }
    }

    // 5, 6 — down against the baseline, over the apps that have one.
    val baselineTotal = baselineMinutes.values.sum()
    if (baselineTotal > 0) {
        val now = last7.map { d -> baselineMinutes.keys.sumOf { d.apps[it]?.minutes ?: 0 } }.average()
        val downMinutes = baselineTotal - now
        val downPercent = downMinutes / baselineTotal * 100
        if (downMinutes >= INSIGHT_MIN_CHANGE_MINUTES && downPercent >= 30) {
            return HomeInsight(
                InsightKind.CUT_BIG,
                "You've cut your scrolling by ${formatPercent(downPercent)}. That's a real change — nice work" +
                    (if (name.isEmpty()) "." else ", $name.")
            )
        }
        if (downMinutes >= INSIGHT_MIN_CHANGE_MINUTES && downPercent >= 10) {
            return HomeInsight(
                InsightKind.CUT,
                "You're scrolling ${formatPercent(downPercent)} less than when you started. Good job" +
                    (if (name.isEmpty()) "." else ", $name.")
            )
        }
    }

    // 7 — holding steady.
    return HomeInsight(
        InsightKind.STEADY,
        "Holding steady" + (if (name.isEmpty()) "" else ", $name") + ". Want to try one swap today?",
        InsightAction.START_SWAP,
        "Start a swap"
    )
}

/** "about 40%" → "about +40%", "15%" → "+15%". */
private fun String.withPlus(): String = if (startsWith("about ")) "about +" + removePrefix("about ") else "+$this"
