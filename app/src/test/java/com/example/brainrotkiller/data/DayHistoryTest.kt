package com.example.brainrotkiller.data

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DayHistoryTest {

    private fun msOf(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, 0, 0)
        }.timeInMillis

    @Test
    fun roundTripsAndIgnoresGarbage() {
        val parsed = parseDayHistory("2026-09-24=W;junk;2026-09-25=O;2026-09-26=X")
        assertEquals(mapOf("2026-09-24" to DayOutcome.WITHIN, "2026-09-25" to DayOutcome.OVER), parsed)
        assertEquals("2026-09-24=W;2026-09-25=O", serializeDayHistory(parsed))
    }

    @Test
    fun serializeKeepsOnlyMostRecentDays() {
        val cal = Calendar.getInstance().apply { timeInMillis = msOf(2026, 1, 1) }
        val big = (0 until DAY_HISTORY_KEEP + 10).associate {
            val key = dateKeyOf(cal)
            cal.add(Calendar.DAY_OF_YEAR, 1)
            key to DayOutcome.WITHIN
        }
        val kept = parseDayHistory(serializeDayHistory(big))
        assertEquals(DAY_HISTORY_KEEP, kept.size)
        assertTrue("2026-01-01" !in kept)
    }

    @Test
    fun lastDaysEndsWithOpenToday() {
        val history = mapOf("2026-09-25" to DayOutcome.OVER, "2026-09-20" to DayOutcome.WITHIN)
        val cells = lastDays(history, msOf(2026, 9, 26))
        assertEquals(7, cells.size)
        assertEquals("2026-09-20", cells.first().dateKey)
        assertEquals(DayOutcome.WITHIN, cells.first().outcome)
        assertEquals(DayOutcome.OVER, cells[5].outcome)
        assertTrue(cells.last().isToday)
        assertNull(cells.last().outcome)
    }

    @Test
    fun streakCountsBackFromYesterdayAndStopsAtOver() {
        val today = msOf(2026, 9, 26)
        val history = mapOf(
            "2026-09-25" to DayOutcome.WITHIN,
            "2026-09-24" to DayOutcome.WITHIN,
            "2026-09-23" to DayOutcome.OVER,
            "2026-09-22" to DayOutcome.WITHIN
        )
        assertEquals(2, currentStreak(history, today))
    }

    @Test
    fun noDataDaysAreSkippedNotCountedAndDontBreakTheStreak() {
        val today = msOf(2026, 9, 26)
        val history = mapOf(
            "2026-09-25" to DayOutcome.WITHIN,
            // 09-24 and 09-23 missing: counter wasn't running
            "2026-09-22" to DayOutcome.WITHIN,
            "2026-09-21" to DayOutcome.OVER,
            "2026-09-20" to DayOutcome.WITHIN
        )
        assertEquals(2, currentStreak(history, today))
        // A gap right before today doesn't zero it either.
        assertEquals(1, currentStreak(mapOf("2026-09-23" to DayOutcome.WITHIN), today))
    }

    @Test
    fun streakIsZeroWithNoHistoryOrOverYesterday() {
        val today = msOf(2026, 9, 26)
        assertEquals(0, currentStreak(emptyMap(), today))
        assertEquals(0, currentStreak(mapOf("2026-09-25" to DayOutcome.OVER, "2026-09-24" to DayOutcome.WITHIN), today))
    }

    @Test
    fun noDataDaysShowAsNullOutcomeInStrip() {
        val cells = lastDays(mapOf("2026-09-25" to DayOutcome.WITHIN), msOf(2026, 9, 26))
        assertNull(cells[4].outcome) // 2026-09-24, no record
        assertTrue(!cells[4].isToday)
    }

    @Test
    fun streakCrossesMonthBoundary() {
        val history = mapOf("2026-09-30" to DayOutcome.WITHIN, "2026-10-01" to DayOutcome.WITHIN)
        assertEquals(2, currentStreak(history, msOf(2026, 10, 2)))
    }
}
