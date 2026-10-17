package com.buddhanag.luminux.designsystem

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TiltTest {
    private val size = Size(100f, 100f)

    @Test fun centrePressPushesInWithoutTipping() {
        val tilt = Tilt.pressed(Offset(50f, 50f), size)
        assertEquals(0.0, tilt.angle, 0.0)
        assertTrue(tilt.scale < 1)
    }

    @Test fun edgePressTipsAroundOtherAxis() {
        val right = Tilt.pressed(Offset(100f, 50f), size)
        assertTrue(right.angle > 0)
        assertEquals(0.0, right.axisX, 0.0)
        assertTrue(right.axisY > 0)

        val top = Tilt.pressed(Offset(50f, 0f), size)
        assertEquals(0.0, top.axisY, 0.0)
        assertTrue(top.axisX > 0)
    }

    @Test fun outsideTouchesAreClamped() {
        assertEquals(Tilt.pressed(Offset(100f, 50f), size), Tilt.pressed(Offset(400f, 50f), size))
    }

    @Test fun largeTilesTipLess() {
        val small = Tilt.pressed(Offset(100f, 50f), size)
        val large = Tilt.pressed(Offset(360f, 180f), Size(360f, 360f))
        assertTrue(large.angle < small.angle)
    }

    @Test fun unknownTouchStillPressesIn() {
        val tilt = Tilt.pressed(null, size)
        assertEquals(0.0, tilt.angle, 0.0)
        assertTrue(tilt.scale < 1)
    }
}
