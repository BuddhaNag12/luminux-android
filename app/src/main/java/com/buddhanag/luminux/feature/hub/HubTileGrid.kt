package com.buddhanag.luminux.feature.hub

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.animateIntSizeAsState
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.buddhanag.luminux.data.hub.HubTile
import com.buddhanag.luminux.data.hub.HubTileKind
import com.buddhanag.luminux.data.hub.HubTilesRepository
import com.buddhanag.luminux.data.hub.TilePacking
import com.buddhanag.luminux.designsystem.LocalMetro
import com.buddhanag.luminux.designsystem.LocalReduceMotion
import com.buddhanag.luminux.designsystem.MetroMetrics
import com.buddhanag.luminux.designsystem.MetroMotion
import com.buddhanag.luminux.designsystem.metroFeather
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The hub's "customize" state: whether tiles are being edited, and which one shows its unpin and resize buttons. */
@Stable
internal class HubEditState {
    var isEditing by mutableStateOf(false)
        private set
    var selected by mutableStateOf<HubTileKind?>(null)
    /** A photo set waiting for the unpin confirmation; its data dies with its tile. */
    var confirmsUnpin by mutableStateOf<HubTileKind?>(null)

    fun begin(selecting: HubTileKind?) {
        isEditing = true
        selected = selecting
    }

    fun end() {
        isEditing = false
        selected = null
    }
}

private data class TileDrag(val kind: HubTileKind, val grab: Offset)

/** How far the edit buttons reach past a tile's corner: an 18 dp offset plus half the 44 dp target. */
internal val EditButtonReach = 40.dp

/**
 * The collection panel's tiles (HubTileGrid.swift). Press and hold one to customise the panel, like the Windows Phone
 * start screen: drag tiles to move them, and use the buttons on the chosen tile to unpin or resize it. Tiles reflow
 * and resize by animating their own offset and size, so moves need no extra animation code.
 */
@Composable
internal fun HubTileGrid(
    repo: HubTilesRepository,
    tiles: List<HubTile>,
    data: TileFaceData,
    edit: HubEditState,
    onOpen: (HubTileKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val width = constraints.maxWidth.toFloat()
        val spacing = with(density) { MetroMetrics.gutter.toPx() }
        val placements = TilePacking.place(tiles.map { it.size })
        val frames = placements.map { TilePacking.frame(it, width, spacing) }
        val height by animateDpAsState(
            with(density) { TilePacking.height(placements, width, spacing).toDp() }, MetroMotion.standard(), label = "gridHeight",
        )
        var drag by remember { mutableStateOf<TileDrag?>(null) }
        var floating by remember { mutableStateOf(Offset.Zero) }
        var release by remember { mutableStateOf<Job?>(null) }
        val scope = rememberCoroutineScope()
        val haptics = LocalHapticFeedback.current
        val currentWidth by rememberUpdatedState(width)
        // The drag reads the repository directly: its moves land at once, before the next recomposition.
        fun framesNow(order: List<HubTile>) =
            TilePacking.place(order.map { it.size }).map { TilePacking.frame(it, currentWidth, spacing) }

        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val start = repo.layout.value.tiles
                        val startFrames = framesNow(start)
                        val index = startFrames.indexOfFirst { it.contains(down.position) }.takeIf { it >= 0 } ?: return@awaitEachGesture
                        val slop = 10.dp.toPx()
                        // Lifting or moving first lets the tap, the panel swipe or the scroll have the touch as usual.
                        val lifted = withTimeoutOrNull(if (edit.isEditing) 200L else 400L) {
                            while (true) {
                                val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                                if (c == null || !c.pressed || (c.position - down.position).getDistance() > slop) break
                            }
                        }
                        if (lifted != null) return@awaitEachGesture

                        val kind = start[index].kind
                        edit.begin(kind)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        release?.cancel()
                        val grab = down.position - startFrames[index].topLeft
                        drag = TileDrag(kind, grab)
                        floating = startFrames[index].topLeft
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            // Taken before anyone else sees it: the panorama, the scroll and the tile's tap all stand down.
                            event.changes.forEach { it.consume() }
                            val c = event.changes.firstOrNull { it.id == down.id }
                            if (c == null || !c.pressed) break
                            floating = c.position - grab
                            val order = repo.layout.value.tiles
                            val from = order.indexOfFirst { it.kind == kind }.takeIf { it >= 0 } ?: break
                            val held = Rect(floating, framesNow(order)[from].size)
                            val to = TilePacking.dropIndex(held, order.map { it.size }, from, currentWidth, spacing)
                            if (to != from) repo.move(kind, to)
                        }
                        // Settle into the slot it was dropped on, then hand back to the tile in the grid.
                        val order = repo.layout.value.tiles
                        val slot = order.indexOfFirst { it.kind == kind }.takeIf { it >= 0 }?.let { framesNow(order)[it].topLeft }
                        release = scope.launch {
                            if (slot != null) {
                                Animatable(floating, Offset.VectorConverter).animateTo(slot, MetroMotion.release()) { floating = value }
                            }
                            drag = null
                        }
                    }
                },
        ) {
            tiles.forEachIndexed { index, tile ->
                key(tile.kind) {
                    TileSlot(
                        tile, index, tiles.size, frames[index], placements[index].let { it.row / 2 to it.column / 2 },
                        data, edit, repo, isHeld = drag?.kind == tile.kind, onOpen,
                    )
                }
            }
            val held = drag?.let { d -> tiles.indexOfFirst { it.kind == d.kind } }?.takeIf { it >= 0 }
            if (held != null) {
                val size = frames[held].size
                HubTileFace(
                    tiles[held], data, onClick = {},
                    Modifier
                        .offset { IntOffset(floating.x.roundToInt(), floating.y.roundToInt()) }
                        .size(with(density) { size.width.toDp() }, with(density) { size.height.toDp() })
                        .zIndex(2f)
                        .graphicsLayer {
                            scaleX = 1.05f
                            scaleY = 1.05f
                        }
                        .clearAndSetSemantics {},
                )
            }
        }
    }
}

