package io.github.gobi12b.reclaimlife.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TreeStageTest {

    @Test
    fun thresholds() {
        assertEquals(TreeStage.SEED, treeStage(0))
        assertEquals(TreeStage.SEED, treeStage(14))
        assertEquals(TreeStage.SPROUT, treeStage(15))
        assertEquals(TreeStage.FRUITING, treeStage(2699))
        assertEquals(TreeStage.FULL_CANOPY, treeStage(2700))
        assertEquals(TreeStage.SEED, treeStage(-5))
        assertEquals(TreeStage.SAPLING, nextTreeStage(TreeStage.YOUNG_PLANT))
        assertNull(nextTreeStage(TreeStage.FULL_CANOPY))
    }

    @Test
    fun details() {
        assertEquals(0, detailsShown(TreeStage.TREE, 650))
        assertEquals(4, detailsShown(TreeStage.TREE, 1099))
        assertEquals(4, detailsShown(TreeStage.BLOSSOMING, 1100 + 300))
        assertEquals(6, detailsShown(TreeStage.FULL_CANOPY, 4200))
        assertEquals(6, detailsShown(TreeStage.FULL_CANOPY, 9999))
        assertEquals(0, detailsShown(TreeStage.SAPLING, 400))
    }

    @Test
    fun stageAndDetailsNeverDecrease() {
        var last = TreeStage.SEED to 0
        for (total in 0L..5000L) {
            val stage = treeStage(total)
            val details = detailsShown(stage, total)
            assertTrue(stage.ordinal >= last.first.ordinal)
            if (stage == last.first) assertTrue("at $total", details >= last.second)
            last = stage to details
        }
    }

    @Test
    fun legacyFloor() {
        val hour = 3_600_000L
        assertEquals(TreeStage.YOUNG_TREE, legacyFloorStage(72 * hour))
        assertEquals(TreeStage.SEED, legacyFloorStage(0))
        assertEquals(TreeStage.YOUNG_PLANT, legacyFloorStage(13 * hour))
    }
}
