package com.buddhanag.luminux.feature.grid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import coil3.request.SuccessResult
import coil3.toBitmap
import com.buddhanag.luminux.container
import com.buddhanag.luminux.data.AssetSource
import com.buddhanag.luminux.data.MediaItem
import com.buddhanag.luminux.data.MediaType
import com.buddhanag.luminux.data.MonthSection
import com.buddhanag.luminux.data.SettingsKeys
import com.buddhanag.luminux.data.Thumbnails
import com.buddhanag.luminux.designsystem.LocalFeatherRowOrigin
import com.buddhanag.luminux.designsystem.LocalGridPinching
import com.buddhanag.luminux.designsystem.LocalMetro
import com.buddhanag.luminux.designsystem.MetroIcons
import com.buddhanag.luminux.designsystem.MetroMetrics
import com.buddhanag.luminux.designsystem.MetroMotion
import com.buddhanag.luminux.designsystem.MetroText
import com.buddhanag.luminux.designsystem.MetroType
import com.buddhanag.luminux.designsystem.Semilight
import com.buddhanag.luminux.designsystem.metroFeather
import com.buddhanag.luminux.designsystem.metroFont
import com.buddhanag.luminux.designsystem.tiltPress
import com.buddhanag.luminux.feature.shell.Route
import com.buddhanag.luminux.feature.viewer.zoomSource
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The square photo grid every photo list uses (AssetGrid.swift): optional month headers, pinch through 3/5/7/15
 * columns with the photos sorting into place (Pro), long-press or "select" for select mode, a sideways swipe to select
 * a range, and at 15 columns a tap that zooms back in.
 *
 * Tapping a photo opens the viewer on [source]. The column count is the saved `gridColumns` setting (5 without Pro),
 * shared by every grid; a pinch saves the new count.
 *
 * Rows are worked out ([GridLayout]) rather than measured, and only the ones near the screen are composed, placed
 * against the grid's own scroll position: a big library is taller than any Compose layout may be.
 *
 * @param source the photos to show, resolved against the library on every change.
 * @param selection the screen's select mode; the app bar reads the same model for its buttons and overline.
 * @param sections month headers above each month's photos (the "all" grid passes the library's sections); null for none.
 * @param emptyMessage shown once the library has loaded and [source] is empty.
 * @param onHeaderTap makes the month headers tappable, to open the jump list.
 * @param jumpTarget a [MonthSection.id] to scroll to the top of the grid; [onJumpConsumed] runs once it has scrolled,
 *   so the same month can be picked again.
 * @param contentPadding only its bottom is used: the space a floating app bar covers. The grid adds its own 24 dp.
 * @param header sits above the photos and scrolls away with them (the journal entry's photos and title).
 */
@Composable
fun AssetGrid(
    source: AssetSource,
    selection: SelectionModel,
    modifier: Modifier = Modifier,
    sections: List<MonthSection>? = null,
    emptyMessage: String = "Nothing here yet.",
    onHeaderTap: (() -> Unit)? = null,
    jumpTarget: String? = null,
    onJumpConsumed: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
    header: (@Composable () -> Unit)? = null,
) {
    val container = LocalContext.current.container
    val metro = LocalMetro.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val snapshot by container.photos.snapshot.collectAsStateWithLifecycle()
    val items = remember(snapshot, source) { container.photos.items(source) }
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val isProUnlocked by container.proStore.isUnlocked.collectAsStateWithLifecycle()
    val controller = remember { PinchController(scope) }
    val pinch = controller.pinch
    val scroll = controller.scroll
    var viewport by remember { mutableStateOf(Size.Zero) }
    var headerHeight by remember { mutableIntStateOf(0) }
    var showsProNote by remember { mutableStateOf(false) }
    var swipe by remember { mutableStateOf<SwipeState?>(null) }

    // Grid sizes come with Pro; without it every grid stays at the default. A level switch applies at once, and the
    // saved value catches up a moment later.
    val savedColumns = settings?.gridColumns ?: GridZoom.DEFAULT_COLUMNS
    LaunchedEffect(savedColumns) { controller.pinchedColumns = null }
    val gutter = with(density) { MetroMetrics.gutter.toPx() }
    val bottomInset = with(density) { contentPadding.calculateBottomPadding().toPx() }
    val ranges = remember(sections) { sections?.map { it.range } }
    controller.unlocksPro = isProUnlocked || container.proStore.isTestBuild
    controller.savedColumns = savedColumns
    controller.items = items
    controller.viewport = viewport
    controller.bottomInset = bottomInset
    controller.setLayoutInputs(viewport.width, gutter, ranges, with(density) { HEADER_HEIGHT.toPx() }, with(density) { 24.dp.toPx() })
    controller.cache = { list, px -> container.thumbnailPrefetcher.cache(list, px) }
    controller.preload = { list, around, px -> container.thumbnailPrefetcher.preload(list, around, px) }
    controller.showProNote = { showsProNote = true }
    controller.saveColumns = { c -> scope.launch { container.settings.set(SettingsKeys.gridColumns, c) } }
    val columns = controller.columns
    val layout = controller.layout(columns)
    val isOverview = columns == GridZoom.levels.last()
    val topInset = headerHeight.toFloat()
    scroll.minOffset = -topInset
    scroll.maxOffset = max(layout.height + bottomInset - viewport.height, -topInset)
    // A source that shrank (photos deleted) leaves the grid scrolled past its end.
    SideEffect { if (!pinch.isActive && scroll.offsetY > scroll.maxOffset) scroll.scrollTo(scroll.maxOffset) }

    LaunchedEffect(columns) {
        selection.isAllowed = GridZoom.allowsSelection(columns)
        if (!selection.isAllowed && selection.isActive) selection.end()
    }
    // The pivot around the grid holds still while it's pinched.
    val pinching = LocalGridPinching.current
    LaunchedEffect(pinch.isActive) { pinching?.value = pinch.isActive }
    DisposableEffect(pinching) { onDispose { if (pinch.isActive) pinching?.value = false } }
    // Once the grid has settled at its new level after the fingers lift, not at each level a pinch passes.
    LaunchedEffect(pinch.settledLevels) {
        if (pinch.settledLevels > 0) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    LaunchedEffect(jumpTarget, layout) {
        val target = jumpTarget ?: return@LaunchedEffect
        if (viewport.width <= 0f) return@LaunchedEffect
        val position = sections?.indexOfFirst { it.id == target } ?: -1
        layout.headerTop(position)?.let { top -> scroll.scrollTo((top + scroll.minOffset).coerceIn(scroll.minOffset, scroll.maxOffset)) }
        onJumpConsumed()
    }
    LaunchedEffect(showsProNote) {
        if (!showsProNote) return@LaunchedEffect
        delay(3_000)
        showsProNote = false
    }

    // Rows above the screen shouldn't hold up the feather of the visible ones.
    val featherOrigin = remember { mutableIntStateOf(0) }
    val currentLayout by rememberUpdatedState(layout)
    val currentTopInset by rememberUpdatedState(topInset)
    LaunchedEffect(Unit) {
        snapshotFlow { currentLayout.rowAt(max(scroll.offsetY + currentTopInset, 0f)) }.collect { featherOrigin.intValue = it }
    }

    // Composition changes only when the scroll crosses a sixth of the screen; in between, scrolling only re-places.
    val band = max(viewport.height / BANDS_PER_SCREEN, 1f)
    val renderBand by remember(band) { derivedStateOf { floor(scroll.offsetY / band).toInt() } }
    val drawnTop = (renderBand - 1) * band
    val drawn = Rect(0f, drawnTop, viewport.width, drawnTop + viewport.height + 3 * band)
    val rows = remember(layout, drawn) { layout.rows(drawn) }
    val headers = remember(layout, drawn) { layout.headers(drawn) }
    LaunchedEffect(renderBand, columns) {
        delay(300)
        if (!pinch.isActive) layout.indexNearest(Offset(viewport.width / 2, scroll.offsetY + viewport.height / 2))?.let(controller::preloadWider)
    }

    // region Swipe to select

    fun photoAt(point: Offset): Int? =
        controller.layout(controller.columns).indexAt(Offset(point.x, point.y + scroll.offsetY))?.takeIf { it < controller.items.size }

    /** Selects (or clears) everything from the photo the swipe started on to the one under the finger. */
    fun extendSwipe() {
        val state = swipe ?: return
        val current = photoAt(state.location) ?: state.current
        if (current != state.current) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        swipe = state.copy(current = current)
        val ids = controller.items
        selection.updateSwipe((min(state.anchor, current)..max(state.anchor, current)).mapNotNull { ids.getOrNull(it)?.id })
    }

    // Scrolls while the finger rests near the top or bottom edge, so a swipe can reach photos off screen.
    LaunchedEffect(swipe != null) {
        val zone = with(density) { 72.dp.toPx() }
        val maxStep = with(density) { 18.dp.toPx() }
        while (true) {
            val y = swipe?.location?.y ?: break
            val top = currentTopInset
            val bottom = viewport.height - bottomInset
            val step = when {
                y < top + zone -> -maxStep * min((top + zone - y) / zone, 1f)
                y > bottom - zone -> maxStep * min((y - (bottom - zone)) / zone, 1f)
                else -> 0f
            }
            // The finger hasn't moved, but the photos under it have.
            if (step != 0f && scroll.scrollBy(step) != 0f) extendSwipe()
            delay(16)
        }
    }

    // endregion

    val scrollState = rememberScrollableState { delta -> -scroll.scrollBy(-delta) }
    Box(
        modifier
            .fillMaxSize()
            // A grid under month titles sits in from the edges, lined up with them. Insetting the whole grid keeps
            // every gesture's coordinates the grid's own.
            .padding(horizontal = if (sections == null) 0.dp else TITLED_INSET)
            .clipToBounds()
            .onSizeChanged { viewport = it.toSize() },
    ) {
        if (items.isEmpty()) {
            if (snapshot.isLoaded) {
                MetroText(
                    emptyMessage, MetroType.body,
                    Modifier.padding(top = 8.dp + with(density) { headerHeight.toDp() }).padding(horizontal = MetroMetrics.margin).metroFeather(row = 0),
                    color = metro.secondary,
                )
            }
        } else Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectPinch(controller) }
                .scrollable(scrollState, Orientation.Vertical, enabled = !pinch.isActive, flingBehavior = ScrollableDefaults.flingBehavior())
                .pointerInput(selection.isActive) {
                    if (!selection.isActive) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (pinch.isActive) return@awaitEachGesture
                        // Only a sideways start becomes a swipe; vertical drags still scroll.
                        awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                            ?: return@awaitEachGesture
                        photoAt(down.position)?.let { anchor ->
                            selection.beginSwipe(on = controller.items[anchor].id)
                            swipe = SwipeState(anchor, anchor, down.position)
                            extendSwipe()
                        }
                        // Consumed even off a photo, so the pivot doesn't page in select mode.
                        drag(down.id) { change ->
                            change.consume()
                            swipe?.let {
                                swipe = it.copy(location = change.position)
                                extendSwipe()
                            }
                        }
                        swipe = null
                    }
                }
                // At 15 columns the photos aren't buttons; a tap anywhere (month titles too) zooms in there.
                .pointerInput(isOverview) {
                    if (isOverview) detectTapGestures { if (!pinch.isActive) controller.zoomIn(it) }
                },
        ) {
            CompositionLocalProvider(LocalFeatherRowOrigin provides featherOrigin) {
                Layout(
                    content = {
                        for (h in headers) key("header", h.position) {
                            MonthHeader(
                                sections!![h.position], items, selection, if (isOverview) null else onHeaderTap,
                                Modifier.metroFeather(row = h.row),
                            )
                        }
                        for (slot in rows) key(slot.row) {
                            GridRow(slot, items, layout, isOverview, selection, controller, source)
                        }
                    },
                    // The pinch layer stands in for the grid (month titles too) while it's on screen.
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (pinch.hidesTiles || pinch.isRebased) 0f else 1f },
                ) { measurables, constraints ->
                    val headerWidth = max((layout.columns * layout.pitch - layout.gutter).roundToInt(), 0)
                    val headerHeightPx = layout.headerHeight.roundToInt()
                    val rowHeight = max(layout.side.roundToInt(), 0)
                    val placeables = measurables.mapIndexed { i, m ->
                        if (i < headers.size) m.measure(Constraints.fixed(headerWidth, headerHeightPx))
                        else m.measure(Constraints.fixed(constraints.maxWidth, rowHeight))
                    }
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        // Read here, so a scroll only re-places the rows.
                        val offset = scroll.offsetY
                        placeables.forEachIndexed { i, placeable ->
                            val y = if (i < headers.size) headers[i].frame.top else rows[i - headers.size].y
                            placeable.place(0, (y - offset).roundToInt())
                        }
                    }
                }
            }
            PinchLayer(pinch, items, metro.chrome)
        }
        if (header != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { headerHeight = it.height }
                    .offset { IntOffset(0, -scroll.position.roundToInt()) },
            ) { header() }
        }
        AnimatedVisibility(
            showsProNote,
            Modifier.align(Alignment.TopCenter).padding(horizontal = MetroMetrics.margin),
            enter = slideInVertically(MetroMotion.standard()) { -it } + fadeIn(MetroMotion.standard()),
            exit = fadeOut(MetroMotion.fade()),
        ) {
            ProNote {
                showsProNote = false
                container.navigator.push(Route.Pro)
            }
        }
    }
}