@Composable
private fun TileSlot(
    tile: HubTile,
    index: Int,
    count: Int,
    frame: Rect,
    feather: Pair<Int, Int>,
    data: TileFaceData,
    edit: HubEditState,
    repo: HubTilesRepository,
    isHeld: Boolean,
    onOpen: (HubTileKind) -> Unit,
) {
    val density = LocalDensity.current
    val kind = tile.kind
    val isSelected = edit.isEditing && edit.selected == kind
    val isDimmed = edit.isEditing && !isSelected
    val offset by animateIntOffsetAsState(IntOffset(frame.left.roundToInt(), frame.top.roundToInt()), MetroMotion.standard(), label = "tileOffset")
    val size by animateIntSizeAsState(IntSize(frame.width.roundToInt(), frame.height.roundToInt()), MetroMotion.standard(), label = "tileSize")
    val scale by animateFloatAsState(if (isDimmed) 0.9f else 1f, MetroMotion.standard(), label = "tileScale")
    val alpha by animateFloatAsState(if (isDimmed) 0.6f else 1f, MetroMotion.standard(), label = "tileAlpha")
    val drift = editDrift(isDimmed, seed = index)
    val askToUnpin = {
        // A photo set only lives on its tile, so check first; an album can be pinned again from its page.
        if (kind is HubTileKind.PhotoSet) edit.confirmsUnpin = kind else unpin(repo, edit, kind)
    }

    Box(
        Modifier
            .offset { offset }
            .size(with(density) { size.width.toDp() }, with(density) { size.height.toDp() })
            .zIndex(if (isSelected) 1f else 0f)
            .metroFeather(row = 2 + feather.first, column = feather.second),
    ) {
        HubTileFace(
            tile, data,
            onClick = { if (edit.isEditing) edit.selected = kind else onOpen(kind) },
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = drift.x.dp.toPx()
                    translationY = drift.y.dp.toPx()
                    // The held tile follows the finger above the grid; its slot stays empty.
                    this.alpha = if (isHeld) 0f else alpha
                }
                .semantics {
                    customActions = buildList {
                        if (index > 0) add(CustomAccessibilityAction("Move earlier") { repo.move(kind, index - 1); true })
                        if (index < count - 1) add(CustomAccessibilityAction("Move later") { repo.move(kind, index + 1); true })
                        add(CustomAccessibilityAction("Make ${tile.size.next.name.lowercase()}") { repo.resize(kind); true })
                        if (!kind.isBuiltIn) add(CustomAccessibilityAction("Unpin") { askToUnpin(); true })
                    }
                },
        )
        AnimatedVisibility(
            isSelected && !kind.isBuiltIn && !isHeld, Modifier.align(Alignment.TopEnd).offset(EditButtonReach, -EditButtonReach),
            enter = fadeIn(MetroMotion.standard()), exit = fadeOut(MetroMotion.standard()),
        ) {
            EditButton(HubIcons.unpin, "Unpin", askToUnpin)
        }
        AnimatedVisibility(
            isSelected && !isHeld, Modifier.align(Alignment.BottomEnd).offset(EditButtonReach, EditButtonReach),
            enter = fadeIn(MetroMotion.standard()), exit = fadeOut(MetroMotion.standard()),
        ) {
            val grows = tile.size.next.width > tile.size.width
            EditButton(if (grows) HubIcons.expand else HubIcons.shrink, "Resize") { repo.resize(kind) }
        }
    }
}

