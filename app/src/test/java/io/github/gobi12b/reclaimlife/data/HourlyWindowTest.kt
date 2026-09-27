package io.github.gobi12b.reclaimlife.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HourlyWindowTest {

    private val now = 10 * HOURLY_WINDOW_MS
    private fun minutesAgo(m: Long) = now - m * 60_000L

    @Test
    fun windowKeepsOnlyTheLastHour() {
        val times = listOf(minutesAgo(61), minutesAgo(60), minutesAgo(59), minutesAgo(1))
        assertEquals(listOf(minutesAgo(59), minutesAgo(1)), reelsInWindow(times, now))
    }

    @Test
    fun underLimitOrOffIsNotBlocked() {
        val times = List(29) { minutesAgo(it.toLong()) }
        assertNull(hourlyUnblockAt(times, 30, now))
        assertNull(hourlyUnblockAt(times + now, 0, now))
    }

    @Test
    fun atLimitUnblocksWhenOldestAgesOut() {
        // 30 reels, the oldest 50 minutes ago → one more allowed in 10 minutes.
        val times = listOf(minutesAgo(50)) + List(29) { minutesAgo(5) }
        assertEquals(minutesAgo(50) + HOURLY_WINDOW_MS, hourlyUnblockAt(times, 30, now))
    }

    @Test
    fun overLimitWaitsForEnoughToAgeOut() {
        // Limit 3 with 5 in the window: the 3 oldest must go, so it's the 3rd-oldest that matters.
        val times = listOf(minutesAgo(50), minutesAgo(40), minutesAgo(30), minutesAgo(20), minutesAgo(10))
        assertEquals(minutesAgo(30) + HOURLY_WINDOW_MS, hourlyUnblockAt(times, 3, now))
    }

    @Test
    fun timesRoundTrip() {
        val times = listOf(1L, 22L, 333L)
        assertEquals(times, parseReelTimes(serializeReelTimes(times)))
        assertEquals(emptyList<Long>(), parseReelTimes(null))
        assertEquals(emptyList<Long>(), parseReelTimes(""))
    }
}
