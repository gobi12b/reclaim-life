package io.github.gobi12b.reclaimlife.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InsightsTest {

    private val ig = TargetApps.INSTAGRAM
    private val yt = TargetApps.YOUTUBE
    private val min = 60_000L
    // 15:30 local time today, whatever the test machine's zone.
    private val today = localDayStart(System.currentTimeMillis())
    private val now = today + 15 * 60 * min + 30 * min

    @Test
    fun creditsTimeToTheRightDayAndHour() {
        val stretches = listOf(
            AppStretch(ig, today + 9 * 60 * min, today + 9 * 60 * min + 20 * min), // 09:00–09:20 today
            AppStretch(yt, today + 14 * 60 * min + 50 * min, today + 15 * 60 * min + 10 * min), // 14:50–15:10
            AppStretch(ig, localDayStart(now, 1) + 22 * 60 * min, localDayStart(now, 1) + 22 * 60 * min + 5 * min)
        )
        val insights = buildInsights(stretches, now, fromSystem = true)

        assertEquals(INSIGHT_DAYS, insights.days.size)
        assertEquals(20 * min, insights.days.last().msByPackage[ig])
        assertEquals(20 * min, insights.days.last().msByPackage[yt])
        assertEquals(5 * min, insights.days[INSIGHT_DAYS - 2].msByPackage[ig])
        // The YouTube stretch straddles 15:00, so it's split across two hours.
        assertEquals(10 * min, insights.msByHour[14])
        assertEquals(10 * min, insights.msByHour[15])
        assertEquals(20 * min, insights.msByHour[9])
        assertEquals(9, insights.peakHour)
        assertEquals(3, insights.opens)
    }

    @Test
    fun quickReturnsAreOneVisitAndOldTimeIsIgnored() {
        val start = today + 10 * 60 * min
        val stretches = listOf(
            AppStretch(ig, start, start + 5 * min),
            AppStretch(ig, start + 5 * min + 30_000L, start + 8 * min), // back within a minute
            AppStretch(ig, localDayStart(now, INSIGHT_DAYS), localDayStart(now, INSIGHT_DAYS) + 60 * min) // too old
        )
        val insights = buildInsights(stretches, now, fromSystem = false)
        assertEquals(1, insights.opens)
        assertEquals(7 * min + 30_000L, insights.totalMs)
    }

    @Test
    fun noUsageHasNoPeak() {
        val insights = buildInsights(emptyList(), now, fromSystem = false)
        assertNull(insights.peakHour)
        assertEquals(0L, insights.averageVisitMs)
    }

    @Test
    fun gateDecisionsRoundTrip() {
        val decisions = listOf(GateDecision(1L, skipped = true), GateDecision(2L, skipped = false))
        assertEquals(decisions, parseGateDecisions(serializeGateDecisions(decisions)))
        assertEquals(emptyList<GateDecision>(), parseGateDecisions("junk"))
    }
}
