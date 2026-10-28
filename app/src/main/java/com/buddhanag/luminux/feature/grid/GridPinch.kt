package com.buddhanag.luminux.feature.grid

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.Job
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** A cell of the next level, by stagger row and column. */
data class GridCell(val row: Int, val column: Int)

/** One photo the pinch layer draws (AssetGrid.swift `PinchTile`). */
data class PinchTile(val index: Int, val kind: Kind, val unsorted: Rect, val start: Rect? = null) {
    enum class Kind {
        /** On screen before the pinch. */
        Shown,
        /** A cell the old photos leave empty: its sorted photo, coming in from beyond the edges. */
        Filler,
        /** Like Filler, in a spot already on screen when the pinch began. */
        Appearing,
        /** The sorted photo under an old one, for the middle block's dissolve. */
        SortedMiddle,
        /** An old photo in a cell with no sorted photo: stays until it's off screen. */
        Edge,
        /** An old photo in another month than the anchor: fades out. */
        Moving,
        /** A sorted photo in another month than the anchor: fades in. */
        Arriving,
    }

    /** A photo can show twice for a moment (unsorted in the block, sorted around it). */
    val id: Pair<Kind, Int> get() = (if (kind == Kind.Edge || kind == Kind.Moving) Kind.Shown else kind) to index

    fun center(progress: Float): Offset {
        val s = start ?: return unsorted.center
        val t = progress.coerceIn(0f, 1f)
        return s.center + (unsorted.center - s.center) * t
    }

    fun opacity(progress: Float, sortedness: Float): Float = when (kind) {
        Kind.Shown -> 1 - sortedness
        // Opaque early, so the old photo dissolves into it: two half-faded photos dimmed the block midway.
        Kind.SortedMiddle -> min(sortedness * 4, 1f)
        Kind.Appearing -> min(progress * 3, 1f)
        Kind.Filler, Kind.Edge -> 1f
        Kind.Moving -> 1 - progress
        Kind.Arriving -> progress
    }
}

/**
 * From the grid as it was (progress 0: the next level's cells magnified to the grid's size, the anchor where it was)
 * to the next level (1). The layer is scaled about its top-left corner.
 */
data class PinchCamera(val from: Offset = Offset.Zero, val to: Offset = Offset.Zero, val startScale: Float = 1f, val startSide: Float = 0f) {
    /** By ratio, so each bit of pinch zooms by the same factor and gaps don't close up midway. */
    fun scale(p: Float) = startScale * (1 / startScale).pow(p)

    /** Draws `to` at the point between from and to. */
    fun offset(p: Float): Offset {
        val s = scale(p)
        return from + (to - from) * p - to * s
    }

    /** Keeps the on-screen gaps at the gutter instead of growing with the camera. */
    fun drawnSide(side: Float, p: Float) = startSide * (side / max(startSide, 1f)).pow(p) / scale(p)
}

/** How far the middle block has sorted: smoothstep over [range]. */
fun sortedness(progress: Float, range: ClosedFloatingPointRange<Float>): Float {
    val t = ((progress - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

/** Zooming in sorts early, so it's done before the photos around leave the screen. */
fun sortRange(zoomsIn: Boolean) = if (zoomsIn) 0.1f..0.5f else 0.3f..0.85f

/** A cubic Bézier leaves at the slope of its first control point: here, the fingers' speed. */
fun settleControlPoints(initialSlope: Float) = floatArrayOf(0.3f, min(initialSlope * 0.3f, 1f), 0.4f, 1f)

fun settleEasing(initialSlope: Float) = settleControlPoints(initialSlope).let { (a, b, c, d) -> CubicBezierEasing(a, b, c, d) }

/** An ease-out starting at [speed] covers [distance] in twice the time a steady one would. */
fun settleDuration(distance: Float, speed: Float): Float = if (speed > 0) (2 * distance / speed).coerceIn(0.15f, 0.45f) else 0.4f

/** One per grid (AssetGrid.swift `GridPinch`). Observable fields drive drawing; the rest are plain. */
class PinchState {
    /** What's drawn; read only in draw and graphicsLayer lambdas, so a pinch frame doesn't recompose. */
    var progress by mutableFloatStateOf(0f)
    var previewColumns by mutableStateOf<Int?>(null)
    var tiles by mutableStateOf<List<PinchTile>>(emptyList())
    /** The grid underneath hides while the layer stands in for it. */
    var hidesTiles by mutableStateOf(false)
    /** From the first finger until the grid settles: scrolling, taps and swipe-select wait. */
    var isActive by mutableStateOf(false)
    /** The pinch carried on past the grid's own level. */
    var isRebased by mutableStateOf(false)
    /** Counts levels the grid settled at, for the haptic. */
    var settledLevels by mutableIntStateOf(0)

    /** Where progress is heading. iOS's withAnimation sets the model value at once, and its logic reads that. */
    var targetProgress = 0f
    /** The running animation's speed, so a retargeted catch-up keeps its momentum. */
    var progressVelocity = 0f
    var sortRange = 0.3f..0.85f
    /** Uptime ms until which the photos ease after the fingers instead of snapping to them. */
    var catchesUpUntil = 0L
    var isTracking = false
    var baseColumns = GridZoom.DEFAULT_COLUMNS
    var baseOffset = 0f
    var anchorsOnSortedColumn = false
    var lastScale = 1f
    var lastCentroid = Offset.Zero
    var camera = PinchCamera()
    var limits = 1f..1f
    var startScale = 1f
    var gestureScale = 1f
    var anchorIndex: Int? = null
    /** Where in the anchor photo the fingers are, 0..1 each way from its top-left. */
    var anchorUnit = Offset(0.5f, 0.5f)
    /** The anchor's frame on screen when the pinch began. */
    var anchorFrame: Rect? = null
    var isRestoring = false
    var anchorPoint = Offset.Zero
    var previewLayout: GridLayout? = null
    var previewOffset = 0f
    /** Set between switching the grid and it lining up under the layer. */
    var swap: Long? = null
    var progressJob: Job? = null
}
