package com.buddhanag.luminux.feature.settings

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buddhanag.luminux.container
import com.buddhanag.luminux.data.AppSettings
import com.buddhanag.luminux.data.HubBackgroundSource
import com.buddhanag.luminux.designsystem.LocalMetro
import com.buddhanag.luminux.designsystem.MetroIcons
import com.buddhanag.luminux.designsystem.MetroMetrics
import com.buddhanag.luminux.designsystem.MetroText
import com.buddhanag.luminux.designsystem.MetroType
import com.buddhanag.luminux.designsystem.Semilight
import com.buddhanag.luminux.designsystem.metroFont

/** The source in use: without Pro, always favorites, so a choice made before a refund comes back with Pro. */
fun effectiveBackground(settings: AppSettings, unlocksPro: Boolean): HubBackgroundSource =
    if (unlocksPro) settings.hubBackground else HubBackgroundSource.Favorites

fun backgroundName(source: HubBackgroundSource, album: (String) -> String?, photoSet: (String) -> String?): String = when (source) {
    HubBackgroundSource.Favorites -> "favorites"
    HubBackgroundSource.Everything -> "all photos"
    is HubBackgroundSource.Album -> album(source.id) ?: "album"
    is HubBackgroundSource.PhotoSet -> photoSet(source.id) ?: "photo set"
    is HubBackgroundSource.Photos -> if (source.ids.size == 1) "1 chosen photo" else "${source.ids.size} chosen photos"
}

/**
 * The system photo picker returns picker URIs, not MediaStore rows. With photo access, a local pick's last path segment
 * is its MediaStore id; keep only ids the library has, which also drops cloud-only picks the hub couldn't show.
 */
fun pickedPhotoIds(lastPathSegments: List<String?>, inLibrary: (Long) -> Boolean): List<Long> =
    lastPathSegments.mapNotNull { it?.toLongOrNull() }.filter(inLibrary).distinct()

private const val MAX_CHOSEN_PHOTOS = 50

/** Settings' "background from" sheet: favorites, every photo, an album, a pinned photo set, or photos picked one by one. */
@Composable
fun BackgroundPicker(
    settings: AppSettings,
    unlocksPro: Boolean,
    onPick: (HubBackgroundSource) -> Unit,
    onLocked: () -> Unit,
    onDismiss: () -> Unit,
) {
    val metro = LocalMetro.current
    val container = LocalContext.current.container
    val snapshot by container.photos.snapshot.collectAsStateWithLifecycle()
    val sets = container.hubTiles.layout.collectAsStateWithLifecycle().value.sets
    val current = effectiveBackground(settings, unlocksPro)
    val isLocked = !unlocksPro
    val name = { source: HubBackgroundSource -> backgroundName(source, album = { container.photos.album(it)?.title }, photoSet = { container.hubTiles.photoSet(it)?.title }) }
    val picker = rememberLauncherForActivityResult(PickMultipleVisualMedia(MAX_CHOSEN_PHOTOS)) { uris ->
        val ids = pickedPhotoIds(uris.map(Uri::getLastPathSegment)) { it in snapshot.byId }
        if (ids.isNotEmpty()) onPick(HubBackgroundSource.Photos(ids))
    }
    BackHandler(onBack = onDismiss)

    @Composable
    fun option(source: HubBackgroundSource) {
        val locked = isLocked && source != HubBackgroundSource.Favorites
        PickerRow(name(source), null, isSelected = current == source, isLocked = locked) {
            if (locked) onLocked() else onPick(source)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(metro.background)
            // A sheet over settings: nothing behind it takes touches.
            .pointerInput(Unit) {}
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MetroMetrics.margin + 12.dp, vertical = 24.dp),
    ) {
        PageHeader("background", Modifier.padding(bottom = 16.dp))
        option(HubBackgroundSource.Favorites)
        option(HubBackgroundSource.Everything)
        val chosen = current is HubBackgroundSource.Photos
        PickerRow(
            "choose photos", if (chosen) name(current) else "Pick the ones to rotate through",
            isSelected = chosen, isLocked = isLocked,
        ) {
            if (isLocked) onLocked() else picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
        }
        if (snapshot.albums.isNotEmpty()) {
            Header("albums")
            snapshot.albums.forEach { option(HubBackgroundSource.Album(it.id)) }
        }
        if (sets.isNotEmpty()) {
            Header("photo sets")
            sets.values.sortedBy { it.title }.forEach { option(HubBackgroundSource.PhotoSet(it.id)) }
        }
    }
}

@Composable
private fun PickerRow(title: String, detail: String?, isSelected: Boolean, isLocked: Boolean, onClick: () -> Unit) {
    val metro = LocalMetro.current
    Column(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .tiltClickable(onClick = onClick)
            .semantics { selected = isSelected },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            MetroText(title, metroFont(22.sp, Semilight), color = if (isSelected) metro.accentColor else metro.foreground, maxLines = 1)
            if (isLocked) {
                Image(
                    MetroIcons.lock, null,
                    Modifier.size(13.dp).semantics { contentDescription = "needs Luminux Pro" },
                    colorFilter = ColorFilter.tint(metro.secondary),
                )
            }
        }
        if (detail != null) MetroText(detail, MetroType.caption, color = metro.secondary)
    }
}

@Composable
private fun Header(title: String) {
    MetroText(
        title, metroFont(20.sp, Semilight),
        Modifier.padding(top = 24.dp, bottom = 4.dp).semantics { heading() },
        color = LocalMetro.current.secondary,
    )
}
