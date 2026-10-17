package com.buddhanag.luminux.feature.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buddhanag.luminux.container
import com.buddhanag.luminux.data.AssetSource
import com.buddhanag.luminux.data.MediaItem
import com.buddhanag.luminux.data.TileCollection
import com.buddhanag.luminux.designsystem.AppBarButton
import com.buddhanag.luminux.designsystem.AppBarMenuItem
import com.buddhanag.luminux.designsystem.LocalMetro
import com.buddhanag.luminux.designsystem.LocalReduceMotion
import com.buddhanag.luminux.designsystem.MetroAppBarHost
import com.buddhanag.luminux.designsystem.MetroMetrics
import com.buddhanag.luminux.designsystem.MetroText
import com.buddhanag.luminux.designsystem.MetroType
import com.buddhanag.luminux.designsystem.metroFeather
import com.buddhanag.luminux.feature.actions.ActionIcons
import com.buddhanag.luminux.feature.actions.AddToAlbumSheet
import com.buddhanag.luminux.feature.actions.ConfirmDialog
import com.buddhanag.luminux.feature.actions.NameDialog
import com.buddhanag.luminux.feature.actions.selectionActionButtons
import com.buddhanag.luminux.feature.actions.widgetMenuItem
import com.buddhanag.luminux.feature.grid.AssetGrid
import com.buddhanag.luminux.feature.grid.SelectionModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** One album's photos (AlbumView.swift); user albums can also be renamed, deleted and have photos taken out. */
@Composable
fun AlbumScreen(id: String) {
    val container = LocalContext.current.container
    val photos = container.photos
    val snapshot by photos.snapshot.collectAsStateWithLifecycle()
    val reduceMotion = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    val selection = remember { SelectionModel() }
    val album = snapshot.album(id)
    // Keeps the title on screen while a deleted album's page swings away.
    var lastTitle by remember { mutableStateOf("album") }
    SideEffect { album?.title?.let { lastTitle = it } }
    val title = album?.title ?: lastTitle
    val isUserAlbum = album?.isUserAlbum == true
    var isAppBarExpanded by remember { mutableStateOf(false) }
    var isRenaming by remember { mutableStateOf(false) }
    var confirmsDelete by remember { mutableStateOf(false) }
    var addToAlbum by remember { mutableStateOf<List<MediaItem>?>(null) }
    val widgetItem = widgetMenuItem(TileCollection.album(id, title))

    val buttons = if (selection.isActive) {
        selectionActionButtons(selection) { addToAlbum = it }
    } else {
        listOf(AppBarButton("select", ActionIcons.select) { selection.begin() })
    }
    val menu = if (selection.isActive) {
        listOfNotNull(
            if (!isUserAlbum) null else AppBarMenuItem("remove from album", isEnabled = selection.selected.isNotEmpty()) {
                val items = selection.selected.mapNotNull(photos::item)
                scope.launch {
                    photos.remove(items, fromAlbum = id)
                    selection.end()
                }
            },
            // shortcut: 05's PinRequest(Photos(ids)) { selection.end() } goes here at merge.
            AppBarMenuItem("pin to collection", isEnabled = selection.selected.isNotEmpty()) {},
            AppBarMenuItem("cancel selection") { selection.end() },
        )
    } else {
        listOfNotNull(
            // shortcut: 05 swaps in "unpin from collection" when hubTiles.isPinned(Album(id)), else PinRequest(Album(id)).
            AppBarMenuItem("pin to collection") {},
            widgetItem,
            if (isUserAlbum) AppBarMenuItem("rename") { isRenaming = true } else null,
            if (isUserAlbum) AppBarMenuItem("delete album") { confirmsDelete = true } else null,
        )
    }

    Box(Modifier.fillMaxSize().background(LocalMetro.current.background)) {
        MetroAppBarHost(buttons, isAppBarExpanded, { isAppBarExpanded = it }, menuItems = menu) { padding ->
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
                MetroText(
                    if (selection.isActive) "LUMINUX · ${selection.selected.size} SELECTED" else "LUMINUX",
                    MetroType.overline,
                    Modifier.metroFeather(row = 0).padding(start = MetroMetrics.margin + 4.dp),
                )
                Box(Modifier.fillMaxWidth().clipToBounds().padding(bottom = 8.dp)) {
                    MetroText(
                        title, MetroType.title,
                        Modifier
                            .metroFeather(row = 0)
                            .wrapContentWidth(Alignment.Start, unbounded = true)
                            .padding(start = MetroMetrics.margin + 1.dp),
                        maxLines = 1, softWrap = false,
                    )
                }
                // Runs under the app bar; the last row can still scroll clear of it.
                AssetGrid(AssetSource.Album(id), selection, emptyMessage = "This album is empty.", contentPadding = padding)
            }
        }
        AddToAlbumSheet(addToAlbum) { addToAlbum = null }
        if (isRenaming) {
            NameDialog("Rename album", "rename", initial = title, onConfirm = { scope.launch { photos.renameAlbum(id, it) } }, onDismiss = {
                isRenaming = false
            })
        }
        if (confirmsDelete) {
            ConfirmDialog("Delete this album? The photos stay in your library.", "delete album", onConfirm = {
                scope.launch {
                    photos.deleteAlbum(id)
                    // Let the library reload land first; it re-renders every page and would stall the feather-out.
                    withTimeoutOrNull(1_000) { while (photos.album(id) != null) delay(50) }
                    container.navigator.pop(reduceMotion)
                }
            }, onDismiss = { confirmsDelete = false })
        }
    }
}
