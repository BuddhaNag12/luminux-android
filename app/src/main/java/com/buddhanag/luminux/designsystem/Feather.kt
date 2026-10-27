package com.buddhanag.luminux.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Where a page's feathered items sit: swung toward the viewer before the page opens (and while it's popped away),
 * flat while it shows, swung away while another page covers it.
 */
enum class FeatherState { Below, Shown, Above }

data class FeatherContext(val state: FeatherState = FeatherState.Shown, val previous: FeatherState = FeatherState.Shown)

/**
 * The turnstile feather: every item turns on a hinge at the left edge of the screen, line after line from the top,
 * using the original timings (in −80° → 0° over 350 ms with a sharp decelerate; out 0° → 50° over 220 ms accelerating).
 */
object Feather {
    const val ENTER_ANGLE = -80f
    const val COVER_ANGLE = 50f

    const val ENTER_MS = 350
    const val EXIT_MS = 220
    const val ROW_DELAY = 0.04
    const val COLUMN_DELAY = 0.012
    const val EXIT_ROW_DELAY = 0.025
    /** Lines further down than this are off screen, so they don't hold up the visible ones. */
    const val VISIBLE_ROWS = 10

    /** The outgoing page is gone by now (its items turn away, then its background fades), so pages never overlap. */
    const val ENTER_BASE_DELAY = 0.22
    /** When a covered page's background fades out, once its items have mostly turned away. */
    const val COVER_FADE_DELAY = 0.12
    const val REVEAL_BASE_DELAY = 0.12

    /** One camera for every item (iOS perspective 0.75/900 of the item's longer side), so all share a vanishing point. */
    val cameraDistance = 1200.dp

    /** How long a full exit takes, so a popped page can be removed once its items are gone: 0.345 s. */
    val exitTime get() = EXIT_MS / 1000.0 + (VISIBLE_ROWS / 2) * EXIT_ROW_DELAY
    /** How long a page's entrance takes, so heavy views (a map) can wait until its lines have settled: 0.97 s. */
    val enterTime get() = ENTER_BASE_DELAY + VISIBLE_ROWS * ROW_DELAY + ENTER_MS / 1000.0

    private const val SCATTER_MS = 220
    private const val SCATTER_STAGGER = 0.03

    fun angle(state: FeatherState) = when (state) {
        FeatherState.Below -> ENTER_ANGLE
        FeatherState.Shown -> 0f
        FeatherState.Above -> COVER_ANGLE
    }

    /** Windows' decelerate curve, and its accelerating mirror for exits. */
    val Decelerate = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1f)
    val Accelerate = CubicBezierEasing(0.7f, 0f, 1f, 0.5f)

    /** Items pop visible as they start turning in and vanish as they finish turning out. [rotation] also drives scatter. */
    data class Timing(val rotation: TweenSpec<Float>, val alpha: TweenSpec<Float>)

    /** [waitsForOutgoing] false: nothing is leaving (a zooming page is only built once the one before has gone). */
    fun timing(toward: FeatherState, from: FeatherState, row: Int, column: Int, waitsForOutgoing: Boolean = true): Timing {
        val line = row.coerceIn(0, VISIBLE_ROWS)
        return if (toward == FeatherState.Shown) {
            val base = if (from == FeatherState.Below) (if (waitsForOutgoing) ENTER_BASE_DELAY else 0.0) else REVEAL_BASE_DELAY
            val delay = ms(base + line * ROW_DELAY + column * COLUMN_DELAY)
            Timing(tween(ENTER_MS, delay, Decelerate), tween(40, delay, LinearEasing))
        } else {
            val delay = line * EXIT_ROW_DELAY + column * COLUMN_DELAY * 0.5
            Timing(tween(EXIT_MS, ms(delay), Accelerate), tween(50, ms(delay + EXIT_MS / 1000.0 - 0.05), LinearEasing))
        }
    }

    /** One item at a time in reading order: the title, the headers, then each tile left to right, line by line. */
    fun scatterTiming(out: Boolean, row: Int, column: Int): Timing {
        val stagger = (max(row, 0) * 2 + column) * SCATTER_STAGGER
        // Out: grows away at full strength, then fades only for the last 80 ms of its 220.
        if (out) return Timing(tween(SCATTER_MS, ms(stagger), MetroMotion.EaseIn), tween(80, ms(stagger + 0.14), LinearEasing))
        val delay = ms(REVEAL_BASE_DELAY + stagger)
        return Timing(tween(350, delay, Decelerate), tween(60, delay, LinearEasing))
    }

    /** The title swings in first, then the headers, then the tiles in reading order, a moment apart. */
    fun forwardTiming(row: Int, column: Int): Timing {
        val delay = ms(forwardDelay(row, column))
        return if (row == 0) Timing(tween(700, delay, Decelerate), tween(300, delay, LinearEasing))
        else Timing(tween(550, delay, Decelerate), tween(200, delay, LinearEasing))
    }

    fun forwardDelay(row: Int, column: Int): Double = when (row) {
        0 -> 0.0
        1 -> 0.25 + column * 0.05
        else -> 0.35 + ((row - 2) * 2 + column) * 0.05
    }

    private fun ms(seconds: Double) = max((seconds * 1000).roundToInt(), 0)
}

