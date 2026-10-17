package com.buddhanag.luminux.feature.hub

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.buddhanag.luminux.container
import com.buddhanag.luminux.data.AssetSource
import com.buddhanag.luminux.data.MediaItem
import com.buddhanag.luminux.data.MediaType
import com.buddhanag.luminux.data.Thumbnails
import com.buddhanag.luminux.data.TileCollection
import com.buddhanag.luminux.data.hub.HubTileKind
import com.buddhanag.luminux.designsystem.AppBarButton
import com.buddhanag.luminux.designsystem.AppBarMenuItem
import com.buddhanag.luminux.designsystem.LocalMetro
import com.buddhanag.luminux.designsystem.LocalReduceMotion
import com.buddhanag.luminux.designsystem.MetroAppBarHost
import com.buddhanag.luminux.designsystem.MetroMetrics
import com.buddhanag.luminux.designsystem.MetroText
import com.buddhanag.luminux.designsystem.MetroType
import com.buddhanag.luminux.designsystem.metroFeather
import com.buddhanag.luminux.feature.actions.AddToAlbumSheet
import com.buddhanag.luminux.feature.actions.selectionActionButtons
import com.buddhanag.luminux.feature.actions.widgetMenuItem
import com.buddhanag.luminux.feature.grid.AssetGrid
import com.buddhanag.luminux.feature.grid.SelectionModel
import kotlinx.coroutines.launch

/** Photos pinned together from select mode, oldest first, with rename, remove and unpin (PhotoSetView.swift). */
@Composable
fun PhotoSetScreen(setId: String) {
    val container = LocalContext.current.container
    val metro = LocalMetro.current
    val reduceMotion = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    val layout by container.hubTiles.layout.collectAsStateWithLifecycle()
    val photoSet = layout.sets[setId]
    // Keeps the title on screen while an unpinned set's page swings away.
    var lastTitle by remember { mutableStateOf("photos") }
    LaunchedEffect(photoSet?.title) { photoSet?.title?.let { lastTitle = it } }
    val title = photoSet?.title ?: lastTitle

    val selection = remember { SelectionModel() }
    var addToAlbum by remember { mutableStateOf<List<MediaItem>?>(null) }
    var isAppBarExpanded by remember { mutableStateOf(false) }
    var isRenaming by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var confirmsUnpin by remember { mutableStateOf(false) }
    val widgetItem = widgetMenuItem(TileCollection.photoSet(setId, title))
    BackHandler(enabled = selection.isActive) { selection.end() }

    val buttons = when {
        photoSet == null -> emptyList()
        selection.isActive -> selectionActionButtons(selection) { addToAlbum = it }
        else -> listOf(AppBarButton("select", HubIcons.select) { selection.begin() })
    }
    val menu = when {
        photoSet == null -> emptyList()
        selection.isActive -> listOf(
            AppBarMenuItem("remove from set", isEnabled = selection.selected.isNotEmpty()) {
                container.hubTiles.remove(selection.selected, fromSet = setId)
                selection.end()
            },
            AppBarMenuItem("cancel selection") { selection.end() },
        )
        else -> listOf(
            AppBarMenuItem("rename") {
                newName = photoSet.title
                isRenaming = true
            },
            widgetItem,
            AppBarMenuItem("unpin") { confirmsUnpin = true },
        )
    }

    val content = @Composable { bottom: PaddingValues ->
        Column(Modifier.fillMaxSize().background(metro.background).windowInsetsPadding(WindowInsets.statusBars)) {
            val overline = if (selection.isActive) "LUMINUX · ${selection.selected.size} SELECTED" else "LUMINUX"
            MetroText(overline, MetroType.overline, Modifier.padding(start = MetroMetrics.margin + 4.dp, top = 16.dp).metroFeather(row = 0))
            MetroText(
                title, MetroType.title,
                Modifier.fillMaxWidth().clipToBounds().padding(start = MetroMetrics.margin + 1.dp, bottom = 8.dp).metroFeather(row = 0),
                maxLines = 1, softWrap = false,
            )
            AssetGrid(
                AssetSource.Identifiers(photoSet?.photoIds.orEmpty()), selection,
                emptyMessage = "These photos are no longer in your library.", contentPadding = bottom,
            )
        }
    }
    // Hidden entirely once the set is gone.
    if (photoSet == null && !selection.isActive) content(PaddingValues())
    else MetroAppBarHost(buttons, isAppBarExpanded, { isAppBarExpanded = it }, menuItems = menu) { content(it) }
    AddToAlbumSheet(addToAlbum) { addToAlbum = null }

    if (isRenaming) {
        HubDialog(
            "Rename",
            listOf(DialogAction("Cancel"), DialogAction("Rename") { container.hubTiles.rename(setId, newName) }),
            onDismiss = { isRenaming = false },
            field = DialogField("Name", newName) { newName = it },
        )
    }
    if (confirmsUnpin) {
        HubDialog(
            null,
            listOf(
                DialogAction("Cancel"),
                DialogAction("Unpin") {
                    container.hubTiles.unpin(HubTileKind.PhotoSet(setId))
                    scope.launch { container.navigator.pop(reduceMotion) }
                },
            ),
            onDismiss = { confirmsUnpin = false },
            message = "Unpin \"$lastTitle\"? The photos stay in your library.",
        )
    }
}
