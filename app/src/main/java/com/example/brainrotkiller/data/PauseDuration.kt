package com.example.brainrotkiller.data

import java.util.Calendar

/**
 * Pauses are always time-boxed. An open-ended pause is the one that gets forgotten and quietly
 * costs days of protection, so every pause picks an end and tracking resumes on its own.
 */
enum class PauseDuration(val label: String) {
    FIFTEEN_MINUTES("15 min"),
    ONE_HOUR("1 hour"),
    REST_OF_TODAY("Rest of today");

    /** Epoch millis when a pause started at [nowMs] should end. */
    fun endsAt(nowMs: Long): Long = when (this) {
        FIFTEEN_MINUTES -> nowMs + 15 * 60_000L
        ONE_HOUR -> nowMs + 60 * 60_000L
        REST_OF_TODAY -> nextLocalMidnight(nowMs)
    }
}

internal fun nextLocalMidnight(nowMs: Long): Long = Calendar.getInstance().run {
    timeInMillis = nowMs
    add(Calendar.DAY_OF_YEAR, 1)
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
    timeInMillis
}

/** "12:04" under an hour, "1 h 05 min" above — for the Home banner, overlay and widget. */
fun formatPauseRemaining(remainingMs: Long): String {
    val totalSeconds = (remainingMs.coerceAtLeast(0L) + 999) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d h %02d min".format(hours, minutes) else "%d:%02d".format(minutes, seconds)
}
