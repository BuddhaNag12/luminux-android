package com.buddhanag.luminux.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class StraightenTest {
    @Test fun straighteningZoomsJustEnoughToCover() {
        assertEquals(1.0, EditRecipe.straightenScale(0.0, 4.0, 3.0), 0.0)
        val scale = EditRecipe.straightenScale(10.0, 400.0, 300.0)
        // The frame's corners, turned back by the angle, land inside the zoomed photo.
        val a = Math.toRadians(10.0)
        assertTrue(200 * cos(a) + 150 * sin(a) <= 200 * scale + 0.001)
        assertTrue(200 * sin(a) + 150 * cos(a) <= 150 * scale + 0.001)
        assertTrue(scale < 1.4)
    }
}

/** EditingTests.swift's render check, on the pure pixel maths `PhotoEditing.render` runs. */
class StraightenRenderTest {
    private val red = 0xFFFF0000.toInt()

    @Test fun aStraightenedPhotoKeepsItsSizeAndHasNoEmptyCorner() {
        for (degrees in listOf(15.0, -45.0, 45.0, 0.5)) {
            val out = Straighten.apply(IntArray(120 * 80) { red }, 120, 80, degrees)
            assertEquals(120 * 80, out.size)
            for (corner in listOf(0, 119, 79 * 120, 80 * 120 - 1)) {
                assertEquals("alpha at $corner for $degrees°", 0xFF, out[corner] ushr 24)
                assertTrue(out[corner] shr 16 and 0xFF > 200)
            }
        }
    }

    @Test fun autoLevelsATiltedHorizon() {
        // Sky over sea, then tilted 8° clockwise by straightening; levelling should turn it back.
        val (w, h) = 600 to 400
        val scene = IntArray(w * h) { if (it / w < 200) 0xFF80B3FF.toInt() else 0xFF0D3359.toInt() }
        val tilted = Straighten.apply(scene, w, h, 8.0)
        val angle = Levelling.angle(tilted, w, h)
        assertNotNull(angle)
        assertTrue("levelling angle $angle", abs(angle!! + 8) < 2)
        assertEquals(0.0, Levelling.angle(scene, w, h)!!, 0.0)
        for (tilt in listOf(-20.0, -3.0, 2.0, 30.0)) {
            val found = Levelling.angle(Straighten.apply(scene, w, h, tilt), w, h)
            assertEquals("tilt $tilt", -tilt, found!!, 0.5)
        }
        // Long, faint wave stripes at another angle lose to the high-contrast horizon.
        val sea = IntArray(w * h) {
            val x = it % w; val y = it / w
            val wave = (12 * kotlin.math.sin(x / 23.0 + y / 7.0)).toInt()
            if (y < 200 + (x - 300) * kotlin.math.tan(Math.toRadians(-6.0))) 0xFF80B3FF.toInt()
            else (0xFF shl 24) or ((20 + wave) shl 16) or ((70 + wave) shl 8) or (110 + wave)
        }
        assertEquals(6.0, Levelling.angle(sea, w, h)!!, 0.5)
        // Nothing to find in a flat frame.
        assertEquals(null, Levelling.angle(IntArray(w * h) { red }, w, h))
    }
}
