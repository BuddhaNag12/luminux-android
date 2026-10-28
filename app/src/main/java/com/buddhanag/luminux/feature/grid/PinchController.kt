package com.buddhanag.luminux.feature.grid

import android.os.SystemClock
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.buddhanag.luminux.data.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * The grid's own scroll position (iOS `GridScrollMetrics`). [position] is how far it has scrolled from the top, so a
 * header that measures late moves the photos down instead of scrolling them; [offsetY] is in the layout's coordinates,
 * from `−topInset` (at rest, under the header) to [maxOffset].
 */
class GridScroll {
    var position by mutableFloatStateOf(0f)
        private set
    /** `−topInset`; set by the grid each composition. */
    var minOffset = 0f
    var maxOffset = 0f
    val offsetY: Float get() = position + minOffset

    /** Only the top is clamped: a level switch scrolls to an offset worked out for the next level's height. */
    fun scrollTo(y: Float) {
        position = max(y - minOffset, 0f)
    }

    /** Returns how far it moved. */
    fun scrollBy(d: Float): Float {
        val before = position
        position = (position + d).coerceIn(0f, max(maxOffset - minOffset, 0f))
        return position - before
    }
}

enum class PinchPhase { Began, Changed, Ended }

/**
 * The pinch state machine (AssetGrid.swift `// MARK: Pinch to zoom`, spec 13). The grid writes the inputs each
 * composition; gesture handlers outlive compositions, so they go through this rather than captured values.
 */
class PinchController(private val scope: CoroutineScope) {
    val pinch = PinchState()
    val scroll = GridScroll()

    // Inputs, from the grid's composition.
    var unlocksPro = false
    var savedColumns = GridZoom.DEFAULT_COLUMNS
    var items: List<MediaItem> = emptyList()
    var viewport = Size.Zero
    var gutter = 0f
    var bottomInset = 0f
    var cache: (items: List<MediaItem>, sidePx: Int) -> Unit = { _, _ -> }
    var preload: (items: List<MediaItem>, around: MediaItem, sidePx: Int) -> Unit = { _, _, _ -> }
    var showProNote: () -> Unit = {}
    var saveColumns: (Int) -> Unit = {}

    /** Set by a level switch so the grid relays out in the same frame; cleared once the saved value catches up. */
    var pinchedColumns by mutableStateOf<Int?>(null)
    val columns: Int get() = if (unlocksPro) pinchedColumns ?: savedColumns else GridZoom.DEFAULT_COLUMNS

    private var layoutKey: Any? = null
    private val layouts = HashMap<Int, GridLayout>()
    private var ranges: List<IntRange>? = null
    private var headerHeight = 0f
    private var bottomPadding = 0f

    /** Rebuilds the layout cache when anything a layout depends on changes. */
    fun setLayoutInputs(width: Float, gutter: Float, ranges: List<IntRange>?, headerHeight: Float, bottomPadding: Float) {
        val key = listOf(width, gutter, ranges, items.size, headerHeight, bottomPadding)
        if (key == layoutKey) return
        layoutKey = key
        layouts.clear()
        this.gutter = gutter
        this.ranges = ranges
        this.headerHeight = headerHeight
        this.bottomPadding = bottomPadding
    }

    fun layout(c: Int): GridLayout = layouts.getOrPut(c) {
        GridLayout(c, viewport.width, gutter, ranges, items.size, headerHeight, bottomPadding)
    }

