package com.buddhanag.luminux.feature.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridZoomTest {
    @Test fun pinchingOutShowsFewerBiggerPhotos() {
        assertEquals(3, GridZoom.columnsAfter(4, 1.4f))
        assertEquals(2, GridZoom.columnsAfter(4, 2.5f))
    }

    @Test fun pinchingInShowsMorePhotos() {
        assertEquals(6, GridZoom.columnsAfter(4, 0.7f))
        assertEquals(8, GridZoom.columnsAfter(4, 0.4f))
    }

    @Test fun smallPinchesAndTheEndsStayPut() {
        assertEquals(4, GridZoom.columnsAfter(4, 1.1f))
        assertEquals(2, GridZoom.columnsAfter(2, 3f))
        assertEquals(8, GridZoom.columnsAfter(8, 0.3f))
    }

    @Test fun oddColumnCountsSnapToALevel() {
        assertEquals(4, GridZoom.nearestLevel(5))
        assertEquals(8, GridZoom.nearestLevel(20))
    }

    @Test fun aQuickFlickTakesOneStep() {
        assertEquals(3, GridZoom.columnsAfter(4, 1.05f, velocity = 2f))
        assertEquals(6, GridZoom.columnsAfter(4, 0.95f, velocity = -2f))
        assertEquals(4, GridZoom.columnsAfter(4, 1.05f, velocity = 0f))
    }

    @Test fun theGridScalesFreelyUpToTheEndLevels() {
        val limits = GridZoom.scaleLimits(4)
        assertEquals(0.5f..2f, limits)
        assertEquals(1.5f, GridZoom.rubberBand(1.5f, limits))
        assertEquals(0.6f, GridZoom.rubberBand(0.6f, limits))
    }

    @Test fun pastTheEndLevelsTheGridResists() {
        val limits = GridZoom.scaleLimits(2)
        val stretched = GridZoom.rubberBand(3f, limits)
        assertTrue(stretched > 1 && stretched < 1.5)
        val squeezed = GridZoom.rubberBand(0.1f, GridZoom.scaleLimits(8))
        assertTrue(squeezed < 1 && squeezed > 0.6)
    }
}
