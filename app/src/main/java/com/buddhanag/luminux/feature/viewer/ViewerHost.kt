package com.buddhanag.luminux.feature.viewer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.toSize
import com.buddhanag.luminux.designsystem.FeatherState
import com.buddhanag.luminux.designsystem.LocalFeather
import com.buddhanag.luminux.feature.shell.Navigator

/**
 * The zoom between a grid tile and the viewer (LuminuxApp.swift:124-127, Navigator.swift:64-85). Tiles report where
 * they are on screen and the viewer reports where it fits the photo; [ZoomStandIn] flies between the two. Not a Compose
 * shared element: those take bounds from the lookahead pass, which lands lazy grid and pager items in the wrong place.
 */
@Stable
class ViewerZoom internal constructor(private val navigator: Navigator) {
    /** The photo the viewer shows, set until the viewer has finished closing; it's where the viewer closes onto. */
    internal var currentId by mutableStateOf<Long?>(null)
    /**
     * The tile hidden under the viewer: from the viewer's first frame (before it reports its page) until the closing
     * zoom has landed on it, so the flying photo never passes over a copy of itself.
     */
    internal val coveredId: Long? get() = currentId ?: navigator.viewer?.startId
    /** On-screen tiles in root coordinates. Plain maps: read only while zooming, so updates needn't recompose. */
    internal val tiles = HashMap<Long, Rect>()
    /** The current photo's rect in the viewer, root coordinates, including a drag to dismiss. */
    internal var page: Rect? = null
}

internal val LocalViewerZoom = staticCompositionLocalOf<ViewerZoom?> { null }

/**
 * Marks a grid tile as where the viewer zooms out of and back into for photo [id]. Put it on the tile's image after
 * its size modifiers. The viewer closes onto the tile of the photo it ends on, so that tile must be on screen; with no
 * tile on screen (or outside [ViewerHost]) the viewer fades instead. Tiles on a page covered by another page don't count.
 */
fun Modifier.zoomSource(id: Long): Modifier = composed {
    val zoom = LocalViewerZoom.current ?: return@composed Modifier
    val covered = LocalFeather.current.state == FeatherState.Above
    DisposableEffect(zoom, id, covered) {
        if (covered) zoom.tiles.remove(id)
        onDispose { zoom.tiles.remove(id) }
    }
    Modifier
        .onGloballyPositioned { if (!covered) zoom.tiles[id] = Rect(it.positionInRoot(), it.size.toSize()) }
        .graphicsLayer { alpha = if (zoom.coveredId == id) 0f else 1f }
}

/** Hosts [content] (the page stack) with the full-screen viewer over it whenever `navigator.viewer` is set. */
@Composable
fun ViewerHost(navigator: Navigator, content: @Composable () -> Unit) {
    val zoom = remember(navigator) { ViewerZoom(navigator) }
    CompositionLocalProvider(LocalViewerZoom provides zoom) {
        Box(Modifier.fillMaxSize()) {
            content()
            // The viewer animates itself (ViewerScreen's zoom); this only keeps a closing viewer composed until it's done.
            // Full size with no SizeTransform: by default it grows from and shrinks to the empty state's 0×0 and clips to
            // that, cropping the viewer to a box in the top-left corner.
            AnimatedContent(
                navigator.viewer,
                Modifier.fillMaxSize(),
                transitionSpec = { (EnterTransition.None togetherWith ExitTransition.None).using(null) },
                label = "viewer",
            ) { request ->
                if (request != null) ViewerScreen(request, navigator, zoom, this)
            }
        }
    }
}
