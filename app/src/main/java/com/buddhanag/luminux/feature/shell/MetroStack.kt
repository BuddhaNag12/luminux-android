package com.buddhanag.luminux.feature.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import com.buddhanag.luminux.data.PageTransition
import com.buddhanag.luminux.designsystem.Feather
import com.buddhanag.luminux.designsystem.FeatherPage
import com.buddhanag.luminux.designsystem.FeatherScope
import com.buddhanag.luminux.designsystem.FeatherState
import com.buddhanag.luminux.designsystem.FeatherStyle
import com.buddhanag.luminux.designsystem.LocalFeatherFromBack
import com.buddhanag.luminux.designsystem.LocalFeatherPage
import com.buddhanag.luminux.designsystem.LocalFeatherScreen
import com.buddhanag.luminux.designsystem.LocalFeatherStyle
import com.buddhanag.luminux.designsystem.LocalReduceMotion
import com.buddhanag.luminux.designsystem.MetroMotion
import com.buddhanag.luminux.designsystem.motionDelay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * MetroStack (Navigator.swift): every page stays composed, like the iOS stack. Pages' items rise in and out in staggered
 * waves; covered pages fold their items away (or scatter them, with the zoom [transition]) and fade, and come back when
 * the page above leaves. Back is the system back gesture (a custom edge strip would fight gesture navigation).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MetroStack(
    navigator: Navigator,
    transition: PageTransition,
    root: @Composable () -> Unit,
    destination: @Composable (Route) -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    BackHandler(enabled = navigator.path.isNotEmpty()) { scope.launch { navigator.pop(reduceMotion) } }
    val zooms = transition == PageTransition.ZOOM && !reduceMotion
    val count = navigator.path.size
    // Pages deeper than this wait, unbuilt, for the page they opened from to scatter away first (zoom only): building
    // one (a photo grid, say) while the tiles flew out dropped frames on iOS.
    var enteredDepth by remember { mutableIntStateOf(count) }
    LaunchedEffect(count, zooms) {
        if (zooms && count > enteredDepth) motionDelay(ZOOM_ENTRY_DELAY_MS)
        enteredDepth = count
    }
    var screen by remember { mutableStateOf(Size.Zero) }
    // Room above every page's first line where there's no status bar on top (a foldable's open screen sideways). Ignoring
    // visibility, so the viewer hiding the bars doesn't shift the stack.
    // shortcut: padding, so a page background doesn't reach into this strip (iOS adds it to the safe area); upgrade by
    // handing pages the extra inset if a device with no top status bar shows a black band.
    val statusBar = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()

    CompositionLocalProvider(
        LocalFeatherStyle provides if (zooms) FeatherStyle.Zoom else FeatherStyle.Turnstile,
        LocalFeatherScreen provides screen,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = (MIN_TOP_INSET - statusBar).coerceAtLeast(0.dp))
                .onSizeChanged { screen = it.toSize() },
        ) {
            StackPage(navigator, depth = 0, zooms) { root() }
            navigator.path.forEachIndexed { index, route ->
                if (!zooms || index + 1 <= enteredDepth) {
                    key(index, route) { StackPage(navigator, depth = index + 1, zooms) { destination(route) } }
                }
            }
        }
    }
}

/** Like Windows 10 Mobile opening an app: the covered page's items fly out, then the new page turns in. */
private const val ZOOM_ENTRY_DELAY_MS = 400L
private val MIN_TOP_INSET = 20.dp
/** A page closing with the zoom shrinks to this. */
private const val ZOOMED_IN = 0.9f

@Composable
private fun StackPage(navigator: Navigator, depth: Int, zooms: Boolean, content: @Composable () -> Unit) {
    val top = navigator.path.size
    val isTop = depth == top
    val isUnder = depth == top - 1
    val isLeaving = isTop && navigator.isLeaving
    val itemState = when {
        // The first page waits under the launch splash and opens as it goes.
        isLeaving || (depth == 0 && LocalLaunchSplashShowing.current) -> FeatherState.Below
        isTop || (isUnder && navigator.isLeaving) -> FeatherState.Shown
        else -> FeatherState.Above
    }
    val isCovered = itemState == FeatherState.Above
    // Leaving and covered pages keep their background until their items have moved away.
    val opacity by animateFloatAsState(
        targetValue = if (isTop) (if (isLeaving) 0f else 1f) else if (isUnder && navigator.isLeaving) 1f else 0f,
        animationSpec = when {
            isLeaving -> tween(120, ((Feather.exitTime - 0.12) * 1000).roundToInt(), MetroMotion.EaseIn)
            // A zooming page's background goes as its items finish scattering, leaving a moment of dark, as on Windows.
            isCovered -> tween(100, if (zooms) 300 else (Feather.COVER_FADE_DELAY * 1000).roundToInt(), MetroMotion.EaseIn)
            else -> MetroMotion.standard()
        },
        label = "stackPage",
    )
    // Only a popped page shrinks; a pushed one isn't built until the page under it has scattered.
    val scale by animateFloatAsState(
        if (zooms && isLeaving) ZOOMED_IN else 1f, tween(400, easing = Feather.Decelerate), label = "stackScale",
    )
    val interactive = isTop && !isLeaving
    val page = remember { FeatherPage() }
    CompositionLocalProvider(LocalFeatherFromBack provides (depth == 0), LocalFeatherPage provides page) {
        FeatherScope(
            Modifier
                .fillMaxSize()
                .zIndex(depth.toFloat())
                .graphicsLayer {
                    alpha = opacity
                    scaleX = scale
                    scaleY = scale
                }
                // Always hit, so touches never fall through to a covered page; swallowed while not on top.
                .pointerInput(interactive) {
                    if (!interactive) awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
                .then(if (isTop) Modifier else Modifier.clearAndSetSemantics {})
                // The space scattering items fly out from the centre of.
                .onPlaced { page.coordinates = it },
            resting = itemState,
        ) { content() }
    }
}
