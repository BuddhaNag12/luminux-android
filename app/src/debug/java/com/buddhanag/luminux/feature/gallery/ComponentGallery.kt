package com.buddhanag.luminux.feature.gallery

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import com.buddhanag.luminux.designsystem.Accent
import com.buddhanag.luminux.designsystem.AppBarButton
import com.buddhanag.luminux.designsystem.AppBarMenuItem
import com.buddhanag.luminux.designsystem.JumpListView
import com.buddhanag.luminux.designsystem.LiveTile
import com.buddhanag.luminux.designsystem.LiveTileScheduler
import com.buddhanag.luminux.designsystem.LiveTileStyle
import com.buddhanag.luminux.designsystem.LocalLiveTileScheduler
import com.buddhanag.luminux.designsystem.LocalMetro
import com.buddhanag.luminux.designsystem.LogoLockup
import com.buddhanag.luminux.designsystem.MetroAppBarHost
import com.buddhanag.luminux.designsystem.MetroButton
import com.buddhanag.luminux.designsystem.MetroMetrics
import com.buddhanag.luminux.designsystem.MetroMotion
import com.buddhanag.luminux.designsystem.MetroSlider
import com.buddhanag.luminux.designsystem.MetroText
import com.buddhanag.luminux.designsystem.MetroTile
import com.buddhanag.luminux.designsystem.MetroType
import com.buddhanag.luminux.designsystem.MonthSection
import com.buddhanag.luminux.designsystem.Panorama
import com.buddhanag.luminux.designsystem.PanoramaSection
import com.buddhanag.luminux.designsystem.Pivot
import com.buddhanag.luminux.designsystem.PivotPage
import com.buddhanag.luminux.designsystem.Semilight
import com.buddhanag.luminux.designsystem.Tilt
import com.buddhanag.luminux.designsystem.TurnstileVisibility
import com.buddhanag.luminux.designsystem.metroFont
import com.buddhanag.luminux.designsystem.perspectiveRotation

/** Debug-only showcase of the Metro components, mirroring the iOS `-gallery panorama|pivot` screens. */
enum class GalleryKind { Panorama, Pivot }

@Composable
fun ComponentGallery(kind: GalleryKind) {
    val scope = rememberCoroutineScope()
    val scheduler = remember { LiveTileScheduler(scope) }
    LifecycleStartEffect(scheduler) {
        scheduler.isPaused = false
        onStopOrDispose { scheduler.isPaused = true }
    }
    CompositionLocalProvider(LocalLiveTileScheduler provides scheduler) {
        when (kind) {
            GalleryKind.Panorama -> PanoramaDemo()
            GalleryKind.Pivot -> PivotDemo()
        }
    }
}

private val swatches = Accent.presets.map { it.color }

@Composable
private fun PanoramaDemo() {
    var appBarExpanded by remember { mutableStateOf(false) }
    var showsJumpList by remember { mutableStateOf(false) }
    var showsPage by remember { mutableStateOf(false) }
    BackHandler(showsPage || showsJumpList) {
        showsPage = false
        showsJumpList = false
    }

    Box(Modifier.fillMaxSize()) {
        MetroAppBarHost(
            buttons = listOf(AppBarButton("select", Icons.checklist) {}, AppBarButton("camera", Icons.camera) {}),
            expanded = appBarExpanded,
            onExpandedChange = { appBarExpanded = it },
            menuItems = listOf(AppBarMenuItem("settings") {}, AppBarMenuItem("jump list") { showsJumpList = true }),
        ) { padding ->
            val bottom = padding.calculateBottomPadding()
            Panorama(
                "photos",
                onPanelChange = { Log.d("ComponentGallery", "panel $it") },
                panels = listOf<@Composable () -> Unit>(
                    { PanoramaSection("collection", bottomInset = bottom) { item { CollectionTiles { showsJumpList = true } } } },
                    {
                        PanoramaSection("what's new", bottomInset = bottom) {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    MetroButton("open a page", { showsPage = true })
                                    StaticTiltSamples()
                                }
                            }
                        }
                    },
                    {
                        PanoramaSection("favorites", bottomInset = bottom) {
                            item { MetroText("third panel", MetroType.body) }
                            // Android-only extras, so the logo and slider can be checked too.
                            item { LogoLockup(LocalMetro.current.background, Modifier.padding(top = 24.dp), unit = 0.5f) }
                            item { SliderSample() }
                        }
                    },
                ),
                background = {
                    Row(Modifier.fillMaxSize()) {
                        swatches.forEach { Box(Modifier.weight(1f).fillMaxHeight().background(it.copy(alpha = 0.6f))) }
                    }
                },
            )
        }

        TurnstileVisibility(showsPage) { DemoPage { showsPage = false } }
        JumpListOverlay(showsJumpList) { showsJumpList = false }
    }
}

