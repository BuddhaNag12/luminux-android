package com.buddhanag.luminux.feature.grid

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** GridZoomTests.swift: the worked-out layout, at density 1 (gutter 4, title 60, bottom padding 24). */
class GridLayoutTest {
    // 4 columns of 96 with a 4 gap make a 396 wide grid: pitch 100.
    private val layout = GridLayout(4, 396f, 4f, listOf(0..5, 6..6), 7, headerHeight = 60f, bottomPadding = 24f)

    /** A month title and the gap under it. */
    private val title = 64f

    private fun rect(x: Float, y: Float, w: Float, h: Float) = Rect(Offset(x, y), Size(w, h))

    @Test fun photosSitUnderTheirMonthTitleRowByRow() {
        assertEquals(96f, layout.side)
        // First month: its title, then two rows.
        assertEquals(rect(0f, title, 96f, 96f), layout.frame(0))
        assertEquals(rect(100f, title + 100, 96f, 96f), layout.frame(5))
        // Second month starts after two rows, its title, then its photo.
        assertEquals(title + 200, layout.headerTop(1))
        assertEquals(rect(0f, 2 * title + 200, 96f, 96f), layout.frame(6))
        assertNull(layout.frame(7))
        assertEquals(2 * title + 200 + 96 + 24, layout.height)
    }

    @Test fun aPointFindsItsPhotoAndTheNearestOneOffAPhoto() {
        assertEquals(1, layout.indexAt(Offset(150f, title + 12)))
        // In a gap, over a title, past the last photo of a row.
        assertNull(layout.indexAt(Offset(98f, title + 12)))
        assertNull(layout.indexAt(Offset(10f, 20f)))
        assertNull(layout.indexAt(Offset(350f, title + 112)))
        assertEquals(0, layout.indexNearest(Offset(98f, title + 12)))
        assertEquals(0, layout.indexNearest(Offset(10f, 20f)))
        assertEquals(5, layout.indexNearest(Offset(350f, title + 112)))
    }

    @Test fun onlyWhatMeetsARectIsDrawn() {
        assertEquals(listOf(0, 1, 2, 3), layout.indices(rect(0f, 0f, 396f, title + 52)))
        assertEquals(listOf(4, 5, 6), layout.indices(rect(0f, title + 150, 396f, 200f)))
        assertEquals(listOf(1), layout.headers(rect(0f, title + 150, 396f, 200f)).map { it.position })
    }

    @Test fun staggerRowsCountMonthTitles() {
        assertEquals(GridLayout.Place(1, 0), layout.cell(0))
        assertEquals(GridLayout.Place(2, 1), layout.cell(5))
        // Title row 3, photos row 4.
        assertEquals(GridLayout.Place(4, 0), layout.cell(6))
        assertEquals(4, layout.rowAt(title + 204))
    }

    @Test fun withoutMonthsThePhotosStartAtTheTop() {
        val plain = GridLayout(3, 308f, 4f, null, 4, headerHeight = 60f, bottomPadding = 24f)
        assertEquals(rect(0f, 104f, 100f, 100f), plain.frame(3))
        assertTrue(plain.headers(rect(0f, 0f, 308f, 1000f)).isEmpty())
    }

    @Test fun aMonthPastTheLastPhotoIsDroppedWithoutShiftingTheOthers() {
        val shrunk = GridLayout(4, 396f, 4f, listOf(0..5, 6..6, 7..9), 7, headerHeight = 60f, bottomPadding = 24f)
        assertEquals(title + 200, shrunk.headerTop(1))
        assertNull(shrunk.headerTop(2))
    }
}