/** Waits [millis] at the system animation speed, so waits between animations keep in step with them. */
suspend fun motionDelay(millis: Long) = delay((millis * (currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f)).toLong())

/**
 * Like marbles thrown at a mirror: where an item at [frame] in a [page] flies as it scatters, in dp. Each flies outward
 * from the centre on a slightly different line; the variation comes from its position, so it's the same every time.
 */
fun scatterOffset(frame: Rect, page: Size): Offset {
    val mix = abs(frame.left) * 7 + abs(frame.top) * 13
    // A view mid-layout can report an infinite frame; it gets vector (0, -1) with seed 0 (swerve -20°): up, a little left.
    val finite = mix.isFinite()
    val seed = if (finite) (mix % 1_000_000f).toInt() else 0
    val swerve = Math.toRadians((seed % 41 - 20).toDouble())
    var dx = if (finite) frame.center.x - page.width / 2 else 0f
    var dy = if (finite) frame.center.y - page.height / 2 else -1f
    val length = max(hypot(dx, dy), 1f)
    // Items near the centre still fly somewhere, not just grow in place.
    val reach = max(length, 60f) * 0.6f
    dx = dx / length * reach
    dy = dy / length * reach
    return Offset((dx * cos(swerve) - dy * sin(swerve)).toFloat(), (dx * sin(swerve) + dy * cos(swerve)).toFloat())
}

/**
 * `Zoom`: instead of turning, a covered page's items grow and fly outward one at a time, like Windows 10 Mobile's start
 * screen as an app opens, and fly back in when it's uncovered. The first page comes forward from behind as the app starts.
 */
enum class FeatherStyle { Turnstile, Zoom }

val LocalFeather = compositionLocalOf { FeatherContext() }

/** The first visible row of a scrolling grid, so rows scrolled above the screen don't hold up the visible ones. */
val LocalFeatherRowOrigin = compositionLocalOf<IntState?> { null }

val LocalFeatherStyle = compositionLocalOf { FeatherStyle.Turnstile }
/** The page stack's size in px: the title comes forward from 0.35 of its width, and it stands in for an unmeasured page. */
val LocalFeatherScreen = compositionLocalOf { Size.Zero }
/** Items opening come forward from behind the screen instead of turning in (the first page, as the app starts). */
val LocalFeatherFromBack = compositionLocalOf { false }

/** The page root scattering items fly out from the centre of (iOS `Feather.pageSpace`). Not observed: read while drawing. */
class FeatherPage {
    var coordinates: LayoutCoordinates? = null
}

val LocalFeatherPage = compositionLocalOf<FeatherPage?> { null }

/** Drives the feathered items inside: they turn in once on appear, then follow [resting]. */
@Composable
fun FeatherScope(modifier: Modifier = Modifier, resting: FeatherState = FeatherState.Shown, content: @Composable () -> Unit) {
    var context by remember { mutableStateOf(FeatherContext(FeatherState.Below, FeatherState.Below)) }
    var appeared by remember { mutableStateOf(false) }
    fun move(to: FeatherState) {
        if (to != context.state) context = FeatherContext(to, context.state)
    }
    LaunchedEffect(Unit) {
        // One frame at the start angle first, or there's nothing to animate from.
        delay(16)
        appeared = true
        move(resting)
    }
    LaunchedEffect(resting) { if (appeared) move(resting) }
    // Backgrounds and chrome that don't feather appear as the first line starts turning; a zooming page waited already.
    val delay = if (LocalFeatherStyle.current == FeatherStyle.Zoom) 0 else (Feather.ENTER_BASE_DELAY * 1000).roundToInt()
    val alpha by animateFloatAsState(if (appeared) 1f else 0f, tween(80, delay, LinearEasing), label = "featherScope")
    CompositionLocalProvider(LocalFeather provides if (LocalReduceMotion.current) FeatherContext() else context) {
        Box(modifier.graphicsLayer { this.alpha = alpha }) { content() }
    }
}

/**
 * Turns this item with its page; [row] sets its line in the feather, [column] a small offset within the line. With the
 * zoom style a page being covered, or uncovered again, scatters instead; the first page opening comes forward from the back.
 */
