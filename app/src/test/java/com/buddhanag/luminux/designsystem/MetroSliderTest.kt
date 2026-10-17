package com.buddhanag.luminux.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class MetroSliderTest {
    @Test fun clampsAndSnapsToRest() {
        assertEquals(1.0, metroSliderValue(1.4, -1.0..1.0, 0.0), 0.0)
        assertEquals(0.0, metroSliderValue(0.029, -1.0..1.0, 0.0), 0.0)
        assertEquals(0.03, metroSliderValue(0.03, -1.0..1.0, 0.0), 0.0)
    }
}
