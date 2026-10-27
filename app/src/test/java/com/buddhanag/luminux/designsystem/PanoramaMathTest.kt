package com.buddhanag.luminux.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class PanoramaMathTest {
    private val margin = 16f
    private val titleWidth = 300f

    @Test fun titleStaysOnStartPanelAndSlidesOffByTheLast() {
        val math = PanoramaMath(screenWidth = 400f, peek = 44f, panels = 3, start = 0)
        assertEquals(0f, math.titleX(0f, titleWidth, margin), 0.001f)
        // The pager stops the last panel at the right edge.
        val lastScroll = 3 * math.panelWidth - 400f
        assertEquals(-(margin + 0.56f * titleWidth), math.titleX(lastScroll, titleWidth, margin), 0.001f)
    }

    @Test fun leadingPanelBringsTheLeadingTitleIn() {
        val math = PanoramaMath(screenWidth = 400f, peek = 44f, panels = 3, start = 1)
        assertEquals(1f, math.leadingProgress(0f), 0f)
        assertEquals(0f, math.leadingProgress(math.panelWidth), 0f)
        assertEquals(400f, math.titleX(0f, titleWidth, margin), 0.001f)
    }

    @Test fun backgroundStartsShiftedByTheLeadingPanels() {
        val math = PanoramaMath(screenWidth = 400f, peek = 44f, panels = 3, start = 1)
        assertEquals(-math.leadingWidth * 0.3f, math.backgroundX(math.leadingWidth), 0.001f)
        assertEquals(400f + 356f * 4 * 0.3f + 356f * 0.3f, math.backgroundWidth, 0.001f)
    }

    @Test fun aLongPanoramaWidensTheBackgroundToItsLastPanel() {
        val math = PanoramaMath(screenWidth = 400f, peek = 44f, panels = 8, start = 0)
        assertEquals(400f + math.maxOffset * 0.3f, math.backgroundWidth, 0.001f) // maxOffset 2448 > 4 panels (1424)
    }

    @Test fun reduceMotionStopsParallaxButNotTheLeadingTitle() {
        val math = PanoramaMath(screenWidth = 400f, peek = 44f, panels = 3, start = 1, reduceMotion = true)
        assertEquals(-math.leadingWidth * 0.3f, math.backgroundX(0f), 0.001f)
        assertEquals(400f, math.titleX(0f, titleWidth, margin), 0.001f)
    }
}
