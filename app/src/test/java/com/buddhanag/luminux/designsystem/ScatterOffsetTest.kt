package com.buddhanag.luminux.designsystem

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class ScatterOffsetTest {
    private val page = Size(400f, 800f)

    @Test fun theSamePlaceAlwaysFliesTheSameWay() {
        val frame = Rect(Offset(30f, 500f), Size(120f, 120f))
        assertEquals(scatterOffset(frame, page), scatterOffset(frame, page))
    }

    @Test fun everyItemOffTheCentreFliesAtLeast36() {
        for (x in 0..400 step 7) for (y in 0..800 step 11) {
            val frame = Rect(Offset(x - 20f, y - 20f), Size(40f, 40f))
            if (hypot(x - 200f, y - 400f) <= 1f) continue
            val o = scatterOffset(frame, page)
            assertTrue("($x, $y) flew ${o.getDistance()}", o.getDistance() >= 36f - 1e-3f)
        }
    }

    @Test fun anItemFliesAwayFromTheCentre() {
        val o = scatterOffset(Rect(Offset(300f, 700f), Size(80f, 80f)), page) // centre (340, 740), swerve within ±20°
        assertTrue(o.x > 0f && o.y > 0f)
    }

    @Test fun anInfiniteFrameFliesUpAndALittleLeft() {
        val o = scatterOffset(Rect(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY), page)
        assertEquals(36f, o.getDistance(), 1e-3f)
        assertTrue(o.x < 0f && o.y < 0f)
    }
}
