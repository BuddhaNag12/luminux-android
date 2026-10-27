package com.buddhanag.luminux.feature.viewer.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Line stand-ins for the editor's SF Symbols, on ViewerIcons' 24 grid. Tint them with a ColorFilter. */
internal object EditorIcons {
    val eye = icon("eye") { eyePath() }
    val eyeSlash = icon("eye.slash") { eyePath(); moveTo(4f, 4f); lineTo(20f, 20f) }
    val flip = icon("arrow.left.and.right.righttriangle.left.righttriangle.right") {
        moveTo(12f, 3f); lineTo(12f, 21f)
        moveTo(9.5f, 6f); lineTo(3f, 18f); lineTo(9.5f, 18f); close()
        moveTo(14.5f, 6f); lineTo(21f, 18f); lineTo(14.5f, 18f); close()
    }
    val level = icon("level") {
        moveTo(2.5f, 8.5f); lineTo(21.5f, 8.5f); lineTo(21.5f, 15.5f); lineTo(2.5f, 15.5f); close()
        moveTo(9.5f, 10.5f); lineTo(14.5f, 10.5f); lineTo(14.5f, 13.5f); lineTo(9.5f, 13.5f); close()
    }
    val landscape = icon("rectangle") { moveTo(3f, 6.5f); lineTo(21f, 6.5f); lineTo(21f, 17.5f); lineTo(3f, 17.5f); close() }
    val portrait = icon("rectangle.portrait") { moveTo(6.5f, 3f); lineTo(17.5f, 3f); lineTo(17.5f, 21f); lineTo(6.5f, 21f); close() }
    val reset = icon("arrow.counterclockwise") {
        moveTo(5.5f, 8f); arcTo(8f, 8f, 0f, true, true, 4f, 12f)
        moveTo(5.5f, 3.5f); lineTo(5.5f, 8f); lineTo(10f, 8f)
    }

    private fun PathBuilder.eyePath() {
        moveTo(2f, 12f); quadTo(12f, 2f, 22f, 12f); quadTo(12f, 22f, 2f, 12f); close()
        moveTo(15f, 12f); arcTo(3f, 3f, 0f, true, true, 9f, 12f); arcTo(3f, 3f, 0f, true, true, 15f, 12f)
    }

    private fun icon(name: String, block: PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .path(
                stroke = SolidColor(Color.White), strokeLineWidth = 1.2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block,
            )
            .build()
}
