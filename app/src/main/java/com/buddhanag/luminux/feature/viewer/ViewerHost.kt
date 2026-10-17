package com.buddhanag.luminux.feature.viewer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import com.buddhanag.luminux.designsystem.MetroMotion
import com.buddhanag.luminux.feature.shell.Navigator

/** The zoom between a grid tile and the viewer (LuminuxApp.swift:124-127, Navigator.swift:64-85). */
@Stable
class ViewerZoom internal constructor(internal val scope: SharedTransitionScope, private val navigator: Navigator) {
    /** The photo the viewer shows; its tile hides while the viewer is open and is where the viewer closes onto. */
    internal var currentId by mutableStateOf<Long?>(null)
    internal val coveredId: Long? get() = if (navigator.viewer != null) currentId else null
}

internal val LocalViewerZoom = staticCompositionLocalOf<ViewerZoom?> { null }

internal fun zoomKey(id: Long) = "asset-$id"

/**
 * Marks a grid tile as where the viewer zooms out of and back into for photo [id]. Put it on the tile's image after
 * its size modifiers. The viewer closes onto the tile of the photo it ends on, so that tile must be on screen (scroll
 * it into view while `navigator.viewer` is set). Outside [ViewerHost], or with no tile on screen, the viewer fades.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.zoomSource(id: Long): Modifier = composed {
    val zoom = LocalViewerZoom.current ?: return@composed Modifier
    with(zoom.scope) {
        Modifier.sharedElementWithCallerManagedVisibility(rememberSharedContentState(zoomKey(id)), visible = zoom.coveredId != id)
    }
}

/** Hosts [content] (the page stack) with the full-screen viewer over it whenever `navigator.viewer` is set. */
@Composable
fun ViewerHost(navigator: Navigator, content: @Composable () -> Unit) {
    SharedTransitionLayout {
        val zoom = remember(this, navigator) { ViewerZoom(this, navigator) }
        CompositionLocalProvider(LocalViewerZoom provides zoom) {
            content()
            AnimatedContent(
                navigator.viewer,
                transitionSpec = { fadeIn(MetroMotion.fade()) togetherWith fadeOut(MetroMotion.fade()) },
                label = "viewer",
            ) { request ->
                if (request != null) ViewerScreen(request, navigator, zoom, this)
            }
        }
    }
}
