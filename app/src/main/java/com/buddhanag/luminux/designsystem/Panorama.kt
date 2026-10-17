package com.buddhanag.luminux.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.drop
import kotlin.math.max
import kotlin.math.roundToInt

/** The panorama's scroll geometry, in px. [scrollX] is the pager's scroll from the first panel's leading edge. */
data class PanoramaMath(
    val screenWidth: Float,
    val peek: Float,
    val panels: Int,
    val start: Int,
    val reduceMotion: Boolean = false,
) {
    val panelWidth = screenWidth - peek
    val leadingWidth = start * panelWidth
    /** The pager clamps the last panel to the right edge, as iOS does. */
    val maxOffset = panels * panelWidth - screenWidth - leadingWidth

    /** Measured from the start panel, so it's negative in the panels to its left. */
    fun offset(scrollX: Float) = scrollX - leadingWidth
    fun parallax(scrollX: Float) = if (reduceMotion) 0f else offset(scrollX)

    /** How far into the panels left of the start: 0 on the start panel, 1 on the first leading panel. */
    fun leadingProgress(scrollX: Float) = if (leadingWidth <= 0f) 0f else (-offset(scrollX) / leadingWidth).coerceIn(0f, 1f)

    /** The title slides off a little more on every panel ("photos", "otos", "tos") until the last one. */
    fun titleShift(scrollX: Float, titleWidth: Float, margin: Float) =
        if (maxOffset <= 0f) 0f
        else (parallax(scrollX) / maxOffset).coerceIn(0f, 1f) * (margin + titleWidth * MetroMotion.TITLE_TRAVEL)

    /** Follows the panels into the leading ones, so that part isn't parallax and moves with Reduce Motion too. */
    fun titleX(scrollX: Float, titleWidth: Float, margin: Float) =
        leadingProgress(scrollX) * screenWidth - titleShift(scrollX, titleWidth, margin)

    /** Wide enough to cover the screen through about four panels of parallax, plus the panels left of the start. */
    val backgroundWidth get() = screenWidth + panelWidth * 4 * MetroMotion.BACKGROUND_PARALLAX + leadingWidth * MetroMotion.BACKGROUND_PARALLAX
    fun backgroundX(scrollX: Float) =
        -(leadingWidth * MetroMotion.BACKGROUND_PARALLAX) - parallax(scrollX) * MetroMotion.BACKGROUND_PARALLAX

    companion object {
        /** How much of the next panel shows at the right edge. */
        val PEEK = 44.dp
    }
}

/**
 * Horizontally snapping hub with a wide title and background that move slower than the panels. It opens on
 * [startPanel]; panels before it sit to the left under their own [leadingTitle]. Panels run to the bottom of the
 * screen; give their content a bottom padding for the app bar (see [PanoramaSection]).
 */
@Composable
fun Panorama(
    title: String,
    panels: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
    leadingTitle: String? = null,
    startPanel: Int = 0,
    onPanelChange: (Int) -> Unit = {},
    background: (@Composable () -> Unit)? = null,
) {
    val metro = LocalMetro.current
    val reduceMotion = LocalReduceMotion.current
    val density = LocalDensity.current
    val pager = rememberPagerState(initialPage = startPanel) { panels.size }
    var titleWidth by remember { mutableIntStateOf(0) }

    val onChange by rememberUpdatedState(onPanelChange)
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.drop(1).collect { onChange(it) } }
    // A panel added or removed on the left shifts every index; stay on the same panel.
    var previousStart by remember { mutableIntStateOf(startPanel) }
    LaunchedEffect(startPanel) {
        if (startPanel == previousStart) return@LaunchedEffect
        pager.scrollToPage(max(pager.currentPage + startPanel - previousStart, 0))
        previousStart = startPanel
    }

    BoxWithConstraints(modifier.fillMaxSize().background(metro.background)) {
        val screenWidth = constraints.maxWidth.toFloat()
        val math = PanoramaMath(screenWidth, with(density) { PanoramaMath.PEEK.toPx() }, panels.size, startPanel, reduceMotion)
        val margin = with(density) { (MetroMetrics.margin + 4.dp).toPx() }
        val scrollX = { (pager.currentPage + pager.currentPageOffsetFraction) * math.panelWidth }

        if (background != null) {
            // Runs under the status bar and app bar too.
            Box(Modifier.fillMaxSize().clipToBounds()) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .wrapContentWidth(Alignment.Start, unbounded = true)
                        .width(with(density) { math.backgroundWidth.toDp() })
                        .offset { IntOffset(math.backgroundX(scrollX()).roundToInt(), 0) }
                        .clipToBounds()
                        .clearAndSetSemantics {},
                ) { background() }
                val veil = if (metro.theme == MetroThemeMode.Dark) 0.45f else 0.55f
                Box(Modifier.fillMaxSize().background(metro.background.copy(alpha = veil)))
            }
        }

        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            val onLeadingPanel = pager.settledPage < startPanel
            Box(Modifier.metroFeather(row = 0).fillMaxWidth().clipToBounds()) {
                Box(
                    Modifier
                        .wrapContentWidth(Alignment.Start, unbounded = true)
                        .offset { IntOffset(math.titleX(scrollX(), titleWidth.toFloat(), margin).roundToInt(), 0) },
                ) {
                    PanoramaTitle(title, Modifier.onSizeChanged { titleWidth = it.width }, hidden = onLeadingPanel)
                    // A screen to the left of the main title, so each sits fully off screen while the other shows.
                    if (leadingTitle != null) {
                        PanoramaTitle(
                            leadingTitle, Modifier.offset { IntOffset(-screenWidth.roundToInt(), 0) }, hidden = !onLeadingPanel,
                        )
                    }
                }
            }
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxWidth().weight(1f),
                pageSize = PageSize.Fixed(with(density) { math.panelWidth.toDp() }),
                flingBehavior = PagerDefaults.flingBehavior(pager, pagerSnapDistance = PagerSnapDistance.atMost(1)),
                verticalAlignment = Alignment.Top,
            ) { page ->
                // Panels left of the one on screen don't feather: they hinge on the screen edge, so their tiles would
                // swing into view.
                val feather = if (page < pager.settledPage) FeatherContext() else LocalFeather.current
                CompositionLocalProvider(LocalFeather provides feather) {
                    Box(Modifier.fillMaxSize()) { panels[page]() }
                }
            }
        }
    }
}

@Composable
private fun PanoramaTitle(text: String, modifier: Modifier, hidden: Boolean) {
    MetroText(
        text, MetroType.panorama,
        modifier
            .padding(start = MetroMetrics.margin + 4.dp)
            .then(if (hidden) Modifier.clearAndSetSemantics {} else Modifier.semantics { heading() }),
        maxLines = 1, softWrap = false,
    )
}

/** One panel of a [Panorama]: a section header over vertically scrolling content. */
@Composable
fun PanoramaSection(
    header: String,
    modifier: Modifier = Modifier,
    /** What the app bar and navigation bar cover; the content can still scroll clear of it. */
    bottomInset: Dp = 0.dp,
    content: LazyListScope.() -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MetroText(
            header, MetroType.section,
            Modifier.metroFeather(row = 1).padding(start = MetroMetrics.margin).semantics { heading() },
        )
        LazyColumn(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = MetroMetrics.margin, bottom = 24.dp + bottomInset),
            content = content,
        )
    }
}
