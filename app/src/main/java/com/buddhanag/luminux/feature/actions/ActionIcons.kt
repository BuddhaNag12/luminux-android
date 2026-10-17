package com.buddhanag.luminux.feature.actions

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The app bar glyphs of the grid screens, drawn like 02's `MetroIcons` (thin lines on a 24 grid, tinted by the bar).
 * shortcut: kept here so the parallel specs don't all edit MetroIcons; fold into MetroIcons after the 05-08 merge.
 */
object ActionIcons {
    /** `checklist`. */
    val select = icon("checklist") {
        moveTo(3.5f, 6.5f); lineTo(5f, 8f); lineTo(8f, 5f); moveTo(11f, 6.5f); lineTo(21f, 6.5f)
        moveTo(3.5f, 12.5f); lineTo(5f, 14f); lineTo(8f, 11f); moveTo(11f, 12.5f); lineTo(21f, 12.5f)
        moveTo(3.5f, 18.5f); lineTo(5f, 20f); lineTo(8f, 17f); moveTo(11f, 18.5f); lineTo(21f, 18.5f)
    }
    val add = icon("plus", 1.6f) { moveTo(12f, 4f); lineTo(12f, 20f); moveTo(4f, 12f); lineTo(20f, 12f) }
    /** `square.and.arrow.up`. */
    val share = icon("square.and.arrow.up") {
        moveTo(12f, 3f); lineTo(12f, 15f); moveTo(8f, 7f); lineTo(12f, 3f); lineTo(16f, 7f)
        moveTo(9f, 10f); lineTo(5f, 10f); lineTo(5f, 21f); lineTo(19f, 21f); lineTo(19f, 10f); lineTo(15f, 10f)
    }
    val favorite = icon("heart") {
        moveTo(12f, 20f)
        curveTo(5f, 15f, 3f, 11.5f, 3f, 8.5f)
        arcTo(4.5f, 4.5f, 0f, false, true, 12f, 6.5f)
        arcTo(4.5f, 4.5f, 0f, false, true, 21f, 8.5f)
        curveTo(21f, 11.5f, 19f, 15f, 12f, 20f)
        close()
    }
    val delete = icon("trash") {
        moveTo(4f, 6f); lineTo(20f, 6f); moveTo(9f, 6f); lineTo(9f, 3.5f); lineTo(15f, 3.5f); lineTo(15f, 6f)
        moveTo(6f, 6f); lineTo(7f, 21f); lineTo(17f, 21f); lineTo(18f, 6f)
        moveTo(10f, 10f); lineTo(10f, 17f); moveTo(14f, 10f); lineTo(14f, 17f)
    }

    private fun icon(name: String, width: Float = 1.2f, block: PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .path(
                stroke = SolidColor(Color.White), strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block,
            )
            .build()
}
