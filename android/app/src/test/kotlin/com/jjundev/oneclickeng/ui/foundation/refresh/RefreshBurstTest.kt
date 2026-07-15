package com.jjundev.oneclickeng.ui.foundation.refresh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshBurstTest {
    @Test fun burstParticles_hasRequestedCount() {
        assertEquals(13, burstParticles(count = 13, seed = 1).size)
    }

    @Test fun burstParticles_isDeterministicForSeed() {
        assertEquals(burstParticles(count = 13, seed = 7), burstParticles(count = 13, seed = 7))
    }

    @Test fun burstParticles_fractionsAreInRange() {
        burstParticles(count = 13, seed = 3).forEach { p ->
            assertTrue(p.distFraction in 0f..1f)
            assertTrue(p.sizeFraction in 0f..1f)
            assertTrue(p.delayFraction in 0f..1f)
            assertTrue(p.colorIndex in 0..2)
        }
    }

    @Test fun burstParticles_anglesSpanFullCircle() {
        val angles = burstParticles(count = 13, seed = 2).map { it.angleRad }
        assertTrue("min angle near 0", angles.min() < 1f)
        assertTrue("max angle near 2pi", angles.max() > 5f)
    }
}
