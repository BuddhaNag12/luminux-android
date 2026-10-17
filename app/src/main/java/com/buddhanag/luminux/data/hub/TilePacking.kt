package com.buddhanag.luminux.data.hub

import androidx.compose.ui.geometry.Rect
import kotlin.math.hypot
import kotlin.math.max

/** Where a tile sits on the four-cell grid, in cells. */
data class TilePlacement(val column: Int, val row: Int, val width: Int, val height: Int)

/**
 * Packs tiles the way the Windows Phone start screen did: in order, each in the first place it fits, with medium and
 * wide tiles on whole 2×2 blocks so small tiles fill in the corners around them. Port of HubTiles.swift `TilePacking`.
 */
object TilePacking {
    const val COLUMNS = 4

    fun place(sizes: List<HubTileSize>): List<TilePlacement> {
        val taken = HashSet<Int>()
        fun fit(size: HubTileSize): TilePlacement {
            val step = if (size == HubTileSize.Small) 1 else 2
            var row = 0
            while (true) {
                for (column in 0..COLUMNS - size.width step step) {
                    val cells = (row until row + size.height).flatMap { r -> (column until column + size.width).map { r * COLUMNS + it } }
                    if (cells.none { it in taken }) {
                        taken += cells
                        return TilePlacement(column, row, size.width, size.height)
                    }
                }
                row += step
            }
        }
        return sizes.map(::fit)
    }

    fun rows(placements: List<TilePlacement>): Int = placements.maxOfOrNull { it.row + it.height } ?: 0

    fun cellSide(width: Float, spacing: Float): Float = max((width - spacing * (COLUMNS - 1)) / COLUMNS, 0f)

    /** `rows × (side + spacing) - spacing`, 0 without tiles. */
    fun height(placements: List<TilePlacement>, width: Float, spacing: Float): Float {
        val rows = rows(placements)
        return if (rows == 0) 0f else rows * (cellSide(width, spacing) + spacing) - spacing
    }

    fun frame(placement: TilePlacement, width: Float, spacing: Float): Rect {
        val step = cellSide(width, spacing) + spacing
        val x = placement.column * step
        val y = placement.row * step
        return Rect(x, y, x + placement.width * step - spacing, y + placement.height * step - spacing)
    }

    /**
     * Where a dragged tile should go: the position whose packed slot sits closest to [held], the floating tile's rect.
     * Every candidate is judged after the reflow it would cause, and the tile only leaves its current slot for one a
     * quarter cell closer, so it can't flip back and forth while the other tiles move underneath it.
     */
    fun dropIndex(held: Rect, sizes: List<HubTileSize>, moving: Int, width: Float, spacing: Float): Int {
        if (moving !in sizes.indices) return moving
        fun distance(index: Int): Float {
            val order = sizes.toMutableList()
            order.add(index, order.removeAt(moving))
            val slot = frame(place(order)[index], width, spacing)
            return hypot(slot.center.x - held.center.x, slot.center.y - held.center.y)
        }
        val current = distance(moving)
        val best = sizes.indices.minBy { distance(it) }
        return if (distance(best) < current - cellSide(width, spacing) / 4) best else moving
    }
}
