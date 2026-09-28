package io.github.gobi12b.reclaimlife.data

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Why someone paused — one tap, required, stored on-device with the pause (never free text). */
enum class PauseReason(val label: String) {
    WORK("Work or study"),
    RELAXING("Relaxing on purpose"),
    FRIENDS("With friends"),
    OTHER("Other");

    companion object {
        fun fromStored(value: String?): PauseReason? = entries.firstOrNull { it.name == value }
    }
}

/**
 * One pause, for the Rest of today budget, the "3rd pause today" rule and Insights. [endMs] is when
 * it actually ended (moved earlier by Resume). A typed intention is never part of it.
 */
data class PauseEntry(val startMs: Long, val endMs: Long, val reason: PauseReason, val duration: PauseDuration)

const val PAUSE_LOG_RETENTION_MS = 90 * DAY_MS
/** At most one Rest of today per rolling window this long. */
const val REST_OF_TODAY_BUDGET_MS = 72 * 60 * 60_000L
/** Rest of today starts this long after it's confirmed, and can be cancelled until then. */
const val REST_OF_TODAY_DELAY_MS = 60_000L
/** From this pause in a day, every length also gets the calm screen first. */
const val CALM_SCREEN_FROM_PAUSE = 3

internal fun parsePauseLog(raw: String?): List<PauseEntry> =
    raw.orEmpty().split(';').mapNotNull { entry ->
        val f = entry.split(',')
        if (f.size != 4) return@mapNotNull null
        PauseEntry(
            startMs = f[0].toLongOrNull() ?: return@mapNotNull null,
            endMs = f[1].toLongOrNull() ?: return@mapNotNull null,
            reason = PauseReason.fromStored(f[2]) ?: return@mapNotNull null,
            duration = PauseDuration.entries.firstOrNull { it.name == f[3] } ?: return@mapNotNull null
        )
    }

internal fun serializePauseLog(log: List<PauseEntry>, nowMs: Long): String =
    log.filter { it.endMs > nowMs - PAUSE_LOG_RETENTION_MS }
        .joinToString(";") { "${it.startMs},${it.endMs},${it.reason.name},${it.duration.name}" }

/** When Rest of today can be picked again, or null when it can be picked now. */
fun restOfTodayAvailableAt(log: List<PauseEntry>, nowMs: Long): Long? =
    log.filter { it.duration == PauseDuration.REST_OF_TODAY }
        .maxOfOrNull { it.startMs + REST_OF_TODAY_BUDGET_MS }
        ?.takeIf { it > nowMs }

/** Pauses started since local midnight (including one waiting on its delayed start). */
fun pausesStartedToday(log: List<PauseEntry>, nowMs: Long): Int {
    val midnight = localDayStart(nowMs)
    return log.count { it.startMs >= midnight || (it.startMs > nowMs && it.startMs - nowMs <= REST_OF_TODAY_DELAY_MS) }
}

/** The stretches tracking was paused, clipped to now — excluded from saved minutes and baselines. */
fun pausedIntervals(log: List<PauseEntry>, nowMs: Long): List<LongRange> =
    log.mapNotNull { e -> (e.startMs..minOf(e.endMs, nowMs)).takeIf { it.last > it.first } }

/**
 * "Available tonight at 21:10" when under 24 hours away, "Available Fri 21:10" otherwise. The
 * Rest of today chip is never hidden — it says when it comes back.
 */
fun formatAvailableAt(atMs: Long, nowMs: Long): String {
    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(atMs))
    if (atMs - nowMs >= DAY_MS) {
        return "Available ${SimpleDateFormat("EEE", Locale.getDefault()).format(Date(atMs))} $time"
    }
    val sameDay = localDayStart(atMs) == localDayStart(nowMs)
    val hour = Calendar.getInstance().apply { timeInMillis = atMs }.get(Calendar.HOUR_OF_DAY)
    return when {
        sameDay && hour >= 17 -> "Available tonight at $time"
        sameDay -> "Available today at $time"
        else -> "Available tomorrow at $time"
    }
}

/** A typed intention for Rest of today: at least 3 words, at most 80 characters. */
const val INTENTION_MAX_CHARS = 80
fun intentionIsValid(text: String): Boolean =
    text.length <= INTENTION_MAX_CHARS && text.trim().split(Regex("\\s+")).count { it.isNotBlank() } >= 3

/** Stretches tracking was off (service switched off, phone off): `start-end;start-end`. */
internal fun parseOffIntervals(raw: String?): List<LongRange> =
    raw.orEmpty().split(';').mapNotNull { entry ->
        val f = entry.split('-')
        val start = f.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
        val end = f.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
        if (f.size != 2 || end <= start) null else start..end
    }

internal fun serializeOffIntervals(intervals: List<LongRange>, nowMs: Long): String =
    intervals.filter { it.last > nowMs - OFF_INTERVAL_RETENTION_MS }.merged()
        .joinToString(";") { "${it.first}-${it.last}" }

/** Long enough for any closed day the recorder can still pick up. */
const val OFF_INTERVAL_RETENTION_MS = 14 * DAY_MS
