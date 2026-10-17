package com.buddhanag.luminux.data

import kotlin.math.abs

/** The grid's zoom levels. shortcut: minimal copy so settings can snap gridColumns; spec 06 owns it and moves it. */
object GridZoom {
    val levels = listOf(2, 3, 4, 6, 8)
    const val DEFAULT_COLUMNS = 4

    /** The closest level; a tie picks the smaller one (5 → 4, 7 → 6). */
    fun nearestLevel(columns: Int): Int = levels.minBy { abs(it - columns) }
}
