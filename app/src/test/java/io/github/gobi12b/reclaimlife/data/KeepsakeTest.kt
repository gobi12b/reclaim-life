package io.github.gobi12b.reclaimlife.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepsakeTest {
    @Test
    fun keepsakesComeFromTheBestStreakAndStay() {
        assertEquals(emptyList<Keepsake>(), keepsakesFor(2))
        assertEquals(listOf(Keepsake.LADYBUG), keepsakesFor(3))
        assertEquals(listOf(Keepsake.LADYBUG, Keepsake.BUTTERFLY), keepsakesFor(13))
        assertEquals(Keepsake.entries, keepsakesFor(365))
    }

    @Test
    fun nextKeepsakeIsTheFirstNotEarned() {
        assertEquals(Keepsake.LADYBUG, nextKeepsake(0))
        assertEquals(Keepsake.NEST, nextKeepsake(7))
        assertNull(nextKeepsake(90))
    }

    @Test
    fun everyKeepsakeLandsOnABoostDay() {
        // A visitor arrives with a streak boost, so the callout can name it.
        Keepsake.entries.forEach { assertTrue(milestoneBonus(it.streak) > 0) }
        assertEquals(Keepsake.BUTTERFLY, keepsakeAt(7))
        assertNull(keepsakeAt(8))
    }

    @Test
    fun aBrokenStreakKeepsWhatWasEarned() {
        val state = treeState("", emptyMap(), 0, null, null, 0, "2026-09-29", 9, null, emptyMap(), false, bestStreak = 10)
        assertEquals(listOf(Keepsake.LADYBUG, Keepsake.BUTTERFLY), state.keepsakes)
        assertEquals(Keepsake.NEST, state.nextKeepsake)
    }
}
