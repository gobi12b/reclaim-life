package io.github.gobi12b.reclaimlife.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GrowthStageTest {

    private val minute = 60_000L
    private val hour = 60 * minute

    @Test
    fun seedUntilHalfAnHour() {
        assertEquals(GrowthStage.SEED, growthStage(0))
        assertEquals(GrowthStage.SEED, growthStage(30 * minute - 1_000))
    }

    @Test
    fun thresholdsAreInclusive() {
        assertEquals(GrowthStage.SPROUT, growthStage(30 * minute))
        assertEquals(GrowthStage.SEEDLING, growthStage(3 * hour))
        assertEquals(GrowthStage.SEEDLING, growthStage(11 * hour + 59 * minute))
        assertEquals(GrowthStage.YOUNG, growthStage(13 * hour))
        assertEquals(GrowthStage.SAPLING, growthStage(24 * hour))
        assertEquals(GrowthStage.BLOOM, growthStage(72 * hour))
    }

    @Test
    fun negativeIsSeed() {
        assertEquals(GrowthStage.SEED, growthStage(-5 * minute))
    }

    @Test
    fun nextStageStopsAtBloom() {
        assertEquals(GrowthStage.SAPLING, nextStage(GrowthStage.YOUNG))
        assertNull(nextStage(GrowthStage.BLOOM))
    }
}
