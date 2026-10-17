package com.buddhanag.luminux.designsystem

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Press feedback for tiles: pressing near an edge tips that edge away, pressing the centre pushes the tile in. */
data class Tilt(val angle: Double, val axisX: Double, val axisY: Double, val scale: Double) {
    companion object {
        val Rest = Tilt(0.0, 0.0, 0.0, 1.0)

        fun pressed(point: Offset?, size: Size, maxAngle: Double = 12.0, depth: Double = 0.04): Tilt {
            if (point == null || size.width <= 0f || size.height <= 0f) return Tilt(0.0, 0.0, 0.0, 1 - depth)
            val dx = ((point.x - size.width / 2.0) / (size.width / 2.0)).coerceIn(-1.0, 1.0)
            val dy = ((point.y - size.height / 2.0) / (size.height / 2.0)).coerceIn(-1.0, 1.0)
            val reach = min(hypot(dx, dy), 1.0)
            // Big tiles tip less, so the far edge doesn't swing too far.
            val sizeFactor = min(1.0, 180.0 / max(size.width, size.height))
            return Tilt(maxAngle * reach * sizeFactor, -dy, dx, 1 - depth * (1 - reach * 0.5))
        }
    }
}
