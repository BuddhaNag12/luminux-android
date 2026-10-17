package com.buddhanag.luminux.feature.places

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buddhanag.luminux.data.AssetSource
import com.buddhanag.luminux.container
import com.buddhanag.luminux.data.MediaItem
import com.buddhanag.luminux.data.journal.JournalTitles
import com.buddhanag.luminux.data.journal.Place
import com.buddhanag.luminux.feature.actions.AddToAlbumSheet
import com.buddhanag.luminux.feature.actions.selectionActionButtons
import com.buddhanag.luminux.feature.grid.AssetGrid
import com.buddhanag.luminux.feature.grid.SelectionModel
import com.buddhanag.luminux.designsystem.AppBarButton
import com.buddhanag.luminux.designsystem.AppBarMenuItem
import com.buddhanag.luminux.designsystem.LocalMetro
import com.buddhanag.luminux.designsystem.MetroAppBarHost
import com.buddhanag.luminux.designsystem.MetroMetrics
import com.buddhanag.luminux.designsystem.MetroText
import com.buddhanag.luminux.designsystem.MetroType
import com.buddhanag.luminux.designsystem.Semilight
import com.buddhanag.luminux.designsystem.metroFeather
import com.buddhanag.luminux.designsystem.metroFont
import com.buddhanag.luminux.designsystem.tiltPress
import com.buddhanag.luminux.feature.journal.JournalIcons
import com.buddhanag.luminux.feature.journal.Thumb
import com.buddhanag.luminux.feature.shell.Route
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The collection's "places" page: every place photos were taken, the most recently visited first (PlacesViews.swift).
 * shortcut: the iOS map above the list is left out until there's a Maps API key; bring back Maps Compose then.
 * Self-contained, so the collection pivot can host it; [contentPadding] carries the pivot's bottom inset.
 */
@Composable
fun PlacesPage(modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues()) {
    val metro = LocalMetro.current
    val container = LocalContext.current.container
    val journal by container.journal.state.collectAsStateWithLifecycle()
    val snapshot by container.photos.snapshot.collectAsStateWithLifecycle()
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = MetroMetrics.margin),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
