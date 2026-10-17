package com.buddhanag.luminux.feature.grid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buddhanag.luminux.container
import com.buddhanag.luminux.data.AssetSource
import com.buddhanag.luminux.data.MediaItem
import com.buddhanag.luminux.data.MediaType
import com.buddhanag.luminux.data.MonthSection
import com.buddhanag.luminux.data.SettingsKeys
import com.buddhanag.luminux.data.Thumbnails
import com.buddhanag.luminux.designsystem.LocalFeatherRowOrigin
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
import com.buddhanag.luminux.feature.viewer.zoomSource
import com.buddhanag.luminux.feature.shell.Route
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The square photo grid every photo list uses (AssetGrid.swift): optional month headers, pinch to change the column
 * count (2/3/4/6/8, Pro), long-press or "select" for select mode, and a sideways swipe to select a range.
 *
 * Tapping a photo opens the viewer on [source]. The column count is the saved `gridColumns` setting (4 without Pro),
 * shared by every grid; a pinch saves the new count.
 *
 * @param source the photos to show, resolved against the library on every change.
 * @param selection the screen's select mode; the app bar reads the same model for its buttons and overline.
 * @param sections month headers above each month's photos (the "all" grid passes the library's sections); null for none.
 * @param emptyMessage shown once the library has loaded and [source] is empty.
 * @param onHeaderTap makes the month headers tappable, to open the jump list.
 * @param jumpTarget a [MonthSection.id] to scroll to the top of the grid; [onJumpConsumed] runs once it has scrolled,
 *   so the same month can be picked again.
 * @param contentPadding only its bottom is used: the space a floating app bar covers. The grid adds its own 24 dp.
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
) {
    val container = LocalContext.current.container
    val metro = LocalMetro.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val snapshot by container.photos.snapshot.collectAsStateWithLifecycle()
    val items = remember(snapshot, source) { container.photos.items(source) }
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val isProUnlocked by container.isProUnlocked.collectAsStateWithLifecycle()
    val unlocksPro = isProUnlocked || container.isTestBuild
    // Grid sizes come with Pro; without it every grid stays at the default. A pinch switches this at once, and the
    // saved value catches up a moment later.
    val savedColumns = settings?.gridColumns ?: GridZoom.DEFAULT_COLUMNS
    var pinchedColumns by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(savedColumns) { pinchedColumns = null }
    val columns = if (unlocksPro) pinchedColumns ?: savedColumns else GridZoom.DEFAULT_COLUMNS

    val entries = remember(items.size, sections) { gridEntries(items.size, sections) }
    val feather = remember(entries, columns) { featherPositions(entries, columns) }
    val gridState = rememberLazyGridState()
    val pinch = remember { PinchState() }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var showsProNote by remember { mutableStateOf(false) }
    var swipe by remember { mutableStateOf<SwipeState?>(null) }
    val gutter = with(density) { MetroMetrics.gutter.toPx() }
    val bottomPadding = contentPadding.calculateBottomPadding()
    val tileSide = GridZoom.tileSide(columns, viewport.width.toFloat(), gutter)
    val latest by rememberUpdatedState(Latest(items, entries, sections, columns, unlocksPro))
    val consumeJump by rememberUpdatedState(onJumpConsumed)

    // Rows above the screen shouldn't hold up the feather of the visible ones.
    val featherOrigin = remember { mutableIntStateOf(0) }
    LaunchedEffect(gridState, feather) {
        snapshotFlow { gridState.firstVisibleItemIndex }.collect { featherOrigin.intValue = feather.getOrNull(it)?.first ?: 0 }
    }
    LaunchedEffect(jumpTarget, entries) {
        val target = jumpTarget ?: return@LaunchedEffect
        headerIndex(entries, target).takeIf { it >= 0 }?.let { gridState.scrollToItem(it) }
        consumeJump()
    }
    LaunchedEffect(showsProNote) {
        if (!showsProNote) return@LaunchedEffect
        delay(3_000)
        showsProNote = false
    }

    fun photoAt(point: Offset): Int? = gridState.photoAt(point, latest.entries)

    // region Pinch to zoom

    fun pinchBegan(centroid: Offset) {
        if (viewport.width == 0 || viewport.height == 0) return
        pinch.active = true
        pinch.startCentroid = centroid
        pinch.origin = TransformOrigin(centroid.x / viewport.width, centroid.y / viewport.height)
        pinch.offset = Offset.Zero
        pinch.limits = GridZoom.scaleLimits(latest.columns)
        // The photo between the fingers, and where in it they are, so it can stay under them across the change.
        val hit = gridState.layoutInfo.visibleItemsInfo.firstOrNull {
            latest.entries.getOrNull(it.index) is GridEntry.Photo && Rect(it.offset.toOffset(), it.size.toSize()).contains(centroid)
        }
        if (hit != null) {
            pinch.anchorIndex = (latest.entries[hit.index] as GridEntry.Photo).assetIndex
            pinch.anchorUnit = Offset((centroid.x - hit.offset.x) / hit.size.width, (centroid.y - hit.offset.y) / hit.size.height)
        } else {
            pinch.anchorIndex = gridState.layoutInfo.visibleItemsInfo
                .firstNotNullOfOrNull { (latest.entries.getOrNull(it.index) as? GridEntry.Photo)?.assetIndex }
            pinch.anchorUnit = Offset(0.5f, 0.5f)
        }
    }

    fun pinchChanged(scale: Float, centroid: Offset) {
        pinch.scale = GridZoom.rubberBand(scale, pinch.limits)
        pinch.offset = centroid - pinch.startCentroid
    }

    suspend fun settle() {
        val fromScale = pinch.scale
        val fromOffset = pinch.offset
        animate(0f, 1f, animationSpec = MetroMotion.standard()) { t, _ ->
            pinch.scale = fromScale + (1f - fromScale) * t
            pinch.offset = fromOffset * (1 - t)
        }
        pinch.active = false
    }

    /** Switches to the level the pinch chose without anything visibly moving, then lets the grid settle into it. */
    suspend fun pinchEnded(scale: Float, velocity: Float, centroid: Offset) {
        val now = latest
        val next = GridZoom.columnsAfter(now.columns, scale, velocity)
        val anchor = pinch.anchorIndex
        if (next != now.columns && !now.unlocksPro) {
            showsProNote = true
            settle()
        } else if (next != now.columns && anchor != null) {
            val width = viewport.width.toFloat()
            val oldSide = GridZoom.tileSide(now.columns, width, gutter)
            val newSide = GridZoom.tileSide(next, width, gutter)
            val visualScale = pinch.scale * oldSide / newSide
            // Where the photo under the fingers lands in the new layout, across and down.
            val fingerX = columnOf(anchor, now.sections, next) * (newSide + gutter) + pinch.anchorUnit.x * newSide
            val tileTop = centroid.y - pinch.anchorUnit.y * newSide
            // One frame: new columns, the anchor's row under the fingers, and a transform that hides the change.
            Snapshot.withMutableSnapshot {
                pinchedColumns = next
                pinch.origin = TransformOrigin(centroid.x / width, centroid.y / viewport.height)
                pinch.scale = visualScale
                pinch.offset = Offset(-(fingerX - centroid.x) * visualScale, 0f)
            }
            val anchorEntry = now.entries.indexOfFirst { it is GridEntry.Photo && it.assetIndex == anchor }
            gridState.requestScrollToItem(anchorEntry, -tileTop.roundToInt())
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            scope.launch { container.settings.set(SettingsKeys.gridColumns, next) }
            val span = next * 30
            container.thumbnailPrefetcher.cache(
                now.items.subList(max(anchor - span, 0), min(anchor + span, now.items.size)),
                Thumbnails.requestSide(newSide.roundToInt()),
            )
            // After the re-based frame, so the settle animates from it rather than from the pinch.
            withFrameNanos {}
            settle()
        } else {
            settle()
        }
    }

    // endregion
    // region Swipe to select

    /** Selects (or clears) everything from the photo the swipe started on to the one under the finger. */
    fun extendSwipe() {
        val state = swipe ?: return
        val current = photoAt(state.location) ?: state.current
        if (current != state.current) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        swipe = state.copy(current = current)
        val ids = latest.items
        selection.updateSwipe((min(state.anchor, current)..max(state.anchor, current)).mapNotNull { ids.getOrNull(it)?.id })
    }

    // Scrolls while the finger rests near the top or bottom edge, so a swipe can reach photos off screen.
    LaunchedEffect(swipe != null) {
        val zone = with(density) { 72.dp.toPx() }
        val maxStep = with(density) { 18.dp.toPx() }
        while (true) {
            val y = swipe?.location?.y ?: break
            val bottom = viewport.height - with(density) { bottomPadding.toPx() }
            val step = when {
                y < zone -> -maxStep * min((zone - y) / zone, 1f)
                y > bottom - zone -> maxStep * min((y - (bottom - zone)) / zone, 1f)
                else -> 0f
            }
            // The finger hasn't moved, but the photos under it have.
            if (step != 0f && gridState.scrollBy(step) != 0f) extendSwipe()
            delay(16)
        }
    }

    // endregion

    Box(
        modifier
            .fillMaxSize()
            .padding(horizontal = MetroMetrics.margin)
            .clipToBounds()
            .onSizeChanged { viewport = it },
    ) {
        if (items.isEmpty()) {
            if (snapshot.isLoaded) {
                MetroText(emptyMessage, MetroType.body, Modifier.padding(top = 8.dp).metroFeather(row = 0), color = metro.secondary)
            }
        } else Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Watches on the Initial pass so the pinch starts alongside a scroll; only two fingers are consumed.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var started = false
                        var scale = 1f
                        var centroid = Offset.Zero
                        val samples = ArrayDeque<Pair<Long, Float>>()
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.size >= 2 && (started || !pinch.active)) {
                                // With one finger lifted the centroid would jump to the other; keep the last of both.
                                centroid = pressed.fold(Offset.Zero) { sum, it -> sum + it.position } / pressed.size.toFloat()
                                if (!started) {
                                    started = true
                                    pinchBegan(centroid)
                                }
                                scale *= event.calculateZoom()
                                pinchChanged(scale, centroid)
                                samples += pressed[0].uptimeMillis to scale
                                while (samples.size > 2 && samples.last().first - samples.first().first > 100) samples.removeFirst()
                            } else if (started) {
                                val velocity = samples.velocity()
                                scope.launch { pinchEnded(scale, velocity, centroid) }
                                started = false
                            }
                            // Until every finger is up, so the one left behind neither scrolls nor taps.
                            if (started || pinch.active) event.changes.forEach { it.consume() }
                            if (pressed.isEmpty()) break
                        }
                    }
                }
                .graphicsLayer {
                    transformOrigin = pinch.origin
                    scaleX = pinch.scale
                    scaleY = pinch.scale
                    translationX = pinch.offset.x
                    translationY = pinch.offset.y
                },
        ) {
            CompositionLocalProvider(LocalFeatherRowOrigin provides featherOrigin) {
                LazyVerticalGrid(
                    GridCells.Fixed(columns),
                    Modifier
                        .fillMaxSize()
                        .pointerInput(selection.isActive) {
                            if (!selection.isActive) return@pointerInput
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                if (pinch.active) return@awaitEachGesture
                                // Only a sideways start becomes a swipe; the grid's own scroll claims vertical drags first.
                                awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                                    ?: return@awaitEachGesture
                                photoAt(down.position)?.let { anchor ->
                                    selection.beginSwipe(on = latest.items[anchor].id)
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
                        },
                    state = gridState,
                    contentPadding = PaddingValues(bottom = bottomPadding + 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(MetroMetrics.gutter),
                    verticalArrangement = Arrangement.spacedBy(MetroMetrics.gutter),
                    userScrollEnabled = !pinch.active,
                ) {
                    items(
                        count = entries.size,
                        key = { i ->
                            when (val entry = entries[i]) {
                                is GridEntry.Header -> entry.section.id
                                is GridEntry.Photo -> items[entry.assetIndex].id
                            }
                        },
                        span = { i -> GridItemSpan(if (entries[i] is GridEntry.Header) maxLineSpan else 1) },
                        contentType = { i -> if (entries[i] is GridEntry.Header) "header" else "photo" },
                    ) { i ->
                        val (row, column) = feather[i]
                        when (val entry = entries[i]) {
                            is GridEntry.Header -> MonthHeader(
                                entry.section, items, selection, onHeaderTap, Modifier.metroFeather(row = row),
                            )
                            is GridEntry.Photo -> {
                                val item = items[entry.assetIndex]
                                PhotoTile(
                                    item,
                                    isSelected = item.id in selection.selected,
                                    isSelecting = selection.isActive,
                                    pixelSide = tileSide.roundToInt(),
                                    onClick = {
                                        when {
                                            pinch.active -> Unit
                                            selection.isActive -> selection.toggle(item.id)
                                            else -> container.navigator.openViewer(source, item.id, entry.assetIndex)
                                        }
                                    },
                                    onLongClick = if (selection.isActive) null else {
                                        {
                                            if (!pinch.active) {
                                                selection.begin(with = item.id)
                                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                            }
                                        }
                                    },
                                    modifier = Modifier.metroFeather(row = row, column = column),
                                )
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            showsProNote,
            Modifier.align(Alignment.TopCenter),
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

/** What the gesture handlers need from the latest composition; they're installed once and outlive it. */
private data class Latest(
    val items: List<MediaItem>,
    val entries: List<GridEntry>,
    val sections: List<MonthSection>?,
    val columns: Int,
    val unlocksPro: Boolean,
)

/** The pinch's live transform, read only inside `graphicsLayer`, so a pinch frame doesn't recompose the tiles. */
private class PinchState {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    var origin by mutableStateOf(TransformOrigin.Center)
    /** From the second finger until the grid has settled; scrolling, taps and swipe-select wait for it. */
    var active by mutableStateOf(false)

    var startCentroid = Offset.Zero
    var limits = 1f..1f
    var anchorIndex: Int? = null
    /** Where in the anchor photo the fingers are, from its top-left corner (0..1 each way). */
    var anchorUnit = Offset(0.5f, 0.5f)
}

private data class SwipeState(val anchor: Int, val current: Int, val location: Offset)

/** The photo (index into the source's items) under [point], in the grid's own coordinates. */
private fun LazyGridState.photoAt(point: Offset, entries: List<GridEntry>): Int? =
    layoutInfo.visibleItemsInfo.firstOrNull { Rect(it.offset.toOffset(), it.size.toSize()).contains(point) }
        ?.let { (entries.getOrNull(it.index) as? GridEntry.Photo)?.assetIndex }

/** Scale per second over the last ~100 ms of the pinch. */
private fun ArrayDeque<Pair<Long, Float>>.velocity(): Float {
    if (size < 2) return 0f
    val seconds = (last().first - first().first) / 1000f
    return if (seconds <= 0f) 0f else (last().second - first().second) / seconds
}

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
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                .then(if (onTap != null) Modifier.clickable(onClickLabel = "Shows all months", onClick = onTap) else Modifier)
                .semantics { heading() },
            contentAlignment = Alignment.CenterStart,
        ) {
            MetroText(title, metroFont(20.sp, Semilight), color = metro.accentColor, maxLines = 1)
        }
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
            .aspectRatio(1f)
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
        AssetThumbnail(item, pixelSide, Modifier.matchParentSize().zoomSource(item.id))
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
