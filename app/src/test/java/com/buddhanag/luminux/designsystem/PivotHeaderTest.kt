package com.buddhanag.luminux.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class PivotHeaderTest {
    private val widths = listOf(100f, 80f)

    @Test fun offsetFollowsTheHeaderEdges() {
        assertEquals(0f, pivotHeaderOffset(0f, widths, 24f), 0f)
        assertEquals(124f, pivotHeaderOffset(1f, widths, 24f), 0.001f)
        assertEquals(62f, pivotHeaderOffset(0.5f, widths, 24f), 0.001f)
        // Overscroll is clamped to the last header.
        assertEquals(124f, pivotHeaderOffset(1.4f, widths, 24f), 0.001f)
        assertEquals(0f, pivotHeaderOffset(1f, emptyList(), 24f), 0f)
    }

    @Test fun inactiveHeadersFadeTo40Percent() {
        assertEquals(1f, pivotHeaderAlpha(2, 2f), 0f)
        assertEquals(0.4f, pivotHeaderAlpha(3, 2f), 0.0001f)
        assertEquals(0.4f, pivotHeaderAlpha(5, 2f), 0.0001f)
        assertEquals(0.7f, pivotHeaderAlpha(1, 0.5f), 0.0001f)
    }
}
