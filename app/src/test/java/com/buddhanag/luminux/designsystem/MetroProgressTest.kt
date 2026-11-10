package com.buddhanag.luminux.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetroProgressTest {
    @Test fun positionStartsAtZeroCentersAtHalfEndsAtOne() {
        assertEquals(0.0, MetroProgressMath.position(0.0), 1e-6)
        assertEquals(0.5, MetroProgressMath.position(0.5), 1e-6)
        assertEquals(1.0, MetroProgressMath.position(1.0), 1e-6)
    }

    @Test fun positionMovesSlowestThroughCenter() {
        val fastStart = MetroProgressMath.position(0.1) - MetroProgressMath.position(0.0)
        val slowMid = MetroProgressMath.position(0.55) - MetroProgressMath.position(0.45)
        assertTrue(fastStart > slowMid)
    }
}
