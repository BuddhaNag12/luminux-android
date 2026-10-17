package com.buddhanag.luminux.feature.viewer.editor

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.scale
import com.buddhanag.luminux.container
import com.buddhanag.luminux.data.Adjustment
import com.buddhanag.luminux.data.Adjustments
import com.buddhanag.luminux.data.CropAspect
import com.buddhanag.luminux.data.EditRecipe
import com.buddhanag.luminux.data.Look
import com.buddhanag.luminux.data.MediaItem
import com.buddhanag.luminux.data.NormRect
import com.buddhanag.luminux.data.CropMath
import com.buddhanag.luminux.data.PhotoEditing
import com.buddhanag.luminux.designsystem.AppBarButton
import com.buddhanag.luminux.designsystem.AppBarMenuItem
import com.buddhanag.luminux.designsystem.LocalMetro
import com.buddhanag.luminux.designsystem.MetroAppBarHost
import com.buddhanag.luminux.designsystem.MetroIcons
import com.buddhanag.luminux.designsystem.MetroMetrics
import com.buddhanag.luminux.designsystem.MetroMotion
import com.buddhanag.luminux.designsystem.MetroSlider
import com.buddhanag.luminux.designsystem.MetroText
import com.buddhanag.luminux.designsystem.MetroType
import com.buddhanag.luminux.designsystem.Semilight
import com.buddhanag.luminux.designsystem.metroFont
import com.buddhanag.luminux.feature.viewer.Spinner
import com.buddhanag.luminux.feature.viewer.swallowTouches
import com.buddhanag.luminux.feature.viewer.ViewerIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

internal enum class EditMode { Crop, Adjust, Looks }

private data class RenderKey(val recipe: EditRecipe, val includesCrop: Boolean, val original: Boolean)

/**
 * Crop, adjust and looks (EditorView.swift). Every save is a new copy, so the recipe always starts at identity and
 * the editor opens on adjust.
 */