/**
 * Two fingers, read before the scroll and the photos see them: the running scale, the point between the fingers (the
 * last one while both were down) and the scale's speed. Once started the touch is the pinch's until every finger lifts.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectPinch(controller: PinchController) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var started = false
        var ended = false
        var scale = 1f
        var centroid = Offset.Zero
        val tracker = VelocityTracker1D(isDataDifferential = false)
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.count { it.pressed }
                if (pressed >= 2 && !ended) {
                    scale *= event.calculateZoom()
                    centroid = event.calculateCentroid(useCurrent = true)
                    val time = event.changes.first().uptimeMillis
                    if (!started) {
                        // Past the touch slop, so two fingers scrolling together don't start a pinch.
                        if (abs(1 - scale) * event.calculateCentroidSize(useCurrent = false) > viewConfiguration.touchSlop) {
                            started = true
                            tracker.addDataPoint(time, scale)
                            controller.pinchChanged(PinchPhase.Began, scale, 0f, centroid)
                        }
                    } else {
                        tracker.addDataPoint(time, scale)
                        controller.pinchChanged(PinchPhase.Changed, scale, tracker.calculateVelocity(), centroid)
                    }
                } else if (started && !ended) {
                    ended = true
                    controller.pinchChanged(PinchPhase.Ended, scale, tracker.calculateVelocity(), centroid)
                }
                // Until every finger is up, so the one left behind neither scrolls nor taps.
                if (started) event.changes.forEach { it.consume() }
                if (pressed == 0) break
            }
        } finally {
            // A cancelled touch (the system or a parent taking it) ends the pinch too, or the grid would stay locked.
            if (started && !ended) controller.pinchChanged(PinchPhase.Ended, scale, 0f, centroid)
        }
    }
}

/** One row of photos at its worked-out place; at 15 columns plain thumbnails, read out as one element. */
@Composable
private fun GridRow(
    slot: GridLayout.RowSlot,
    items: List<MediaItem>,
    layout: GridLayout,
    isOverview: Boolean,
    selection: SelectionModel,
    controller: PinchController,
    source: AssetSource,
) {
    val container = LocalContext.current.container
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val sidePx = max(layout.side.roundToInt(), 1)
    val pitch = layout.pitch
    val showsBadge = with(density) { layout.side.toDp() } >= BADGE_MIN_SIDE
    val count = slot.end - slot.start
    val rowModifier = if (isOverview) {
        Modifier.metroFeather(row = slot.row).clearAndSetSemantics {
            contentDescription = if (count == 1) "1 photo" else "$count photos"
            onClick(label = "Zooms in on them") { controller.zoomIn(toIndex = slot.start); true }
        }
    } else Modifier
    Layout(
        content = {
            for (i in slot.start until min(slot.end, items.size)) {
                val item = items[i]
                key(item.id) {
                    if (isOverview) {
                        AssetThumbnail(item, sidePx, showsBadge = false)
                    } else {
                        PhotoTile(
                            item,
                            isSelected = item.id in selection.selected,
                            isSelecting = selection.isActive,
                            pixelSide = sidePx,
                            showsBadge = showsBadge,
                            onClick = {
                                when {
                                    controller.pinch.isActive -> Unit
                                    selection.isActive -> selection.toggle(item.id)
                                    else -> container.navigator.openViewer(source, item.id, i)
                                }
                            },
                            onLongClick = if (selection.isActive || !selection.isAllowed) null else {
                                {
                                    if (!controller.pinch.isActive) {
                                        selection.begin(with = item.id)
                                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    }
                                }
                            },
                            modifier = Modifier.metroFeather(row = slot.row, column = i - slot.start),
                        )
                    }
                }
            }
        },
        modifier = rowModifier,
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints.fixed(sidePx, sidePx)) }
        layout(constraints.maxWidth, sidePx) {
            placeables.forEachIndexed { k, p -> p.place((k * pitch).roundToInt(), 0) }
        }
    }
}

