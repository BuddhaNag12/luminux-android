package com.buddhanag.luminux.feature.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.zIndex
import com.buddhanag.luminux.designsystem.Feather
import com.buddhanag.luminux.designsystem.FeatherScope
import com.buddhanag.luminux.designsystem.FeatherState
import com.buddhanag.luminux.designsystem.LocalReduceMotion
import com.buddhanag.luminux.designsystem.MetroMotion
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * MetroStack (Navigator.swift): every page stays composed, like the iOS stack. Pages' items rise in and out in staggered
 * waves; covered pages fold their items away and fade, and come back when the page above leaves. Back is the system
 * back gesture (a custom edge strip would fight gesture navigation).
 */
@Composable
fun MetroStack(navigator: Navigator, root: @Composable () -> Unit, destination: @Composable (Route) -> Unit) {
    val reduceMotion = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    BackHandler(enabled = navigator.path.isNotEmpty()) { scope.launch { navigator.pop(reduceMotion) } }

    Box(Modifier.fillMaxSize()) {
        StackPage(navigator, depth = 0) { root() }
        navigator.path.forEachIndexed { index, route ->
            key(index, route) { StackPage(navigator, depth = index + 1) { destination(route) } }
        }
    }
}

@Composable
private fun StackPage(navigator: Navigator, depth: Int, content: @Composable () -> Unit) {
    val top = navigator.path.size
    val isTop = depth == top
    val isUnder = depth == top - 1
    val isLeaving = isTop && navigator.isLeaving
    val itemState = when {
        isLeaving -> FeatherState.Below
        isTop || (isUnder && navigator.isLeaving) -> FeatherState.Shown
        else -> FeatherState.Above
    }
    val isCovered = itemState == FeatherState.Above
    // Leaving and covered pages keep their background until their items have moved away.
    val opacity by animateFloatAsState(
        targetValue = if (isTop) (if (isLeaving) 0f else 1f) else if (isUnder && navigator.isLeaving) 1f else 0f,
        animationSpec = when {
            isLeaving -> tween(120, ((Feather.exitTime - 0.12) * 1000).roundToInt(), MetroMotion.EaseIn)
            isCovered -> tween(100, (Feather.COVER_FADE_DELAY * 1000).roundToInt(), MetroMotion.EaseIn)
            else -> MetroMotion.standard()
        },
        label = "stackPage",
    )
    val interactive = isTop && !isLeaving
    FeatherScope(
        Modifier
            .fillMaxSize()
            .zIndex(depth.toFloat())
            .graphicsLayer { alpha = opacity }
            // Always hit, so touches never fall through to a covered page; swallowed while not on top.
            .pointerInput(interactive) {
                if (!interactive) awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
            .then(if (isTop) Modifier else Modifier.clearAndSetSemantics {}),
        resting = itemState,
    ) { content() }
}