@Composable
internal fun EditorScreen(item: MediaItem, onClose: () -> Unit) {
    val metro = LocalMetro.current
    val photos = LocalContext.current.container.photos
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var couldNotOpen by remember { mutableStateOf(false) }
    var recipe by remember { mutableStateOf(EditRecipe()) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var previewSize by remember { mutableStateOf(0 to 0) }
    /** Whether [preview] has the crop applied; the crop handles only fit an uncropped preview. */
    var previewIsCropped by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(EditMode.Adjust) }
    var adjusting by remember { mutableStateOf(Adjustment.EXPOSURE) }
    var aspect by remember { mutableStateOf(CropAspect.FREE) }
    var showsOriginal by remember { mutableStateOf(false) }
    var lookThumbnails by remember { mutableStateOf(mapOf<Look?, ImageBitmap>()) }
    var isSaving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var appBarExpanded by remember { mutableStateOf(false) }

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
    // Renders the newest state once the previous render finishes: a slider drag never queues renders or cancels one.
    LaunchedEffect(source) {
        val src = source ?: return@LaunchedEffect
        snapshotFlow { RenderKey(recipe, includesCrop = mode != EditMode.Crop, original = showsOriginal) }
            .conflate()
            .collect { key ->
                val shown = if (key.original) key.recipe.copy(adjustments = Adjustments()) else key.recipe
                val rendered = withContext(Dispatchers.Default) { PhotoEditing.render(src, shown, key.includesCrop) }
                preview = rendered.asImageBitmap()
                previewSize = rendered.width to rendered.height
                previewIsCropped = key.includesCrop
            }
    }

    fun setAspect(option: CropAspect) {
        aspect = option
        val src = source ?: return
        val (w, h) = recipe.rotatedSize(src.width, src.height)
        val ratio = option.ratio(w, h)
        recipe = recipe.copy(crop = if (ratio == null) NormRect.UNIT else CropMath.centered(ratio, w, h))
    }

    fun rotate() {
        recipe = recipe.rotatedLeft()
        if (aspect != CropAspect.FREE) setAspect(aspect)
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

    val loaded = source != null
    Box(Modifier.fillMaxSize().background(metro.background).pointerInput(Unit) { swallowTouches() }) {
        MetroAppBarHost(
            buttons = listOf(
                AppBarButton("rotate", ViewerIcons.rotateLeft, isEnabled = loaded) { rotate() },
                AppBarButton("save", MetroIcons.check, isEnabled = loaded && !isSaving) { save() },
                AppBarButton("cancel", ViewerIcons.xmark) { onClose() },
            ),
            expanded = appBarExpanded,
            onExpandedChange = { appBarExpanded = it },
            menuItems = listOf(
                AppBarMenuItem("reset all", isEnabled = !recipe.isIdentity) {
                    recipe = EditRecipe()
                    aspect = CropAspect.FREE
                },
            ),
        ) { padding ->
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
                BoxWithConstraints(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .holdToCompare(enabled = mode != EditMode.Crop) { showsOriginal = it }
                        .semantics { contentDescription = if (showsOriginal) "Original photo" else "Edited photo" },
                    contentAlignment = Alignment.Center,
                ) {
                    val image = preview
                    when {
                        image != null -> {
                            val density = LocalDensity.current
                            val boxW = with(density) { maxWidth.toPx() }
                            val boxH = with(density) { maxHeight.toPx() }
                            val (pw, ph) = previewSize
                            val scale = min(boxW / pw, boxH / ph)
                            val rect = Rect(
                                (boxW - pw * scale) / 2, (boxH - ph * scale) / 2,
                                (boxW + pw * scale) / 2, (boxH + ph * scale) / 2,
                            )
                            Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                            if (mode == EditMode.Crop && !previewIsCropped) {
                                CropOverlay(recipe.crop, { recipe = recipe.copy(crop = it) }, aspect, rect, pw, ph)
                            }
                        }
                        couldNotOpen -> MetroText("Couldn't open this photo for editing.", MetroType.body, color = metro.secondary)
                        else -> Spinner()
                    }
                }
                Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
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
                    mode, Modifier.height(96.dp).fillMaxWidth().alpha(if (loaded) 1f else 0.4f),
                    animationSpec = MetroMotion.fade(), label = "editMode",
                ) { shown ->
                    Box(Modifier.fillMaxSize().then(if (loaded) Modifier else Modifier.pointerInput(Unit) { swallowTouches() }), contentAlignment = Alignment.TopStart) {
                        when (shown) {
                            EditMode.Crop -> AspectRow(aspect, ::setAspect)
                            EditMode.Adjust -> AdjustControls(recipe.adjustments, adjusting, { adjusting = it }) { value ->
                                recipe = recipe.copy(adjustments = recipe.adjustments.with(adjusting, value), look = null)
                            }
                            EditMode.Looks -> LooksRow(recipe, lookThumbnails) { look ->
                                recipe = recipe.copy(adjustments = look?.adjustments ?: Adjustments(), look = look)
                            }
                        }
                    }
                }
                if (failed) {
                    MetroText("Couldn't save this edit. Try again.", MetroType.caption, Modifier.padding(bottom = 8.dp), color = metro.secondary)
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

@Composable
private fun AspectRow(aspect: CropAspect, onSelect: (CropAspect) -> Unit) {
    val metro = LocalMetro.current
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        CropAspect.entries.forEach { option ->
            MetroText(
                option.label, metroFont(17.sp, Semilight),
                Modifier.clickable(role = Role.Button) { onSelect(option) }.semantics { selected = option == aspect },
                color = if (option == aspect) metro.accentColor else metro.foreground,
            )
        }
    }
}

@Composable
private fun AdjustControls(adjustments: Adjustments, current: Adjustment, onSelect: (Adjustment) -> Unit, onValue: (Double) -> Unit) {
    val metro = LocalMetro.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Adjustment.entries.forEach { adjustment ->
                Row(
                    Modifier
                        .defaultMinSize(minHeight = 32.dp)
                        .clickable(role = Role.Button) { onSelect(adjustment) }
                        .semantics {
                            selected = adjustment == current
                            if (adjustments[adjustment] != 0.0) stateDescription = percent(adjustments[adjustment])
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    MetroText(
                        adjustment.title, metroFont(17.sp, Semilight),
                        color = if (adjustment == current) metro.accentColor else metro.foreground,
                    )
                    // Marks the sliders that have been moved.
                    if (adjustments[adjustment] != 0.0) Box(Modifier.size(5.dp).background(metro.accentColor))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetroSlider(current.title, adjustments[current], onValue, Modifier.weight(1f), range = current.range, resting = 0.0)
            MetroText(
                percent(adjustments[current]), MetroType.caption.copy(fontFeatureSettings = "tnum", textAlign = TextAlign.End),
                Modifier.width(40.dp), color = metro.secondary,
            )
        }
    }
}

@Composable
private fun LooksRow(recipe: EditRecipe, thumbnails: Map<Look?, ImageBitmap>, onSelect: (Look?) -> Unit) {
    val metro = LocalMetro.current
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (listOf<Look?>(null) + Look.entries).forEach { look ->
            val isSelected = if (look == null) recipe.adjustments.isNeutral else recipe.look == look
            val title = look?.title ?: "none"
            Column(
                Modifier
                    .clickable(role = Role.Button) { onSelect(look) }
                    .semantics { contentDescription = title; selected = isSelected },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier
                        .size(64.dp)
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
}

private fun percent(value: Double): String {
    val rounded = (value * 100).roundToInt()
    return if (rounded > 0) "+$rounded" else "$rounded"
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
