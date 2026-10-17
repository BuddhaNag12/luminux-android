package com.buddhanag.luminux.feature.viewer.slideshow

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.buddhanag.luminux.data.MediaItem
import com.buddhanag.luminux.data.MediaType
import com.buddhanag.luminux.designsystem.LocalReduceMotion
import com.buddhanag.luminux.designsystem.MetroMotion
import com.buddhanag.luminux.feature.viewer.SystemBarsHidden
import kotlinx.coroutines.delay

private const val SLIDE_MS = 5_000L

/** Full-screen slideshow of the still photos in [assets]: slow pan and zoom with cross-fades. Tap to stop. */
@Composable
internal fun SlideshowScreen(assets: List<MediaItem>, startIndex: Int, onClose: () -> Unit) {
    val reduceMotion = LocalReduceMotion.current
    // Taken once, like iOS: a library change mid-show doesn't reshuffle the order.
    val photos = remember { assets.filter { it.type == MediaType.IMAGE } }
    var position by remember {
        mutableIntStateOf(assets.indices.filter { assets[it].type == MediaType.IMAGE }.indexOfFirst { it >= startIndex }.coerceAtLeast(0))
    }
    LaunchedEffect(photos) {
        while (photos.isNotEmpty()) {
            delay(SLIDE_MS)
            position = (position + 1) % photos.size
        }
    }
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    SystemBarsHidden(true)
    BackHandler(onBack = onClose)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose)
            .semantics { customActions = listOf(CustomAccessibilityAction("Stop slideshow") { onClose(); true }) },
    ) {
        if (photos.isNotEmpty()) {
            Crossfade(position, animationSpec = tween(1_200, easing = MetroMotion.EaseInOut), label = "slide") { slide ->
                KenBurnsSlide(photos[slide % photos.size], seed = slide, animates = !reduceMotion)
            }
        }
    }
}

/** Alternates pan direction and zoom, so consecutive slides don't feel the same; motion runs on through the fade. */
@Composable
private fun KenBurnsSlide(item: MediaItem, seed: Int, animates: Boolean) {
    val direction = if (seed % 2 == 0) 1f else -1f
    val zoomIn = seed % 3 != 0
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (animates) progress.animateTo(1f, tween((SLIDE_MS + 1_500).toInt(), easing = LinearEasing))
    }
    AsyncImage(
        item.contentUri, null,
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .graphicsLayer {
                val p = progress.value
                // Never below 1.08, so the pan never uncovers an edge.
                val scale = if (zoomIn) 1.08f + 0.12f * p else 1.2f - 0.12f * p
                scaleX = scale
                scaleY = scale
                translationX = (direction * 18 * (p - 0.5f)).dp.toPx()
                translationY = (-10 * (p - 0.5f)).dp.toPx()
            },
        contentScale = ContentScale.Crop,
    )
}