/**
 * The pinch's photos, all drawn in one canvas (iOS draws them as Core Animation layers): a composable per photo took
 * most of every frame with hundreds of them. Only [PinchState.progress] changes per frame, read while drawing.
 */
@Composable
private fun PinchLayer(pinch: PinchState, items: List<MediaItem>, chrome: Color) {
    val context = LocalContext.current
    val loader = remember(context) { SingletonImageLoader.get(context) }
    val bitmaps = remember { mutableStateMapOf<Pair<PinchTile.Kind, Int>, ImageBitmap>() }
    val tiles = pinch.tiles
    // A new set of photos: keep the images still in it, start the rest from whatever size is cached, and load a
    // sharper one only where the cache has nothing that big. A newer set cancels the loads of this one.
    LaunchedEffect(tiles) {
        val ids = tiles.mapTo(HashSet()) { it.id }
        bitmaps.keys.filter { it !in ids }.forEach { bitmaps.remove(it) }
        val startScale = max(pinch.camera.startScale, 1f)
        coroutineScope {
            for (t in tiles) {
                val item = items.getOrNull(t.index) ?: continue
                // Photos already on screen ask for the size they were; new ones for their own cell, the size warmed for them.
                val magnified = t.kind == PinchTile.Kind.Shown || t.kind == PinchTile.Kind.Edge || t.kind == PinchTile.Kind.Moving
                val side = Thumbnails.requestSide((t.unsorted.width * (if (magnified) startScale else 1f)).roundToInt())
                val cached = Thumbnails.cached(loader, item)
                if (t.id !in bitmaps) cached?.let { bitmaps[t.id] = it.first.toBitmap().asImageBitmap() }
                if ((cached?.second ?: 0) >= side) continue
                launch {
                    val result = loader.execute(Thumbnails.gridRequest(context, item, side))
                    (result as? SuccessResult)?.let { bitmaps[t.id] = it.image.toBitmap().asImageBitmap() }
                }
            }
        }
    }
    Spacer(
        Modifier
            .fillMaxSize()
            .clearAndSetSemantics {}
            .drawBehind {
                val shown = pinch.tiles
                if (shown.isEmpty()) return@drawBehind
                val p = pinch.progress
                val cam = pinch.camera
                val s = cam.drawnSide(shown.first().unsorted.width, p)
                val sorted = sortedness(p, pinch.sortRange)
                val o = cam.offset(p)
                val k = cam.scale(p)
                withTransform({
                    translate(o.x, o.y)
                    scale(k, k, pivot = Offset.Zero)
                }) {
                    for (t in shown) {
                        val a = t.opacity(p, sorted)
                        if (a <= 0f) continue
                        val c = t.center(p)
                        val topLeft = Offset(c.x - s / 2, c.y - s / 2)
                        // One fade for a photo and its backdrop, as a layer's opacity fades both: drawing each at `a`
                        // let the chrome show through and dimmed the dissolve.
                        val bitmap = bitmaps[t.id]
                        if (bitmap != null) drawCropped(bitmap, topLeft, s, a) else drawRect(chrome, topLeft, Size(s, s), alpha = a)
                    }
                }
            },
    )
}

