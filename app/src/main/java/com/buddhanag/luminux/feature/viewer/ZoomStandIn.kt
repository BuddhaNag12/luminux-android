package com.buddhanag.luminux.feature.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.layout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.buddhanag.luminux.data.MediaItem
import com.buddhanag.luminux.data.MediaThumb
import com.buddhanag.luminux.data.Thumbnails
import com.buddhanag.luminux.feature.grid.GridZoom
import kotlin.math.roundToInt

/** The rect a page of [width] × [height] px fits [item] into, centred, like ContentScale.Fit. */
internal fun fittedRect(item: MediaItem, width: Float, height: Float): Rect {
    val aspect = item.width.coerceAtLeast(1).toFloat() / item.height.coerceAtLeast(1)
    val size = if (width / height < aspect) Size(width, width / aspect) else Size(height * aspect, height)
    return Rect(Offset((width - size.width) / 2, (height - size.height) / 2), size)
}

/** An invisible box over the page where the photo sits; reports it so the closing zoom starts from the photo itself. */
@Composable
internal fun ZoomTarget(item: MediaItem, onPlaced: (Rect) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rect = fittedRect(item, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        Box(
            Modifier
                .align(Alignment.TopStart)
                .fixedRect(rect)
                // Corners mapped through the dismiss drag's scale and offset, so a dragged photo closes from where it
                // was let go. Not boundsInRoot: that clips through the viewer's layers and came back empty.
                .onGloballyPositioned {
                    onPlaced(Rect(it.localToRoot(Offset.Zero), it.localToRoot(Offset(it.size.width.toFloat(), it.size.height.toFloat()))))
                },
        )
    }
}

/**
 * The photo flying between its grid tile ([from], root coordinates) and the viewer ([to]) as [fraction] goes 0 → 1.
 * Cropped to fill, so it starts as the square tile and ends as the whole photo. With no tile on screen it fades in place.
 */
@Composable
internal fun ZoomStandIn(item: MediaItem, from: Rect?, to: Rect?, fraction: Float) {
    val context = LocalContext.current
    val tileSide = Thumbnails.requestSide(LocalWindowInfo.current.containerSize.width / GridZoom.DEFAULT_COLUMNS)
    val request = remember(item.thumbKey, tileSide) {
        ImageRequest.Builder(context)
            .data(MediaThumb(item.contentUri, STAND_IN_SIDE))
            // The grid's thumbnail is in memory already, so the zoom never starts blank.
            .placeholderMemoryCacheKey(Thumbnails.memoryKey(item, tileSide))
            .build()
    }
    val origin = remember { OffsetHolder() }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { origin.value = it.positionInRoot() }) {
        val page = to ?: fittedRect(item, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()).translate(origin.value)
        val rect = (if (from == null) page else lerp(from, page, fraction)).translate(-origin.value)
        AsyncImage(
            request, contentDescription = null,
            Modifier
                .align(Alignment.TopStart)
                .fixedRect(rect)
                .graphicsLayer { alpha = if (from == null) fraction else 1f },
            contentScale = ContentScale.Crop,
        )
    }
}

/**
 * Lays this out at exactly [rect] (px) within a parent with bounded constraints. The node itself fills the parent, so
 * the content sits inside it: `boundsInRoot` clips to the node, and a node only the rect's size would clip it away.
 */
private fun Modifier.fixedRect(rect: Rect) = layout { measurable, constraints ->
    val placeable = measurable.measure(
        Constraints.fixed(rect.width.roundToInt().coerceAtLeast(0), rect.height.roundToInt().coerceAtLeast(0)),
    )
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(rect.left.roundToInt(), rect.top.roundToInt()) }
}

/** Where the stand-in's box sits in the root; plain, as it only changes with the window. */
private class OffsetHolder { var value = Offset.Zero }

/** Matches the video poster's size, so photos and videos share one cached request. */
private const val STAND_IN_SIDE = 1536
