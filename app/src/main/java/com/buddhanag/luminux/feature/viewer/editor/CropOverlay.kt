package com.buddhanag.luminux.feature.viewer.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.buddhanag.luminux.data.Corner
import com.buddhanag.luminux.data.CropMath
import com.buddhanag.luminux.data.NormRect
import kotlin.math.roundToInt

/**
 * Dims outside the crop, draws thirds, and lets it be moved (drag inside) or resized (drag a corner). [imageRect] is
 * where the uncropped preview sits, in px; [imageW]×[imageH] is its pixel size, for keeping the pixel [ratio].
 */
@Composable
internal fun CropOverlay(crop: NormRect, onCrop: (NormRect) -> Unit, ratio: Double?, imageRect: Rect, imageW: Int, imageH: Int) {
    val current by rememberUpdatedState(crop)
    val update by rememberUpdatedState(onCrop)
    val lockedRatio by rememberUpdatedState(ratio)
    val frame = Rect(
        imageRect.left + (crop.minX * imageRect.width).toFloat(),
        imageRect.top + (crop.minY * imageRect.height).toFloat(),
        imageRect.left + (crop.maxX * imageRect.width).toFloat(),
        imageRect.top + (crop.maxY * imageRect.height).toFloat(),
    )
    val density = LocalDensity.current
    val handle = with(density) { 44.dp.toPx() }

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val outside = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(imageRect)
                addRect(frame)
            }
            drawPath(outside, Color.Black.copy(alpha = 0.55f))
            // Thirds, for lining things up while cropping and straightening.
            for (third in listOf(1f / 3, 2f / 3)) {
                val x = frame.left + frame.width * third
                val y = frame.top + frame.height * third
                drawLine(Color.White.copy(alpha = 0.35f), Offset(x, frame.top), Offset(x, frame.bottom), 0.5.dp.toPx())
                drawLine(Color.White.copy(alpha = 0.35f), Offset(frame.left, y), Offset(frame.right, y), 0.5.dp.toPx())
            }
            drawRect(Color.White, frame.topLeft, frame.size, style = Stroke(1.dp.toPx()))
        }
        // Drags work from the rect at drag start, so the crop never drifts from the finger.
        Box(
            Modifier
                .offset { IntOffset(frame.left.roundToInt(), frame.top.roundToInt()) }
                .size(with(density) { frame.width.toDp() }, with(density) { frame.height.toDp() })
                .semantics { contentDescription = "Crop area" }
                .pointerInput(imageRect) {
                    var start = current
                    var total = Offset.Zero
                    detectDragGestures(onDragStart = { start = current; total = Offset.Zero }) { change, drag ->
                        change.consume()
                        total += drag
                        update(CropMath.move(start, total.x / imageRect.width.toDouble(), total.y / imageRect.height.toDouble()))
                    }
                },
        )
        Corner.entries.forEach { corner ->
            val center = Offset(frame.left + corner.x * frame.width, frame.top + corner.y * frame.height)
            Canvas(
                Modifier
                    .offset { IntOffset((center.x - handle / 2).roundToInt(), (center.y - handle / 2).roundToInt()) }
                    .size(44.dp)
                    .pointerInput(corner, imageRect) {
                        var start = current
                        var total = Offset.Zero
                        detectDragGestures(onDragStart = { start = current; total = Offset.Zero }) { change, drag ->
                            change.consume()
                            total += drag
                            val startX = start.minX + corner.x * start.w
                            val startY = start.minY + corner.y * start.h
                            update(CropMath.drag(
                                start, corner,
                                startX + total.x / imageRect.width, startY + total.y / imageRect.height,
                                lockedRatio, imageW, imageH,
                            ))
                        }
                    },
            ) {
                // An L along the two edges that meet at this corner.
                val arm = 18.dp.toPx()
                val mid = size.width / 2
                val h = if (corner.x == 0) 1 else -1
                val v = if (corner.y == 0) 1 else -1
                val l = Path().apply {
                    moveTo(mid + h * arm, mid)
                    lineTo(mid, mid)
                    lineTo(mid, mid + v * arm)
                }
                drawPath(l, Color.White, style = Stroke(4.dp.toPx(), cap = StrokeCap.Butt))
            }
        }
    }
}
