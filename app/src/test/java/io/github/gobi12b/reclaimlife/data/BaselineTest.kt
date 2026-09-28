package io.github.gobi12b.reclaimlife.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BaselineTest {

    private val min = 60_000L
    private val anchor = 1_700_000_000_000L
    private val ig = TargetApps.INSTAGRAM

    @Test
    fun underThreeDaysGivesNoBaseline() {
        assertNull(baselineFromHistory(100 * min, 2, anchor))
    }

    @Test
    fun fourteenDaysIsLockedStraightAway() {
        val b = baselineFromHistory(14 * 90 * min, 14, anchor)!!
        assertEquals(90.0, b.minutesPerDay, 0.001)
        assertFalse(b.isProvisional)
    }

    @Test
    fun moreThanFourteenDaysUsesFourteen() {
        assertEquals(14, baselineFromHistory(14 * 90 * min, 30, anchor)!!.preDays)
    }

    @Test
    fun aShortHistoryIsProvisionalForAWeek() {
        val b = baselineFromHistory(5 * 60 * min, 5, anchor)!!
        assertTrue(b.isProvisional)
        assertEquals(anchor + BASELINE_SETTLE_MS, b.provisionalUntilMs)
    }

    private fun day(key: String, minutes: Int, tracked: Int = WAKING_WINDOW_MINUTES) =
        DailyRecord(key, mapOf(ig to AppDay(minutes, 0)), tracked)

    @Test
    fun refiningBlendsByDayCountAndOnlyLowers() {
        val b = baselineFromHistory(4 * 100 * min, 4, anchor)!!
        val lower = refineBaseline(b, listOf(day("2026-01-01", 40), day("2026-01-02", 40)), ig, anchor + DAY_MS)
        assertEquals((4 * 100 + 80) / 6.0, lower.minutesPerDay, 0.001)
        assertTrue(lower.isProvisional)
        // Heavier observed days never lift it above the pre-ReclaimLife figure.
        val higher = refineBaseline(b, listOf(day("2026-01-01", 300)), ig, anchor + DAY_MS)
        assertEquals(100.0, higher.minutesPerDay, 0.001)
    }

    @Test
    fun partlyTrackedDaysAreScaledAndMostlyUntrackedOnesSkipped() {
        val b = baselineFromHistory(3 * 100 * min, 3, anchor)!!
        val halfDay = day("2026-01-01", 30, tracked = WAKING_WINDOW_MINUTES / 2) // → 60 a full day
        val barelyTracked = day("2026-01-02", 0, tracked = 60)
        val refined = refineBaseline(b, listOf(halfDay, barelyTracked), ig, anchor + DAY_MS)
        assertEquals((300 + 60) / 4.0, refined.minutesPerDay, 0.001)
    }

    @Test
    fun locksOnceTheSettleDatePasses() {
        val b = baselineFromHistory(5 * 60 * min, 5, anchor)!!
        assertFalse(refineBaseline(b, emptyList(), ig, b.provisionalUntilMs).isProvisional)
        val locked = b.copy(provisionalUntilMs = 0L)
        assertEquals(locked, refineBaseline(locked, listOf(day("2026-01-01", 0)), ig, anchor))
    }

    @Test
    fun resetIsAllowedOncePerThirtyDays() {
        assertNull(baselineResetAvailableAt(0L, anchor))
        assertEquals(anchor + BASELINE_RESET_INTERVAL_MS, baselineResetAvailableAt(anchor, anchor + DAY_MS))
        assertNull(baselineResetAvailableAt(anchor, anchor + BASELINE_RESET_INTERVAL_MS))
    }

    @Test
    fun roundTripsAndDropsBadEntries() {
        val baselines = mapOf(ig to Baseline(64.25, 90.0, 5, anchor, anchor + 3), "a.b" to Baseline(10.0, 10.0, 14, anchor))
        assertEquals(baselines, parseBaselines(serializeBaselines(baselines)))
        assertEquals(emptyMap<String, Baseline>(), parseBaselines("junk;x=1,2"))
        assertEquals(90, baselineTotalMinutes(listOf(Baseline(60.4, 60.4, 14, 0), Baseline(29.7, 29.7, 14, 0))))
    }
}
