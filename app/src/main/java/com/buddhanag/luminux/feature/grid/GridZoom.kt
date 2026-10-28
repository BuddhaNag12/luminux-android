package com.buddhanag.luminux.feature.grid

import kotlin.math.abs
import kotlin.math.ln

/** The photo grid's zoom levels, like Photos: pinch out for bigger photos, in for more of them (GridGestures.swift). */
object GridZoom {
    /** Odd counts, so each level is the one before with the same number of columns added on either side. */
    val levels = listOf(3, 5, 7, 15)
    /** Where a tap on the 15-column overview zooms in to. */
    const val OVERVIEW_ZOOM_COLUMNS = 5
    const val DEFAULT_COLUMNS = 5

    /** The smallest photos are for finding your place, not for acting on: no selecting there. */
    fun allowsSelection(columns: Int) = columns < levels.last()

    /** The closest level; a tie picks the smaller one (4 → 3, 6 → 5), like Swift's `min(by:)`. */
    fun nearestLevel(columns: Int): Int = levels.minBy { abs(it - columns) }

    /**
     * The level a pinch is heading to, given how many columns the grid looks like at the pinch's scale: the next one
     * out when more than it has, the next one in when fewer; null past the ends.
     */
    fun levelToward(columns: Float, from: Int): Int? = when {
        columns > from -> levels.firstOrNull { it > from }
        columns < from -> levels.lastOrNull { it < from }
        else -> null
    }

    /** How far (0..1) a grid that looks like [columns] columns has gone from [from] to [to], on a log scale. */
    fun progress(columns: Float, from: Int, to: Int): Float =
        (ln(columns / from) / ln(to.toFloat() / from)).coerceIn(0f, 1f)

    /** How far a pinch can scale before it resists: one pinch can carry on through every level, so only past the ends. */
    fun scaleLimits(columns: Int): ClosedFloatingPointRange<Float> {
        val current = nearestLevel(columns).toFloat()
        return current / levels.last()..current / levels.first()
    }

    /** Follows the fingers inside the limits and slows down past them, so the grid resists instead of stopping dead. */
    fun rubberBand(scale: Float, limits: ClosedFloatingPointRange<Float>): Float {
        val damping = 0.2f
        return when {
            scale > limits.endInclusive -> limits.endInclusive * (1 + ln(scale / limits.endInclusive) * damping)
            scale < limits.start -> limits.start / (1 + ln(limits.start / scale) * damping)
            else -> scale
        }
    }

    /** The side of a tile at [columns] across [width], with the grid's gutters. */
    fun tileSide(columns: Int, width: Float, gutter: Float): Float = (width - gutter * (columns - 1)) / columns
}
