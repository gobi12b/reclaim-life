package io.github.gobi12b.reclaimlife.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** Per-app reels and their migration, daily usage, pauses, limits and intervals. */
class PerAppDataTest {

    private val ig = TargetApps.INSTAGRAM
    private val yt = TargetApps.YOUTUBE
    private val hour = 60 * 60_000L

    @Test
    fun upgradeDayKeepsTheOldTotalAsEarlierToday() {
        assertEquals(mapOf(EARLIER_TODAY_KEY to 30), reelCountsFromStored(null, 30))
        assertEquals(emptyMap<String, Int>(), reelCountsFromStored(null, 0))
        // Once the split exists, the legacy number is never read again.
        assertEquals(mapOf(ig to 2), reelCountsFromStored("$ig=2", 30))
    }

    @Test
    fun perAppCountsAddUpToTheTotal() {
        val counts = mapOf(ig to 18, yt to 12)
        assertEquals(counts, parseReelCounts(serializeReelCounts(counts)))
        assertEquals(30, totalReels(counts))
        assertEquals(mapOf(ig to 1), parseReelCounts("$ig=1;bad;$yt=0;=4"))
    }

    @Test
    fun reelsByDayKeepsTheNewestDays() {
        val byDay = (1..12).associate { "2026-01-%02d".format(it) to mapOf(ig to it) }
        val kept = parseReelsByDay(serializeReelsByDay(byDay))
        assertEquals(REELS_BY_DAY_KEEP, kept.size)
        assertEquals(mapOf(ig to 12), kept["2026-01-12"])
        assertNull(kept["2026-01-01"])
    }

    @Test
    fun dailyUsageRoundTripsAndReadsTheSpecFormat() {
        val records = mapOf(
            "2026-09-28" to DailyRecord("2026-09-28", mapOf(ig to AppDay(42, 18, 40), yt to AppDay(10, 12)), 900)
        )
        assertEquals(records, parseDailyUsage(serializeDailyUsage(records)))
        // The spec's pkg:minutes:reels form, with no tracked entry, reads as a fully tracked day.
        val plain = parseDailyUsage("2026-09-27=$ig:30:5")["2026-09-27"]!!
        assertEquals(AppDay(30, 5, 30), plain.apps[ig])
        assertEquals(WAKING_WINDOW_MINUTES, plain.trackedWakingMinutes)
        assertEquals(emptyMap<String, DailyRecord>(), parseDailyUsage("junk"))
    }

    @Test
    fun dailyUsageKeeps400Days() {
        val many = (0 until 450).associate { i ->
            val key = "%04d-01-01".format(2000 + i)
            key to DailyRecord(key, emptyMap(), 960)
        }
        assertEquals(DAILY_USAGE_KEEP_DAYS, parseDailyUsage(serializeDailyUsage(many)).size)
    }

    private val now = 1_800_000_000_000L

    @Test
    fun restOfTodayIsOncePerSeventyTwoHours() {
        val used = PauseEntry(now - 24 * hour, now - 20 * hour, PauseReason.FRIENDS, PauseDuration.REST_OF_TODAY)
        assertEquals(now + 48 * hour, restOfTodayAvailableAt(listOf(used), now))
        assertNull(restOfTodayAvailableAt(listOf(used.copy(startMs = now - 73 * hour)), now))
        val short = PauseEntry(now - hour, now, PauseReason.WORK, PauseDuration.FIFTEEN_MINUTES)
        assertNull(restOfTodayAvailableAt(listOf(short), now))
    }

    @Test
    fun availabilityReadsTonightUnderADayAndAWeekdayBeyond() {
        val eveningToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 21); set(Calendar.MINUTE, 10); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val morning = eveningToday - 12 * hour
        assertTrue(formatAvailableAt(eveningToday, morning).startsWith("Available tonight at "))
        assertTrue(formatAvailableAt(eveningToday + 2 * 24 * hour, morning).matches(Regex("Available \\S+ .+")))
        assertFalse(formatAvailableAt(eveningToday + 2 * 24 * hour, morning).contains("tonight"))
    }

    @Test
    fun pauseLogRoundTripsAndForgetsAfterNinetyDays() {
        val log = listOf(
            PauseEntry(now - hour, now, PauseReason.RELAXING, PauseDuration.ONE_HOUR),
            PauseEntry(now - 100 * DAY_MS, now - 100 * DAY_MS + hour, PauseReason.OTHER, PauseDuration.ONE_HOUR)
        )
        assertEquals(log.take(1), parsePauseLog(serializePauseLog(log, now)))
    }

    @Test
    fun intentionNeedsThreeWords() {
        assertFalse(intentionIsValid("Movie night"))
        assertTrue(intentionIsValid("Movie night with friends"))
        assertFalse(intentionIsValid("word ".repeat(20) + "and more"))
    }

    @Test
    fun theLimitsRowMovesUpNearTheLimit() {
        // Spec: 4 of 50 left → directly under Alerts. 20 left stays in place.
        assertTrue(limitStatus(LimitMode.DAILY, 50, 0, 46, 30, emptyList(), now).nearLimit)
        assertFalse(limitStatus(LimitMode.DAILY, 50, 0, 30, 30, emptyList(), now).nearLimit)
        val blocked = limitStatus(LimitMode.DAILY, 50, 5, 55, 30, emptyList(), now)
        assertEquals(0, blocked.left)
        assertTrue(blocked.blocked)
        val hourly = limitStatus(LimitMode.HOURLY, 50, 0, 5, 10, List(4) { now - it }, now)
        assertEquals(6, hourly.left)
        assertTrue(hourly.hourlyOnly)
    }

    @Test
    fun intervalsMergeAndSubtract() {
        assertEquals(listOf(0L..30L, 40L..50L), listOf(20L..30L, 0L..25L, 40L..50L).merged())
        assertEquals(60L, uncoveredMs(0L..100L, listOf(10L..30L, 20L..40L, 90L..200L)))
        assertEquals(15L, stretchMsOutside(listOf(0L..10L, 5L..20L, 30L..40L), 0L..35L, listOf(10L..20L)))
    }
}
