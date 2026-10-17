package com.buddhanag.luminux.feature.grid

import com.buddhanag.luminux.data.MonthSection
import org.junit.Assert.assertEquals
import org.junit.Test

class GridEntriesTest {
    private val sections = listOf(MonthSection(2026, 10, 0 until 3), MonthSection(2026, 9, 3 until 5))

    @Test fun entriesAndHeaderIndex() {
        val entries = gridEntries(5, sections)
        assertEquals(0, headerIndex(entries, sections[0].id))
        assertEquals(4, headerIndex(entries, sections[1].id))
        assertEquals((0..4).toList(), entries.filterIsInstance<GridEntry.Photo>().map { it.assetIndex })
        assertEquals(List(5) { GridEntry.Photo(it) }, gridEntries(5, null))
    }

    @Test fun featherLinesFollowTheHeaders() {
        // Two columns: header 0, photos on lines 1-2, header 3, photos on line 4.
        val lines = featherPositions(gridEntries(5, sections), columns = 2)
        assertEquals(listOf(0 to 0, 1 to 0, 1 to 1, 2 to 0, 3 to 0, 4 to 0, 4 to 1), lines)
    }

    @Test fun columnsCountFromTheStartOfTheMonth() {
        assertEquals(0, columnOf(3, sections, columns = 2))
        assertEquals(1, columnOf(3, null, columns = 2))
    }
}
