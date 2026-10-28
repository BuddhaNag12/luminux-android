package com.buddhanag.luminux.feature.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/** GridZoomTests.swift: the levels and the pinch's maths. */
class GridZoomTest {
    @Test fun levelsAddTheSameColumnsOnEitherSide() {
        assertEquals(listOf(3, 5, 7, 15), GridZoom.levels)
        GridZoom.levels.zipWithNext().forEach { (fewer, more) -> assertEquals(0, (more - fewer) % 2) }
    }

    @Test fun aPinchHeadsToTheNextLevelEitherWay() {
        // 5 columns drawn at 1.4× look like 3.6 columns.
        assertEquals(3, GridZoom.levelToward(5 / 1.4f, 5))
        assertEquals(7, GridZoom.levelToward(5 / 0.7f, 5))
        assertEquals(15, GridZoom.levelToward(7 / 0.7f, 7))
        assertNull(GridZoom.levelToward(5f, 5))
        assertNull(GridZoom.levelToward(2f, 3))
        assertNull(GridZoom.levelToward(20f, 15))
    }

    @Test fun progressIsHalfwayAtTheLogMidpoint() {
        assertEquals(0f, GridZoom.progress(3f, 3, 5))
        assertEquals(1f, GridZoom.progress(5f, 3, 5))
        assertEquals(0.5f, GridZoom.progress(sqrt(15f), 3, 5), 0.001f)
        assertEquals(1f, GridZoom.progress(9f, 3, 5))
        // Pinching the other way counts from the same start.
        assertEquals(0.5f, GridZoom.progress(sqrt(75f), 15, 5), 0.001f)
    }

    @Test fun savedColumnCountsSnapToALevel() {
        assertEquals(3, GridZoom.nearestLevel(4))
        assertEquals(7, GridZoom.nearestLevel(8))
        assertEquals(15, GridZoom.nearestLevel(20))
        // The levels that are gone come back as the biggest photos there are.
        assertEquals(3, GridZoom.nearestLevel(2))
        assertEquals(3, GridZoom.nearestLevel(1))
    }

    @Test fun onePinchCanGoThroughEveryLevel() {
        val fromFewest = GridZoom.scaleLimits(3)
        assertTrue(abs(fromFewest.start - 3f / 15) < 0.001f && fromFewest.endInclusive == 1f)
        assertEquals(0.3f, GridZoom.rubberBand(0.3f, fromFewest))
        val fromMost = GridZoom.scaleLimits(15)
        assertTrue(fromMost.start == 1f && fromMost.endInclusive == 5f)
    }

    @Test fun pastTheEndLevelsTheGridResists() {
        val stretched = GridZoom.rubberBand(2f, GridZoom.scaleLimits(2))
        assertTrue(stretched > 1 && stretched < 1.5f)
        val squeezed = GridZoom.rubberBand(0.5f, GridZoom.scaleLimits(15))
        assertTrue(squeezed < 1 && squeezed > 0.8f)
    }

    @Test fun theSmallestPhotosCantBeSelected() {
        assertTrue(GridZoom.allowsSelection(7))
        assertFalse(GridZoom.allowsSelection(15))
        val selection = SelectionModel()
        selection.isAllowed = false
        selection.begin(with = 1L)
        assertTrue(!selection.isActive && selection.selected.isEmpty())
    }
}
