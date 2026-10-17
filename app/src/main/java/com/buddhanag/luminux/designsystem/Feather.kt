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
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.roundToInt

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

    fun angle(state: FeatherState) = when (state) {
        FeatherState.Below -> ENTER_ANGLE
        FeatherState.Shown -> 0f
        FeatherState.Above -> COVER_ANGLE
    }

    /** Windows' decelerate curve, and its accelerating mirror for exits. */
    val Decelerate = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1f)
    val Accelerate = CubicBezierEasing(0.7f, 0f, 1f, 0.5f)

    /** Items pop visible as they start turning in and vanish as they finish turning out. */
    data class Timing(val rotation: TweenSpec<Float>, val alpha: TweenSpec<Float>)

    fun timing(toward: FeatherState, from: FeatherState, row: Int, column: Int): Timing {
        val line = row.coerceIn(0, VISIBLE_ROWS)
        return if (toward == FeatherState.Shown) {
            val base = if (from == FeatherState.Below) ENTER_BASE_DELAY else REVEAL_BASE_DELAY
            val delay = ms(base + line * ROW_DELAY + column * COLUMN_DELAY)
            Timing(tween(ENTER_MS, delay, Decelerate), tween(40, delay, LinearEasing))
        } else {
            val delay = line * EXIT_ROW_DELAY + column * COLUMN_DELAY * 0.5
            Timing(tween(EXIT_MS, ms(delay), Accelerate), tween(50, ms(delay + EXIT_MS / 1000.0 - 0.05), LinearEasing))
        }
    }

    private fun ms(seconds: Double) = (seconds * 1000).roundToInt()
}

val LocalFeather = compositionLocalOf { FeatherContext() }

/** The first visible row of a scrolling grid, so rows scrolled above the screen don't hold up the visible ones. */
val LocalFeatherRowOrigin = compositionLocalOf<IntState?> { null }

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
    // Backgrounds and chrome that don't feather appear as the first line starts turning.
    val alpha by animateFloatAsState(
        if (appeared) 1f else 0f, tween(80, (Feather.ENTER_BASE_DELAY * 1000).roundToInt(), LinearEasing), label = "featherScope",
    )
    CompositionLocalProvider(LocalFeather provides if (LocalReduceMotion.current) FeatherContext() else context) {
        Box(modifier.graphicsLayer { this.alpha = alpha }) { content() }
    }
}

/** Turns this item with its page; [row] sets its line in the feather, [column] a small offset within the line. */
fun Modifier.metroFeather(row: Int, column: Int = 0): Modifier = composed {
    val feather = LocalFeather.current
    val visibleRow = row - (LocalFeatherRowOrigin.current?.intValue ?: 0)
    val offScreen = visibleRow < -1 || visibleRow > Feather.VISIBLE_ROWS + 1
    val timing = Feather.timing(feather.state, feather.previous, visibleRow, column)
    val angle by animateFloatAsState(Feather.angle(feather.state), if (offScreen) snap() else timing.rotation, label = "feather")
    val alpha by animateFloatAsState(
        if (feather.state == FeatherState.Shown) 1f else 0f, if (offScreen) snap() else timing.alpha, label = "featherAlpha",
    )
    var left by remember { mutableFloatStateOf(0f) }
    val distancePx = with(LocalDensity.current) { Feather.cameraDistance.toPx() }
    this
        .onGloballyPositioned { left = it.positionInWindow().x }
        // Hinge on the screen's left edge, with one shared perspective for every item.
        .perspectiveRotation(
            { angle }, axisY = 1f,
            origin = { TransformOrigin(-left / max(size.width, 1f), 0.5f) },
            fixedDistancePx = distancePx,
        )
        .graphicsLayer { this.alpha = alpha }
}
