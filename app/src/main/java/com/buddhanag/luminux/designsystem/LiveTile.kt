package com.buddhanag.luminux.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.random.Random

/** How a live tile changes its picture. Zoom: the picture grows toward you as if coming out of the tile, then fades. */
enum class LiveTileStyle { Flip, Slide, Zoom }

/** Where a slide's next picture comes in from, as a unit vector of the tile's size. */
enum class SlideEdge(val x: Float, val y: Float) { Top(0f, -1f), Bottom(0f, 1f), Left(-1f, 0f), Right(1f, 0f) }

/** [style], or a random one when null (LiveTile.swift `advance`). */
fun pickLiveTileStyle(style: LiveTileStyle?, random: Random = Random.Default) = style ?: LiveTileStyle.entries.random(random)

/** A different side from [last] every time, so two slides in a row never look like a stutter. */
fun nextSlideEdge(last: SlideEdge, random: Random = Random.Default) = SlideEdge.entries.filter { it != last }.random(random)

private enum class Layer { Upcoming, Shown, Leaving }

/**
 * Tile content that changes with a flip, a slide or a zoom when the [LocalLiveTileScheduler] gives it a turn
 * (LiveTile.swift). [style] null picks one at random for every change. Only one tile on screen changes at a time, like
 * the original start screen. With Reduce Motion it never registers, so it never changes (faithful to iOS).
 */
@Composable
fun <T> LiveTile(
    items: List<T>,
    modifier: Modifier = Modifier,
    style: LiveTileStyle? = null,
    key: (T) -> Any = { it as Any },
    face: @Composable (T) -> Unit,
) {
    val scheduler = LocalLiveTileScheduler.current
    val reduceMotion = LocalReduceMotion.current
    val id = remember { Any() }
    var index by remember { mutableIntStateOf(0) }
    // The picture on its way out during a slide or zoom, drawn next to the incoming one until [progress] reaches 1.
    var leaving by remember { mutableStateOf<Int?>(null) }
    var progress by remember { mutableFloatStateOf(1f) }
    var turnStyle by remember { mutableStateOf(LiveTileStyle.Flip) }
    var edge by remember { mutableStateOf(SlideEdge.Bottom) }
    val flip = remember { Animatable(0f) }
    val currentItems by rememberUpdatedState(items)
    val currentStyle by rememberUpdatedState(style)
    val canChange = items.size > 1 && !reduceMotion

    LaunchedEffect(canChange) { scheduler.register(id, canChange) }
    DisposableEffect(Unit) { onDispose { scheduler.unregister(id) } }
    LaunchedEffect(Unit) {
        var change: Job? = null
        scheduler.turn.filterNotNull().filter { it.tile === id }.collect {
            val count = currentItems.size
            // A turn that comes while the last change still runs is skipped, so a slow change never stacks up.
            if (count <= 1 || change?.isActive == true) return@collect
            val next = (index + 1) % count
            val picked = pickLiveTileStyle(currentStyle)
            change = launch {
                turnStyle = picked
                if (picked == LiveTileStyle.Flip) {
                    flip.animateTo(90f, tween(180, easing = MetroMotion.EaseIn))
                    index = next
                    flip.snapTo(-90f)
                    flip.animateTo(0f, tween(240, easing = MetroMotion.EaseOut))
                    return@launch
                }
                // All in one snapshot, so no frame shows the new picture before the change starts.
                edge = nextSlideEdge(edge)
                leaving = index
                index = next
                progress = 0f
                // A slide comes in fast and settles slowly; a zoom grows steadily, fading at the end.
                val spec = if (picked == LiveTileStyle.Slide) tween<Float>(850, easing = SlideEasing) else tween(900, easing = MetroMotion.EaseIn)
                try {
                    animate(0f, 1f, animationSpec = spec) { value, _ -> progress = value }
                } finally {
                    progress = 1f
                    leaving = null
                }
            }
        }
    }
    // With live tiles off, each tile shows one picture and moves to the next whenever the hub comes back.
    LaunchedEffect(Unit) {
        scheduler.returns.drop(1).collect {
            val count = currentItems.size
            if (!scheduler.isAnimated && count > 1) index = (index + 1) % count
        }
    }

    if (items.isEmpty()) return
    val shown = index % items.size
    val out = leaving?.takeIf { it < items.size && it != shown }
    val upcoming = ((shown + 1) % items.size).takeIf { items.size > 1 && it != out }
    // Back to front, keyed by picture: the upcoming one becomes the shown one without being composed (and loaded) again.
    val layers = listOfNotNull(upcoming?.let { Layer.Upcoming to it }, Layer.Shown to shown, out?.let { Layer.Leaving to it })
    Box(
        modifier
            .fillMaxSize()
            .perspectiveRotation({ flip.value }, axisX = 1f, perspective = 0.5f)
            .clipToBounds(),
    ) {
        layers.forEach { (layer, i) ->
            key(key(items[i])) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Drawn unseen so the next picture has loaded before its turn; otherwise a slide could
                            // bring in an empty tile, which looked like the tile itself moving away.
                            if (layer == Layer.Upcoming) alpha = 0f else change(turnStyle, edge, progress, layer == Layer.Leaving)
                        }
                        // Faces take no touches, so only accessibility needs hiding from the extra layers.
                        .then(if (layer == Layer.Shown) Modifier else Modifier.clearAndSetSemantics {}),
                ) { face(items[i]) }
            }
        }
    }
}

private val SlideEasing = CubicBezierEasing(0.2f, 0.8f, 0.1f, 1f)

/**
 * One picture of a slide or zoom at progress [p]. Slide: the incoming picture pushes in from [edge] and the leaving one
 * goes out the opposite side, both solid. Zoom: the leaving picture grows and fades at the end, while the incoming one
 * fades in underneath it. A flip turns the whole tile instead, so it changes nothing here.
 */
private fun GraphicsLayerScope.change(style: LiveTileStyle, edge: SlideEdge, p: Float, isLeaving: Boolean) {
    when (style) {
        LiveTileStyle.Flip -> Unit
        LiveTileStyle.Slide -> {
            val shift = if (isLeaving) -p else 1 - p
            translationX = edge.x * size.width * shift
            translationY = edge.y * size.height * shift
        }
        LiveTileStyle.Zoom -> if (isLeaving) {
            scaleX = 1 + 0.3f * p
            scaleY = scaleX
            alpha = if (p < 0.65f) 1f else 1 - (p - 0.65f) / 0.35f
        } else {
            alpha = if (p < 0.6f) 0f else (p - 0.6f) / 0.4f
        }
    }
}