fun Modifier.metroFeather(row: Int, column: Int = 0): Modifier = composed {
    val feather = LocalFeather.current
    val zoom = LocalFeatherStyle.current == FeatherStyle.Zoom
    val forward = zoom && LocalFeatherFromBack.current
    val page = LocalFeatherPage.current
    val screen = LocalFeatherScreen.current
    val visibleRow = row - (LocalFeatherRowOrigin.current?.intValue ?: 0)
    val offScreen = visibleRow < -1 || visibleRow > Feather.VISIBLE_ROWS + 1
    val (state, previous) = feather
    val scatters = zoom && (state == FeatherState.Above || (state == FeatherState.Shown && previous == FeatherState.Above))
    val comesForward = !scatters && forward &&
        (state == FeatherState.Below || (state == FeatherState.Shown && previous == FeatherState.Below))
    val timing = when {
        scatters -> Feather.scatterTiming(state == FeatherState.Above, visibleRow, column)
        comesForward -> Feather.forwardTiming(visibleRow, column)
        else -> Feather.timing(state, previous, visibleRow, column, waitsForOutgoing = !zoom)
    }
    val motion = if (offScreen) snap() else timing.rotation
    // One chain for every style, so switching never rebuilds the item.
    val angle by animateFloatAsState(if (scatters || comesForward) 0f else Feather.angle(state), motion, label = "feather")
    // 1 scattered toward the viewer, -1 back behind the screen.
    val out by animateFloatAsState(
        when {
            scatters && state == FeatherState.Above -> 1f
            comesForward && state == FeatherState.Below -> -1f
            else -> 0f
        },
        motion, label = "featherOut",
    )
    val alpha by animateFloatAsState(
        if (state == FeatherState.Shown) 1f else 0f, if (offScreen) snap() else timing.alpha, label = "featherAlpha",
    )
    var left by remember { mutableFloatStateOf(0f) }
    val item = remember { FeatherPage() }
    val distancePx = with(LocalDensity.current) { Feather.cameraDistance.toPx() }
    val slidesIn = visibleRow == 0
    this
        .onGloballyPositioned {
            left = it.positionInWindow().x
            item.coordinates = it
        }
        .graphicsLayer {
            this.alpha = alpha
            if (out < 0f) {
                // Starts small and tilted, as if swinging in from behind the screen; the title from further back right.
                scaleX = 1f - (if (slidesIn) 0.35f else 0.4f) * -out
                scaleY = scaleX
                if (slidesIn) translationX = screen.width * 0.35f * -out
            } else if (out > 0f) {
                // Measured only while out, so an item at rest doesn't follow every scroll.
                val o = scatterOffsetPx(item.coordinates, page?.coordinates, screen)
                scaleX = 1f + 0.8f * out
                scaleY = scaleX
                translationX = o.x * out
                translationY = o.y * out
            }
        }
        // The turn: on a hinge at the screen's left edge with one shared perspective; coming forward, about the centre.
        .perspectiveRotation(
            { if (forward) (if (slidesIn) -55f else -35f) * max(-out, 0f) else angle },
            axisX = if (forward) (if (slidesIn) 0.2f else 0.6f) else 0f, axisY = 1f,
            origin = { if (forward) TransformOrigin.Center else TransformOrigin(-left / max(size.width, 1f), 0.5f) },
            perspective = 0.6f,
            fixedDistancePx = if (forward) null else distancePx,
        )
}

private fun GraphicsLayerScope.scatterOffsetPx(item: LayoutCoordinates?, page: LayoutCoordinates?, screen: Size): Offset {
    val placed = item?.takeIf { it.isAttached } ?: return Offset.Zero
    val pageCoordinates = page?.takeIf { it.isAttached }
    val frame = (pageCoordinates ?: placed.findRootCoordinates()).localBoundingBoxOf(placed, clipBounds = false)
    val pageSize = pageCoordinates?.size?.toSize() ?: screen
    // The constants are in dp, layout is in px.
    return scatterOffset(Rect(frame.topLeft / density, frame.size / density), pageSize / density) * density
}

/**
 * For a live platform view (a map, a video surface): fades with its page instead of moving. Turning or scattering one
 * re-clips and re-lays it out on every frame.
 */
fun Modifier.metroFeatherFade(): Modifier = composed {
    val shown = LocalFeather.current.state == FeatherState.Shown
    val alpha by animateFloatAsState(
        if (shown) 1f else 0f, tween(200, if (shown) (Feather.REVEAL_BASE_DELAY * 1000).roundToInt() else 0, MetroMotion.EaseOut),
        label = "featherFade",
    )
    graphicsLayer { this.alpha = alpha }
}
