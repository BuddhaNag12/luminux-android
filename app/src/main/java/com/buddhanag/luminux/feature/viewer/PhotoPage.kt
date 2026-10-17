package com.buddhanag.luminux.feature.viewer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import coil3.request.ImageRequest
import com.buddhanag.luminux.data.GridZoom
import com.buddhanag.luminux.data.MediaItem
import com.buddhanag.luminux.data.Thumbnails
import com.buddhanag.luminux.designsystem.MetroMotion
import kotlinx.coroutines.flow.distinctUntilChanged
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import kotlin.math.min

/** Zoomed is anything past 1.01× (ZoomableImageView.swift:175); on the 1…4× range that's this zoom fraction. */
private const val ZOOMED_FRACTION = 0.01f / 3

/** At 1× → 2.5× centered on the tap; zoomed → back to 1×. */
private val doubleClickToZoom = DoubleClickToZoomListener { state, centroid ->
    if ((state.zoomFraction ?: 0f) > ZOOMED_FRACTION) state.resetZoom() else state.zoomTo(2.5f, centroid)
}

/**
 * A photo with pinch (1–4×) and double-tap zoom; Telephoto subsamples the original, so zooming stays sharp. A page
 * that isn't current, or [resetsZoom], snaps back to 1×. [quarterTurns] turns the preview counterclockwise for the
 * viewer's quick rotate, scaled to keep fitting.
 */
@Composable
internal fun PhotoPage(
    item: MediaItem,
    isCurrent: Boolean,
    resetsZoom: Boolean,
    quarterTurns: Int,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onZoomChange: (Boolean) -> Unit,
    onCopySubject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val zoomable = rememberZoomableState(ZoomSpec(maxZoomFactor = 4f))
    val state = rememberZoomableImageState(zoomable)
    LaunchedEffect(isCurrent, resetsZoom) {
        if (!isCurrent || resetsZoom) zoomable.resetZoom(snap())
    }
    val zoomChange by rememberUpdatedState(onZoomChange)
    LaunchedEffect(zoomable) {
        snapshotFlow { (zoomable.zoomFraction ?: 0f) > ZOOMED_FRACTION }.distinctUntilChanged().collect { zoomChange(it) }
    }

    // The grid's thumbnail shows until the full image arrives, so the zoom-in never starts blank.
    val tileSide = Thumbnails.requestSide(LocalWindowInfo.current.containerSize.width / GridZoom.DEFAULT_COLUMNS)
    val request = remember(item.thumbKey, tileSide) {
        ImageRequest.Builder(context)
            .data(item.contentUri)
            .placeholderMemoryCacheKey(Thumbnails.memoryKey(item, tileSide))
            .build()
    }
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = item.width.coerceAtLeast(1).toFloat()
        val h = item.height.coerceAtLeast(1).toFloat()
        val boxW = with(density) { maxWidth.toPx() }
        val boxH = with(density) { maxHeight.toPx() }
        val turnedFit = min(boxW / h, boxH / w) / min(boxW / w, boxH / h)
        val turn by animateFloatAsState(-90f * quarterTurns, MetroMotion.standard(), label = "turn")
        val scale by animateFloatAsState(if (quarterTurns % 2 == 1) turnedFit else 1f, MetroMotion.standard(), label = "turnScale")
        ZoomableAsyncImage(
            model = request,
            contentDescription = "Photo",
            modifier = modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = turn
                    scaleX = scale
                    scaleY = scale
                }
                // TalkBack can't touch and hold to lift, so it gets the copy directly.
                .semantics { customActions = listOf(CustomAccessibilityAction("copy subject") { onCopySubject(); true }) },
            state = state,
            contentScale = ContentScale.Fit,
            onClick = { onTap() },
            onLongClick = { onLongPress() },
            onDoubleClick = doubleClickToZoom,
        )
    }
}
