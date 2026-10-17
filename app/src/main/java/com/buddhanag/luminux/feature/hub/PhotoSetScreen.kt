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
import com.buddhanag.luminux.data.GridZoom
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
import kotlinx.coroutines.launch

/** Photos pinned together from select mode, oldest first, with rename, remove and unpin (PhotoSetView.swift). */
@Composable
fun PhotoSetScreen(setId: String) {
    val container = LocalContext.current.container
    val metro = LocalMetro.current
    val reduceMotion = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    val layout by container.hubTiles.layout.collectAsStateWithLifecycle()
    val snapshot by container.photos.snapshot.collectAsStateWithLifecycle()
    val columns = container.settings.settings.collectAsStateWithLifecycle(initialValue = null).value?.gridColumns ?: GridZoom.DEFAULT_COLUMNS
    val photoSet = layout.sets[setId]
    // Keeps the title on screen while an unpinned set's page swings away.
    var lastTitle by remember { mutableStateOf("photos") }
    LaunchedEffect(photoSet?.title) { photoSet?.title?.let { lastTitle = it } }
    val title = photoSet?.title ?: lastTitle

    // shortcut: a plain Set stands in for 06's SelectionModel; swap at merge (it also brings the 4 shared select buttons).
    var selection by remember { mutableStateOf<Set<Long>?>(null) }
    var isAppBarExpanded by remember { mutableStateOf(false) }
    var isRenaming by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var confirmsUnpin by remember { mutableStateOf(false) }
    val widget = TileCollection.photoSet(setId, title)
    var isWidget by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isWidget = container.liveTileExporter.contains(widget.id) }
    BackHandler(enabled = selection != null) { selection = null }

    val buttons = when {
        photoSet == null || selection != null -> emptyList()
        else -> listOf(AppBarButton("select", HubIcons.select) { selection = emptySet() })
    }
    val menu = when {
        photoSet == null -> emptyList()
        selection != null -> listOf(
            AppBarMenuItem("remove from set", isEnabled = selection.orEmpty().isNotEmpty()) {
                container.hubTiles.remove(selection.orEmpty(), fromSet = setId)
                selection = null
            },
            AppBarMenuItem("cancel selection") { selection = null },
        )
        else -> listOf(
            AppBarMenuItem("rename") {
                newName = photoSet.title
                isRenaming = true
            },
            // shortcut: stands in for 06's WidgetCollectionMenu, without 10's "Widget ready" instructions; swap at merge.
            AppBarMenuItem(if (isWidget) "remove from widgets" else "make a widget") {
                scope.launch {
                    if (isWidget) container.liveTileExporter.remove(widget.id) else container.liveTileExporter.exportOne(widget)
                    isWidget = !isWidget
                }
            },
            AppBarMenuItem("unpin") { confirmsUnpin = true },
        )
    }

    val content = @Composable { bottom: PaddingValues ->
        Column(Modifier.fillMaxSize().background(metro.background).windowInsetsPadding(WindowInsets.statusBars)) {
            val overline = selection?.let { "LUMINUX · ${it.size} SELECTED" } ?: "LUMINUX"
            MetroText(overline, MetroType.overline, Modifier.padding(start = MetroMetrics.margin + 4.dp, top = 16.dp).metroFeather(row = 0))
            MetroText(
                title, MetroType.title,
                Modifier.fillMaxWidth().clipToBounds().padding(start = MetroMetrics.margin + 1.dp, bottom = 8.dp).metroFeather(row = 0),
                maxLines = 1, softWrap = false,
            )
            val items = container.photos.items(AssetSource.Identifiers(photoSet?.photoIds.orEmpty()))
            if (items.isEmpty() && snapshot.isLoaded) {
                MetroText(
                    "These photos are no longer in your library.", MetroType.body,
                    Modifier.padding(horizontal = MetroMetrics.margin + 12.dp).metroFeather(row = 1), color = metro.secondary,
                )
            } else {
                SetGrid(items, columns, selection, bottom) { index, id ->
                    val current = selection
                    if (current == null) container.navigator.openViewer(AssetSource.Identifiers(photoSet?.photoIds.orEmpty()), id, index)
                    else selection = if (id in current) current - id else current + id
                }
            }
        }
    }
    // Hidden entirely once the set is gone.
    if (photoSet == null && selection == null) content(PaddingValues())
    else MetroAppBarHost(buttons, isAppBarExpanded, { isAppBarExpanded = it }, menuItems = menu) { content(it) }

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

/** shortcut: swap for 06's AssetGrid at merge (no month headers, no jump list, oldest first). */
@Composable
private fun SetGrid(
    items: List<MediaItem>,
    columns: Int,
    selection: Set<Long>?,
    bottom: PaddingValues,
    onTap: (Int, Long) -> Unit,
) {
    val context = LocalContext.current
    val metro = LocalMetro.current
    val cellPx = LocalWindowInfo.current.containerSize.width / columns
    LazyVerticalGrid(
        GridCells.Fixed(columns),
        Modifier.fillMaxSize(),
        contentPadding = bottom,
        verticalArrangement = Arrangement.spacedBy(MetroMetrics.gutter),
        horizontalArrangement = Arrangement.spacedBy(MetroMetrics.gutter),
    ) {
        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
            val isSelected = selection?.contains(item.id) == true
            AsyncImage(
                Thumbnails.gridRequest(context, item, cellPx), null,
                Modifier
                    .aspectRatio(1f)
                    .metroFeather(row = 1 + index / columns, column = index % columns)
                    .then(if (isSelected) Modifier.border(3.dp, metro.accentColor) else Modifier)
                    .graphicsLayer { alpha = if (selection != null && !isSelected) 0.75f else 1f }
                    .clickable(interactionSource = null, indication = null, role = Role.Button) { onTap(index, item.id) }
                    .semantics {
                        contentDescription = if (item.type == MediaType.VIDEO) "video" else "photo"
                        if (selection != null) selected = isSelected
                    },
                contentScale = ContentScale.Crop,
            )
        }
    }
}
