package io.github.gobi12b.reclaimlife.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class SavedHeadlineTest {

    private val minute = 60_000L
    private val hour = 60 * minute

    @Test
    fun todayLeadsOnceThereIsAMinute() {
        assertEquals(SavedHeadline(HeadlineKind.TODAY, 42 * minute), savedHeadline(42 * minute, false, 38 * minute, 6 * hour))
    }

    @Test
    fun yesterdayBeforeTheWakingWindow() {
        assertEquals(SavedHeadline(HeadlineKind.YESTERDAY, 38 * minute), savedHeadline(0, true, 38 * minute, 6 * hour))
    }

    @Test
    fun zeroYesterdayFallsThroughToSinceStart() {
        assertEquals(SavedHeadline(HeadlineKind.SINCE_START, 6 * hour), savedHeadline(0, true, 0, 6 * hour))
    }

    @Test
    fun underAMinuteTodayLeadsWithSinceStart() {
        assertEquals(SavedHeadline(HeadlineKind.SINCE_START, 6 * hour), savedHeadline(30_000, false, null, 6 * hour))
    }

    @Test
    fun nothingEverIsFreshNotZero() {
        assertEquals(SavedHeadline(HeadlineKind.FRESH, 0), savedHeadline(0, false, 0, 0))
        assertEquals(SavedHeadline(HeadlineKind.FRESH, 0), savedHeadline(0, true, null, 0))
    }

    @Test
    fun spokenSpellsOutUnits() {
        assertEquals("6 hours 10 minutes", spoken("6 h 10 min"))
        assertEquals("42 minutes", spoken("42 min"))
        assertEquals("1 hour 5 minutes", spoken("1 h 05 min"))
        assertEquals("10 days", spoken("10 days"))
    }
}
