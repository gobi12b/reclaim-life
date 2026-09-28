package io.github.gobi12b.reclaimlife.ui.home

import org.junit.Assert.assertTrue
import org.junit.Test

class TreeGeometryTest {

    @Test
    fun heightAndCanopyNeverDecreaseAlongGrowth() {
        var lastHeight = 0f
        var lastMass = 0f
        growthOrder().forEach { (stage, details) ->
            val height = treeHeight(stage, details)
            val mass = treeMass(stage, details)
            assertTrue("$stage/$details height $height < $lastHeight", height >= lastHeight)
            assertTrue("$stage/$details mass $mass < $lastMass", mass >= lastMass)
            lastHeight = height
            lastMass = mass
        }
    }

    @Test
    fun everyShapeStaysOnTheCanvas() {
        growthOrder().forEach { (stage, details) ->
            treeBounds(stage, details).forEach { (left, top, right, bottom) ->
                assertTrue("$stage/$details $left,$top,$right,$bottom", left >= 0f && top >= 0f && right <= 112f && bottom <= 148f)
            }
        }
    }
}
