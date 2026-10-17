package com.buddhanag.luminux.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Thin line stand-ins for the SF Symbols the iOS app uses, on a 24 grid. Tint them with a ColorFilter. */
object MetroIcons {
    val check = icon("checkmark", 2f) { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) }
    val lock = icon("lock.fill", 2f) {
        moveTo(8f, 11f); lineTo(8f, 7.5f); arcTo(4f, 4f, 0f, false, true, 16f, 7.5f); lineTo(16f, 11f)
        moveTo(5f, 11f); lineTo(19f, 11f); lineTo(19f, 21f); lineTo(5f, 21f); close()
    }
    val eyedropper = icon("eyedropper") {
        moveTo(14f, 6f); lineTo(18f, 10f)
        moveTo(16f, 8f); lineTo(6f, 18f); lineTo(4f, 20f)
        moveTo(15f, 5f); lineTo(17f, 3f); arcTo(2f, 2f, 0f, false, true, 21f, 7f); lineTo(19f, 9f)
    }
    val arrowRight = icon("arrow.right") { moveTo(4f, 12f); lineTo(20f, 12f); moveTo(14f, 6f); lineTo(20f, 12f); lineTo(14f, 18f) }
    /** `square.stack.3d.up`. */
    val stack = icon("square.stack.3d.up") {
        moveTo(12f, 3f); lineTo(21f, 7.5f); lineTo(12f, 12f); lineTo(3f, 7.5f); close()
        moveTo(3f, 12f); lineTo(12f, 16.5f); lineTo(21f, 12f)
        moveTo(3f, 16.5f); lineTo(12f, 21f); lineTo(21f, 16.5f)
    }
    /** `lock`, the open-line glyph. */
    val lockGlyph = icon("lock") {
        moveTo(8f, 10f); lineTo(8f, 7f); arcTo(4f, 4f, 0f, false, true, 16f, 7f); lineTo(16f, 10f)
        moveTo(5.5f, 10f); lineTo(18.5f, 10f); lineTo(18.5f, 21f); lineTo(5.5f, 21f); close()
    }

    private fun icon(name: String, width: Float = 1.2f, block: PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .path(
                stroke = SolidColor(Color.White), strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block,
            )
            .build()
}