/** Draws the centre square of [image] into the square at [topLeft], like ContentScale.Crop, without rounding to pixels. */
private fun DrawScope.drawCropped(image: ImageBitmap, topLeft: Offset, side: Float, alpha: Float) {
    val src = min(image.width, image.height)
    if (src <= 0) return
    withTransform({
        translate(topLeft.x, topLeft.y)
        scale(side / src, side / src, pivot = Offset.Zero)
    }) {
        drawImage(image, IntOffset((image.width - src) / 2, (image.height - src) / 2), IntSize(src, src), IntOffset.Zero, IntSize(src, src), alpha = alpha)
    }
}

private data class SwipeState(val anchor: Int, val current: Int, val location: Offset)

/** Grids with month titles sit this far in from the edges, lined up with the titles (AssetGrid.swift `titledInset`). */
private val TITLED_INSET = 8.dp
/** A month title's row (GridLayout.swift `headerHeight`). */
private val HEADER_HEIGHT = 60.dp
/** Smaller tiles (the 15-column overview) drop the video badge, which would cover the photo. */
private val BADGE_MIN_SIDE = 60.dp
/** The drawn rows reach a band above the screen and two below; the composition changes once per band. */
private const val BANDS_PER_SCREEN = 6f

@Composable
private fun MonthHeader(
    section: MonthSection,
    items: List<MediaItem>,
    selection: SelectionModel,
    onTap: (() -> Unit)?,
    modifier: Modifier,
) {
    val metro = LocalMetro.current
    val title = section.title()
    Row(modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        // Just the name and a little around it can be tapped, not the whole row.
        MetroText(
            title, metroFont(20.sp, Semilight),
            Modifier
                .then(if (onTap != null) Modifier.clickable(onClickLabel = "Shows all months", onClick = onTap) else Modifier)
                .padding(top = 8.dp, bottom = 8.dp, end = 8.dp)
                .semantics { heading() },
            color = metro.accentColor, maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        AnimatedVisibility(selection.isActive, enter = fadeIn(MetroMotion.standard()), exit = fadeOut(MetroMotion.standard())) {
            val ids = section.range.filter { it < items.size }.map { items[it].id }
            val count = ids.count { it in selection.selected }
            val all = count == ids.size
            Row {
                Box(
                    Modifier
                        .size(44.dp)
                        .clickable(role = Role.Checkbox) { selection.toggleAll(ids) }
                        .semantics { contentDescription = if (all) "Clear $title" else "Select all of $title" },
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    SelectionBox(isSelected = all, isMixed = count in 1 until ids.size)
                }
                Spacer(Modifier.width(6.dp))
            }
        }
    }
}

@Composable
private fun PhotoTile(
    item: MediaItem,
    isSelected: Boolean,
    isSelecting: Boolean,
    pixelSide: Int,
    showsBadge: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier,
) {
    val metro = LocalMetro.current
    val interaction = remember { MutableInteractionSource() }
    val label = remember(item) {
        val kind = if (item.type == MediaType.VIDEO) "Video" else "Photo"
        item.effectiveDate?.let { "$kind, ${DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT).format(Date(it))}" } ?: kind
    }
    Box(
        modifier
            .tiltPress(interaction, trackTouch = false)
            .combinedClickable(
                interactionSource = interaction, indication = null, hapticFeedbackEnabled = false,
                onLongClick = onLongClick, onClick = onClick,
            )
            .semantics {
                contentDescription = label
                if (isSelecting) selected = isSelected
            },
    ) {
        // The viewer zooms out of, and back into, this image.
        AssetThumbnail(item, pixelSide, Modifier.matchParentSize().zoomSource(item.id), showsBadge)
        AnimatedVisibility(
            isSelecting, Modifier.align(Alignment.TopEnd).padding(6.dp),
            enter = fadeIn(MetroMotion.standard()), exit = fadeOut(MetroMotion.standard()),
        ) {
            SelectionBox(isSelected)
        }
        if (isSelected) Box(Modifier.matchParentSize().border(3.dp, metro.accentColor))
    }
}

/** "Grid sizes come with Luminux Pro", shown for 3 s when a pinch without Pro springs back. */
@Composable
private fun ProNote(onClick: () -> Unit) {
    val metro = LocalMetro.current
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .tiltPress(interaction, trackTouch = false)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .background(metro.accentColor)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(MetroIcons.lock, null, Modifier.size(14.dp), colorFilter = ColorFilter.tint(Color.White))
        MetroText("Grid sizes come with Luminux Pro. Tap to see what it adds.", metroFont(15.sp, Semilight), color = Color.White)
    }
}
