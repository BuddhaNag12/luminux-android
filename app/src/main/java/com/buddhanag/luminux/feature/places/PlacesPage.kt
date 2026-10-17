package com.buddhanag.luminux.feature.places

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.buddhanag.luminux.data.AssetSource
import com.buddhanag.luminux.container
import com.buddhanag.luminux.data.MediaItem
import com.buddhanag.luminux.data.MediaThumb
import com.buddhanag.luminux.data.Thumbnails
import com.buddhanag.luminux.data.journal.JournalTitles
import com.buddhanag.luminux.data.journal.Place
import com.buddhanag.luminux.data.journal.PlaceGrouping
import com.buddhanag.luminux.feature.actions.AddToAlbumSheet
import com.buddhanag.luminux.feature.actions.selectionActionButtons
import com.buddhanag.luminux.feature.grid.AssetGrid
import com.buddhanag.luminux.feature.grid.SelectionModel
import com.buddhanag.luminux.designsystem.AppBarButton
import com.buddhanag.luminux.designsystem.AppBarMenuItem
import com.buddhanag.luminux.designsystem.LocalMetro
import com.buddhanag.luminux.designsystem.MetroAppBarHost
import com.buddhanag.luminux.designsystem.MetroMetrics
import com.buddhanag.luminux.designsystem.MetroMotion
import com.buddhanag.luminux.designsystem.MetroText
import com.buddhanag.luminux.designsystem.MetroThemeMode
import com.buddhanag.luminux.designsystem.MetroType
import com.buddhanag.luminux.designsystem.Semilight
import com.buddhanag.luminux.designsystem.metroFeather
import com.buddhanag.luminux.designsystem.metroFont
import com.buddhanag.luminux.designsystem.tiltPress
import com.buddhanag.luminux.feature.journal.JournalIcons
import com.buddhanag.luminux.feature.journal.Thumb
import com.buddhanag.luminux.feature.shell.Route
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.clustering.view.DefaultClusterRenderer
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.clustering.Clustering
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The collection's "places" page: a map of where photos were taken, nearby places merged into one bubble when zoomed
 * out, and every place listed below it, the most recently visited first (PlacesViews.swift). Self-contained, so the
 * collection pivot can host it; [contentPadding] carries the pivot's bottom inset.
 */
@Composable
fun PlacesPage(modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues()) {
    val metro = LocalMetro.current
    val container = LocalContext.current.container
    val journal by container.journal.state.collectAsStateWithLifecycle()
    val snapshot by container.photos.snapshot.collectAsStateWithLifecycle()
    // The map is created once the page has turned in: setting it up takes long enough to drop frames mid-entrance.
    var showsMap by remember { mutableStateOf(false) }
    // A finger on the map pans the map, not the list.
    var touchingMap by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(450)
        showsMap = true
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = MetroMetrics.margin),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = !touchingMap,
    ) {
        if (journal.places.isEmpty()) {
            item {
                MetroText(
                    if (journal.isBuilt) {
                        "No photos with a location yet. Photos taken with location tags turned on in the camera show up here."
                    } else {
                        "Finding where your photos were taken…"
                    },
                    MetroType.body, Modifier.metroFeather(row = 0), color = metro.secondary,
                )
            }
        } else {
            item {
                Box(
                    Modifier
                        .padding(bottom = 4.dp)
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(metro.chrome)
                        .clipToBounds()
                        .metroFeather(row = 0)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                touchingMap = true
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                } while (event.changes.any { it.pressed })
                                touchingMap = false
                            }
                        },
                ) {
                    AnimatedVisibility(showsMap, enter = fadeIn(MetroMotion.fade())) { PlacesMap(journal.places) }
                }
            }
            itemsIndexed(journal.places, key = { _, p -> p.id }) { index, place ->
                PlaceRow(place, journal.title(place), snapshot.byId[place.photoIds.first()], Modifier.metroFeather(row = 1 + minOf(index, 12))) {
                    container.navigator.push(Route.Place(place.id))
                }
            }
        }
        item { Box(Modifier.padding(contentPadding).padding(bottom = 12.dp)) }
    }
}

