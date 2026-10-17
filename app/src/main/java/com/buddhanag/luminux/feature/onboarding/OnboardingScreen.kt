package com.buddhanag.luminux.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buddhanag.luminux.container
import com.buddhanag.luminux.data.MEDIA_PERMISSIONS
import com.buddhanag.luminux.data.MediaAccess
import com.buddhanag.luminux.data.appSettingsIntent
import com.buddhanag.luminux.designsystem.LocalMetro
import com.buddhanag.luminux.designsystem.LocalReduceMotion
import com.buddhanag.luminux.designsystem.LogoLockup
import com.buddhanag.luminux.designsystem.MetroButton
import com.buddhanag.luminux.designsystem.MetroIcons
import com.buddhanag.luminux.designsystem.MetroMetrics
import com.buddhanag.luminux.designsystem.MetroMotion
import com.buddhanag.luminux.designsystem.MetroText
import com.buddhanag.luminux.designsystem.MetroType
import com.buddhanag.luminux.designsystem.Semilight
import com.buddhanag.luminux.designsystem.metroFont
import com.buddhanag.luminux.feature.shell.LocalLaunchSplashShowing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class OnboardingPage(val glyph: ImageVector?, val title: String, val body: String, val accessNote: String? = null)

// OnboardingView.swift:115-132, with the iPhone and Apple Maps wording made Android-neutral.
private val pages = listOf(
    OnboardingPage(
        null, "your photos, front and centre",
        "A tile-based home for the photos and videos already on your phone.",
    ),
    OnboardingPage(
        MetroIcons.stack, "browse, relive, make it yours",
        "A hub of live tiles, a grid you pinch to zoom, and a journal of your days and trips. Edit with light, colour and looks, lift a person or pet out of a photo, and put any album on your home screen as a widget.",
    ),
    OnboardingPage(
        MetroIcons.lockGlyph, "your photos stay yours",
        "Luminux shows the photos already on this phone, and they never leave it. Journal place names come from your phone's map service, which gets locations only.",
        accessNote = "To begin, allow access to your photo library.",
    ),
)

/** The first-launch tour, ending with photo access. Settings can replay it; then the last page just closes it. */
@Composable
fun OnboardingScreen(isReplay: Boolean, onFinish: () -> Unit) {
    val metro = LocalMetro.current
    val context = LocalContext.current
    val activity = LocalActivity.current
    val photos = context.container.photos
    val access by photos.access.collectAsStateWithLifecycle()
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val page = pager.currentPage
    val isLast = page == pages.lastIndex
    val request = rememberLauncherForActivityResult(RequestMultiplePermissions()) {
        activity?.let(photos::onPermissionResult)
        onFinish()
    }
    fun go(to: Int) = scope.launch { pager.animateScrollToPage(to, animationSpec = MetroMotion.standard()) }
    if (isReplay) BackHandler(onBack = onFinish)

    Column(
        Modifier
            .fillMaxSize()
            .background(metro.background)
            // Full screen over settings when replayed: nothing behind takes touches.
            .pointerInput(Unit) {}
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = MetroMetrics.margin + 12.dp), verticalAlignment = Alignment.CenterVertically) {
            MetroText("LUMINUX", MetroType.overline, Modifier.heightIn(min = 44.dp).padding(top = 15.dp))
            Spacer(Modifier.weight(1f))
            if (!isLast) {
                MetroText(
                    "skip", metroFont(17.sp, Semilight),
                    Modifier
                        .defaultMinSize(minHeight = 44.dp)
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { go(pages.lastIndex) }
                        .padding(top = 12.dp)
                        .semantics { contentDescription = "skip, goes to the last page" },
                    color = metro.secondary,
                )
            }
        }

        HorizontalPager(pager, Modifier.fillMaxWidth().weight(1f)) { index ->
            PageView(pages[index], isCurrent = index == page, asksForAccess = !isReplay && !access.canRead)
        }

        Column(
            Modifier.padding(start = MetroMetrics.margin + 12.dp, end = MetroMetrics.margin + 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Square page markers, the current one filled with the accent.
            Row(
                Modifier.semantics(mergeDescendants = true) { contentDescription = "Page ${page + 1} of ${pages.size}" },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                pages.indices.forEach { index ->
                    val width by animateDpAsState(if (index == page) 18.dp else 8.dp, MetroMotion.snappy(), label = "marker")
                    val fill by animateColorAsState(
                        if (index == page) metro.accentColor else metro.foreground.copy(alpha = 0.25f), MetroMotion.snappy(), label = "markerFill",
                    )
                    Box(Modifier.size(width, 8.dp).background(fill))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (page > 0) MetroButton("back", { go(page - 1) })
                Spacer(Modifier.weight(1f))
                when {
                    !isLast -> MetroButton("next", { go(page + 1) })
                    isReplay || access.canRead -> MetroButton(if (isReplay) "done" else "get started", onFinish)
                    access == MediaAccess.DENIED -> MetroButton("open settings", { context.startActivity(appSettingsIntent(context)) })
                    // Finishes whatever the answer, once the result is in; the gate then shows the hub or the access page.
                    else -> MetroButton("allow access", { request.launch(MEDIA_PERMISSIONS) })
                }
            }
        }
    }
}

