package com.buddhanag.luminux.feature.grid

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max

/** The photo grid's zoom levels, like Photos: pinch out for bigger photos, in for more of them (GridGestures.swift). */
object GridZoom {
    val levels = listOf(2, 3, 4, 6, 8)
    const val DEFAULT_COLUMNS = 4

    /** How far a pinch has to go before the grid changes level. */
    const val THRESHOLD = 0.15f

    /** The closest level; a tie picks the smaller one (5 → 4, 7 → 6), like Swift's `min(by:)`. */
    fun nearestLevel(columns: Int): Int = levels.minBy { abs(it - columns) }

    /** The level a pinch lands on: one step per pinch, two for a big one. */
    fun columnsAfter(current: Int, magnification: Float): Int {
        val index = levels.indexOf(nearestLevel(current))
        val steps = when {
            magnification > 1 + THRESHOLD -> if (magnification > 2) -2 else -1
            magnification < 1 - THRESHOLD -> if (magnification < 0.5f) 2 else 1
            else -> 0
        }
        return levels[(index + steps).coerceIn(0, levels.lastIndex)]
    }

    /** A quick flick counts as a step even when the fingers barely moved. [velocity] is scale per second. */
    fun columnsAfter(current: Int, magnification: Float, velocity: Float): Int =
        // Where the pinch would have ended about a tenth of a second later.
        columnsAfter(current, max(magnification + velocity * 0.1f, 0.1f))

    /** How far a pinch can scale the grid before it starts to resist: as far as the biggest and smallest levels. */
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