@Composable
private fun PlaceRow(place: Place, title: String, cover: MediaItem?, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val month = DateTimeFormatter.ofPattern("MMM yyyy").format(place.latest.atZone(ZoneId.systemDefault())).lowercase()
    Row(
        modifier.fillMaxWidth().tiltPress(interaction, trackTouch = false).clickable(interaction, indication = null, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(cover, 72.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            MetroText(title, metroFont(20.sp, Semilight), maxLines = 1, softWrap = false)
            MetroText("${JournalTitles.photoCount(place.count)} · $month", MetroType.caption, color = LocalMetro.current.secondary)
        }
    }
}

/** A place as the clustering library sees it. */
private class PlaceMarker(val place: Place) : ClusterItem {
    override val position = LatLng(place.center.latitude, place.center.longitude)
    override val title: String? = null
    override val snippet: String? = null
    override val zIndex: Float? = null
}

private fun PlaceGrouping.Region.bounds() = LatLngBounds(
    LatLng(center.latitude - latDelta / 2, center.longitude - lonDelta / 2),
    LatLng(center.latitude + latDelta / 2, center.longitude + lonDelta / 2),
)

private const val NO_POINTS_OF_INTEREST = """[{"featureType":"poi","stylers":[{"visibility":"off"}]},{"featureType":"transit","elementType":"labels.icon","stylers":[{"visibility":"off"}]}]"""

@OptIn(MapsComposeExperimentalApi::class)
@Composable
private fun PlacesMap(places: List<Place>) {
    val context = LocalContext.current
    val metro = LocalMetro.current
    val navigator = context.container.navigator
    val snapshot by context.container.photos.snapshot.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val camera = rememberCameraPositionState {
        PlaceGrouping.region(places)?.let { position = CameraPosition.fromLatLngZoom(LatLng(it.center.latitude, it.center.longitude), 3f) }
    }
    var isLoaded by remember { mutableStateOf(false) }
    val markers = remember(places) { places.map(::PlaceMarker) }

    // Markers are drawn to bitmaps once, so a thumbnail that loads later would never show: load them all first.
    val px = with(LocalDensity.current) { 56.dp.roundToPx() }
    var thumbs by remember { mutableStateOf<Map<Long, ImageBitmap>?>(null) }
    LaunchedEffect(places, snapshot.generation) {
        val loader = SingletonImageLoader.get(context)
        thumbs = places.mapNotNull { it.photoIds.firstOrNull() }.distinct().mapNotNull { id ->
            val item = snapshot.byId[id] ?: return@mapNotNull null
            val request = ImageRequest.Builder(context).data(MediaThumb(item.contentUri, Thumbnails.requestSide(px))).allowHardware(false).build()
            (loader.execute(request) as? SuccessResult)?.image?.toBitmap()?.let { id to it.asImageBitmap() }
        }.toMap()
    }

    fun frame(shown: List<Place>, animated: Boolean) {
        val update = CameraUpdateFactory.newLatLngBounds(PlaceGrouping.region(shown)?.bounds() ?: return, 0)
        scope.launch { if (animated) camera.animate(update, durationMs = 350) else camera.move(update) }
    }
    // newLatLngBounds needs the map's size, so only once it has loaded.
    LaunchedEffect(places.size, isLoaded) { if (isLoaded) frame(places, animated = false) }

    GoogleMap(
        Modifier.fillMaxSize(),
        cameraPositionState = camera,
        contentDescription = "Map of where your photos were taken",
        properties = MapProperties(mapStyleOptions = MapStyleOptions(NO_POINTS_OF_INTEREST)),
        uiSettings = MapUiSettings(
            compassEnabled = false, indoorLevelPickerEnabled = false, mapToolbarEnabled = false, myLocationButtonEnabled = false,
            rotationGesturesEnabled = false, tiltGesturesEnabled = false, zoomControlsEnabled = false,
        ),
        mapColorScheme = if (metro.theme == MetroThemeMode.Dark) ComposeMapColorScheme.DARK else ComposeMapColorScheme.LIGHT,
        onMapLoaded = { isLoaded = true },
    ) {
        // Markers are bitmaps TalkBack can't read; the list below names every place.
        val bitmaps = thumbs ?: return@GoogleMap
        Clustering(
            items = markers,
            // One place opens; several zoom the map in on them.
            onClusterClick = { cluster -> frame(cluster.items.map { it.place }, animated = true); true },
            onClusterItemClick = { marker -> navigator.push(Route.Place(marker.place.id)); true },
            clusterContent = { cluster -> Bubble(PlaceGrouping.bubble(cluster.items.map { it.place }), bitmaps) },
            clusterItemContent = { marker -> Bubble(PlaceGrouping.bubble(listOf(marker.place)), bitmaps) },
            clusterContentAnchor = Offset(0.5f, 0.5f),
            clusterItemContentAnchor = Offset(0.5f, 0.5f),
            // The library merges from 4 markers by default; iOS merges any two that would overlap.
            onClusterManager = { (it.renderer as? DefaultClusterRenderer<*>)?.minClusterSize = 2 },
        )
    }
}

/** 52 pt thumbnail of the lead place's newest photo, white border, photo-count badge at the top right. */
@Composable
private fun Bubble(bubble: PlaceGrouping.Bubble, thumbs: Map<Long, ImageBitmap>) {
    val metro = LocalMetro.current
    Box {
        Box(Modifier.padding(top = 6.dp, end = 6.dp).size(52.dp).background(metro.chrome).border(2.dp, Color.White)) {
            thumbs[bubble.lead.photoIds.first()]?.let {
                Image(it, null, Modifier.fillMaxSize().padding(2.dp), contentScale = ContentScale.Crop)
            }
        }
        MetroText(
            "${bubble.count}", metroFont(12.sp, FontWeight.SemiBold),
            Modifier.align(Alignment.TopEnd).background(metro.accentColor).padding(horizontal = 5.dp, vertical = 1.dp),
            color = Color.White,
        )
    }
}

/** The photos taken at one place, oldest first, like a journal entry (PlaceView). */
@Composable
fun PlaceScreen(placeId: String) {
    val metro = LocalMetro.current
    val context = LocalContext.current
    val container = context.container
    val journal by container.journal.state.collectAsStateWithLifecycle()
    val snapshot by container.photos.snapshot.collectAsStateWithLifecycle()
    val place = journal.place(placeId)
    var expanded by remember { mutableStateOf(false) }
    val selection = remember { SelectionModel() }
    var addToAlbum by remember { mutableStateOf<List<MediaItem>?>(null) }
    val buttons = if (selection.isActive) {
        selectionActionButtons(selection) { addToAlbum = it }
    } else {
        listOf(AppBarButton("select", JournalIcons.checklist) { selection.begin() })
    }
    val menu = if (selection.isActive) listOf(AppBarMenuItem("cancel selection") { selection.end() }) else emptyList()

    MetroAppBarHost(buttons, expanded, { expanded = it }, Modifier.background(metro.background), menu) { padding ->
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(top = 16.dp)) {
            MetroText(
                if (selection.isActive) "LUMINUX · ${selection.selected.size} SELECTED" else "LUMINUX · PLACES", MetroType.overline,
                Modifier.padding(start = MetroMetrics.margin + 4.dp).metroFeather(row = 0),
            )
            MetroText(
                place?.let { journal.title(it) } ?: "place", MetroType.title,
                Modifier.padding(start = MetroMetrics.margin + 1.dp, bottom = 8.dp).fillMaxWidth().clipToBounds().metroFeather(row = 0).semantics { heading() },
                maxLines = 1, softWrap = false,
            )
            AssetGrid(
                AssetSource.Identifiers(place?.photoIds.orEmpty()), selection, Modifier.weight(1f),
                emptyMessage = "These photos are no longer in your library.", contentPadding = padding,
            )
        }
    }
    AddToAlbumSheet(addToAlbum) { addToAlbum = null }
}
