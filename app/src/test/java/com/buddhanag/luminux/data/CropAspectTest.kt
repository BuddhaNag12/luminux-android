package com.buddhanag.luminux.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CropAspectTest {
    @Test fun shapesTurnWithTheirOrientation() {
        assertNull(CropAspect.FREE.ratio(4000, 3000, landscape = true))
        assertEquals(1.0, CropAspect.SQUARE.ratio(4000, 3000, landscape = false)!!, 0.0)
        assertEquals(16.0 / 9, CropAspect.SIXTEEN_NINE.ratio(4000, 3000, landscape = true)!!, 0.0001)
        assertEquals(9.0 / 16, CropAspect.SIXTEEN_NINE.ratio(4000, 3000, landscape = false)!!, 0.0001)
        assertEquals(4.0 / 3, CropAspect.FOUR_THREE.ratio(3000, 4000, landscape = true)!!, 0.0001)
        assertEquals(5.0 / 7, CropAspect.SEVEN_FIVE.ratio(4000, 3000, landscape = false)!!, 0.0001)
        // The photo's own shape, either way round.
        assertEquals(4.0 / 3, CropAspect.ORIGINAL.ratio(4000, 3000, landscape = true)!!, 0.0001)
        assertEquals(3.0 / 4, CropAspect.ORIGINAL.ratio(3000, 4000, landscape = false)!!, 0.0001)
        assertEquals(8, CropAspect.entries.size)
    }
}

class AdjustmentGroupTest {
    @Test fun everyAdjustmentIsInOneGroup() {
        val grouped = AdjustmentGroup.entries.flatMap { it.adjustments }
        assertEquals(Adjustment.entries.size, grouped.size)
        assertEquals(Adjustment.entries.toSet(), grouped.toSet())
        assertEquals(listOf(AdjustmentGroup.LIGHT, AdjustmentGroup.COLOUR, AdjustmentGroup.EFFECTS), AdjustmentGroup.entries)
    }
}
