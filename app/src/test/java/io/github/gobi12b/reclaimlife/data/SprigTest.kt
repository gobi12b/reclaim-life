package io.github.gobi12b.reclaimlife.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SprigTest {
    private fun grew(key: String, points: Int) = key to GrowthDay(key, GrowthCode.GREW, dayPoints = points)

    @Test
    fun aLeafForEachGrowingDayAfterTheStageBegan() {
        // 20 points on the 1st reaches Sprout (15); the next two growing days are leaves.
        val rows = mapOf(grew("2026-09-01", 20), grew("2026-09-02", 10), grew("2026-09-03", 10))
        assertEquals(TreeStage.SPROUT, treeStage(growthTotal(rows, 0)))
        assertEquals(2, sprigsInStage(rows, 0, TreeStage.SPROUT))
    }

    @Test
    fun restingDaysAddNoLeaf() {
        val rows = mapOf(grew("2026-09-01", 20), "2026-09-02" to GrowthDay("2026-09-02", GrowthCode.OVER), grew("2026-09-03", 10))
        assertEquals(1, sprigsInStage(rows, 0, TreeStage.SPROUT))
    }

    @Test
    fun leavesAreCapped() {
        val rows = (1..20).associate { grew("2026-10-%02d".format(it), 1) } + grew("2026-09-30", 15)
        assertEquals(MAX_SPRIGS, sprigsInStage(rows, 0, TreeStage.SPROUT))
    }

    @Test
    fun everyThirdLeafIsCalledABranch() {
        val rows = mapOf(grew("2026-09-01", 20), grew("2026-09-02", 10), grew("2026-09-03", 10))
        val c = treeCallout(null, null, TreeStage.SPROUT, rows, 0, 1, "Fern", "2026-09-04", 9, sprigs = 3)
        assertEquals("Fern grew a new branch overnight.", c.text)
        val d = treeCallout(null, null, TreeStage.SPROUT, rows, 0, 1, "Fern", "2026-09-04", 9, sprigs = 2)
        assertEquals("Fern grew a new leaf overnight.", d.text)
    }
}
