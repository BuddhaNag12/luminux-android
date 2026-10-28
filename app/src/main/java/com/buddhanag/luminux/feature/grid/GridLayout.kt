package com.buddhanag.luminux.feature.grid

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Where every photo and month title of a grid sits, worked out rather than measured (GridGestures.swift `GridLayout`):
 * every row is the same height, so nothing has to be laid out to know where it goes. The grid draws only what's on
 * screen from it, and a pinch lines one level up with the next from it exactly. All values are px.
 *
 * @param sections each month's photo indices; null for one run of photos with no month titles.
 */
class GridLayout(
    val columns: Int,
    width: Float,
    val gutter: Float,
    sections: List<IntRange>?,
    count: Int,
    val headerHeight: Float,
    bottomPadding: Float,
) {
    /** [position]: the month's index in the sections passed in, which a dropped empty month would otherwise shift. */
    class Section(val position: Int, val start: Int, val end: Int, val hasHeader: Boolean, val top: Float, val firstRow: Int, val rows: Int)
    /** A row of photos: its stagger row (unique across the grid), its photos `start until end` and its top. */
    data class RowSlot(val row: Int, val start: Int, val end: Int, val y: Float)
    data class Place(val row: Int, val column: Int)

    val side = GridZoom.tileSide(columns, width, gutter)
    val pitch: Float get() = side + gutter
    val sections: List<Section>
    val height: Float

    init {
        val list = mutableListOf<Section>()
        var y = 0f
        var row = 0
        for ((position, r) in (sections ?: listOf(0 until count)).withIndex()) {
            // A month the source no longer fills all of (it shrank before the sections caught up) stops at its end.
            val end = min(r.last + 1, count)
            if (end <= r.first) continue
            val hasHeader = sections != null
            val rows = (end - r.first + columns - 1) / columns
            list += Section(position, r.first, end, hasHeader, y, row, rows)
            y += (if (hasHeader) headerHeight + gutter else 0f) + rows * pitch
            row += (if (hasHeader) 1 else 0) + rows
        }
        this.sections = list
        height = max(y - gutter, 0f) + bottomPadding
    }

    private fun rowsTop(s: Section) = s.top + if (s.hasHeader) headerHeight + gutter else 0f

    /** The section holding [index], by binary search. */
    private fun section(index: Int): Section? {
        var lo = 0
        var hi = sections.lastIndex
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            when {
                index < sections[mid].start -> hi = mid - 1
                index >= sections[mid].end -> lo = mid + 1
                else -> return sections[mid]
            }
        }
        return null
    }

    /** The last section starting at or above [y]. */
    private fun sectionIndex(y: Float): Int? {
        if (sections.isEmpty()) return null
        var lo = 0
        var hi = sections.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (sections[mid].top <= y) lo = mid else hi = mid - 1
        }
        return lo
    }

    fun frame(index: Int): Rect? {
        val s = section(index) ?: return null
        val o = index - s.start
        return Rect(Offset((o % columns) * pitch, rowsTop(s) + (o / columns) * pitch), Size(side, side))
    }

    /** A photo's row and column within its month. */
    fun placeInSection(index: Int): Place? = section(index)?.let { val o = index - it.start; Place(o / columns, o % columns) }

    fun rowCount(index: Int): Int? = section(index)?.rows

    fun isSameSection(a: Int, b: Int): Boolean = section(a)?.start == section(b)?.start

    /** The cell at [row] and [column] of the month holding [index]; either may lie outside the month's photos. */
    fun frameInSection(index: Int, row: Int, column: Int): Rect? =
        section(index)?.let { Rect(Offset(column * pitch, rowsTop(it) + row * pitch), Size(side, side)) }

    /** The stagger row of [row] in the month holding [index], unique across the grid for rows inside a month. */
    fun gridRow(index: Int, row: Int): Int? = section(index)?.let { it.firstRow + (if (it.hasHeader) 1 else 0) + row }

    /** The stagger row and column of a photo. */
    fun cell(index: Int): Place? = section(index)?.let {
        val o = index - it.start
        Place(it.firstRow + (if (it.hasHeader) 1 else 0) + o / columns, o % columns)
    }

    /** The photo whose square contains [p]; null in a gutter, over a title or past a row's last photo. */
    fun indexAt(p: Offset): Int? {
        val s = sections[sectionIndex(p.y) ?: return null]
        val y = p.y - rowsTop(s)
        if (y < 0 || p.x < 0) return null
        val row = (y / pitch).toInt()
        val column = (p.x / pitch).toInt()
        if (column >= columns || y - row * pitch >= side || p.x - column * pitch >= side) return null
        val i = s.start + row * columns + column
        return if (i < s.end) i else null
    }

    /** The photo nearest [p]: the fingers often sit on a gutter or a month title. */
    fun indexNearest(p: Offset): Int? {
        val s = sections[sectionIndex(p.y) ?: return null]
        val row = ((p.y - rowsTop(s)) / pitch).toInt().coerceIn(0, s.rows - 1)
        val column = (p.x / pitch).toInt().coerceIn(0, columns - 1)
        return min(s.start + row * columns + column, s.end - 1)
    }

    /** Every photo whose square meets [r], in order. */
    fun indices(r: Rect): List<Int> = buildList {
        val first = sectionIndex(r.top) ?: return@buildList
        for (s in sections.subList(first, sections.size)) {
            if (s.top > r.bottom) break
            val top = rowsTop(s)
            val a = max(floor((r.top - top) / pitch).toInt(), 0)
            val b = min(floor((r.bottom - top) / pitch).toInt(), s.rows - 1)
            if (a > b) continue
            addAll(s.start + a * columns until min(s.start + (b + 1) * columns, s.end))
        }
    }

    /** Every row of photos that meets [r]. */
    fun rows(r: Rect): List<RowSlot> = buildList {
        val first = sectionIndex(r.top) ?: return@buildList
        for (s in sections.subList(first, sections.size)) {
            if (s.top > r.bottom) break
            val top = rowsTop(s)
            val a = max(floor((r.top - top) / pitch).toInt(), 0)
            val b = min(floor((r.bottom - top) / pitch).toInt(), s.rows - 1)
            if (a > b) continue
            for (row in a..b) {
                val lo = s.start + row * columns
                add(RowSlot(s.firstRow + (if (s.hasHeader) 1 else 0) + row, lo, min(lo + columns, s.end), top + row * pitch))
            }
        }
    }

    /** A month title meeting a rect: its section position, frame and stagger row. */
    data class HeaderSlot(val position: Int, val frame: Rect, val row: Int)

    /** The month titles that meet [r]. */
    fun headers(r: Rect): List<HeaderSlot> = sections.mapNotNull { s ->
        if (!s.hasHeader) return@mapNotNull null
        val f = Rect(Offset(0f, s.top), Size(columns * pitch - gutter, headerHeight))
        if (f.overlaps(r)) HeaderSlot(s.position, f, s.firstRow) else null
    }

    fun headerTop(position: Int): Float? = sections.firstOrNull { it.position == position }?.top

    /** The first stagger row at or below [y] (the feather's origin row). */
    fun rowAt(y: Float): Int {
        val s = sections[sectionIndex(y) ?: return 0]
        val inRows = (max(y - rowsTop(s), 0f) / pitch).toInt()
        return s.firstRow + (if (s.hasHeader) 1 else 0) + min(inRows, s.rows)
    }
}