    private val base get() = pinch.baseColumns
    private val baseSide get() = GridZoom.tileSide(base, viewport.width, gutter)
    private val resizeMotion = spring<Float>(dampingRatio = 1f, stiffness = 631.7f)      // response 0.25
    private val catchUpMotion = spring<Float>(dampingRatio = 1f, stiffness = 987f)      // response 0.2
    private val tapZoomMotion = tween<Float>(800, easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f))

    // region Progress

    /**
     * [doneIfCancelled]: a settle's done runs even when something cuts the animation short, as iOS's backstop does at
     * `duration + 0.25 s` (a stuck pinch blocks scrolling and taps). Here it runs as soon as it's cancelled. Every
     * settle done is guarded (swap id, `swap == null`), so running it early is safe.
     */
    private fun animateProgress(to: Float, spec: AnimationSpec<Float>, doneIfCancelled: Boolean = false, done: () -> Unit = {}) {
        // Read before cancelling: the old job's finally runs later, and a retargeted catch-up keeps the old speed.
        val from = pinch.progress
        val velocity = pinch.progressVelocity
        pinch.progressJob?.cancel()
        pinch.targetProgress = to
        pinch.progressJob = scope.launch {
            var finished = false
            try {
                animate(from, to, velocity, spec) { v, speed ->
                    pinch.progress = v
                    pinch.progressVelocity = speed
                }
                finished = true
                pinch.progressVelocity = 0f
            } finally {
                if (finished || doneIfCancelled) withContext(NonCancellable) { done() }
            }
        }
    }

    private fun setProgress(v: Float) {
        pinch.progressJob?.cancel()
        pinch.progress = v
        pinch.targetProgress = v
        pinch.progressVelocity = 0f
    }

    // endregion
    // region Begin, change, end

    fun pinchChanged(phase: PinchPhase, scale: Float, velocity: Float, centroid: Offset) {
        pinch.lastScale = scale
        pinch.lastCentroid = centroid
        if (phase == PinchPhase.Began) pinch.isTracking = true
        if (phase == PinchPhase.Ended) pinch.isTracking = false
        // While the grid switches level mid-pinch, the pinch carries on from the new level once it's done.
        if (viewport.width <= 0 || viewport.height <= 0 || pinch.swap != null) return
        when (phase) {
            PinchPhase.Began -> {
                // A spring-back still running keeps isRestoring, so applyPinch waits; its done calls resumeTracking,
                // which picks this pinch up.
                pinch.isActive = true
                pinch.baseColumns = columns
                pinch.baseOffset = scroll.offsetY
                // The detector starts after the fingers have moved; measure from here.
                pinch.startScale = max(scale, 0.01f)
                pinch.gestureScale = 1f
                pinch.limits = GridZoom.scaleLimits(base)
                pinch.anchorsOnSortedColumn = false
                setAnchor(centroid)
                pinch.anchorIndex?.let { i ->
                    preloadWider(i)
                    GridZoom.levels.firstOrNull { it > base }?.let { wider ->
                        cache(nearby(i, wider), sidePx(GridZoom.tileSide(wider, viewport.width, gutter)))
                    }
                }
                applyPinch()
            }
            PinchPhase.Changed -> {
                pinch.gestureScale = GridZoom.rubberBand(scale / pinch.startScale, pinch.limits)
                applyPinch()
            }
            PinchPhase.Ended -> release(scale, velocity)
        }
    }

    /** The photo at [point] on screen, and where in it the point is, so the next level can line it up. */
    private fun setAnchor(point: Offset) {
        pinch.anchorPoint = point
        val content = Offset(point.x, point.y + pinch.baseOffset)
        val current = layout(base)
        pinch.anchorIndex = current.indexNearest(content)
        val frame = pinch.anchorIndex?.let(current::frame)
        if (frame != null) pinch.anchorUnit = Offset((content.x - frame.left) / frame.width, (content.y - frame.top) / frame.height)
        pinch.anchorFrame = frame?.translate(0f, -pinch.baseOffset)
    }

    /** Lays out the next level and the scroll that keeps the anchor photo under the same point. */
    private fun preparePreview(target: Int): Boolean {
        val anchor = pinch.anchorIndex ?: return false
        val next = layout(target)
        val a = next.frame(anchor) ?: return false
        val rest = a.top + pinch.anchorUnit.y * a.height - pinch.anchorPoint.y
        val maxOffset = max(next.height + bottomInset - viewport.height, scroll.minOffset)
        pinch.previewLayout = next
        pinch.previewOffset = rest.coerceIn(scroll.minOffset, maxOffset)
        pinch.previewColumns = target
        pinch.sortRange = sortRange(target < base)
        // Each level the pinch heads to.
        cache(nearby(anchor, target), sidePx(next.side))
        return true
    }

    // endregion
    // region Following the fingers

    private fun applyPinch() {
        val looksLike = base / pinch.gestureScale
        val target = if (unlocksPro && pinch.anchorIndex != null) GridZoom.levelToward(looksLike, base) else null
        if (target == null) {
            // Carried on past the grid's level: the layer is all there is.
            if (!pinch.isRebased) restoreTiles()
            return
        }
        val progress = GridZoom.progress(looksLike, base, target)
        val catchesUp = SystemClock.uptimeMillis() < pinch.catchesUpUntil
        if (pinch.previewColumns != target) {
            if (pinch.isRebased) {
                // Turned the other way: start toward that level at once.
                Snapshot.withMutableSnapshot { resetTiles() }
                if (!preparePreview(target)) return
            } else {
                turnBack()
                if (pinch.isRestoring || progress <= 0.05f || !preparePreview(target)) return
            }
        } else if (pinch.isRestoring) {
            // Still on their way back; moving them now would pop.
            return
        }
        if (pinch.tiles.isEmpty()) pinchTiles(target)?.let { t ->
            // Seen through the camera at progress 0 they look just like the grid.
            Snapshot.withMutableSnapshot {
                setProgress(if (catchesUp) 0f else progress)
                pinch.tiles = t
                pinch.hidesTiles = true
            }
        }
        if (catchesUp) animateProgress(progress, catchUpMotion) else setProgress(progress)
        carryOn(looksLike, target)
    }

    /** Past a fully sorted level with the fingers still going: carry on toward the next one from there. */
    private fun carryOn(looksLike: Float, target: Int) {
        if (!pinch.isTracking || pinch.targetProgress < 1f || pinch.swap != null) return
        val beyond = GridZoom.levelToward(looksLike, target) ?: return
        if ((beyond > target) != (target > base)) return
        val carried = GridZoom.progress(looksLike, target, beyond)
        Snapshot.withMutableSnapshot {
            pinch.baseColumns = target
            pinch.baseOffset = pinch.previewOffset
            pinch.isRebased = target != columns
            resetTiles()
            // Measured from here: looks like as many columns as the fingers say.
            pinch.gestureScale = target / looksLike
            pinch.startScale = pinch.lastScale / pinch.gestureScale
            pinch.limits = GridZoom.scaleLimits(target)
            pinch.anchorsOnSortedColumn = false
            setAnchor(pinch.lastCentroid)
            if (preparePreview(beyond)) pinchTiles(beyond)?.let { t ->
                setProgress(carried)
                pinch.tiles = t
            }
        }
        applyPinch()
    }

    /** setProgress cancels a running glide too. */
    private fun resetTiles() {
        pinch.tiles = emptyList()
        setProgress(0f)
        pinch.isRestoring = false
    }

    /** Near the grid's own level the photos look just like it, so they go at once; a block mid-sort animates back. */
    private fun turnBack() =
        if (!pinch.isRestoring && pinch.targetProgress < min(0.25f, pinch.sortRange.start)) clearTiles() else restoreTiles()

    private fun restoreTiles() {
        if (pinch.tiles.isEmpty() || pinch.isRestoring) {
            if (pinch.tiles.isEmpty()) setProgress(0f)
            return
        }
        pinch.isRestoring = true
        animateProgress(0f, resizeMotion) {
            clearTiles()
            resumeTracking()
        }
    }

    /** Pinched again while photos were going back: carry on from where the fingers are now. */
    private fun resumeTracking() {
        if (!pinch.isTracking) return
        pinch.catchesUpUntil = SystemClock.uptimeMillis() + 250
        applyPinch()
    }

    private fun clearTiles() {
        // iOS sets pinch.settle = nil, stopping the glide.
        pinch.progressJob?.cancel()
        Snapshot.withMutableSnapshot {
            pinch.hidesTiles = false
            pinch.tiles = emptyList()
        }
        pinch.isRestoring = false
    }

    // endregion
    // region Which photos the layer draws, and where

    private fun pinchTiles(target: Int): List<PinchTile>? {
        val anchor = pinch.anchorIndex ?: return null
        val anchorOld = pinch.anchorFrame ?: return null
        val next = pinch.previewLayout ?: return null
        val anchorNew = next.frame(anchor) ?: return null
        val current = layout(base)
        val previewOffset = pinch.previewOffset
        val side = GridZoom.tileSide(target, viewport.width, next.gutter)
        val pitch = side + next.gutter
        val oldPitch = baseSide + next.gutter
        val anchorColumn = (anchorNew.left / pitch).roundToInt()
        val screen = Rect(Offset.Zero, viewport)
        val zoomsIn = target < base
        val assetCount = items.size
        fun sorted(i: Int) = next.frame(i)?.translate(0f, -previewOffset)

        // The photos on screen keep their rows; the anchor goes on the cell nearest where it is, kept inside the next
        // level's columns. Each level adds or drops the same columns on either side, hence (target − base) / 2.
        val anchorUnsortedColumn = if (pinch.anchorsOnSortedColumn) anchorColumn
        else ((anchorOld.left / oldPitch).roundToInt() + (target - base) / 2).coerceIn(0, target - 1)
        // Each photo keeps its row in its own month, lined up with where it'll be: the anchor's month on the anchor's
        // row, months above on their last row, below on their first. (Counting across a hidden title misplaced them.)
        val anchorPlace = current.placeInSection(anchor) ?: return null
        val anchorSortedPlace = next.placeInSection(anchor) ?: return null
        val anchorUnsorted = next.frameInSection(anchor, anchorSortedPlace.row, anchorUnsortedColumn)
            ?.translate(0f, -previewOffset) ?: return null
        val columnShift = anchorUnsortedColumn - anchorPlace.column
        fun rowShift(i: Int) = when {
            current.isSameSection(i, anchor) -> anchorSortedPlace.row - anchorPlace.row
            i >= anchor -> 0
            else -> (next.rowCount(i) ?: 0) - (current.rowCount(i) ?: 0)
        }
        // Centres: photos are drawn around their cells' centres.
        pinch.camera = PinchCamera(anchorOld.center, anchorUnsorted.center, oldPitch / pitch, baseSide)
        val cam = pinch.camera

        val tiles = mutableListOf<PinchTile>()
        val shown = HashSet<Int>()
        val taken = LinkedHashSet<GridCell>()
        val spots = HashMap<Int, GridCell>()
        for (i in current.indices(screen.translate(0f, pinch.baseOffset))) {
            if (i >= assetCount) continue
            val now = current.frame(i)?.translate(0f, -pinch.baseOffset) ?: continue
            val place = current.placeInSection(i) ?: continue
            // Zooming in, other months keep their columns (shifting them with the anchor's month sent them off the side).
            val column = place.column + if (zoomsIn && !current.isSameSection(i, anchor)) 0 else columnShift
            val row = place.row + rowShift(i)
            val unsorted = next.frameInSection(i, row, column)?.translate(0f, -previewOffset) ?: continue
            // By the next level's rows.
            val spot = GridCell(next.gridRow(i, row) ?: continue, column)
            if (!taken.add(spot)) continue
            shown += i
            spots[i] = spot
            // Starts exactly where the camera's start shows it: the pinch begins with nothing moving.
            val c = cam.to + (now.center - cam.from) / cam.startScale
            val kind = if (zoomsIn || current.isSameSection(i, anchor)) PinchTile.Kind.Shown else PinchTile.Kind.Moving
            tiles += PinchTile(i, kind, unsorted, Rect(c - Offset(side / 2, side / 2), Size(side, side)))
        }

        // The cells around are already sorted. Zooming in, the camera starts further out, so all it sees then counts.
        val o0 = cam.offset(0f)
        val seenAtStart = Rect(Offset(-o0.x / cam.startScale, -o0.y / cam.startScale), viewport / cam.startScale)
        val seen = screen.union(seenAtStart)
        val sortedAt = HashMap<GridCell, Int>()
        val fillers = mutableListOf<PinchTile>()
        val cells = next.indices(seen.translate(0f, previewOffset)).mapNotNull { i -> next.cell(i)?.let { GridCell(it.row, it.column) to i } }
        cells.forEach { (cell, i) -> sortedAt[cell] = i }
        for ((cell, i) in cells) {
            if (cell in taken) continue
            // Where the next level puts it: the handover moves nothing.
            val slot = sorted(i) ?: continue
            // Zooming in, every end cell is under an old photo already.
            if (zoomsIn) continue
            if (i in shown && !current.isSameSection(i, anchor)) {
                fillers += PinchTile(i, PinchTile.Kind.Arriving, slot)
                continue
            }
            val c = cam.from + (slot.center - cam.to) * cam.startScale
            val half = cam.startSide / 2
            val startsOnScreen = Rect(c - Offset(half, half), Size(half * 2, half * 2)).deflate(1f).overlaps(screen)
            fillers += PinchTile(i, if (startsOnScreen) PinchTile.Kind.Appearing else PinchTile.Kind.Filler, slot)
        }
        val underneath = taken.mapNotNull { spot ->
            sortedAt[spot]?.let { i ->
                sorted(i)?.let {
                    PinchTile(i, if (zoomsIn || current.isSameSection(i, anchor)) PinchTile.Kind.SortedMiddle else PinchTile.Kind.Arriving, it)
                }
            }
        }
        // An old photo beyond the next level's columns has no sorted photo under it: it stays until it's off screen.
        val old = tiles.map { t ->
            val spot = spots[t.index]
            if (t.kind == PinchTile.Kind.Shown && spot != null && (spot.column < 0 || spot.column >= target)) t.copy(kind = PinchTile.Kind.Edge) else t
        }
        // Draw order: the old photos on top.
        return fillers + underneath + old
    }

    // endregion
    // region Release, commit, settle, swap

    private fun release(scale: Float, velocity: Float) {
        // A quick flick carries on to where the pinch was heading, about a tenth of a second on.
        val projected = pinch.gestureScale * max(1 + velocity * 0.1f / max(scale, 0.01f), 0.1f)
        val looksLike = base / projected
        val target = GridZoom.levelToward(looksLike, base)
        val commits = pinch.targetProgress >= pinch.sortRange.start ||
            (target != null && GridZoom.progress(looksLike, base, target) >= sortRange(target < base).start)
        // Progress per second toward the previewed level, for the finish to carry on at.
        val progressVelocity = pinch.previewColumns?.takeIf { scale > 0.01f }?.let { t ->
            val now = GridZoom.progress(base / pinch.gestureScale, base, t)
            val soon = GridZoom.progress(base / (pinch.gestureScale * (1 + velocity / scale / 60)), base, t)
            (soon - now) * 60
        } ?: 0f
        if (commits && !unlocksPro) showProNote()
        if (commits && target != null && target == pinch.previewColumns && pinch.tiles.isNotEmpty() && !pinch.isRestoring) {
            commit(target, progressVelocity)
        } else {
            springBack(progressVelocity)
        }
    }

    /** The layer finishes its move while the grid switches underneath (hidden); then the grid takes over in one frame. */
    private fun commit(target: Int, velocity: Float) {
        val id = System.nanoTime()
        pinch.swap = id
        settle(1f, velocity) { finishSwap(id) }
        // After the settle's first frame, so the heavy rebuild at the new level happens while the photos glide.
        scope.launch {
            withFrameNanos { }
            switchGrid(target)
        }
    }

    private fun settle(to: Float, velocity: Float, done: () -> Unit) {
        val distance = abs(to - pinch.progress)
        val toward = if (sign(to - pinch.progress) == sign(velocity)) abs(velocity) else 0f
        val duration = settleDuration(distance, toward)
        val slope = if (distance > 0.001f) toward / distance * duration else 0f
        // Nothing to glide (past the last level, or already there): finish on the next frame.
        if (distance <= 0.001f || pinch.tiles.isEmpty()) {
            setProgress(to)
            scope.launch {
                withFrameNanos { }
                done()
            }
            return
        }
        animateProgress(to, tween((duration * 1000).roundToInt(), easing = settleEasing(slope)), doneIfCancelled = true, done = done)
    }

    private fun switchAndReveal(target: Int, offset: Float? = null) {
        val id = System.nanoTime()
        pinch.swap = id
        switchGrid(target, offset)
        // A couple of frames for the grid to draw at its new level.
        scope.launch {
            delay(50)
            finishSwap(id)
        }
    }

    private fun switchGrid(target: Int, offset: Float? = null) {
        Snapshot.withMutableSnapshot {
            pinchedColumns = target
            scroll.scrollTo(offset ?: pinch.previewOffset)
        }
        saveColumns(target)
        cache(nearby(pinch.anchorIndex ?: 0, target), sidePx(GridZoom.tileSide(target, viewport.width, gutter)))
    }

    private fun finishSwap(id: Long) {
        if (pinch.swap != id) return
        pinch.swap = null
        // The grid under the photos is now laid out as they are.
        pinch.hidesTiles = false
        scope.launch {
            withFrameNanos { }
            setProgress(0f)
            clearTiles()
            pinch.previewColumns = null
            pinch.previewLayout = null
            pinch.isRebased = false
            if (pinch.isTracking) {
                // Switched mid-pinch.
                pinchChanged(PinchPhase.Began, pinch.lastScale, 0f, pinch.lastCentroid)
            } else {
                pinch.isActive = false
                pinch.settledLevels++
            }
        }
    }

    private fun springBack(velocity: Float) {
        pinch.isRestoring = pinch.tiles.isNotEmpty()
        settle(0f, velocity) {
            if (pinch.swap != null) return@settle
            if (pinch.isRebased) {
                // Ends where the pinch was working from.
                switchAndReveal(base, pinch.baseOffset)
                return@settle
            }
            clearTiles()
            pinch.previewColumns = null
            pinch.previewLayout = null
            pinch.isActive = pinch.isTracking
            resumeTracking()
        }
    }

    // endregion
    // region Overview tap

    /** At 15 columns a tap zooms into 5 on the tapped photo, which stays under the finger. */
    fun zoomIn(at: Offset) {
        if (pinch.isActive || !unlocksPro || viewport.width <= 0) return
        pinch.isActive = true
        pinch.isTracking = false
        pinch.baseColumns = columns
        pinch.baseOffset = scroll.offsetY
        pinch.anchorsOnSortedColumn = true
        setAnchor(at)
        val target = GridZoom.OVERVIEW_ZOOM_COLUMNS
        val t = if (preparePreview(target)) pinchTiles(target) else null
        if (t == null) {
            pinch.previewColumns = null
            pinch.previewLayout = null
            pinch.isActive = false
            return
        }
        Snapshot.withMutableSnapshot {
            setProgress(0f)
            pinch.tiles = t
            pinch.hidesTiles = true
        }
        animateProgress(1f, tapZoomMotion) { switchAndReveal(target) }
    }

    fun zoomIn(toIndex: Int) {
        layout(columns).frame(toIndex)?.let { zoomIn(Offset(it.center.x, it.center.y - scroll.offsetY)) }
    }

    // endregion
    // region Preload

    private fun sidePx(side: Float) = side.roundToInt()

    private fun nearby(i: Int, columns: Int): List<MediaItem> {
        val count = items.size
        if (count == 0) return emptyList()
        return items.subList((i - columns * 30).coerceIn(0, count), min(i + columns * 30, count))
    }

    /** Decodes the photos the next level out would show around [index], so a pinch out's edges draw at once. */
    fun preloadWider(index: Int) {
        val wider = GridZoom.levels.firstOrNull { it > columns } ?: return
        val count = items.size
        if (index !in 0 until count) return
        val side = GridZoom.tileSide(wider, viewport.width, gutter)
        val span = wider * ceil(viewport.height * 1.5f / (side + gutter)).toInt() / 2
        preload(items.subList(max(index - span, 0), min(index + span, count)), items[index], sidePx(side))
    }

    // endregion
}

private fun Rect.union(o: Rect) = Rect(min(left, o.left), min(top, o.top), max(right, o.right), max(bottom, o.bottom))
