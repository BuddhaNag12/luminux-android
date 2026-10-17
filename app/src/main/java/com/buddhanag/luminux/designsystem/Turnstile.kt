package com.buddhanag.luminux.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Page transition that swings the page on a hinge at its left edge (gallery only; real navigation uses the feather).
 * Inserts as iOS `.forward` (from −80°) and removes as `.backward` (to −80°), so both ends sit at −80°.
 * Reduce Motion cross-fades instead.
 */
@Composable
fun TurnstileVisibility(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (LocalReduceMotion.current) {
        AnimatedVisibility(visible, modifier, enter = fadeIn(), exit = fadeOut()) { content() }
        return
    }
    AnimatedVisibility(visible, modifier, enter = EnterTransition.None, exit = ExitTransition.None) {
        val angle by transition.animateFloat({ MetroMotion.standard() }, label = "turnstile") {
            if (it == EnterExitState.Visible) 0f else -80f
        }
        val alpha by transition.animateFloat({ MetroMotion.standard() }, label = "turnstileAlpha") {
            if (it == EnterExitState.Visible) 1f else 0f
        }
        Box(
            Modifier
                .perspectiveRotation({ angle }, axisY = 1f, origin = { TransformOrigin(0f, 0.5f) }, perspective = 0.7f)
                .graphicsLayer { this.alpha = alpha },
        ) { content() }
    }
}
