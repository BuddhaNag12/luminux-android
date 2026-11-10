package com.buddhanag.luminux.feature.viewer

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.buddhanag.luminux.designsystem.LocalMetro
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
