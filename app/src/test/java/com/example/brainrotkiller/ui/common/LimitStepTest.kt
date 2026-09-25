package com.example.brainrotkiller.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class LimitStepTest {

    @Test
    fun stepsSnapToMultiplesOfFive() {
        assertEquals(190, stepUp(186, 300))
        assertEquals(185, stepDown(186))
        assertEquals(195, stepUp(190, 300))
        assertEquals(185, stepDown(190))
    }

    @Test
    fun stepsRespectBounds() {
        assertEquals(1, stepDown(5))
        assertEquals(1, stepDown(1))
        assertEquals(5, stepUp(1, 300))
        assertEquals(300, stepUp(298, 300))
        assertEquals(300, stepUp(300, 300))
    }
}
