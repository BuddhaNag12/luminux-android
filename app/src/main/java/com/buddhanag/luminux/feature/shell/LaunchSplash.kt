package com.buddhanag.luminux.feature.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.buddhanag.luminux.designsystem.LocalReduceMotion
import com.buddhanag.luminux.designsystem.LogoMark
import com.buddhanag.luminux.designsystem.LogoPhase
import com.buddhanag.luminux.designsystem.MetroMotion
import com.buddhanag.luminux.designsystem.perspectiveRotation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** True while the launch animation covers the screen, so the logo underneath waits to play its own entrance. */
val LocalLaunchSplashShowing = compositionLocalOf { false }

/**
 * Plays once on a cold launch, over the first screen (LaunchSplash.swift): the mark assembles, then turns away on its
 * left edge like a page leaving while the screen fades into the app. It starts on black, like the system splash, so the
 * hand-over doesn't flash. Reduce Motion gets a short fade instead.
 */
@Composable
fun LaunchSplash(onFinish: () -> Unit) {
    val reduceMotion = LocalReduceMotion.current
    val finish by rememberUpdatedState(onFinish)
    val baseIn = remember { Animatable(LogoPhase.Hidden.baseIn) }
    val flap = remember { Animatable(LogoPhase.Hidden.flapAngle) }
    val sun = remember { Animatable(LogoPhase.Hidden.sun) }
    val hills = remember { Animatable(LogoPhase.Hidden.hills) }
    val leave = remember { Animatable(0f) }
    var isLeaving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (reduceMotion) {
            delay(400)
            isLeaving = true
            leave.animateTo(1f, tween(250, easing = MetroMotion.EaseOut))
        } else {
            // LogoMark.playEntrance: it returns once the hills start drawing.
            launch { baseIn.animateTo(1f, MetroMotion.spring(0.4, 0.85f)) }
            delay(120)
            launch { flap.animateTo(LogoPhase.REST_ANGLE, MetroMotion.spring(0.45, 0.6f)) }
            delay(300)
            launch { sun.animateTo(1f, MetroMotion.spring(0.3, 0.5f)) }
            delay(120)
            launch { hills.animateTo(1f, tween(350, easing = MetroMotion.EaseOut)) }
            delay(380)
            isLeaving = true
            leave.animateTo(1f, tween(300, easing = MetroMotion.EaseIn))
        }
        finish()
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - leave.value }
            .background(Color.Black)
            .clearAndSetSemantics {}
            // Blocks touches until it starts leaving; an input node at all would keep blocking, so drop it then.
            .then(
                if (isLeaving) Modifier else Modifier.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                },
            ),
    ) {
        LogoMark(
            2.1.dp, Color.Black,
            Modifier
                .align(Alignment.Center)
                .perspectiveRotation(
                    { if (reduceMotion) 0f else 70f * leave.value }, axisY = 1f,
                    origin = { TransformOrigin(0f, 0.5f) }, perspective = 0.7f,
                ),
        ) {
            if (reduceMotion) LogoPhase.Finished else LogoPhase(baseIn.value, flap.value, sun.value, hills.value)
        }
    }
}
