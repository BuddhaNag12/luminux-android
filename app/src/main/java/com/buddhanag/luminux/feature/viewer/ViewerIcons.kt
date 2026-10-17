package com.buddhanag.luminux.feature.viewer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Line stand-ins for the viewer's SF Symbols, on the same 24 grid as MetroIcons. Tint them with a ColorFilter. */
internal object ViewerIcons {
    val play = icon("play.fill", fill = true) { moveTo(7f, 4.5f); lineTo(19.5f, 12f); lineTo(7f, 19.5f); close() }
    val pause = icon("pause.fill", fill = true) {
        moveTo(6.5f, 4.5f); lineTo(10f, 4.5f); lineTo(10f, 19.5f); lineTo(6.5f, 19.5f); close()
        moveTo(14f, 4.5f); lineTo(17.5f, 4.5f); lineTo(17.5f, 19.5f); lineTo(14f, 19.5f); close()
    }
    val share = icon("square.and.arrow.up") {
        moveTo(12f, 3f); lineTo(12f, 15f); moveTo(8f, 7f); lineTo(12f, 3f); lineTo(16f, 7f)
        moveTo(8.5f, 10f); lineTo(5.5f, 10f); lineTo(5.5f, 21f); lineTo(18.5f, 21f); lineTo(18.5f, 10f); lineTo(15.5f, 10f)
    }
    val heart = icon("heart") { heartPath() }
    val heartFill = icon("heart.fill", fill = true) { heartPath() }
    val edit = icon("crop.rotate") {
        moveTo(6f, 2.5f); lineTo(6f, 18f); lineTo(21.5f, 18f)
        moveTo(2.5f, 6f); lineTo(18f, 6f); lineTo(18f, 14.5f)
    }
    val trash = icon("trash") {
        moveTo(4f, 6f); lineTo(20f, 6f); moveTo(9f, 6f); lineTo(9f, 3.5f); lineTo(15f, 3.5f); lineTo(15f, 6f)
        moveTo(6f, 6f); lineTo(7f, 21f); lineTo(17f, 21f); lineTo(18f, 6f)
        moveTo(10f, 10f); lineTo(10f, 17f); moveTo(14f, 10f); lineTo(14f, 17f)
    }
    val rotateLeft = icon("rotate.left") {
        moveTo(8.5f, 9f); lineTo(19.5f, 9f); lineTo(19.5f, 20f); lineTo(8.5f, 20f); close()
        moveTo(4f, 12f); lineTo(4f, 8f); arcTo(5f, 5f, 0f, false, true, 9f, 3f); lineTo(12f, 3f)
        moveTo(2f, 10f); lineTo(4f, 12f); lineTo(6f, 10f)
    }
    val xmark = icon("xmark") { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) }
    val speaker = icon("speaker.wave.2") {
        speakerPath()
        moveTo(15f, 9f); arcTo(4f, 4f, 0f, false, true, 15f, 15f)
        moveTo(17.5f, 6.5f); arcTo(7.5f, 7.5f, 0f, false, true, 17.5f, 17.5f)
    }
    val speakerSlash = icon("speaker.slash") { speakerPath(); moveTo(3f, 3f); lineTo(21f, 21f) }

    private fun PathBuilder.heartPath() {
        moveTo(12f, 20f); lineTo(4.5f, 12.5f); arcTo(4.2f, 4.2f, 0f, false, true, 12f, 6.5f)
        arcTo(4.2f, 4.2f, 0f, false, true, 19.5f, 12.5f); close()
    }

    private fun PathBuilder.speakerPath() {
        moveTo(3.5f, 9f); lineTo(7f, 9f); lineTo(12f, 4.5f); lineTo(12f, 19.5f); lineTo(7f, 15f); lineTo(3.5f, 15f); close()
    }

    private fun icon(name: String, fill: Boolean = false, block: PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .path(
                fill = if (fill) SolidColor(Color.White) else null,
                stroke = SolidColor(Color.White), strokeLineWidth = 1.2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block,
            )
            .build()
}
