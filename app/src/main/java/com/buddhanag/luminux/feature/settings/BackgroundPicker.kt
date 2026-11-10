package com.buddhanag.luminux.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buddhanag.luminux.container
import com.buddhanag.luminux.data.AppSettings
import com.buddhanag.luminux.data.AssetSource
import com.buddhanag.luminux.data.HubBackgroundSource
import com.buddhanag.luminux.data.MediaItem
import com.buddhanag.luminux.feature.actions.ActionIcons
import com.buddhanag.luminux.feature.shell.Route
import kotlinx.coroutines.launch

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

/** Settings' pushed "background from" page (`BackgroundPicker.swift`): favorites, all photos, albums, sets, or picks. */
@Composable
fun BackgroundPickerScreen() {
    val container = LocalContext.current.container
    val navigator = container.navigator
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val snapshot by container.photos.snapshot.collectAsStateWithLifecycle()
    val sets = container.hubTiles.layout.collectAsStateWithLifecycle().value.sets
    val isProUnlocked by container.proStore.isUnlocked.collectAsStateWithLifecycle()
    val unlocksPro = isProUnlocked || container.proStore.isTestBuild
    val current = effectiveBackground(settings, unlocksPro)
    val isLocked = !unlocksPro
    val scope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(PickMultipleVisualMedia(MAX_CHOSEN_PHOTOS)) { uris ->
        val ids = pickedPhotoIds(uris.map(Uri::getLastPathSegment)) { it in snapshot.byId }
        if (ids.isNotEmpty()) scope.launch { container.settings.setHubBackground(HubBackgroundSource.Photos(ids)) }
    }

    fun pick(source: HubBackgroundSource) {
        scope.launch { container.settings.setHubBackground(source) }
    }

    @Composable
    fun option(source: HubBackgroundSource, cover: MediaItem?, count: Int) {
        val locked = isLocked && source != HubBackgroundSource.Favorites
        val title = backgroundName(source, album = { container.photos.album(it)?.title }, photoSet = { container.hubTiles.photoSet(it)?.title })
        ChoiceRow(
            cover = cover,
            title = title,
            detail = if (count == 1) "1 photo" else "$count photos",
            isSelected = current == source,
            isLocked = locked,
            onClick = { if (locked) navigator.push(Route.Pro) else pick(source) },
        )
    }

    ChoicePage("background", "The hub shows a photo from here each time Luminux opens.") {
        option(HubBackgroundSource.Favorites, snapshot.favorites.firstOrNull(), snapshot.favorites.size)
        option(HubBackgroundSource.Everything, snapshot.all.firstOrNull(), snapshot.all.size)

        val chosen = current as? HubBackgroundSource.Photos
        ChoiceRow(
            cover = chosen?.ids?.firstOrNull()?.let(snapshot.byId::get),
            title = "choose photos",
            detail = if (chosen != null) backgroundName(chosen, { null }, { null }) else "Pick the ones to rotate through",
            isSelected = chosen != null,
            fallbackIcon = ActionIcons.add,
            isLocked = isLocked,
            onClick = { if (isLocked) navigator.push(Route.Pro) else picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
        )

        if (snapshot.albums.isNotEmpty()) {
            ChoiceHeader("albums")
            snapshot.albums.forEach { album ->
                option(HubBackgroundSource.Album(album.id), album.keyItemId?.let(snapshot.byId::get), album.count)
            }
        }
        if (sets.isNotEmpty()) {
            ChoiceHeader("photo sets")
            sets.values.sortedBy { it.title }.forEach { set ->
                val count = container.photos.items(AssetSource.Identifiers(set.photoIds)).size
                option(HubBackgroundSource.PhotoSet(set.id), set.photoIds.firstOrNull()?.let(snapshot.byId::get), count)
            }
        }
    }
}
