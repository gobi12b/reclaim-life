package io.github.gobi12b.reclaimlife.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LimitModeTest {

    @Test
    fun droppingAnEnforcedLimitLoosens() {
        assertTrue(LimitMode.BOTH.loosensTo(LimitMode.DAILY))
        assertTrue(LimitMode.BOTH.loosensTo(LimitMode.HOURLY))
        assertTrue(LimitMode.DAILY.loosensTo(LimitMode.HOURLY))
        assertTrue(LimitMode.HOURLY.loosensTo(LimitMode.DAILY))
    }

    @Test
    fun addingALimitDoesNotLoosen() {
        assertFalse(LimitMode.DAILY.loosensTo(LimitMode.BOTH))
        assertFalse(LimitMode.HOURLY.loosensTo(LimitMode.BOTH))
        assertFalse(LimitMode.BOTH.loosensTo(LimitMode.BOTH))
    }

    @Test
    fun unknownOrMissingStoredModeIsDaily() {
        assertEquals(LimitMode.DAILY, LimitMode.fromStored(null))
        assertEquals(LimitMode.DAILY, LimitMode.fromStored("garbage"))
        assertEquals(LimitMode.HOURLY, LimitMode.fromStored("HOURLY"))
    }

    @Test
    fun dailyAndBothScoreTheDailyCount() {
        for (mode in listOf(LimitMode.DAILY, LimitMode.BOTH)) {
            assertTrue(dayWithinLimit(mode, count = 40, dailyLimit = 30, extraAllowance = 10, hourlyBreaks = 9))
            assertFalse(dayWithinLimit(mode, count = 41, dailyLimit = 30, extraAllowance = 10, hourlyBreaks = 0))
        }
    }

    @Test
    fun hourlyOnlyScoresHowManyBreaksReopenedReels() {
        assertTrue(dayWithinLimit(LimitMode.HOURLY, count = 500, dailyLimit = 30, extraAllowance = 0, hourlyBreaks = 0))
        assertTrue(dayWithinLimit(LimitMode.HOURLY, count = 5, dailyLimit = 30, extraAllowance = 0, hourlyBreaks = HOURLY_BREAKS_WITHIN_LIMIT))
        assertFalse(dayWithinLimit(LimitMode.HOURLY, count = 5, dailyLimit = 30, extraAllowance = 0, hourlyBreaks = HOURLY_BREAKS_WITHIN_LIMIT + 1))
    }
}