@Composable
private fun PageView(page: OnboardingPage, isCurrent: Boolean, asksForAccess: Boolean) {
    val metro = LocalMetro.current
    val reduceMotion = LocalReduceMotion.current
    val tiles = remember { List(3) { Animatable(if (reduceMotion) 1f else 0f) } }
    val text = remember { Animatable(if (reduceMotion) 1f else 0f) }

    // The tiles stack in and the text slides in each time the page comes into view.
    LaunchedEffect(isCurrent, reduceMotion) {
        if (!isCurrent || reduceMotion) return@LaunchedEffect
        tiles.forEach { it.snapTo(0f) }
        text.snapTo(0f)
        tiles.forEach { tile ->
            launch { tile.animateTo(1f, MetroMotion.spring(0.42, 0.82f)) }
            delay(80)
        }
        text.animateTo(1f, MetroMotion.spring(0.45, 0.9f))
    }

    Column(Modifier.fillMaxSize().padding(horizontal = MetroMetrics.margin + 12.dp)) {
        Spacer(Modifier.weight(1f).heightIn(min = 12.dp))
        Box(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
            if (page.glyph == null) {
                LogoLockup(metro.background, unit = 1.1f, isSplashShowing = LocalLaunchSplashShowing.current)
            } else {
                GlyphStack(page.glyph) { tiles[it].value }
            }
        }
        Spacer(Modifier.weight(1f).heightIn(min = 24.dp))
        Column(
            Modifier.graphicsLayer {
                translationX = (1 - text.value) * 24.dp.toPx()
                alpha = text.value.coerceIn(0f, 1f)
            },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetroText(page.title, metroFont(38.sp, FontWeight.Light), Modifier.semantics { heading() })
            val body = if (asksForAccess && page.accessNote != null) "${page.body} ${page.accessNote}" else page.body
            MetroText(body, MetroType.body, color = metro.secondary)
        }
        Spacer(Modifier.weight(1f).heightIn(min = 24.dp))
    }
}

private val TileSide = 132.dp
private val TileStep = 24.dp
private val TileOpacities = listOf(0.35f, 0.65f, 1f)

/** The logo's stacked tiles with a feature glyph on the front one, so every page looks like part of the same family. */
@Composable
private fun GlyphStack(glyph: ImageVector, shown: (Int) -> Float) {
    val metro = LocalMetro.current
    Box(Modifier.size(TileSide + TileStep * 2)) {
        for (index in 0..2) {
            val back = 2 - index
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .graphicsLayer {
                        translationX = (TileStep * back).toPx()
                        translationY = -(TileStep * back).toPx() + (1 - shown(index)) * 60.dp.toPx()
                        alpha = shown(index).coerceIn(0f, 1f)
                    }
                    .size(TileSide)
                    .background(metro.accentColor.copy(alpha = TileOpacities[index])),
                contentAlignment = Alignment.Center,
            ) {
                if (index == 2) Image(glyph, null, Modifier.size(64.dp), colorFilter = ColorFilter.tint(Color.White))
            }
        }
    }
}
