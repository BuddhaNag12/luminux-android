package com.buddhanag.luminux.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.hypot
import kotlin.math.max

/** Calibrated in the gallery: with +1 a positive angle about Y pushes the RIGHT edge away and about X the TOP edge, as on iOS. */
const val ROTATION_SIGN = 1f

/**
 * Compose camera distance for a perspective distance in px. The RenderNode camera sits `cameraDistance × 72` px away,
 * with no density factor: measured in the gallery (a 100 dp tile at 12° with the camera 166.7 dp away has edges
 * 313 px and 227 px tall on a 2.625× screen, which fits a 171 px camera with `px / (72 × density)`).
 */
fun cameraDistanceFor(distancePx: Float) = distancePx / 72f

/**
 * iOS `PerspectiveRotation`: a 3D turn with `m34 = -perspective / max(w, h)`, so the camera sits
 * `max(w, h) / perspective` away, or at [fixedDistancePx]. Sets nothing at 0°, so the layer is an exact identity.
 * The angle is read inside the layer, so animating it only redraws.
 */
fun Modifier.perspectiveRotation(
    degrees: () -> Float,
    axisX: Float = 0f,
    axisY: Float = 0f,
    origin: GraphicsLayerScope.() -> TransformOrigin = { TransformOrigin.Center },
    perspective: Float = 1f,
    fixedDistancePx: Float? = null,
) = graphicsLayer {
    val angle = degrees()
    if (angle == 0f || (axisX == 0f && axisY == 0f)) return@graphicsLayer
    // CATransform3DMakeRotation normalizes the axis. Splitting into Euler angles is close enough up to ~12° for
    // diagonal axes; every larger turn in the app is about a single axis, where it is exact.
    val length = hypot(axisX, axisY)
    rotationX = ROTATION_SIGN * angle * axisX / length
    rotationY = ROTATION_SIGN * angle * axisY / length
    cameraDistance = cameraDistanceFor(fixedDistancePx ?: (max(size.width, size.height, 1f) / perspective))
    transformOrigin = origin()
}

private fun max(a: Float, b: Float, c: Float) = max(max(a, b), c)
