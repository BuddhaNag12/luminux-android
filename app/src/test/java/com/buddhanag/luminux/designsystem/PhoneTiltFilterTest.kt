package com.buddhanag.luminux.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneTiltFilterTest {
    @Test fun holdingStillDoesNotMove() {
        val filter = PhoneTiltFilter()
        assertFalse(filter.update(0.0, -1.0, 0.0))
        assertEquals(0f, filter.value.x, 0f)
    }

    @Test fun tiltingRightMovesPositiveAndRecentres() {
        val filter = PhoneTiltFilter()
        filter.update(0.0, -1.0, 0.0)
        assertTrue(filter.update(0.25, -0.97, 0.0))
        // First sample: the rest moved 1% toward the new gravity, then the value followed 18% of the way.
        assertEquals(0.18 * (0.25 - 0.0025) / 0.25, filter.value.x.toDouble(), 1e-6)
        repeat(2000) { filter.update(0.25, -0.97, 0.0) }
        assertEquals(0f, filter.value.x, 0.01f)
    }
}
