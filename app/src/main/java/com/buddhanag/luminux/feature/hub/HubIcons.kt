package com.buddhanag.luminux.feature.hub

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Thin line stand-ins for the SF Symbols on the hub's tiles and buttons, drawn like `MetroIcons` on a 24 grid. */
internal object HubIcons {
    /** `photo.on.rectangle`. */
    val photo = icon("photo") {
        moveTo(3f, 5f); lineTo(21f, 5f); lineTo(21f, 19f); lineTo(3f, 19f); close()
        moveTo(3f, 16f); lineTo(9f, 11f); lineTo(13f, 15f); lineTo(16f, 12.5f); lineTo(21f, 16.5f)
        moveTo(17f, 8f); arcTo(1.5f, 1.5f, 0f, true, true, 17f, 8.01f)
    }
    val heart = icon("heart") {
        moveTo(12f, 20f); lineTo(4.5f, 12.5f); arcTo(4.2f, 4.2f, 0f, false, true, 12f, 6.8f)
        arcTo(4.2f, 4.2f, 0f, false, true, 19.5f, 12.5f); close()
    }
    val map = icon("map") {
        moveTo(3f, 6f); lineTo(9f, 4f); lineTo(15f, 6f); lineTo(21f, 4f); lineTo(21f, 18f); lineTo(15f, 20f)
        lineTo(9f, 18f); lineTo(3f, 20f); close()
        moveTo(9f, 4f); lineTo(9f, 18f); moveTo(15f, 6f); lineTo(15f, 20f)
    }
    /** `rectangle.stack`. */
    val albumStack = icon("rectangle.stack") {
        moveTo(3f, 9f); lineTo(21f, 9f); lineTo(21f, 20f); lineTo(3f, 20f); close()
        moveTo(5f, 6.5f); lineTo(19f, 6.5f); moveTo(7f, 4f); lineTo(17f, 4f)
    }
    /** `photo.stack`. */
    val photoStack = icon("photo.stack") {
        moveTo(3f, 8f); lineTo(18f, 8f); lineTo(18f, 20f); lineTo(3f, 20f); close()
        moveTo(6f, 5f); lineTo(21f, 5f); lineTo(21f, 17f)
        moveTo(3f, 17f); lineTo(8f, 13f); lineTo(11f, 16f); lineTo(14f, 13.5f); lineTo(18f, 17f)
    }
    val unpin = icon("pin.slash", 2.2f) {
        moveTo(9f, 4f); lineTo(15f, 4f); moveTo(10f, 4f); lineTo(10f, 10f); lineTo(7f, 14f); lineTo(17f, 14f)
        lineTo(14f, 10f); lineTo(14f, 4f); moveTo(12f, 14f); lineTo(12f, 20f); moveTo(4f, 4f); lineTo(20f, 20f)
    }
    /** `arrow.up.left.and.arrow.down.right`. */
    val expand = icon("expand", 2.2f) {
        moveTo(4f, 4f); lineTo(10f, 10f); moveTo(4f, 9f); lineTo(4f, 4f); lineTo(9f, 4f)
        moveTo(20f, 20f); lineTo(14f, 14f); moveTo(20f, 15f); lineTo(20f, 20f); lineTo(15f, 20f)
    }
    /** `arrow.down.right.and.arrow.up.left`. */
    val shrink = icon("shrink", 2.2f) {
        moveTo(4f, 4f); lineTo(10f, 10f); moveTo(5f, 10f); lineTo(10f, 10f); lineTo(10f, 5f)
        moveTo(20f, 20f); lineTo(14f, 14f); moveTo(19f, 14f); lineTo(14f, 14f); lineTo(14f, 19f)
    }
    /** `checklist`, the "select" button. */
    val select = icon("checklist") {
        moveTo(3f, 6f); lineTo(5f, 8f); lineTo(8f, 4.5f); moveTo(11f, 6.5f); lineTo(21f, 6.5f)
        moveTo(3f, 16f); lineTo(5f, 18f); lineTo(8f, 14.5f); moveTo(11f, 16.5f); lineTo(21f, 16.5f)
    }

    private fun icon(name: String, width: Float = 1f, block: PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .path(
                stroke = SolidColor(Color.White), strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block,
            )
            .build()
}
