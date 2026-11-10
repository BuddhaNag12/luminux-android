package com.buddhanag.luminux.designsystem

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import androidx.window.layout.WindowMetricsCalculator
import kotlin.math.max

/** Horizontal coordinate of a vertical fold hinge in window dp, or null when unsplit / folded. */
val LocalFoldX = compositionLocalOf<Dp?> { null }

/** Vertical coordinate of a horizontal fold hinge in window dp, or null when no horizontal fold is active. */
val LocalFoldY = compositionLocalOf<Dp?> { null }

/** Horizontal split line in window dp during tabletop posture, used by the editor. */
val LocalSplitY = compositionLocalOf<Dp?> { null }

/** True when a hardware hinge is currently half-opened (`HALF_OPENED`). */
val LocalIsHalfOpen = compositionLocalOf { false }

/** True when the current window is at least 600 dp wide (unfolded inner display, tablet, or desktop window). */
val LocalExpandedWidth = compositionLocalOf { false }

@Immutable
data class FoldInfo(
    val foldX: Dp? = null,
    val foldY: Dp? = null,
    val splitY: Dp? = null,
    val isHalfOpen: Boolean = false,
    val expandedWidth: Boolean = false,
)

enum class HingeOrientation { VERTICAL, HORIZONTAL }
enum class HingePosture { FLAT, HALF_OPENED }

data class HingeFeature(
    val orientation: HingeOrientation,
    val posture: HingePosture,
    val centerPx: Float,
)

object FoldGeometry {
    val EXPANDED_WIDTH_DP = 600.dp

    fun compute(
        windowWidthPx: Int,
        windowHeightPx: Int,
        density: Float,
        hinge: HingeFeature?,
    ): FoldInfo {
        val safeDensity = max(density, 0.01f)
        val widthDp = (windowWidthPx / safeDensity).dp
        val heightDp = (windowHeightPx / safeDensity).dp
        val expandedWidth = widthDp >= EXPANDED_WIDTH_DP
        if (hinge == null) return FoldInfo(expandedWidth = expandedWidth)

        val isHalfOpen = hinge.posture == HingePosture.HALF_OPENED
        val centerDp = (hinge.centerPx / safeDensity).dp
        val foldX = if (hinge.orientation == HingeOrientation.VERTICAL && centerDp > 0.dp && centerDp < widthDp) centerDp else null
        val foldY = if (hinge.orientation == HingeOrientation.HORIZONTAL && centerDp > 0.dp && centerDp < heightDp) centerDp else null
        val splitY = if (isHalfOpen && foldY != null) foldY else null
        return FoldInfo(
            foldX = foldX,
            foldY = foldY,
            splitY = splitY,
            isHalfOpen = isHalfOpen,
            expandedWidth = expandedWidth,
        )
    }

    /**
     * Normalized horizontal transform-origin for a Turnstile tile: items on the right display (`left >= foldX`) swing
     * around the vertical fold line `(foldX - left) / width`; items to the left hinge at the window edge `0`.
     */
    fun turnstileOriginX(leftPx: Float, widthPx: Float, foldXPx: Float?): Float {
        val hingeX = if (foldXPx != null && leftPx >= foldXPx) foldXPx else 0f
        return (hingeX - leftPx) / max(widthPx, 1f)
    }
}

private fun WindowLayoutInfo?.primaryHinge(): HingeFeature? {
    val feature = this?.displayFeatures?.filterIsInstance<FoldingFeature>()?.firstOrNull() ?: return null
    val orientation = when (feature.orientation) {
        FoldingFeature.Orientation.VERTICAL -> HingeOrientation.VERTICAL
        FoldingFeature.Orientation.HORIZONTAL -> HingeOrientation.HORIZONTAL
        else -> return null
    }
    val posture = when (feature.state) {
        FoldingFeature.State.HALF_OPENED -> HingePosture.HALF_OPENED
        FoldingFeature.State.FLAT -> HingePosture.FLAT
        else -> return null
    }
    val centerPx = if (orientation == HingeOrientation.VERTICAL) {
        feature.bounds.exactCenterX()
    } else {
        feature.bounds.exactCenterY()
    }
    return HingeFeature(orientation, posture, centerPx)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Observes `WindowInfoTracker` and current window bounds and publishes fold geometry down the tree (spec 12G). */
@Composable
fun FoldProvider(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var layoutInfo by remember { mutableStateOf<WindowLayoutInfo?>(null) }

    LaunchedEffect(activity) {
        if (activity != null) {
            WindowInfoTracker.getOrCreate(activity)
                .windowLayoutInfo(activity)
                .collect { layoutInfo = it }
        }
    }

    val density = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    val fallbackBounds = remember(activity, containerSize) {
        if (containerSize.width > 0 && containerSize.height > 0) null
        else activity?.let { WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(it).bounds }
    }
    val widthPx = if (containerSize.width > 0) containerSize.width else fallbackBounds?.width() ?: 0
    val heightPx = if (containerSize.height > 0) containerSize.height else fallbackBounds?.height() ?: 0
    val info = FoldGeometry.compute(widthPx, heightPx, density.density, layoutInfo.primaryHinge())

    CompositionLocalProvider(
        LocalFoldX provides info.foldX,
        LocalFoldY provides info.foldY,
        LocalSplitY provides info.splitY,
        LocalIsHalfOpen provides info.isHalfOpen,
        LocalExpandedWidth provides info.expandedWidth,
        content = content,
    )
}
