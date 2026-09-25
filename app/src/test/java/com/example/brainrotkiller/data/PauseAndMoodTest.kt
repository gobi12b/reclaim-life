package com.example.brainrotkiller.data

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

class PauseAndMoodTest {

    @Test
    fun fixedDurations() {
        assertEquals(1_000L + 15 * 60_000L, PauseDuration.FIFTEEN_MINUTES.endsAt(1_000L))
        assertEquals(1_000L + 60 * 60_000L, PauseDuration.ONE_HOUR.endsAt(1_000L))
    }

    @Test
    fun restOfTodayEndsAtNextLocalMidnight() {
        val now = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.SEPTEMBER, 26, 23, 30, 0)
        }.timeInMillis
        val end = Calendar.getInstance().apply { timeInMillis = PauseDuration.REST_OF_TODAY.endsAt(now) }
        assertEquals(27, end.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, end.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, end.get(Calendar.MINUTE))
    }

    @Test
    fun remainingFormatting() {
        assertEquals("15:00", formatPauseRemaining(15 * 60_000L))
        assertEquals("0:01", formatPauseRemaining(1))
        assertEquals("1 h 05 min", formatPauseRemaining(65 * 60_000L))
        assertEquals("0:00", formatPauseRemaining(-5))
    }

    @Test
    fun moodThresholdsHoldNegativeMoodsBack() {
        // The screenshot case: 104 / 190 (~55%) used to read "Getting close".
        assertEquals(Mood.GOOD, Mood.forProgress(104, 190))
        assertEquals(Mood.UNEASY, Mood.forProgress(70, 100))
        assertEquals(Mood.TIRED, Mood.forProgress(90, 100))
        assertEquals(Mood.DONE, Mood.forProgress(100, 100))
        assertEquals(Mood.ENERGIZED, Mood.forProgress(0, 100))
    }
}
