package com.buddhanag.luminux.feature.viewer.editor

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.scale
import com.buddhanag.luminux.container
import com.buddhanag.luminux.data.Adjustment
import com.buddhanag.luminux.data.AdjustmentGroup
import com.buddhanag.luminux.data.Adjustments
import com.buddhanag.luminux.data.CropAspect
import com.buddhanag.luminux.data.CropMath
import com.buddhanag.luminux.data.EditRecipe
import com.buddhanag.luminux.data.Look
import com.buddhanag.luminux.data.MediaItem
import com.buddhanag.luminux.data.NormRect
import com.buddhanag.luminux.data.PhotoEditing
import com.buddhanag.luminux.designsystem.AppBarButton
import com.buddhanag.luminux.designsystem.AppBarMenuItem
import com.buddhanag.luminux.designsystem.Feather
import com.buddhanag.luminux.designsystem.LocalMetro
import com.buddhanag.luminux.designsystem.LocalReduceMotion
import com.buddhanag.luminux.designsystem.MetroAppBarHost
import com.buddhanag.luminux.designsystem.MetroDial
import com.buddhanag.luminux.designsystem.MetroIcons
import com.buddhanag.luminux.designsystem.MetroMetrics
import com.buddhanag.luminux.designsystem.MetroMotion
import com.buddhanag.luminux.designsystem.MetroText
import com.buddhanag.luminux.designsystem.MetroType
import com.buddhanag.luminux.designsystem.Semilight
import com.buddhanag.luminux.designsystem.dialDegrees
import com.buddhanag.luminux.designsystem.dialText
import com.buddhanag.luminux.designsystem.dialValue
import com.buddhanag.luminux.designsystem.metroFont
import com.buddhanag.luminux.designsystem.tiltPress
import com.buddhanag.luminux.feature.viewer.Spinner
import com.buddhanag.luminux.feature.viewer.ViewerIcons
import com.buddhanag.luminux.feature.viewer.swallowTouches
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal enum class EditMode(val group: AdjustmentGroup?) {
    Crop(null), Light(AdjustmentGroup.LIGHT), Colour(AdjustmentGroup.COLOUR), Effects(AdjustmentGroup.EFFECTS), Looks(null)
}

/** Tools under the photo, below a fold's split line, or beside the photo. */
internal enum class EditorArrangement { Compact, Split, SideBySide }

/**
 * Chosen from the space, not the device: an expanded window (≥ 600 dp) wider than tall goes side by side, even with a
 * split line; otherwise a split line splits; otherwise compact.
 */
internal fun editorArrangement(width: Dp, height: Dp, splitY: Dp?): EditorArrangement = when {
    width >= 600.dp && width > height -> EditorArrangement.SideBySide
    splitY != null -> EditorArrangement.Split
    else -> EditorArrangement.Compact
}

private enum class PhotoTurn { Rotate, Flip }

/** A turn or flip waiting for the preview with this geometry, so the photo can animate into it. */
private data class PendingTurn(val turn: PhotoTurn, val turns: Int, val flipped: Boolean)

private data class RenderKey(val recipe: EditRecipe, val includesCrop: Boolean, val original: Boolean)

/**
 * Crop, light, colour, effects and looks (EditorView.swift). Each adjustment is changed on a ruler dial, or by swiping
 * across the photo; holding the photo, or the compare button, shows the original. Every save is a new copy, so the
 * recipe always starts at identity.
 *
 * [splitY] is the fold's split line in window coordinates, for the split arrangement.
 */
