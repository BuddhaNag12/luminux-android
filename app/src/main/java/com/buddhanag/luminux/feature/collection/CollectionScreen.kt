package com.buddhanag.luminux.feature.collection

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buddhanag.luminux.container
import com.buddhanag.luminux.data.AssetSource
import com.buddhanag.luminux.data.LibrarySnapshot
import com.buddhanag.luminux.data.MediaItem
import com.buddhanag.luminux.designsystem.AppBarButton
import com.buddhanag.luminux.designsystem.AppBarMenuItem
import com.buddhanag.luminux.designsystem.JumpListView
import com.buddhanag.luminux.designsystem.LocalMetro
import com.buddhanag.luminux.designsystem.MetroAppBarHost
import com.buddhanag.luminux.designsystem.MetroMetrics
import com.buddhanag.luminux.designsystem.MetroMotion
import com.buddhanag.luminux.designsystem.MetroText
import com.buddhanag.luminux.designsystem.MetroType
import com.buddhanag.luminux.designsystem.Pivot
import com.buddhanag.luminux.designsystem.PivotPage
import com.buddhanag.luminux.designsystem.Semilight
import com.buddhanag.luminux.designsystem.metroFeather
import com.buddhanag.luminux.designsystem.metroFont
import com.buddhanag.luminux.designsystem.tiltPress
import com.buddhanag.luminux.feature.actions.ActionIcons
import com.buddhanag.luminux.feature.actions.AddToAlbumSheet
import com.buddhanag.luminux.feature.actions.AlbumCover
import com.buddhanag.luminux.feature.actions.NameDialog
import com.buddhanag.luminux.feature.actions.selectionActionButtons
import com.buddhanag.luminux.feature.grid.AssetGrid
import com.buddhanag.luminux.feature.grid.SelectionModel
import com.buddhanag.luminux.feature.hub.HubPinningHost
import com.buddhanag.luminux.feature.hub.PinRequest
import com.buddhanag.luminux.feature.hub.PinTarget
import com.buddhanag.luminux.feature.places.PlacesPage
import com.buddhanag.luminux.feature.shell.Route
import kotlinx.coroutines.launch

/** Pivot over the whole library (CollectionView.swift): all (by month), albums, places, favorites, videos. */
object CollectionPages {
    const val ALL = 0
    const val ALBUMS = 1
    const val PLACES = 2
    const val FAVORITES = 3
    const val VIDEOS = 4
}