@Composable
private fun CollectionTiles(onDate: () -> Unit) {
    val tile = Modifier.size(150.dp)
    Column(verticalArrangement = Arrangement.spacedBy(MetroMetrics.gutter)) {
        Row(horizontalArrangement = Arrangement.spacedBy(MetroMetrics.gutter)) {
            MetroTile({}, tile, title = "camera roll") { Swatch(swatches[4]) }
            MetroTile({}, tile, title = "albums") {
                Box(Modifier.fillMaxSize().background(LocalMetro.current.accentColor), contentAlignment = Alignment.Center) {
                    Image(Icons.stack, null, Modifier.size(40.dp), colorFilter = ColorFilter.tint(Color.White))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MetroMetrics.gutter)) {
            MetroTile(onDate, tile, title = "date") { LiveTile(swatches, style = LiveTileStyle.Slide) { Swatch(it) } }
            MetroTile({}, tile, title = "favorites") { LiveTile(swatches.reversed(), style = LiveTileStyle.Flip) { Swatch(it) } }
        }
    }
}

@Composable
private fun Swatch(color: Color) {
    Box(Modifier.fillMaxSize().background(color))
}

/** Fixed poses for checking the 3D directions in screenshots against iOS. */
@Composable
private fun StaticTiltSamples() {
    val accent = LocalMetro.current.accentColor
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        val tilt = Tilt.pressed(Offset(100f, 50f), Size(100f, 100f))
        Box(
            Modifier
                .perspectiveRotation({ tilt.angle.toFloat() }, tilt.axisX.toFloat(), tilt.axisY.toFloat(), perspective = 0.6f)
                .size(100.dp)
                .background(accent),
            contentAlignment = Alignment.Center,
        ) { MetroText("right edge", MetroType.caption) }
        Box(
            Modifier
                .perspectiveRotation({ -40f }, axisY = 1f, origin = { TransformOrigin(0f, 0.5f) }, perspective = 0.7f)
                .size(100.dp)
                .background(accent),
            contentAlignment = Alignment.Center,
        ) { MetroText("turnstile in", MetroType.caption) }
    }
}

@Composable
private fun SliderSample() {
    var value by remember { mutableDoubleStateOf(0.0) }
    MetroSlider("exposure", value, { value = it }, Modifier.padding(top = 24.dp, end = MetroMetrics.margin))
}

@Composable
private fun DemoPage(onBack: () -> Unit) {
    val metro = LocalMetro.current
    Column(
        Modifier
            .fillMaxSize()
            .background(metro.background)
            .statusBarsPadding()
            .padding(horizontal = MetroMetrics.margin + 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetroText("LUMINUX", MetroType.overline)
        MetroText("page", MetroType.title)
        MetroText("Turnstile in, back to turnstile out.", MetroType.body, color = metro.secondary)
        MetroButton("back", onBack)
    }
}

@Composable
private fun PivotDemo() {
    var selection by remember { mutableIntStateOf(0) }
    var showsJumpList by remember { mutableStateOf(false) }
    BackHandler(showsJumpList) { showsJumpList = false }
    Box(Modifier.fillMaxSize()) {
        Pivot(
            pages = listOf("all", "albums", "favorites", "videos").map { PivotPage(it) { PivotDemoPage { showsJumpList = true } } },
            selection = selection,
            onSelectionChange = { selection = it },
            overline = "LUMINUX",
        )
        JumpListOverlay(showsJumpList) { showsJumpList = false }
    }
}

@Composable
private fun PivotDemoPage(onHeader: () -> Unit) {
    val metro = LocalMetro.current
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyVerticalGrid(
        GridCells.Fixed(4),
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = MetroMetrics.margin, end = MetroMetrics.margin, bottom = bottom),
        horizontalArrangement = Arrangement.spacedBy(MetroMetrics.gutter),
        verticalArrangement = Arrangement.spacedBy(MetroMetrics.gutter),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            MetroText(
                "october 2026", metroFont(20.sp, Semilight),
                Modifier.clickable(onClick = onHeader).padding(bottom = 4.dp), color = metro.accentColor,
            )
        }
        items(swatches) { color -> MetroTile({}, Modifier.aspectRatio(1f)) { Swatch(color) } }
    }
}

@Composable
private fun JumpListOverlay(visible: Boolean, onDismiss: () -> Unit) {
    AnimatedVisibility(
        visible,
        enter = scaleIn(MetroMotion.standard(), initialScale = 1.1f) + fadeIn(MetroMotion.standard()),
        exit = scaleOut(MetroMotion.standard(), targetScale = 1.1f) + fadeOut(MetroMotion.standard()),
    ) {
        JumpListView(DemoData.sections, DemoData.sections.first().id, onSelect = { onDismiss() })
    }
}

private object DemoData {
    val sections = listOf(
        MonthSection(2026, 10, 0 until 12),
        MonthSection(2026, 9, 12 until 13),
        MonthSection(2026, 7, 13 until 20),
        MonthSection(2025, 12, 20 until 22),
        MonthSection(2025, 3, 22 until 30),
        MonthSection(0, 0, 30 until 31),
    )
}

/** Stand-ins for the SF Symbols the iOS gallery uses. */
private object Icons {
    val checklist = icon("checklist") {
        for (y in listOf(6f, 12f, 18f)) {
            moveTo(3f, y); lineTo(4.5f, y + 1.5f); lineTo(7.5f, y - 1.5f)
            moveTo(10f, y); lineTo(21f, y)
        }
    }
    val camera = icon("camera") {
        moveTo(3f, 8f); lineTo(7f, 8f); lineTo(9f, 5f); lineTo(15f, 5f); lineTo(17f, 8f); lineTo(21f, 8f)
        lineTo(21f, 19f); lineTo(3f, 19f); close()
        moveTo(16f, 13.5f); arcTo(4f, 4f, 0f, true, true, 8f, 13.5f); arcTo(4f, 4f, 0f, true, true, 16f, 13.5f)
    }
    val stack = icon("stack") {
        moveTo(4f, 9f); lineTo(20f, 9f); lineTo(20f, 20f); lineTo(4f, 20f); close()
        moveTo(6f, 6f); lineTo(18f, 6f)
        moveTo(8f, 3f); lineTo(16f, 3f)
    }

    private fun icon(name: String, block: PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .path(stroke = SolidColor(Color.White), strokeLineWidth = 1.2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block)
            .build()
}
