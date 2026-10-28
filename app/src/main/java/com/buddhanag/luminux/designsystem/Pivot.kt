package com.buddhanag.luminux.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

data class PivotPage(val title: String, val content: @Composable () -> Unit)

private val HeaderSpacing = 24.dp

/** How far the header strip has slid left at [progress] (pages), given the measured header [widths]. */
fun pivotHeaderOffset(progress: Float, widths: List<Float>, spacing: Float): Float {
    if (widths.isEmpty()) return 0f
    val p = progress.coerceIn(0f, (widths.size - 1).toFloat())
    val lower = floor(p).toInt()
    val upper = min(lower + 1, widths.size - 1)
    fun edge(i: Int) = (0 until i).sumOf { (widths[it] + spacing).toDouble() }.toFloat()
    return edge(lower) + (edge(upper) - edge(lower)) * (p - lower)
}

/** The active header is opaque; the others fade to 40% one page away. */
fun pivotHeaderAlpha(index: Int, progress: Float) = 1f - 0.6f * min(abs(index - progress), 1f)

/** Set by a photo grid while it's pinched, so the pivot it sits in holds still (GridGestures.swift `GridPinchingKey`). */
val LocalGridPinching = compositionLocalOf<MutableState<Boolean>?> { null }

/**
 * Paged content under a row of headers that slide with the swipe; the active header sits at the left. Pages beside
 * the opening one stay hidden until the pivot is swiped or a header tapped, so a page push doesn't drag a neighbour
 * across the screen while the tiles turn in.
 */
@Composable
fun Pivot(
    pages: List<PivotPage>,
    selection: Int,
    onSelectionChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    overline: String? = null,
) {
    val metro = LocalMetro.current
    val pager = rememberPagerState(initialPage = selection) { pages.size }
    var showsNeighbours by remember { mutableStateOf(false) }
    val widths = remember { mutableStateListOf<Float>() }
    val pinching = remember { mutableStateOf(false) }
    val spacing = with(LocalDensity.current) { HeaderSpacing.toPx() }
    val progress = { pager.currentPage + pager.currentPageOffsetFraction }
    val onChange by rememberUpdatedState(onSelectionChange)
    val currentSelection by rememberUpdatedState(selection)

    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { if (it != currentSelection) onChange(it) } }
    LaunchedEffect(selection) {
        if (selection == pager.settledPage) return@LaunchedEffect
        showsNeighbours = true
        pager.animateScrollToPage(selection, animationSpec = MetroMotion.standard())
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.isScrollInProgress }.first { it }
        showsNeighbours = true
    }

    Column(modifier.fillMaxSize().background(metro.background).windowInsetsPadding(WindowInsets.statusBars)) {
        if (overline != null) {
            MetroText(overline, MetroType.overline, Modifier.metroFeather(row = 0).padding(start = MetroMetrics.margin + 4.dp))
        }
        // The titles repeat once so the row reads as a loop; the row is far wider than the screen and must not
        // widen the layout, so it's unbounded inside a clipped box.
        Box(Modifier.padding(bottom = 8.dp).metroFeather(row = 0).fillMaxWidth().clipToBounds()) {
            Row(
                Modifier
                    .wrapContentWidth(Alignment.Start, unbounded = true)
                    .padding(start = MetroMetrics.margin + 4.dp)
                    .graphicsLayer { translationX = -pivotHeaderOffset(progress(), widths.toList(), spacing) },
                horizontalArrangement = Arrangement.spacedBy(HeaderSpacing),
            ) {
                val looped = pages + pages
                looped.forEachIndexed { index, page ->
                    val target = index % pages.size
                    val isCopy = index >= pages.size
                    MetroText(
                        page.title, MetroType.pivot,
                        Modifier
                            .alignByBaseline()
                            .onSizeChanged {
                                if (isCopy) return@onSizeChanged
                                while (widths.size < pages.size) widths += 0f
                                widths[index] = it.width.toFloat()
                            }
                            .graphicsLayer { alpha = pivotHeaderAlpha(index, progress()) }
                            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab) {
                                onSelectionChange(target)
                            }
                            .then(
                                if (isCopy) Modifier.clearAndSetSemantics {}
                                else Modifier.semantics { selected = target == selection },
                            ),
                        maxLines = 1, softWrap = false,
                    )
                }
            }
        }
        HorizontalPager(
            pager, Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.Top, userScrollEnabled = !pinching.value,
        ) { page ->
            // Only the page on screen feathers. The others sit off screen, and turning on the screen-edge hinge would
            // swing their tiles into view.
            val feather = if (page != pager.settledPage) FeatherContext() else LocalFeather.current
            val visible = showsNeighbours || page == selection
            CompositionLocalProvider(LocalFeather provides feather, LocalGridPinching provides pinching) {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (visible) 1f else 0f }) { pages[page].content() }
            }
        }
    }
}
