package com.buddhanag.luminux.feature.grid

import com.buddhanag.luminux.data.MonthSection

/** One cell of the lazy grid: a full-width month header or a photo, by its index in the source's items. */
sealed interface GridEntry {
    data class Header(val section: MonthSection) : GridEntry
    data class Photo(val assetIndex: Int) : GridEntry
}

/** The grid's cells: each month's header followed by its photos, or just the photos without [sections]. */
fun gridEntries(count: Int, sections: List<MonthSection>?): List<GridEntry> =
    sections?.flatMap { s -> listOf(GridEntry.Header(s)) + s.range.filter { it < count }.map { GridEntry.Photo(it) } }
        ?: List(count) { GridEntry.Photo(it) }

fun headerIndex(entries: List<GridEntry>, sectionId: String): Int =
    entries.indexOfFirst { it is GridEntry.Header && it.section.id == sectionId }

/**
 * The feather line and column of every entry (AssetGrid.swift:342-350, 383): a header takes a line, and its photos
 * the lines after it.
 */
fun featherPositions(entries: List<GridEntry>, columns: Int): List<Pair<Int, Int>> {
    var nextRow = 0
    var sectionStart = 0
    var firstRow = 0
    return entries.map { entry ->
        when (entry) {
            is GridEntry.Header -> {
                val row = nextRow
                firstRow = row + 1
                sectionStart = entry.section.range.first
                nextRow = firstRow + (entry.section.count + columns - 1) / columns
                row to 0
            }
            is GridEntry.Photo -> {
                val offset = entry.assetIndex - sectionStart
                (firstRow + offset / columns) to offset % columns
            }
        }
    }
}

/** The column a photo sits in, counting from the start of its month when the grid has month headers. */
fun columnOf(index: Int, sections: List<MonthSection>?, columns: Int): Int {
    val start = sections?.firstOrNull { index in it.range }?.range?.first ?: 0
    return (index - start) % columns
}