/**
 * While tiles are being rearranged, the ones not held float slowly about their slots, now and then edging over a
 * neighbour, like the start screen's edit mode (HubTileGrid.swift `EditDrift`). Keyframes are (dp, segment ms).
 */
internal object EditDrift {
    /** More than the 4 dp gutter, so neighbours can overlap a little. */
    const val REACH = 8f

    // Each tile gets its own path and pace, so they don't move in step.
    private fun turn(seed: Int) = seed % 5 * 230
    private fun sign(seed: Int) = if (seed % 2 == 0) 1f else -1f

    fun x(seed: Int) = listOf(sign(seed) * REACH to 1700 + turn(seed), -sign(seed) * REACH * 0.6f to 2100 + turn(seed), 0f to 1600)
    fun y(seed: Int) = listOf(-REACH * 0.7f to 1400 + turn(seed), REACH to 2200, 0f to 1800 + turn(seed))

    /** [strength] eases the drift in and back out; at 0 the tile sits exactly in its slot, whatever the keyframes say. */
    fun offset(raw: Float, strength: Float) = if (strength <= 0f) 0f else raw * strength
}

/** The drift offset in dp, read in a graphics layer so it never re-lays out. Zero, and no animation, when not drifting. */
@Stable
private class Drift(private val raw: () -> Offset, private val strength: () -> Float) {
    val x get() = EditDrift.offset(raw().x, strength())
    val y get() = EditDrift.offset(raw().y, strength())
}

@Composable
private fun editDrift(isDimmed: Boolean, seed: Int): Drift {
    val drifts = isDimmed && !LocalReduceMotion.current
    // Back into its slot smoothly when editing ends or it's picked.
    val strength = animateFloatAsState(if (drifts) 1f else 0f, MetroMotion.standard(), label = "driftStrength")
    // Composed only while it moves: a stopped tile runs no animation and keeps no stale offset after a resize.
    if (!drifts && strength.value == 0f) return remember { Drift({ Offset.Zero }) { 0f } }
    val transition = rememberInfiniteTransition(label = "drift")
    val x = transition.track(EditDrift.x(seed))
    val y = transition.track(EditDrift.y(seed))
    return remember(x, y, strength) { Drift({ Offset(x.value, y.value) }) { strength.value } }
}

@Composable
private fun InfiniteTransition.track(frames: List<Pair<Float, Int>>) = animateFloat(
    0f, 0f,
    infiniteRepeatable(
        keyframes {
            durationMillis = frames.sumOf { it.second }
            // Compose keyframes are linear by default; SwiftUI's CubicKeyframe draws a smooth curve.
            0f at 0 using MetroMotion.EaseInOut
            var t = 0
            frames.forEach { (value, ms) ->
                t += ms
                value at t using MetroMotion.EaseInOut
            }
        },
    ),
    label = "driftTrack",
)

internal fun unpin(repo: HubTilesRepository, edit: HubEditState, kind: HubTileKind) {
    repo.unpin(kind)
    if (edit.selected == kind) edit.selected = null
}

/** A foreground-filled 30 dp circle with a background-coloured glyph, on a 44 dp target. */
@Composable
private fun EditButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val metro = LocalMetro.current
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(30.dp).background(metro.foreground, CircleShape), contentAlignment = Alignment.Center) {
            Image(icon, null, Modifier.size(15.dp), colorFilter = ColorFilter.tint(metro.background))
        }
    }
}
