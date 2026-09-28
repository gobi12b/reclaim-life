package io.github.gobi12b.reclaimlife.data

/**
 * One app on one closed ReclaimLife day. [countedMinutes] leaves out time while tracking was off or
 * paused, so saved minutes never credit (or blame) a stretch nobody was measuring.
 */
data class AppDay(val minutes: Int, val reels: Int, val countedMinutes: Int = minutes)

/**
 * A closed ReclaimLife day, kept for [DAILY_USAGE_KEEP_DAYS] for the month, year and "since you
 * started" figures (raw spans only last [USAGE_RETENTION_MS]). [trackedWakingMinutes] is how much
 * of the 16-hour waking window was tracked; 0 means no data (phone off, service off all day).
 */
data class DailyRecord(val dateKey: String, val apps: Map<String, AppDay>, val trackedWakingMinutes: Int) {
    val totalMinutes: Int get() = apps.values.sumOf { it.minutes }
    val hasData: Boolean get() = trackedWakingMinutes > 0
}

const val DAILY_USAGE_KEEP_DAYS = 400

/** The day-level entry in a record: tracked waking minutes. `~` can't start a package name. */
private const val TRACKED_ENTRY = "~"

/**
 * `2026-09-28=com.instagram.android:42:18:40,com.google.android.youtube:10:0,~:960;2026-09-29=…`
 * Per app: minutes, reels, and (when it differs from minutes) counted minutes.
 */
internal fun parseDailyUsage(raw: String?): Map<String, DailyRecord> =
    raw.orEmpty().split(';').mapNotNull { entry ->
        val eq = entry.indexOf('=')
        if (eq != 10) return@mapNotNull null
        val date = entry.substring(0, eq)
        var tracked = WAKING_WINDOW_MINUTES
        val apps = mutableMapOf<String, AppDay>()
        entry.substring(eq + 1).split(',').forEach { part ->
            val f = part.split(':')
            if (f[0] == TRACKED_ENTRY) {
                f.getOrNull(1)?.toIntOrNull()?.let { tracked = it.coerceIn(0, WAKING_WINDOW_MINUTES) }
                return@forEach
            }
            val minutes = f.getOrNull(1)?.toIntOrNull() ?: return@forEach
            val reels = f.getOrNull(2)?.toIntOrNull() ?: return@forEach
            if (f[0].isBlank()) return@forEach
            apps[f[0]] = AppDay(minutes, reels, f.getOrNull(3)?.toIntOrNull() ?: minutes)
        }
        date to DailyRecord(date, apps, tracked)
    }.toMap()

internal fun serializeDailyUsage(records: Map<String, DailyRecord>): String =
    records.values.sortedBy { it.dateKey }.takeLast(DAILY_USAGE_KEEP_DAYS).joinToString(";") { record ->
        val apps = record.apps.entries.map { (pkg, day) ->
            "$pkg:${day.minutes}:${day.reels}" + if (day.countedMinutes != day.minutes) ":${day.countedMinutes}" else ""
        }
        "${record.dateKey}=" + (apps + "$TRACKED_ENTRY:${record.trackedWakingMinutes}").joinToString(",")
    }

internal const val WAKING_WINDOW_MINUTES = (WAKING_WINDOW_MS / 60_000L).toInt()
