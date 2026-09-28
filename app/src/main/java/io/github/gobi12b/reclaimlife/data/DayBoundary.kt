package io.github.gobi12b.reclaimlife.data

import java.util.Calendar

/**
 * The ReclaimLife day for time saved, baselines and trends. It starts at [DEFAULT_DAY_START_HOUR]
 * (the usual sleep-app boundary) rather than midnight, so a 01:00 scroll belongs to the evening
 * before it. Reel limits keep resetting at local midnight — the block screen is unchanged.
 */
const val DEFAULT_DAY_START_HOUR = 4

/** The waking window opens this long after the day starts (07:00 by default)… */
const val WAKING_OFFSET_MS = 3 * 60 * 60_000L

/** …and lasts this long. Today's baseline is spread over it, so 09:00 doesn't read as "saved 90 min". */
const val WAKING_WINDOW_MS = 16 * 60 * 60_000L

const val DAY_MS = 24 * 60 * 60_000L

/** Start (epoch millis) of the ReclaimLife day containing [nowMs], or of the one [daysAgo] days before it. */
fun reclaimDayStart(nowMs: Long, dayStartHour: Int, daysAgo: Int = 0): Long = Calendar.getInstance().run {
    timeInMillis = nowMs
    if (get(Calendar.HOUR_OF_DAY) < dayStartHour) add(Calendar.DAY_OF_YEAR, -1)
    set(Calendar.HOUR_OF_DAY, dayStartHour)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
    add(Calendar.DAY_OF_YEAR, -daysAgo)
    timeInMillis
}

/** Start of the day after the one starting at [dayStartMs] — a calendar step, so DST days stay whole. */
fun nextReclaimDayStart(dayStartMs: Long): Long = Calendar.getInstance().run {
    timeInMillis = dayStartMs
    add(Calendar.DAY_OF_YEAR, 1)
    timeInMillis
}

/** `yyyy-MM-dd` of the date the ReclaimLife day starting at [dayStartMs] belongs to. */
fun reclaimDayKey(dayStartMs: Long): String =
    dateKeyOf(Calendar.getInstance().apply { timeInMillis = dayStartMs })

/** The waking window of the day starting at [dayStartMs]. */
fun wakingWindow(dayStartMs: Long): LongRange =
    (dayStartMs + WAKING_OFFSET_MS)..(dayStartMs + WAKING_OFFSET_MS + WAKING_WINDOW_MS)