@Composable
internal fun EditorScreen(
    item: MediaItem,
    onClose: () -> Unit,
    // shortcut: spec 12G provides splitY (from WindowInfoTracker's FoldingFeature); until then the editor never splits.
    splitY: Dp? = null,
) {
    val metro = LocalMetro.current
    val reduceMotion = LocalReduceMotion.current
    val photos = LocalContext.current.container.photos
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var couldNotOpen by remember { mutableStateOf(false) }
    var recipe by remember { mutableStateOf(EditRecipe()) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var previewSize by remember { mutableStateOf(0 to 0) }
    /** Whether [preview] has the crop applied; the crop handles only fit an uncropped preview. */
    var previewIsCropped by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(EditMode.Crop) }
    /** The adjustment last picked in each group, so switching groups and back returns to it. */
    var picked by remember { mutableStateOf(mapOf<AdjustmentGroup, Adjustment>()) }
    var aspect by remember { mutableStateOf(CropAspect.FREE) }
    /** The crop shape's orientation when picked by hand; null follows the photo's. */
    var cropLandscape by remember { mutableStateOf<Boolean?>(null) }
    var isLevelling by remember { mutableStateOf(false) }
    var cropNote by remember { mutableStateOf<String?>(null) }
    var noteJob by remember { mutableStateOf<Job?>(null) }
    var pendingTurn by remember { mutableStateOf<PendingTurn?>(null) }
    /** 1 when the new preview is shown turned back to where the old one was, easing to 0. */
    val turnProgress = remember { Animatable(0f) }
    val flipProgress = remember { Animatable(0f) }
    /** The crop handles wait until the photo has turned into place. */
    var isTurning by remember { mutableStateOf(false) }
    /** Held down on the photo. */
    var showsOriginal by remember { mutableStateOf(false) }
    /** The compare button keeps the original showing. */
    var pinsOriginal by remember { mutableStateOf(false) }
    /** The value a swipe across the photo started from, while one is under way. */
    var swipeStart by remember { mutableStateOf<Double?>(null) }
    var lookThumbnails by remember { mutableStateOf(mapOf<Look?, ImageBitmap>()) }
    var isSaving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var appBarExpanded by remember { mutableStateOf(false) }
    var photoTop by remember { mutableStateOf(0.dp) }

    /** The adjustment the dial and a swipe change; null in crop and looks. */
    val active = mode.group?.let { picked[it] ?: it.adjustments.first() }

    BackHandler(onBack = onClose)
    LaunchedEffect(item.id) {
        val loaded = runCatching { photos.editPreview(item) }.getOrNull()
        if (loaded == null) {
            couldNotOpen = true
            return@LaunchedEffect
        }
        source = loaded
        // Look thumbnails come from a 160 px copy, one after another.
        val small = withContext(Dispatchers.Default) {
            val scale = 160f / maxOf(loaded.width, loaded.height)
            loaded.scale((loaded.width * scale).roundToInt().coerceAtLeast(1), (loaded.height * scale).roundToInt().coerceAtLeast(1))
        }
        (listOf<Look?>(null) + Look.entries).forEach { look ->
            val thumbnail = withContext(Dispatchers.Default) {
                PhotoEditing.render(small, EditRecipe(adjustments = look?.adjustments ?: Adjustments()), includingCrop = false).asImageBitmap()
            }
            lookThumbnails = lookThumbnails + (look to thumbnail)
        }
    }
    // Renders the newest state once the previous render finishes: a dial drag never queues renders or cancels one.
    LaunchedEffect(source) {
        val src = source ?: return@LaunchedEffect
        snapshotFlow { RenderKey(recipe, includesCrop = mode != EditMode.Crop, original = showsOriginal || pinsOriginal) }
            .conflate()
            .collect { key ->
                // Compare zeroes only the adjustments; rotation, mirror, straighten and crop stay.
                val shown = if (key.original) key.recipe.copy(adjustments = Adjustments()) else key.recipe
                val rendered = withContext(Dispatchers.Default) { PhotoEditing.render(src, shown, key.includesCrop) }
                val pending = pendingTurn
                if (pending != null && pending.turns == key.recipe.turns && pending.flipped == key.recipe.flipped) {
                    pendingTurn = null
                    if (!reduceMotion) {
                        // Show the new photo turned back to where the old one was, then let it turn into place.
                        val progress = if (pending.turn == PhotoTurn.Rotate) turnProgress else flipProgress
                        progress.snapTo(1f)
                        isTurning = true
                        scope.launch {
                            progress.animateTo(0f, tween(if (pending.turn == PhotoTurn.Rotate) 400 else 450, easing = Feather.Decelerate))
                            isTurning = false
                        }
                    }
                }
                preview = rendered.asImageBitmap()
                previewSize = rendered.width to rendered.height
                previewIsCropped = key.includesCrop
            }
    }

    /** The turned photo's size before cropping, for fitting crop ratios. */
    fun rotatedSize(): Pair<Int, Int> = source?.let { recipe.rotatedSize(it.width, it.height) } ?: (0 to 0)
    fun isLandscapeCrop() = cropLandscape ?: rotatedSize().let { (w, h) -> w >= h }
    fun cropRatio() = rotatedSize().let { (w, h) -> aspect.ratio(w, h, isLandscapeCrop()) }

    fun setAspect(option: CropAspect) {
        aspect = option
        val (w, h) = rotatedSize()
        val ratio = option.ratio(w, h, isLandscapeCrop())
        recipe = recipe.copy(crop = if (ratio == null) NormRect.UNIT else CropMath.centered(ratio, w, h))
    }

    fun rotate() {
        recipe = recipe.rotatedLeft()
        pendingTurn = PendingTurn(PhotoTurn.Rotate, recipe.turns, recipe.flipped)
        // The photo turned, so a shape picked by hand turns with it.
        cropLandscape = cropLandscape?.not()
        if (aspect != CropAspect.FREE) setAspect(aspect)
    }

    fun flip() {
        recipe = recipe.flippedHorizontally()
        pendingTurn = PendingTurn(PhotoTurn.Flip, recipe.turns, recipe.flipped)
    }

    fun showCropNote(note: String?) {
        noteJob?.cancel()
        cropNote = note
        if (note != null) noteJob = scope.launch {
            delay(2500)
            cropNote = null
        }
    }

    /** Finds the horizon and straightens it, like Photos' auto. */
    fun level() {
        val src = source ?: return
        val unstraightened = recipe
        isLevelling = true
        scope.launch {
            val angle = withContext(Dispatchers.Default) { PhotoEditing.levellingAngle(src, unstraightened) }
            isLevelling = false
            if (angle == null) return@launch showCropNote("No horizon found to level.")
            showCropNote(if (angle == 0.0) "Already level." else null)
            Animatable(recipe.straighten.toFloat()).animateTo(angle.toFloat(), MetroMotion.standard()) {
                recipe = recipe.copy(straighten = value.toDouble())
            }
            recipe = recipe.copy(straighten = angle)
        }
    }

    fun resetGeometry() {
        recipe = EditRecipe(adjustments = recipe.adjustments, look = recipe.look)
        aspect = CropAspect.FREE
        cropLandscape = null
    }

    fun save() {
        if (recipe.isIdentity) return onClose()
        isSaving = true
        failed = false
        scope.launch {
            val saved = runCatching { photos.saveEditedCopy(item, recipe) }.isSuccess
            isSaving = false
            if (saved) onClose() else failed = true
        }
    }

    fun setValue(adjustment: Adjustment, value: Double) {
        recipe = recipe.copy(adjustments = recipe.adjustments.with(adjustment, value), look = null)
    }

    val loaded = source != null

    @Composable
    fun PhotoPane(modifier: Modifier) {
        val currentActive by rememberUpdatedState(active)
        BoxWithConstraints(
            modifier
                .holdToCompare(enabled = mode != EditMode.Crop) { showsOriginal = it && swipeStart == null }
                // Swiping across the photo nudges the adjustment on the dial, like Snapseed.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var started = false
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val adjustment = currentActive ?: continue
                            val d = change.position - down.position
                            started = started || d.getDistance() >= 8.dp.toPx()
                            if (!started || abs(d.x) <= abs(d.y)) continue
                            change.consume()
                            val start = swipeStart ?: recipe.adjustments[adjustment].also { swipeStart = it }
                            showsOriginal = false
                            // Rightward raises it; the dial's own drag runs the other way, as its ruler moves with the finger.
                            setValue(adjustment, dialValue(start, -d.x.toDp().value, adjustment.range, 0.0))
                        } while (change.pressed)
                        swipeStart = null
                    }
                }
                .semantics { contentDescription = if (showsOriginal || pinsOriginal) "Original photo" else "Edited photo" },
            contentAlignment = Alignment.Center,
        ) {
            val image = preview
            when {
                image != null -> {
                    val density = LocalDensity.current
                    val boxW = with(density) { maxWidth.toPx() }
                    val boxH = with(density) { maxHeight.toPx() }
                    val (pw, ph) = previewSize
                    val rect = fittedRect(pw.toFloat(), ph.toFloat(), boxW, boxH)
                    // Turned back a quarter, the new photo sits as the old one did; this scale matches the old size.
                    val startScale = fittedRect(ph.toFloat(), pw.toFloat(), boxW, boxH).width / max(rect.height, 1f)
                    Image(
                        image, null,
                        Modifier.fillMaxSize().graphicsLayer {
                            // A flip swings the photo round like a card; a rotate turns it a quarter into place.
                            val turn = turnProgress.value
                            val scale = 1 + (startScale - 1) * turn
                            scaleX = (1 - 2 * flipProgress.value) * scale
                            scaleY = scale
                            rotationZ = 90 * turn
                        },
                        contentScale = ContentScale.Fit,
                    )
                    AnimatedVisibility(
                        mode == EditMode.Crop && !previewIsCropped && !isTurning,
                        enter = fadeIn(MetroMotion.fade()), exit = ExitTransition.None,
                    ) {
                        CropOverlay(recipe.crop, { recipe = recipe.copy(crop = it) }, cropRatio(), rect, pw, ph)
                    }
                }
                couldNotOpen -> MetroText("Couldn't open this photo for editing.", MetroType.body, color = metro.secondary)
                else -> Spinner()
            }
            AnimatedVisibility(
                swipeStart != null && active != null, Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                enter = fadeIn(MetroMotion.fade()), exit = fadeOut(MetroMotion.fade()),
            ) {
                val shown = active ?: return@AnimatedVisibility
                MetroText(
                    "${shown.title} ${dialText(recipe.adjustments[shown])}",
                    metroFont(17.sp, Semilight).copy(fontFeatureSettings = "tnum"),
                    Modifier.background(metro.background.copy(alpha = 0.75f)).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }

    @Composable
    fun CropControls(roomy: Boolean) {
        val (rw, rh) = rotatedSize()
        val landscape = isLandscapeCrop()
        val tools: List<@Composable (Modifier) -> Unit> = listOf(
            { m -> ToolTile("rotate", ViewerIcons.rotateLeft, m, roomy) { rotate() } },
            { m -> ToolTile("flip", EditorIcons.flip, m, roomy) { flip() } },
            { m -> ToolTile(if (isLevelling) "levelling" else "auto", EditorIcons.level, m, roomy, enabled = !isLevelling) { level() } },
            { m ->
                ToolTile(
                    if (landscape) "landscape" else "portrait", if (landscape) EditorIcons.landscape else EditorIcons.portrait,
                    m, roomy, enabled = aspect != CropAspect.FREE && aspect != CropAspect.SQUARE,
                ) {
                    cropLandscape = !landscape
                    setAspect(aspect)
                }
            },
            { m -> ToolTile("reset", EditorIcons.reset, m, roomy, enabled = !recipe.hasNoGeometry) { resetGeometry() } },
        )
        val aspects: List<@Composable (Modifier) -> Unit> = CropAspect.entries.map { option ->
            { m ->
                Tile(
                    { setAspect(option) }, m, roomy, minWidth = 68.dp, isSelected = option == aspect,
                    description = if (option == CropAspect.FREE) "free crop" else "${option.title} crop",
                ) { color ->
                    AspectShape(option.ratio(rw, rh, landscape) ?: 1.0, dashed = option == CropAspect.FREE, color)
                    MetroText(option.title, metroFont(14.sp, Semilight), color = color, maxLines = 1)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (roomy) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { tools.forEach { it(Modifier.weight(1f)) } }
                AdaptiveGrid(76.dp, aspects)
            } else {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    tools.forEach { it(Modifier) }
                    Box(Modifier.padding(horizontal = 4.dp).size(1.dp, 36.dp).background(metro.secondary.copy(alpha = 0.4f)))
                    aspects.forEach { it(Modifier) }
                }
            }
            MetroDial(
                "straighten", recipe.straighten, { recipe = recipe.copy(straighten = it) },
                Modifier.padding(top = if (roomy) 8.dp else 0.dp),
                range = EditRecipe.straightenRange, ticks = 90, format = ::dialDegrees,
            )
            AnimatedVisibility(cropNote != null, enter = fadeIn(MetroMotion.fade()), exit = fadeOut(MetroMotion.fade())) {
                MetroText(cropNote.orEmpty(), MetroType.caption, color = metro.secondary)
            }
        }
    }

    @Composable
    fun AdjustControls(group: AdjustmentGroup, adjustment: Adjustment, roomy: Boolean) {
        val chips: List<@Composable (Modifier) -> Unit> = group.adjustments.map { option ->
            { m ->
                val amount = recipe.adjustments[option]
                Tile(
                    { picked = picked + (group to option) }, m, roomy, minWidth = 92.dp,
                    isSelected = option == adjustment, description = option.title, state = if (amount == 0.0) null else dialText(amount),
                    spacing = 2.dp,
                ) { color ->
                    MetroText(option.title, metroFont(15.sp, Semilight), color = color, maxLines = 1)
                    MetroText(
                        if (amount == 0.0) " " else dialText(amount), MetroType.caption.copy(fontFeatureSettings = "tnum"),
                        color = color.copy(alpha = 0.85f),
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (roomy) {
                AdaptiveGrid(104.dp, chips)
            } else {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    chips.forEach { it(Modifier) }
                }
            }
            MetroDial(
                adjustment.title, recipe.adjustments[adjustment], { setValue(adjustment, it) },
                Modifier.padding(top = if (roomy) 8.dp else 0.dp), range = adjustment.range,
            )
        }
    }

    @Composable
    fun ToolPanel(modifier: Modifier, roomy: Boolean) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                EditMode.entries.forEach { option ->
                    MetroText(
                        option.name.lowercase(), metroFont(24.sp, Semilight),
                        Modifier
                            .clickable(role = Role.Button) { mode = option }
                            .semantics { selected = option == mode },
                        color = if (option == mode) metro.foreground else metro.secondary,
                    )
                }
            }
            Crossfade(
                mode,
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    // The compact panel's fixed height may cut the crop note; let it run past instead, as on iOS.
                    .then(if (roomy) Modifier else Modifier.wrapContentHeight(Alignment.Top, unbounded = true))
                    .alpha(if (loaded) 1f else 0.4f),
                animationSpec = MetroMotion.fade(), label = "editMode",
            ) { shown ->
                Box(if (loaded) Modifier else Modifier.pointerInput(Unit) { swallowTouches() }) {
                    when (shown) {
                        EditMode.Crop -> CropControls(roomy)
                        EditMode.Looks -> LooksPicker(recipe, lookThumbnails, roomy) { look ->
                            recipe = recipe.copy(adjustments = look?.adjustments ?: Adjustments(), look = look)
                        }
                        else -> shown.group?.let { group -> AdjustControls(group, picked[group] ?: group.adjustments.first(), roomy) }
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(metro.background).pointerInput(Unit) { swallowTouches() }) {
        MetroAppBarHost(
            buttons = listOf(
                AppBarButton(
                    if (pinsOriginal) "edited" else "compare", if (pinsOriginal) EditorIcons.eyeSlash else EditorIcons.eye,
                    isEnabled = loaded, id = "compare",
                ) { pinsOriginal = !pinsOriginal },
                AppBarButton("save", MetroIcons.check, isEnabled = loaded && !isSaving) { save() },
                AppBarButton("cancel", ViewerIcons.xmark) { onClose() },
            ),
            expanded = appBarExpanded,
            onExpandedChange = { appBarExpanded = it },
            menuItems = listOf(
                AppBarMenuItem("reset all", isEnabled = !recipe.isIdentity) {
                    recipe = EditRecipe()
                    aspect = CropAspect.FREE
                    cropLandscape = null
                },
            ),
        ) { padding ->
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val arrangement = editorArrangement(maxWidth, maxHeight, splitY)
                val roomy = arrangement != EditorArrangement.Compact
                Column(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(padding)
                        .padding(horizontal = MetroMetrics.margin + 12.dp)
                        .padding(top = 16.dp),
                ) {
                    MetroText("LUMINUX", MetroType.overline)
                    MetroText("edit", MetroType.title, Modifier.offset(x = (-3).dp))
                    when (arrangement) {
                        EditorArrangement.Compact -> {
                            PhotoPane(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp))
                            // A fixed height keeps the photo still as the tools change.
                            ToolPanel(Modifier.fillMaxWidth().padding(bottom = 8.dp).height(188.dp), roomy)
                        }
                        EditorArrangement.Split -> {
                            val density = LocalDensity.current
                            // Ends a little above the line, and the tools start well below it: nothing to touch in the fold.
                            PhotoPane(
                                Modifier
                                    .padding(top = 12.dp)
                                    .onGloballyPositioned { photoTop = with(density) { it.positionInWindow().y.toDp() } }
                                    .fillMaxWidth()
                                    .height(max((splitY ?: 0.dp).value - photoTop.value - 8, 120f).dp),
                            )
                            ToolPanel(Modifier.fillMaxWidth().weight(1f).padding(top = 28.dp), roomy)
                        }
                        EditorArrangement.SideBySide -> Row(
                            Modifier.weight(1f).padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(40.dp),
                        ) {
                            PhotoPane(Modifier.weight(1f).fillMaxHeight())
                            ToolPanel(Modifier.weight(1f).fillMaxHeight(), roomy)
                        }
                    }
                    if (failed) {
                        MetroText("Couldn't save this edit. Try again.", MetroType.caption, Modifier.padding(bottom = 8.dp), color = metro.secondary)
                    }
                }
            }
        }
        if (isSaving) {
            Box(Modifier.fillMaxSize().background(metro.background.copy(alpha = 0.6f)).pointerInput(Unit) { swallowTouches() }, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spinner()
                    MetroText("saving", MetroType.body, Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

private fun fittedRect(w: Float, h: Float, boxW: Float, boxH: Float): Rect {
    if (w <= 0f || h <= 0f) return Rect.Zero
    val scale = min(boxW / w, boxH / h)
    return Rect((boxW - w * scale) / 2, (boxH - h * scale) / 2, (boxW + w * scale) / 2, (boxH + h * scale) / 2)
}

/** A Metro tile for the editor's tools: chrome, or accent-filled when selected; it tilts while pressed. */
@Composable
private fun Tile(
    onClick: () -> Unit,
    modifier: Modifier,
    roomy: Boolean,
    minWidth: Dp,
    isSelected: Boolean = false,
    enabled: Boolean = true,
    description: String,
    state: String? = null,
    spacing: Dp = 6.dp,
    content: @Composable ColumnScope.(Color) -> Unit,
) {
    val metro = LocalMetro.current
    val interaction = remember { MutableInteractionSource() }
    val background by animateColorAsState(if (isSelected) metro.accentColor else metro.chrome, MetroMotion.snappy(), label = "tile")
    Column(
        modifier
            .tiltPress(interaction, trackTouch = false)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                selected = isSelected
                state?.let { stateDescription = it }
            }
            .alpha(if (enabled) 1f else 0.4f)
            .background(background)
            .then(if (roomy) Modifier.fillMaxWidth() else Modifier)
            .defaultMinSize(minWidth, 52.dp)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        content(if (isSelected) Color.White else metro.foreground)
    }
}

@Composable
private fun ToolTile(title: String, icon: ImageVector, modifier: Modifier, roomy: Boolean, enabled: Boolean = true, onClick: () -> Unit) =
    Tile(onClick, modifier, roomy, minWidth = 68.dp, enabled = enabled, description = title) { color ->
        Image(icon, null, Modifier.size(16.dp), colorFilter = ColorFilter.tint(color))
        MetroText(title, metroFont(14.sp, Semilight), color = color, maxLines = 1)
    }

/** A small outline of the crop shape, turned the way the crop is. */
@Composable
private fun AspectShape(ratio: Double, dashed: Boolean, color: Color) {
    Canvas(Modifier.size(16.dp)) {
        val side = size.width
        val w = if (ratio >= 1) side else side * ratio.toFloat()
        val h = if (ratio >= 1) side / ratio.toFloat() else side
        val line = 1.5.dp.toPx()
        drawRect(
            color, topLeft = androidx.compose.ui.geometry.Offset(line / 2, line / 2),
            size = androidx.compose.ui.geometry.Size(w - line, h - line),
            style = Stroke(line, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())) else null),
        )
    }
}

/** Cells at least [minCell] wide, as many to a row as fit, stretched to fill it (SwiftUI's adaptive grid). */
@Composable
private fun AdaptiveGrid(minCell: Dp, cells: List<@Composable (Modifier) -> Unit>, across: Dp = 6.dp, down: Dp = 6.dp) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = max(1, ((maxWidth + across) / (minCell + across)).toInt())
        Column(verticalArrangement = Arrangement.spacedBy(down)) {
            cells.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(across)) {
                    row.forEach { it(Modifier.weight(1f)) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun LooksPicker(recipe: EditRecipe, thumbnails: Map<Look?, ImageBitmap>, roomy: Boolean, onSelect: (Look?) -> Unit) {
    val metro = LocalMetro.current
    val tiles: List<@Composable (Modifier) -> Unit> = (listOf<Look?>(null) + Look.entries).map { look ->
        { m ->
            val isSelected = if (look == null) recipe.adjustments.isNeutral else recipe.look == look
            val title = look?.title ?: "none"
            val interaction = remember { MutableInteractionSource() }
            Column(
                m
                    .tiltPress(interaction, trackTouch = false)
                    .clickable(interaction, indication = null, role = Role.Button) { onSelect(look) }
                    .semantics { contentDescription = title; selected = isSelected },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    (if (roomy) Modifier.fillMaxWidth().aspectRatio(1f) else Modifier.size(64.dp))
                        .clipToBounds()
                        .background(metro.chrome),
                ) {
                    thumbnails[look]?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    if (isSelected) Box(Modifier.fillMaxSize().border(3.dp, metro.accentColor))
                }
                MetroText(title, MetroType.caption, color = if (isSelected) metro.accentColor else metro.foreground)
            }
        }
    }
    if (roomy) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
            AdaptiveGrid(88.dp, tiles, across = 8.dp, down = 10.dp)
        }
    } else {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tiles.forEach { it(Modifier.width(64.dp)) }
        }
    }
}

/** Touch and hold for 0.15 s without moving 40 dp shows the photo before adjustments, until the finger lifts. */
private fun Modifier.holdToCompare(enabled: Boolean, onHold: (Boolean) -> Unit) = if (!enabled) this else pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = 40.dp.toPx()
        // Finishes early when the finger lifts or wanders; times out (null) when it's held.
        val letGo = withTimeoutOrNull(150) {
            do {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
            } while (change != null && change.pressed && (change.position - down.position).getDistance() <= slop)
        }
        if (letGo != null) return@awaitEachGesture
        onHold(true)
        waitForUpOrCancellation()
        onHold(false)
    }
}
