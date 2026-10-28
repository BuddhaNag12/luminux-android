package com.buddhanag.luminux.feature.grid

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** GridZoomTests.swift: the pinch layer's dissolve, sort and settle. */
class PinchTest {
    @Test fun middleBlockDissolvesWithoutDimming() {
        val old = PinchTile(0, PinchTile.Kind.Shown, Rect.Zero)
        val new = PinchTile(0, PinchTile.Kind.SortedMiddle, Rect.Zero)
        for (step in 0..20) {
            val sortedness = step / 20f
            val top = old.opacity(progress = 1f, sortedness = sortedness)
            val under = new.opacity(progress = 1f, sortedness = sortedness)
            // How much of the cell is covered, the old photo drawn over the new one.
            assertTrue(top + under * (1 - top) >= 0.93f)
        }
    }

    @Test fun sortFollowsThePinch() {
        val range = 0.3f..0.85f
        assertEquals(0f, sortedness(0.2f, range))
        assertEquals(1f, sortedness(0.9f, range))
        assertEquals(0.5f, sortedness(0.575f, range), 0.001f)
        var last = 0f
        for (step in 0..20) {
            val value = sortedness(step / 20f, range)
            assertTrue(value >= last)
            last = value
        }
    }

    @Test fun releaseFinishesAtTheFingersSpeed() {
        // A flick finishes fast, a slow release eases in, still fingers take the default, and it's never instant.
        assertEquals(0.15f, settleDuration(0.5f, 20f))
        assertEquals(0.45f, settleDuration(0.5f, 1f))
        assertEquals(0.4f, settleDuration(0.5f, 0f))
        assertEquals(0.25f, settleDuration(0.5f, 4f), 0.0001f)
    }

    @Test fun releaseCurveStartsAtTheFingersSpeed() {
        // The curve leaves at y/x of its first control point.
        val (x, y) = settleControlPoints(2f)
        assertEquals(2f, y / x, 0.001f)
        // Still fingers start from rest; a flick can't leave faster than the curve allows.
        assertEquals(0f, settleControlPoints(0f)[1])
        assertEquals(1f, settleControlPoints(50f)[1])
    }

    @Test fun pinchTilesMoveCentreToCentre() {
        val tile = PinchTile(0, PinchTile.Kind.Shown, Rect(Offset(0f, 200f), Size(100f, 150f)), Rect(Offset.Zero, Size(100f, 100f)))
        assertEquals(Offset(50f, 50f), tile.center(0f))
        assertEquals(Offset(50f, 275f), tile.center(1f))
    }
}
