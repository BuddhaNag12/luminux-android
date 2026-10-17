package com.buddhanag.luminux.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CropMathTest {
    private val w = 4000
    private val h = 3000

    @Test fun squareOnLandscapeTrimsTheSides() {
        val crop = CropMath.centered(1.0, w, h)
        assertEquals(1.0, crop.h, 0.0)
        assertEquals(0.75, crop.w, 1e-4)
        assertEquals(0.5, crop.midX, 1e-4)
    }

    @Test fun wideOnLandscapeTrimsTopAndBottom() {
        val crop = CropMath.centered(16.0 / 9, w, h)
        assertEquals(1.0, crop.w, 0.0)
        assertTrue(crop.h < 1)
        assertEquals(0.5, crop.midY, 1e-4)
    }

    @Test fun freeDragMovesOnlyTheDraggedCorner() {
        val crop = CropMath.drag(NormRect.UNIT, Corner.TOP_LEADING, 0.2, 0.3, null, w, h)
        assertEquals(0.2, crop.minX, 1e-4)
        assertEquals(0.3, crop.minY, 1e-4)
        assertEquals(1.0, crop.maxX, 0.0)
        assertEquals(1.0, crop.maxY, 0.0)
    }

    @Test fun dragKeepsAMinimumSize() {
        val crop = CropMath.drag(NormRect.UNIT, Corner.BOTTOM_TRAILING, -1.0, -1.0, null, w, h)
        assertTrue(crop.w >= CropMath.MINIMUM_SIDE - 1e-4)
        assertTrue(crop.h >= CropMath.MINIMUM_SIDE - 1e-4)
    }

    @Test fun lockedDragKeepsThePixelRatio() {
        val start = CropMath.centered(1.0, w, h)
        val crop = CropMath.drag(start, Corner.BOTTOM_TRAILING, 0.7, 0.9, 1.0, w, h)
        val pixelRatio = (crop.w * w) / (crop.h * h)
        assertTrue(abs(pixelRatio - 1) < 0.001)
    }

    @Test fun moveStaysInsideTheImage() {
        val crop = NormRect(0.5, 0.5, 0.4, 0.4)
        val moved = CropMath.move(crop, 0.5, -0.9)
        assertTrue(moved.maxX <= 1)
        assertTrue(moved.minY >= 0)
        assertEquals(crop.w, moved.w, 0.0)
        assertEquals(crop.h, moved.h, 0.0)
    }
}
