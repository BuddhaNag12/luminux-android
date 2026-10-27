package com.buddhanag.luminux.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.Locale

class MetroDialTest {
    private val full = DIAL_DP_PER_RANGE.toFloat()

    @Test fun theDialFollowsTheDragAndCatchesZero() {
        // Dragging left raises the value; a quarter of the drag distance covers a quarter of the range.
        assertEquals(0.5, dialValue(0.0, -full / 4, -1.0..1.0, 0.0), 0.0001)
        // Clamped at the ends.
        assertEquals(1.0, dialValue(0.9, -full, -1.0..1.0, 0.0), 0.0)
        assertEquals(0.0, dialValue(0.5, full, 0.0..1.0, 0.0), 0.0)
        assertEquals(-1.0, dialValue(-0.5, full, -1.0..1.0, 0.0), 0.0)
        // Caught at zero only when close to it.
        assertEquals(0.0, dialValue(0.0, -full * 0.01f, -1.0..1.0, 0.0), 0.0)
        assertNotEquals(0.0, dialValue(0.0, -full * 0.05f, -1.0..1.0, 0.0), 0.0)
        assertEquals("+24", dialText(0.24))
        assertEquals("-7", dialText(-0.07))
        assertEquals("0", dialText(0.0))
    }

    @Test fun degreesReadCleanly() {
        assertEquals("0°", dialDegrees(0.0))
        assertEquals("+12°", dialDegrees(12.0))
        assertEquals("-3.5°", dialDegrees(-3.46))
        // The straighten ruler catches level within a degree or so.
        assertEquals(0.0, dialValue(0.0, -4f, -45.0..45.0, 0.0), 0.0)
    }

    @Test fun halvesRoundAwayFromZeroLikeSwift() {
        // -0.375 × 100 is exactly -37.5; roundToInt() would give -37.
        assertEquals("-38", dialText(-0.375))
        assertEquals("+38", dialText(0.375))
        assertEquals("-3.3°", dialDegrees(-3.25))
    }

    @Test fun noCommaUnderAGermanLocale() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("-3.5°", dialDegrees(-3.46))
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test fun ticksCountFromTheStartOfTheRange() {
        assertEquals(20, dialTick(0.0, -1.0..1.0, 40))
        assertEquals(40, dialTick(1.0, -1.0..1.0, 40))
        assertEquals(45, dialTick(0.0, -45.0..45.0, 90))
    }
}
