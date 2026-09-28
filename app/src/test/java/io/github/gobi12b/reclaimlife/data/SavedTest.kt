package io.github.gobi12b.reclaimlife.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SavedTest {

    private val min = 60_000L
    private val hour = 60 * min
    private val ig = TargetApps.INSTAGRAM
    private val yt = TargetApps.YOUTUBE

    /** Today at [h]:00 local time, whatever the machine's zone. */
    private fun todayAt(h: Int, m: Int = 0): Long = Calendar.getInstance().run {
        set(Calendar.HOUR_OF_DAY, h)
        set(Calendar.MINUTE, m)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        timeInMillis
    }

    private val dayStart = todayAt(DEFAULT_DAY_START_HOUR)
    private fun baseline(minutes: Double, anchor: Long = dayStart - 20 * DAY_MS) = Baseline(minutes, minutes, 14, anchor)

    @Test
    fun oneAmBelongsToTheDayBefore() {
        val oneAm = todayAt(1)
        assertEquals(reclaimDayStart(oneAm, 4), reclaimDayStart(todayAt(23), 4, daysAgo = 1))
        assertEquals(todayAt(7), wakingWindow(dayStart).first)
        assertEquals(todayAt(23), wakingWindow(dayStart).last)
    }

    @Test
    fun halfTheWakingDayGetsHalfTheBaseline() {
        // Spec: 90 min/day baseline, 30 min used by 15:00 (8 of 16 waking hours) → 15 min saved.
        val used = listOf(todayAt(10)..(todayAt(10) + 30 * min))
        val saved = savedToday(mapOf(ig to baseline(90.0)), mapOf(ig to used), emptyList(), dayStart, todayAt(15))
        assertEquals(15 * min, saved.totalMs)
        assertEquals(8 * hour, saved.trackedWakingMs)
    }

    @Test
    fun aHeavyDayIsZeroNeverNegative() {
        val used = listOf(todayAt(8)..todayAt(12))
        val saved = savedToday(mapOf(ig to baseline(60.0)), mapOf(ig to used), emptyList(), dayStart, todayAt(20))
        assertEquals(0L, saved.totalMs)
    }

    @Test
    fun hoursWithTrackingOffCountTowardNeither() {
        // Accessibility off 12:00–15:00, with scrolling during it that Android still recorded.
        val off = listOf(todayAt(12)..todayAt(15))
        val used = listOf(todayAt(13)..todayAt(14))
        val saved = savedToday(mapOf(ig to baseline(96.0)), mapOf(ig to used), off, dayStart, todayAt(19))
        // 12 waking hours passed, 3 untracked → 9 tracked: 96 × 9/16 = 54 min, none of it used.
        assertEquals(9 * hour, saved.trackedWakingMs)
        assertEquals(54 * min, saved.totalMs)
    }

    @Test
    fun savedDoesNotGrowDuringAPause() {
        val pause = listOf(todayAt(14)..todayAt(15))
        val before = savedToday(mapOf(ig to baseline(96.0)), emptyMap(), pause, dayStart, todayAt(14))
        val after = savedToday(mapOf(ig to baseline(96.0)), emptyMap(), pause, dayStart, todayAt(15))
        assertEquals(before.totalMs, after.totalMs)
    }

    @Test
    fun beforeTheWakingWindowNothingIsDue() {
        val saved = savedToday(mapOf(ig to baseline(90.0)), emptyMap(), emptyList(), dayStart, todayAt(6))
        assertTrue(saved.beforeWaking)
        assertEquals(0L, saved.totalMs)
    }

    @Test
    fun perAppRowsAddUpToTheTotal() {
        val saved = savedToday(
            mapOf(ig to baseline(64.0), yt to baseline(32.0)),
            mapOf(ig to listOf(todayAt(9)..(todayAt(9) + 10 * min))),
            emptyList(), dayStart, todayAt(15)
        )
        assertEquals(saved.totalMs, saved.byApp.values.sum())
        assertEquals(22 * min, saved.byApp[ig]) // 32 − 10
        assertEquals(16 * min, saved.byApp[yt])
    }

    @Test
    fun monthAndYearProjectFromTheSevenDayAverage() {
        // Spec: 40 min/day average → "≈ 20 h" a month and "≈ 10 days" a year.
        assertEquals("20 h", formatProjection(monthProjectionMs(40 * min)))
        assertEquals("10 days", formatProjection(yearProjectionMs(40 * min)))
        assertEquals("10 full days", formatYearProjection(yearProjectionMs(40 * min)))
    }

    @Test
    fun percentagesFollowTheOneRoundingRule() {
        assertEquals("15%", formatPercent(15.0))
        assertEquals("19%", formatPercent(19.4))
        assertEquals("about 20%", formatPercent(22.0))
        assertEquals("about 35%", formatPercent(34.0))
        assertEquals("about 25%", formatPercent(23.0))
    }

    @Test
    fun savedFormatsWithoutSeconds() {
        assertEquals("42 min", formatSaved(42 * min + 59_000L))
        assertEquals("6 h 10 min", formatSaved(6 * hour + 10 * min))
        assertEquals("21 h", formatSaved(21 * hour))
    }

    private fun record(daysAgo: Int, igMinutes: Int, tracked: Int = WAKING_WINDOW_MINUTES): DailyRecord {
        val key = reclaimDayKey(reclaimDayStart(dayStart, DEFAULT_DAY_START_HOUR, daysAgo))
        return DailyRecord(key, mapOf(ig to AppDay(igMinutes, 0)), tracked)
    }

    @Test
    fun historyAveragesTheLastSevenClosedDaysWithData() {
        val records = listOf(
            record(1, 50), record(2, 70), record(3, 90, tracked = 0), // no data: excluded
            record(9, 30) // outside the week, but counts since the start
        ).associateBy { it.dateKey }
        val history = savedHistory(records, mapOf(ig to baseline(90.0)), DEFAULT_DAY_START_HOUR, dayStart)
        assertEquals(3, history.daysWithData)
        assertEquals(30 * min, history.averageLast7Ms) // (40 + 20) / 2
        assertEquals(120 * min, history.sinceStartedMs) // 40 + 20 + 60
        assertEquals(40 * min, history.yesterdayMs)
    }

    @Test
    fun daysBeforeTheBaselineDoNotCount() {
        val records = listOf(record(1, 50), record(5, 50)).associateBy { it.dateKey }
        val anchor = reclaimDayStart(dayStart, DEFAULT_DAY_START_HOUR, daysAgo = 2) + hour
        val history = savedHistory(records, mapOf(ig to baseline(90.0, anchor)), DEFAULT_DAY_START_HOUR, dayStart)
        assertEquals(1, history.daysWithData)
    }

    @Test
    fun theYearWaitsForDayFourteen() {
        val anchor = reclaimDayStart(dayStart, DEFAULT_DAY_START_HOUR, daysAgo = 4) + 2 * hour
        assertEquals(5, dayNumberSince(anchor, DEFAULT_DAY_START_HOUR, dayStart))
        assertFalse(dayNumberSince(anchor, DEFAULT_DAY_START_HOUR, dayStart) >= YEAR_MIN_DAY)
    }

    @Test
    fun closingADayLeavesUntrackedTimeOutOfCountedMinutes() {
        val end = nextReclaimDayStart(dayStart)
        val stretches = mapOf(ig to listOf(todayAt(9)..todayAt(10), todayAt(20)..(todayAt(20) + 30 * min)))
        val record = closeDay("k", dayStart, end, listOf(ig, yt), stretches, mapOf(ig to 18, EARLIER_TODAY_KEY to 3), listOf(todayAt(20)..todayAt(22)))
        assertEquals(AppDay(minutes = 90, reels = 18, countedMinutes = 60), record.apps[ig])
        assertEquals(AppDay(0, 0), record.apps[yt])
        assertNull(record.apps[EARLIER_TODAY_KEY])
        assertEquals(WAKING_WINDOW_MINUTES - 120, record.trackedWakingMinutes)
    }
}
