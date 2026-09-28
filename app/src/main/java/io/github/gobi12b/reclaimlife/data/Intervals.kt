package io.github.gobi12b.reclaimlife.data

/**
 * Time ranges as `start..end` epoch millis, the same shape [foregroundStretches] returns. A range's
 * length is `last - first`, so `t..t` is empty.
 */
val LongRange.lengthMs: Long get() = (last - first).coerceAtLeast(0L)

/** The part of [this] that falls inside [other], or null when they don't overlap. */
fun LongRange.clipTo(other: LongRange): LongRange? {
    val start = maxOf(first, other.first)
    val end = minOf(last, other.last)
    return if (end > start) start..end else null
}

/** Overlapping or touching ranges joined, in time order. */
fun List<LongRange>.merged(): List<LongRange> {
    val out = mutableListOf<LongRange>()
    for (range in filter { it.last > it.first }.sortedBy { it.first }) {
        val last = out.lastOrNull()
        if (last != null && range.first <= last.last) {
            out[out.lastIndex] = last.first..maxOf(last.last, range.last)
        } else {
            out += range
        }
    }
    return out
}

/** Milliseconds of [window] not covered by any of [excluded]. */
fun uncoveredMs(window: LongRange, excluded: List<LongRange>): Long =
    window.lengthMs - excluded.mapNotNull { it.clipTo(window) }.merged().sumOf { it.lengthMs }

/** Milliseconds of [stretches] inside [window], minus any part that falls in [excluded]. */
fun stretchMsOutside(stretches: List<LongRange>, window: LongRange, excluded: List<LongRange>): Long =
    stretches.mapNotNull { it.clipTo(window) }.merged().sumOf { uncoveredMs(it, excluded) }
