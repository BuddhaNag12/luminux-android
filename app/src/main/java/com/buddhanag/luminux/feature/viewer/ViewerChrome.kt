package com.buddhanag.luminux.feature.viewer

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.buddhanag.luminux.designsystem.LocalMetro
import com.buddhanag.luminux.designsystem.MetroMetrics
import com.buddhanag.luminux.designsystem.MetroText
import com.buddhanag.luminux.designsystem.MetroType
import com.buddhanag.luminux.designsystem.Semilight
import com.buddhanag.luminux.designsystem.metroFont

/** The status and navigation bars hide while [hidden]; a swipe from the edge shows them for a moment. */
@Composable
internal fun SystemBarsHidden(hidden: Boolean) {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(hidden) {
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hidden) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars())
        onDispose { if (hidden) controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

/** The iOS ProgressView, in the accent. */
@Composable
internal fun Spinner(modifier: Modifier = Modifier) {
    val accent = LocalMetro.current.accentColor
    val turn by rememberInfiniteTransition("spinner").animateFloat(
        0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "turn",
    )
    Canvas(modifier.size(24.dp).graphicsLayer { rotationZ = turn }) {
        drawArc(accent, 0f, 270f, useCenter = false, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

/**
 * shortcut: a stand-in for spec 09's Pro page with a use-once trial, so the viewer's gates and their "run after the
 * page closes" order can be exercised; 09 replaces it with ProScreen(trial = …) and a rewarded ad.
 */
@Composable
internal fun ProOfferPage(trialTitle: String, onTrial: () -> Unit, onClose: () -> Unit) {
    val metro = LocalMetro.current
    BackHandler(onBack = onClose)
    Column(
        Modifier
            .fillMaxSize()
            .background(metro.background)
            .pointerInput(Unit) { swallowTouches() }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = MetroMetrics.margin + 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        MetroText("LUMINUX", MetroType.overline)
        MetroText("pro", MetroType.title, Modifier.offset(x = (-3).dp))
        MetroText("This page comes with spec 09.", MetroType.body, color = metro.secondary)
        TextButton(trialTitle, onTrial)
        TextButton("not now", onClose)
    }
}

@Composable
internal fun TextButton(title: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    BasicText(
        title,
        modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        style = metroFont(22.sp, Semilight).copy(color = LocalMetro.current.foreground),
    )
}

/**
 * Takes the touches so nothing under a full-screen page reacts: the topmost sibling with pointer input wins the hit
 * test. Nothing is consumed, since that would cancel the page's own drags.
 */
internal suspend fun PointerInputScope.swallowTouches() = awaitEachGesture {
    do {
        val event = awaitPointerEvent()
    } while (event.changes.any { it.pressed })
}
