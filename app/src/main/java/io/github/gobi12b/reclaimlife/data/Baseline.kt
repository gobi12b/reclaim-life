package io.github.gobi12b.reclaimlife.data

import java.util.Locale
import kotlin.math.roundToInt

/**
 * What "saved" is measured against for one app: its average daily minutes before ReclaimLife,
 * read from Android's usage record. Locked once set, because a baseline that moves would quietly
 * shrink people's progress.
 *
 * With 3–13 days of history it's provisional ([provisionalUntilMs] > 0): refined daily from the
 * days observed since [anchorMs] until then, and never above [preMinutes] — observed days run
 * under the pause and limits, so blending them in keeps "saved" conservative.
 */
data class Baseline(
    val minutesPerDay: Double,
    val preMinutes: Double,
    val preDays: Int,
    val anchorMs: Long,
    val provisionalUntilMs: Long = 0L
) {
    val isProvisional: Boolean get() = provisionalUntilMs > 0L
}

const val BASELINE_HISTORY_DAYS = 14
const val BASELINE_MIN_HISTORY_DAYS = 3
/** A provisional baseline settles this long after it was first taken. */
const val BASELINE_SETTLE_MS = 7 * DAY_MS
/** "Reset baseline" is allowed at most this often, so it can't be used to game the number. */
const val BASELINE_RESET_INTERVAL_MS = 30 * DAY_MS
/** Observed days need at least this much tracked waking time to refine a baseline. */
private const val REFINE_MIN_TRACKED_MINUTES = WAKING_WINDOW_MINUTES / 2

/**
 * A baseline from [historyMs] of the app's time over the [historyDays] days before [anchorMs], or
 * null with under [BASELINE_MIN_HISTORY_DAYS] days (a baseline that short is noisy and permanent).
 */
fun baselineFromHistory(historyMs: Long, historyDays: Int, anchorMs: Long): Baseline? {
    val days = historyDays.coerceAtMost(BASELINE_HISTORY_DAYS)
    if (days < BASELINE_MIN_HISTORY_DAYS) return null
    val minutes = historyMs / 60_000.0 / days
    return Baseline(
        minutesPerDay = minutes,
        preMinutes = minutes,
        preDays = days,
        anchorMs = anchorMs,
        provisionalUntilMs = if (days < BASELINE_HISTORY_DAYS) anchorMs + BASELINE_SETTLE_MS else 0L
    )
}

/**
 * A provisional baseline blended with [observed] days (counted minutes of the app on closed days
 * since the anchor, keyed by the day's start), weighted by day count and capped at the
 * pre-ReclaimLife figure. Locks once [nowMs] passes the settle date.
 */
fun refineBaseline(baseline: Baseline, observed: List<DailyRecord>, packageName: String, nowMs: Long): Baseline {
    if (!baseline.isProvisional) return baseline
    val days = observed
        .filter { it.trackedWakingMinutes >= REFINE_MIN_TRACKED_MINUTES }
        .map { (it.apps[packageName]?.countedMinutes ?: 0) * WAKING_WINDOW_MINUTES.toDouble() / it.trackedWakingMinutes }
    val blended = (baseline.preMinutes * baseline.preDays + days.sum()) / (baseline.preDays + days.size)
    return baseline.copy(
        minutesPerDay = minOf(baseline.preMinutes, blended),
        provisionalUntilMs = if (nowMs >= baseline.provisionalUntilMs) 0L else baseline.provisionalUntilMs
    )
}

/** When "Reset baseline" is next allowed, or null if it's allowed now. */
fun baselineResetAvailableAt(lastResetMs: Long, nowMs: Long): Long? =
    (lastResetMs + BASELINE_RESET_INTERVAL_MS).takeIf { lastResetMs > 0L && it > nowMs }

/** `pkg=minutes,preMinutes,preDays,anchorMs,provisionalUntilMs;…` */
internal fun parseBaselines(raw: String?): Map<String, Baseline> =
    raw.orEmpty().split(';').mapNotNull { entry ->
        val eq = entry.indexOf('=')
        if (eq <= 0) return@mapNotNull null
        val f = entry.substring(eq + 1).split(',')
        if (f.size != 5) return@mapNotNull null
        val baseline = Baseline(
            minutesPerDay = f[0].toDoubleOrNull() ?: return@mapNotNull null,
            preMinutes = f[1].toDoubleOrNull() ?: return@mapNotNull null,
            preDays = f[2].toIntOrNull() ?: return@mapNotNull null,
            anchorMs = f[3].toLongOrNull() ?: return@mapNotNull null,
            provisionalUntilMs = f[4].toLongOrNull() ?: return@mapNotNull null
        )
        entry.substring(0, eq) to baseline
    }.toMap()

internal fun serializeBaselines(baselines: Map<String, Baseline>): String =
    baselines.entries.joinToString(";") { (pkg, b) ->
        "$pkg=" + listOf(
            "%.2f".format(Locale.US, b.minutesPerDay),
            "%.2f".format(Locale.US, b.preMinutes),
            b.preDays, b.anchorMs, b.provisionalUntilMs
        ).joinToString(",")
    }

/** Whole minutes a day, as shown in "Your baseline goes from 90 to 64 min a day". */
fun baselineTotalMinutes(baselines: Collection<Baseline>): Int = baselines.sumOf { it.minutesPerDay }.roundToInt()
