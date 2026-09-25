package com.example.brainrotkiller.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** How a closed-out day ended. Stored per date so Home can draw a trend, not just two totals. */
enum class DayOutcome(val code: Char) { WITHIN('W'), OVER('O') }

/**
 * One cell in the Home 7-day strip. [outcome] is null for today (still open) or for days with no
 * record (the counter wasn't running). [dateKey] is `yyyy-MM-dd`, same as the usage repository.
 */
data class DayCell(val dateKey: String, val weekdayInitial: String, val outcome: DayOutcome?, val isToday: Boolean)

/** How many closed-out days we keep. Only the last week is drawn; the rest feeds the streak. */
internal const val DAY_HISTORY_KEEP = 120

// java.time needs API 26 (minSdk is 24, no desugaring), so dates stay yyyy-MM-dd strings + Calendar.
private fun dateKeyFormat() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

internal fun dateKeyOf(calendar: Calendar): String = dateKeyFormat().format(calendar.time)

/** Compact storage format: `2026-09-24=W;2026-09-25=O`. */
internal fun parseDayHistory(raw: String?): Map<String, DayOutcome> {
    if (raw.isNullOrBlank()) return emptyMap()
    return raw.split(';').mapNotNull { entry ->
        val parts = entry.split('=')
        if (parts.size != 2 || parts[0].length != 10) return@mapNotNull null
        val outcome = DayOutcome.entries.firstOrNull { it.code == parts[1].singleOrNull() } ?: return@mapNotNull null
        parts[0] to outcome
    }.toMap()
}

/** yyyy-MM-dd sorts chronologically as a plain string, so no date parsing is needed to trim. */
internal fun serializeDayHistory(history: Map<String, DayOutcome>): String =
    history.entries
        .sortedBy { it.key }
        .takeLast(DAY_HISTORY_KEEP)
        .joinToString(";") { "${it.key}=${it.value.code}" }

/** The last [days] days ending with the day [todayMs] falls on, oldest first. */
fun lastDays(history: Map<String, DayOutcome>, todayMs: Long, days: Int = 7): List<DayCell> {
    val weekdayFormat = SimpleDateFormat("EEE", Locale.getDefault())
    return (days - 1 downTo 0).map { back ->
        val cal = Calendar.getInstance().apply {
            timeInMillis = todayMs
            add(Calendar.DAY_OF_YEAR, -back)
        }
        val key = dateKeyOf(cal)
        DayCell(
            dateKey = key,
            weekdayInitial = weekdayFormat.format(cal.time).take(1),
            outcome = if (back == 0) null else history[key],
            isToday = back == 0
        )
    }
}

/**
 * Within-limit days counted back from yesterday, stopping at the first day over the limit.
 * Days with no record (the counter wasn't running, e.g. the phone was off) are skipped — they
 * neither break the streak nor add to it. The walk stops at the oldest recorded day.
 */
fun currentStreak(history: Map<String, DayOutcome>, todayMs: Long): Int {
    val oldest = history.keys.minOrNull() ?: return 0
    val cal = Calendar.getInstance().apply { timeInMillis = todayMs }
    var streak = 0
    while (true) {
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val key = dateKeyOf(cal)
        if (key < oldest) return streak
        when (history[key]) {
            DayOutcome.WITHIN -> streak++
            DayOutcome.OVER -> return streak
            null -> Unit
        }
    }
}