/** [showsJumpList] opens on the month list, for the hub's "date" tile. */
@Composable
fun CollectionScreen(initialPage: Int, showsJumpList: Boolean = false) {
    val container = LocalContext.current.container
    val navigator = container.navigator
    val snapshot by container.photos.snapshot.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val selection = remember { SelectionModel() }
    var page by rememberSaveable { mutableIntStateOf(initialPage) }
    var isJumpListShown by rememberSaveable { mutableStateOf(showsJumpList) }
    var jumpTarget by remember { mutableStateOf<String?>(null) }
    var isAppBarExpanded by remember { mutableStateOf(false) }
    var isNamingAlbum by remember { mutableStateOf(false) }
    var addToAlbum by remember { mutableStateOf<List<MediaItem>?>(null) }
    var pin by remember { mutableStateOf<PinRequest?>(null) }

    val buttons = when {
        selection.isActive -> selectionActionButtons(selection) { addToAlbum = it }
        page == CollectionPages.ALBUMS -> listOf(AppBarButton("new album", ActionIcons.add) { isNamingAlbum = true })
        page == CollectionPages.PLACES -> emptyList()
        else -> listOf(AppBarButton("select", ActionIcons.select) { selection.begin() })
    }
    val menu = if (selection.isActive) {
        listOf(
            AppBarMenuItem("pin to collection", isEnabled = selection.selected.isNotEmpty()) {
                pin = PinRequest(PinTarget.Photos(selection.selected.toList())) { selection.end() }
            },
            AppBarMenuItem("cancel selection") { selection.end() },
        )
    } else {
        listOf(AppBarMenuItem("settings") { navigator.push(Route.Settings) })
    }

    Box(Modifier.fillMaxSize()) {
        MetroAppBarHost(buttons, isAppBarExpanded, { isAppBarExpanded = it }, menuItems = menu) { padding ->
            Pivot(
                pages = listOf(
                    PivotPage("all") {
                        AssetGrid(
                            AssetSource.All, selection,
                            sections = snapshot.sections,
                            emptyMessage = "No photos yet.",
                            onHeaderTap = { isJumpListShown = true },
                            jumpTarget = jumpTarget,
                            onJumpConsumed = { jumpTarget = null },
                            contentPadding = padding,
                        )
                    },
                    PivotPage("albums") { AlbumsGrid(snapshot, padding) { navigator.push(Route.Album(it)) } },
                    PivotPage("places") { PlacesPage(contentPadding = padding) },
                    PivotPage("favorites") {
                        AssetGrid(
                            AssetSource.Favorites, selection,
                            emptyMessage = "No favorites yet. Tap the heart on a photo to add it here.",
                            contentPadding = padding,
                        )
                    },
                    PivotPage("videos") {
                        AssetGrid(AssetSource.Videos, selection, emptyMessage = "No videos yet.", contentPadding = padding)
                    },
                ),
                selection = page,
                onSelectionChange = {
                    page = it
                    if (it == CollectionPages.ALBUMS || it == CollectionPages.PLACES) selection.end()
                },
                overline = if (selection.isActive) "LUMINUX · ${selection.selected.size} SELECTED" else "LUMINUX",
            )
        }
        AnimatedVisibility(
            isJumpListShown,
            Modifier.zIndex(1f),
            enter = scaleIn(MetroMotion.standard(), initialScale = 1.1f) + fadeIn(MetroMotion.standard()),
            exit = scaleOut(MetroMotion.standard(), targetScale = 1.1f) + fadeOut(MetroMotion.standard()),
        ) {
            // Always marks the newest month, not the scrolled one, like the original.
            JumpListView(snapshot.sections, snapshot.sections.firstOrNull()?.id, onSelect = { id ->
                page = CollectionPages.ALL
                jumpTarget = id
                isJumpListShown = false
            })
        }
        BackHandler(enabled = isJumpListShown) { isJumpListShown = false }
        AddToAlbumSheet(addToAlbum) { addToAlbum = null }
        HubPinningHost(pin) { pin = null }
        if (isNamingAlbum) {
            NameDialog("New album", "create", onConfirm = { scope.launch { container.photos.createAlbum(it) } }, onDismiss = {
                isNamingAlbum = false
            })
        }
    }
}

/** User albums, then the automatic ones, two across (CollectionView.swift `AlbumsGrid`). */
@Composable
private fun AlbumsGrid(snapshot: LibrarySnapshot, padding: PaddingValues, onOpen: (String) -> Unit) {
    val metro = LocalMetro.current
    LazyVerticalGrid(
        GridCells.Fixed(2),
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = MetroMetrics.margin, end = MetroMetrics.margin, bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (snapshot.albums.isEmpty() && snapshot.isLoaded) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                MetroText(
                    "No albums yet. Use \"new album\" below to make one.", MetroType.body, Modifier.metroFeather(row = 0),
                    color = metro.secondary,
                )
            }
        }
        itemsIndexed(snapshot.albums, key = { _, album -> album.id }) { index, album ->
            val interaction = remember { MutableInteractionSource() }
            Column(
                Modifier
                    .metroFeather(row = index / 2, column = index % 2)
                    .tiltPress(interaction, trackTouch = false)
                    .clickable(interaction, indication = null, role = Role.Button) { onOpen(album.id) },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AlbumCover(album, 200.dp, Modifier.fillMaxWidth().aspectRatio(1f))
                MetroText(album.title, metroFont(20.sp, Semilight), maxLines = 1, softWrap = false)
                MetroText("${album.count}", MetroType.caption, color = metro.secondary)
            }
        }
    }
}
