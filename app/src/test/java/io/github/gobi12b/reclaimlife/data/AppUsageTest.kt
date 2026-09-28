package io.github.gobi12b.reclaimlife.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppUsageTest {

    private val now = 10 * USAGE_WINDOW_MS
    private fun minutesAgo(m: Long) = now - m * 60_000L
    private val ig = TargetApps.INSTAGRAM
    private val yt = TargetApps.YOUTUBE

    @Test
    fun countsOnlyThatAppWithinTheLast24Hours() {
        val spans = listOf(
            UsageSpan(ig, minutesAgo(25 * 60), minutesAgo(24 * 60 + 10)), // entirely too old
            UsageSpan(ig, minutesAgo(24 * 60 + 5), minutesAgo(24 * 60 - 5)), // 5 of 10 min inside
            UsageSpan(yt, minutesAgo(30), minutesAgo(20)),
            UsageSpan(ig, minutesAgo(12), minutesAgo(2))
        )
        assertEquals(15 * 60_000L, usageMsInWindow(spans, ig, now))
        assertEquals(10 * 60_000L, usageMsInWindow(spans, yt, now))
    }

    @Test
    fun touchingSpansOfTheSameAppMerge() {
        val first = addUsageSpan(emptyList(), UsageSpan(ig, minutesAgo(10), minutesAgo(9)), now)
        val merged = addUsageSpan(first, UsageSpan(ig, minutesAgo(9) + 500, minutesAgo(8)), now)
        assertEquals(listOf(UsageSpan(ig, minutesAgo(10), minutesAgo(8))), merged)

        val other = addUsageSpan(merged, UsageSpan(yt, minutesAgo(8), minutesAgo(7)), now)
        assertEquals(2, other.size)
    }

    @Test
    fun addingDropsSpansOlderThanTheWindowAndEmptySpans() {
        val old = listOf(UsageSpan(ig, now - USAGE_RETENTION_MS - 60 * 60_000L, now - USAGE_RETENTION_MS - 1))
        assertEquals(
            listOf(UsageSpan(yt, minutesAgo(5), minutesAgo(1))),
            addUsageSpan(old, UsageSpan(yt, minutesAgo(5), minutesAgo(1)), now)
        )
        assertEquals(old, addUsageSpan(old, UsageSpan(ig, minutesAgo(1), minutesAgo(1)), now))
    }

    @Test
    fun formatsMinutesAndHours() {
        assertEquals("under a minute", formatUsage(59_000L))
        assertEquals("1 min", formatUsage(60_000L))
        assertEquals("42 min", formatUsage(42 * 60_000L + 30_000L))
        assertEquals("2 h 05 min", formatUsage(125 * 60_000L))
    }

    @Test
    fun spansRoundTrip() {
        val spans = listOf(UsageSpan(ig, 1L, 2L), UsageSpan(yt, 3L, 4L))
        assertEquals(spans, parseUsageSpans(serializeUsageSpans(spans)))
        assertEquals(emptyList<UsageSpan>(), parseUsageSpans(null))
        assertEquals(emptyList<UsageSpan>(), parseUsageSpans("garbage;${ig},x,1"))
    }

    @Test
    fun foregroundEventsAreClippedToTheWindow() {
        val windowStart = now - USAGE_WINDOW_MS
        val events = listOf(
            // Began 10 min before the window, ended 5 min into it: only those 5 count.
            ForegroundEvent(windowStart - 10 * 60_000L, resumed = true),
            ForegroundEvent(windowStart + 5 * 60_000L, resumed = false),
            ForegroundEvent(minutesAgo(30), resumed = true),
            ForegroundEvent(minutesAgo(20), resumed = false),
            // Still open now.
            ForegroundEvent(minutesAgo(3), resumed = true)
        )
        assertEquals(18 * 60_000L, foregroundMsFromEvents(events, windowStart, now))
    }

    @Test
    fun switchingBetweenAnAppsOwnScreensIsOneStretch() {
        val events = listOf(
            ForegroundEvent(minutesAgo(10), resumed = true),
            ForegroundEvent(minutesAgo(8), resumed = false),
            ForegroundEvent(minutesAgo(8), resumed = true),
            ForegroundEvent(minutesAgo(6), resumed = false)
        )
        assertEquals(4 * 60_000L, foregroundMsFromEvents(events, now - USAGE_WINDOW_MS, now))
    }

    @Test
    fun projectionsPickAReadableUnit() {
        assertEquals("40 min", formatProjection(40 * 60_000L))
        assertEquals("36 h", formatProjection(36 * 60 * 60_000L))
        // 72 min a day for a year ≈ 438 h ≈ 18 days.
        assertEquals("18 days", formatProjection(72 * 60_000L * 365))
    }
}
