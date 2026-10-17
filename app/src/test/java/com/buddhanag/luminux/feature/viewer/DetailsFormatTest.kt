package com.buddhanag.luminux.feature.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetailsFormatTest {
    @Test fun exposureRowFormatsLikeIos() {
        assertEquals("f/1.8 · 1/120 s · ISO 100 · 26 mm", DetailsFormat.exposure(1.8, 1.0 / 120, 100, 26))
        assertEquals("2 s", DetailsFormat.exposure(null, 2.0, null, null))
        assertEquals("f/2", DetailsFormat.exposure(2.0, null, null, null))
        assertNull(DetailsFormat.exposure(null, null, null, null))
    }

    @Test fun videoTimesFloorElapsedAndCeilRemaining() {
        assertEquals("1:05", DetailsFormat.time(65.9))
        assertEquals("-0:07", DetailsFormat.remaining(10.0, 3.2))
    }

    @Test fun dimensionsShowMegapixels() {
        assertEquals("4032 × 3024 · 12.2 MP", DetailsFormat.dimensions(4032, 3024))
    }
}
