package com.buddhanag.luminux.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull

enum class LiveTileStyle { Flip, Slide }

/**
 * Tile content that changes with a flip or an upward slide when the [LocalLiveTileScheduler] gives it a turn. Only one
 * tile on screen changes at a time, like the original start screen. With Reduce Motion it never registers, so it
 * never changes (faithful to iOS).
 */
@Composable
fun <T> LiveTile(
    items: List<T>,
    modifier: Modifier = Modifier,
    style: LiveTileStyle = LiveTileStyle.Flip,
    key: (T) -> Any = { it as Any },
    face: @Composable (T) -> Unit,
) {
    val scheduler = LocalLiveTileScheduler.current
    val reduceMotion = LocalReduceMotion.current
    val id = remember { Any() }
    var index by remember { mutableIntStateOf(0) }
    val flip = remember { Animatable(0f) }
    val currentItems by rememberUpdatedState(items)
    val canChange = items.size > 1 && !reduceMotion

    LaunchedEffect(canChange) { scheduler.register(id, canChange) }
    DisposableEffect(Unit) { onDispose { scheduler.unregister(id) } }
    LaunchedEffect(Unit) {
        scheduler.turn.filterNotNull().filter { it.tile === id }.collect {
            val count = currentItems.size
            if (count <= 1) return@collect
            val next = (index + 1) % count
            when (style) {
                LiveTileStyle.Slide -> index = next
                LiveTileStyle.Flip -> {
                    flip.animateTo(90f, tween(180, easing = MetroMotion.EaseIn))
                    index = next
                    flip.snapTo(-90f)
                    flip.animateTo(0f, tween(240, easing = MetroMotion.EaseOut))
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
    Box(
        modifier
            .fillMaxSize()
            .perspectiveRotation({ flip.value }, axisX = 1f, perspective = 0.5f)
            .clipToBounds(),
    ) {
        if (style == LiveTileStyle.Flip || reduceMotion) {
            key(key(items[shown])) { face(items[shown]) }
        } else {
            AnimatedContent(shown, transitionSpec = { push() }, label = "liveTile") { face(items[it % items.size]) }
        }
    }
}

/** SwiftUI `.push(from: .bottom)` on a `spring(0.6, 1)`: the new face pushes the old one up, both fading. */
private fun push(): ContentTransform {
    val slide = MetroMotion.spring<IntOffset>(0.6, 1f)
    val fade = MetroMotion.spring<Float>(0.6, 1f)
    return (slideInVertically(slide) { it } + fadeIn(fade)) togetherWith (slideOutVertically(slide) { -it } + fadeOut(fade))
}
