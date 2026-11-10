package com.buddhanag.luminux.designsystem

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoldTest {
    @Test fun verticalHingePublishesFoldXRegardlessOfPosture() {
        val book = FoldGeometry.compute(
            windowWidthPx = 2400,
            windowHeightPx = 1800,
            density = 3f,
            hinge = HingeFeature(HingeOrientation.VERTICAL, HingePosture.HALF_OPENED, centerPx = 1200f),
        )
        assertEquals(400.dp, book.foldX)
        assertNull(book.foldY)
        assertNull(book.splitY)
        assertTrue(book.isHalfOpen)
        assertTrue(book.expandedWidth)

        val flat = FoldGeometry.compute(
            windowWidthPx = 2400,
            windowHeightPx = 1800,
            density = 3f,
            hinge = HingeFeature(HingeOrientation.VERTICAL, HingePosture.FLAT, centerPx = 1200f),
        )
        assertEquals(400.dp, flat.foldX)
        assertNull(flat.splitY)
        assertFalse(flat.isHalfOpen)
    }

    @Test fun horizontalHingeSetsSplitYOnlyInHalfOpenedTabletop() {
        val tabletop = FoldGeometry.compute(
            windowWidthPx = 1800,
            windowHeightPx = 2400,
            density = 3f,
            hinge = HingeFeature(HingeOrientation.HORIZONTAL, HingePosture.HALF_OPENED, centerPx = 1200f),
        )
        assertNull(tabletop.foldX)
        assertEquals(400.dp, tabletop.foldY)
        assertEquals(400.dp, tabletop.splitY)
        assertTrue(tabletop.isHalfOpen)
        assertTrue(tabletop.expandedWidth)

        val flat = FoldGeometry.compute(
            windowWidthPx = 1800,
            windowHeightPx = 2400,
            density = 3f,
            hinge = HingeFeature(HingeOrientation.HORIZONTAL, HingePosture.FLAT, centerPx = 1200f),
        )
        assertEquals(400.dp, flat.foldY)
        assertNull(flat.splitY)
        assertFalse(flat.isHalfOpen)
    }

    @Test fun expandedWidthThresholdIs600DpWhileCompactFlipStillSplitsInTabletop() {
        val below = FoldGeometry.compute(
            windowWidthPx = 1797, // 599 dp at 3x
            windowHeightPx = 2400,
            density = 3f,
            hinge = HingeFeature(HingeOrientation.HORIZONTAL, HingePosture.HALF_OPENED, centerPx = 1200f),
        )
        assertFalse(below.expandedWidth)
        assertEquals(400.dp, below.splitY)

        val atThreshold = FoldGeometry.compute(
            windowWidthPx = 1800, // 600 dp at 3x
            windowHeightPx = 2400,
            density = 3f,
            hinge = null,
        )
        assertTrue(atThreshold.expandedWidth)
        assertNull(atThreshold.splitY)
    }

    @Test fun turnstileHingeUsesWindowEdgeLeftOfFoldAndFoldXRightOfFold() {
        // Without a fold, tiles hinge at window x = 0.
        assertEquals(-0.5f, FoldGeometry.turnstileOriginX(leftPx = 100f, widthPx = 200f, foldXPx = null), 0.001f)
        // Left of a vertical fold at 600 px still hinges at window x = 0.
        assertEquals(-0.5f, FoldGeometry.turnstileOriginX(leftPx = 100f, widthPx = 200f, foldXPx = 600f), 0.001f)
        // Starting right on the fold hinges at local x = 0.
        assertEquals(0f, FoldGeometry.turnstileOriginX(leftPx = 600f, widthPx = 200f, foldXPx = 600f), 0.001f)
        // Starting 100 px right of the fold hinges at -100 / width = -0.5.
        assertEquals(-0.5f, FoldGeometry.turnstileOriginX(leftPx = 700f, widthPx = 200f, foldXPx = 600f), 0.001f)
    }
}
