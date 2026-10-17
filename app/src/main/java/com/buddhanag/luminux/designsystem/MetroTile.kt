package com.buddhanag.luminux.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize

/** A square-cornered tile that tilts toward the finger while pressed. */
@Composable
fun MetroTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    /** Small tiles have no room for a label; the title still names the tile for TalkBack. */
    showsTitle: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .tiltPress(interaction)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .then(if (title != null) Modifier.semantics { contentDescription = title } else Modifier),
    ) {
        Box(Modifier.fillMaxSize().clipToBounds(), content = content)
        if (title != null && showsTitle) {
            MetroText(
                title, MetroType.caption,
                Modifier.align(Alignment.BottomStart).padding(10.dp).clearAndSetSemantics {},
                color = Color.White,
            )
        }
    }
}

/**
 * The tilt of `TiltButtonStyle`, driven by [interaction]'s pressed state. With [trackTouch] the edge under the finger
 * sinks; without it (or for an unknown touch) the tile only pushes in. Reduce Motion shows 70% opacity instead.
 */
fun Modifier.tiltPress(interaction: InteractionSource, trackTouch: Boolean = true): Modifier = composed {
    val reduceMotion = LocalReduceMotion.current
    val pressed by interaction.collectIsPressedAsState()
    var touch by remember { mutableStateOf<Offset?>(null) }
    var size by remember { mutableStateOf(Size.Zero) }
    val target = if (pressed && !reduceMotion) Tilt.pressed(touch, size) else Tilt.Rest
    // Swift swaps the axis instantly and animates only the angle; keeping the last axis while the angle springs back
    // gives the release its bounce instead of snapping flat.
    var axis by remember { mutableStateOf(Offset.Zero) }
    if (target.angle != 0.0) axis = Offset(target.axisX.toFloat(), target.axisY.toFloat())
    val spec = if (pressed) MetroMotion.press<Float>() else MetroMotion.release()
    val angle by animateFloatAsState(target.angle.toFloat(), spec, label = "tilt")
    val scale by animateFloatAsState(target.scale.toFloat(), spec, label = "tiltScale")
    val pressedAlpha = if (pressed && reduceMotion) 0.7f else 1f

    this
        .then(
            if (!trackTouch) Modifier else Modifier.pointerInput(Unit) {
                // Reads the finger on the Initial pass without consuming, so buttons and scrolling keep the touch.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    touch = down.position
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.firstOrNull { it.id == down.id && it.pressed }?.let { touch = it.position }
                    } while (event.changes.any { it.pressed })
                }
            },
        )
        .onSizeChanged { size = it.toSize() }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            alpha = pressedAlpha
        }
        .perspectiveRotation({ angle }, axisX = axis.x, axisY = axis.y, perspective = 0.6f)
}
