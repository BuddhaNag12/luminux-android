package com.buddhanag.luminux.feature.hub

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.buddhanag.luminux.feature.news.NewsPanel
import com.buddhanag.luminux.feature.pro.ProPitch
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.buddhanag.luminux.container
import com.buddhanag.luminux.data.AssetSource
import com.buddhanag.luminux.data.HubBackgroundSource
import com.buddhanag.luminux.data.MEDIA_PERMISSIONS
import com.buddhanag.luminux.data.MediaAccess
import com.buddhanag.luminux.data.MediaItem
import com.buddhanag.luminux.data.MediaType
import com.buddhanag.luminux.data.PhotoRepository
import com.buddhanag.luminux.data.Thumbnails
import com.buddhanag.luminux.data.hub.HubTileKind
import com.buddhanag.luminux.data.hub.PhotoSet
import com.buddhanag.luminux.feature.collection.CollectionPages
import com.buddhanag.luminux.feature.journal.JournalEntryTile
import com.buddhanag.luminux.feature.journal.JournalPlaceholder
import com.buddhanag.luminux.designsystem.AppBarButton
import com.buddhanag.luminux.designsystem.AppBarMenuItem
import com.buddhanag.luminux.designsystem.LocalLiveTileScheduler
import com.buddhanag.luminux.designsystem.LocalMetro
import com.buddhanag.luminux.designsystem.LocalPhoneTilt
import com.buddhanag.luminux.designsystem.LocalReduceMotion
import com.buddhanag.luminux.designsystem.MetroAppBarHost
import com.buddhanag.luminux.designsystem.MetroButton
import com.buddhanag.luminux.designsystem.MetroIcons
import com.buddhanag.luminux.designsystem.MetroMetrics
import com.buddhanag.luminux.designsystem.MetroMotion
import com.buddhanag.luminux.designsystem.MetroText
import com.buddhanag.luminux.designsystem.MetroType
import com.buddhanag.luminux.designsystem.Panorama
import com.buddhanag.luminux.designsystem.PanoramaSection
import com.buddhanag.luminux.designsystem.metroFeather
import com.buddhanag.luminux.designsystem.rememberPhoneTilt
import com.buddhanag.luminux.designsystem.tiltParallax
import com.buddhanag.luminux.designsystem.tiltPress
import com.buddhanag.luminux.feature.settings.effectiveBackground
import com.buddhanag.luminux.feature.shell.Route

/** The collection panel's index; it moves to 1 once spec 09's news panel sits to its left. */

/**
 * The photos hub (HubView.swift): a panorama with collection tiles, recent photos, the journal and favorites over a
 * photo background. Live tiles, ambient patterns and phone tilt run only while it is what's on screen.
 */
@Composable
fun HubScreen() {
    val container = LocalContext.current.container
    val navigator = container.navigator
    val repo = container.hubTiles
    val settings = container.settings.settings.collectAsStateWithLifecycle(initialValue = null).value ?: return
    val snapshot by container.photos.snapshot.collectAsStateWithLifecycle()
    val access by container.photos.access.collectAsStateWithLifecycle()
    val journal by container.journal.state.collectAsStateWithLifecycle()
    val layout by repo.layout.collectAsStateWithLifecycle()
    val isProUnlocked by container.proStore.isUnlocked.collectAsStateWithLifecycle()
    val unlocksPro = isProUnlocked || container.proStore.isTestBuild
    val reduceMotion = LocalReduceMotion.current
    val edit = remember { HubEditState() }
    // Back finishes customizing, as the "done" button does, rather than leaving the app (the hub is the stack's root).
    BackHandler(enabled = edit.isEditing && navigator.path.isEmpty()) { edit.end() }
    val news by container.news.state.collectAsStateWithLifecycle()
    val showsNews = !isProUnlocked && news.isEnabled
    // The news panel sits left of the collection, and the hub opens on the collection.
    val collectionPanel = if (showsNews) 1 else 0
    var panel by remember { mutableIntStateOf(collectionPanel) }
    val scope = rememberCoroutineScope()
    var isAppBarExpanded by remember { mutableStateOf(false) }

    val activity = LocalActivity.current
    LaunchedEffect(isProUnlocked) {
        // Picks up the remote switch quietly; ads wait until the panel is actually shown.
        if (isProUnlocked) container.newsAds.clear() else container.news.refreshIfStale()
    }
    // Under partial access, asking again shows the system's reselect sheet.
    val chooseMore = rememberLauncherForActivityResult(RequestMultiplePermissions()) { activity?.let(container.photos::onPermissionResult) }
    val isLimited = access == MediaAccess.LIMITED

    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val tracksHub = lifecycleState.isAtLeast(Lifecycle.State.RESUMED) && navigator.path.isEmpty() && navigator.viewer == null
    val tilesPaused = !(tracksHub && panel == collectionPanel)
    val scheduler = LocalLiveTileScheduler.current
    LaunchedEffect(tilesPaused) { scheduler.isPaused = tilesPaused }
    LaunchedEffect(settings.animatesLiveTiles) { scheduler.isAnimated = settings.animatesLiveTiles }
    val tilt = rememberPhoneTilt(settings.movesWithPhone && !reduceMotion && tracksHub)

    val placeCovers = journal.places.take(LIVE_PLACES).mapNotNull { place -> place.photoIds.firstOrNull()?.let(snapshot.byId::get) }
    val faces = TileFaceData(container.photos, layout, settings.animatesTilePatterns, placeCovers)
    val open = { kind: HubTileKind -> navigator.push(routeFor(kind)) }
    val openViewer = { source: AssetSource, item: MediaItem, index: Int -> navigator.openViewer(source, item.id, index) }

    CompositionLocalProvider(LocalPhoneTilt provides tilt, LocalHubTilesPaused provides tilesPaused) {
        MetroAppBarHost(
            buttons = if (edit.isEditing) listOf(AppBarButton("done", MetroIcons.check) { edit.end() }) else emptyList(),
            expanded = isAppBarExpanded,
            onExpandedChange = { isAppBarExpanded = it },
            menuItems = if (edit.isEditing) emptyList() else buildList {
                add(AppBarMenuItem("customize tiles") { edit.begin(null) })
                add(AppBarMenuItem("settings") { navigator.push(Route.Settings) })
                if (isLimited) add(AppBarMenuItem("choose more photos") { chooseMore.launch(MEDIA_PERMISSIONS) })
            },
        ) { padding ->
            val bottom = padding.calculateBottomPadding()
            Panorama(
                "photos",
                leadingTitle = if (showsNews) "news" else null,
                startPanel = collectionPanel,
                panels = listOfNotNull(
                    if (showsNews) ({ NewsPanel(bottom) }) else null,
                    {
                        CollectionPanel(edit, bottom) {
                            item {
                                HubTileGrid(
                                    repo, layout.tiles, faces, edit, open,
                                    Modifier
                                        .padding(end = MetroMetrics.margin)
                                        // No wider than on the largest phone, so tiles keep their size on an unfolded screen.
                                        .widthIn(max = 384.dp)
                                        // The tiles drift against the background, so they seem to float above it.
                                        .tiltParallax(-8.dp * settings.tiltStrength.scale),
                                )
                            }
                            if (isLimited) item {
                                Column(Modifier.padding(top = 16.dp, end = MetroMetrics.margin), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    MetroText("Luminux can see only the photos you picked.", MetroType.body, color = LocalMetro.current.secondary)
                                    MetroButton("choose more", { chooseMore.launch(MEDIA_PERMISSIONS) })
                                }
                            }
                        }
                    },
                    {
                        PanoramaSection("what's new", bottomInset = bottom) {
                            photoGrid(container.photos, AssetSource.Recent, snapshot.isLoaded, "No photos from the last 30 days.", openViewer)
                        }
                    },
                    {
                        PanoramaSection("journal", bottomInset = bottom) {
                            // The latest few entries; the journal page has the rest.
                            val endPadding = Modifier.padding(end = MetroMetrics.margin + 12.dp)
                            if (journal.entries.isEmpty()) {
                                item { JournalPlaceholder(journal.isBuilt, endPadding.metroFeather(row = 2)) }
                            } else {
                                itemsIndexed(journal.entries.take(3), key = { _, e -> e.id }) { index, entry ->
                                    JournalEntryTile(entry, journal, endPadding.metroFeather(row = 2 + index))
                                }
                                item { MetroButton("see all", { navigator.push(Route.Journal) }, Modifier.metroFeather(row = 5)) }
                            }
                        }
                    },
                    {
                        PanoramaSection("favorites", bottomInset = bottom) {
                            photoGrid(
                                container.photos, AssetSource.Favorites, snapshot.isLoaded,
                                "No favorites yet. Tap the heart on a photo to add it here.", openViewer,
                            )
                        }
                    },
                    if (!isProUnlocked) ({
                        PanoramaSection("pro", bottomInset = bottom) {
                            item { ProPitch(Modifier.padding(end = MetroMetrics.margin + 12.dp), firstRow = 2) }
                        }
                    }) else null,
                ),
                onPanelChange = {
                    panel = it
                    edit.end()
                    // The only path that loads native ads: swiping to the news panel.
                    if (showsNews && it == 0 && activity != null) scope.launch {
                        container.news.refreshIfStale()
                        container.newsAds.prepare(activity, container.news.state.value.articles.size)
                    }
                },
                background = if (settings.showsHubBackground) {
                    {
                        HubBackground(
                            effectiveBackground(settings, unlocksPro), snapshot.isLoaded, container.photos, layout.sets,
                            settings.tiltStrength.scale,
                        )
                    }
                } else null,
            )
        }
    }

    edit.confirmsUnpin?.let { kind ->
        val title = (kind as? HubTileKind.PhotoSet)?.let { layout.sets[it.id]?.title } ?: "photos"
        HubDialog(
            null,
            listOf(DialogAction("Cancel"), DialogAction("Unpin") { unpin(repo, edit, kind) }),
            onDismiss = { edit.confirmsUnpin = null },
            message = "Unpin \"$title\"? The photos stay in your library.",
        )
    }
}

internal fun routeFor(kind: HubTileKind): Route = when (kind) {
    HubTileKind.CameraRoll -> Route.Collection(page = CollectionPages.ALL)
    HubTileKind.Albums -> Route.Collection(page = CollectionPages.ALBUMS)
    HubTileKind.Date -> Route.Collection(page = CollectionPages.ALL, showsJumpList = true)
    HubTileKind.Places -> Route.Collection(page = CollectionPages.PLACES)
    HubTileKind.Favorites -> Route.Collection(page = CollectionPages.FAVORITES)
    is HubTileKind.Album -> Route.Album(kind.id)
    is HubTileKind.PhotoSet -> Route.PhotoSet(kind.id)
}

/**
 * The collection panel. While editing, unlike [PanoramaSection], its list starts [EditButtonReach] higher with that much
 * top padding, so a top-row tile's unpin button isn't clipped off (the tiles stay put); a tap between or below the
 * tiles leaves edit mode.
 */
@Composable
private fun CollectionPanel(edit: HubEditState, bottomInset: Dp, content: LazyListScope.() -> Unit) {
    val room = if (edit.isEditing) EditButtonReach else 0.dp
    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(edit.isEditing) { if (edit.isEditing) detectTapGestures { edit.end() } },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetroText(
            "collection", MetroType.section,
            Modifier.metroFeather(row = 1).padding(start = MetroMetrics.margin).semantics { heading() },
        )
        LazyColumn(
            Modifier.fillMaxWidth().offset(y = -room),
            contentPadding = PaddingValues(start = MetroMetrics.margin, top = room, bottom = 24.dp + bottomInset),
            content = content,
        )
    }
}

/** What's new and favorites: up to 60 photos, three to a row. */
private fun LazyListScope.photoGrid(
    photos: PhotoRepository,
    source: AssetSource,
    isLoaded: Boolean,
    empty: String,
    onOpen: (AssetSource, MediaItem, Int) -> Unit,
) {
    val items = photos.items(source).take(60)
    if (items.isEmpty() && isLoaded) {
        item {
            MetroText(empty, MetroType.body, Modifier.padding(end = MetroMetrics.margin), color = LocalMetro.current.secondary)
        }
        return
    }
    items.chunked(3).forEachIndexed { row, chunk ->
        item(key = chunk.first().id) {
            Row(
                Modifier.padding(end = MetroMetrics.margin, bottom = MetroMetrics.gutter),
                horizontalArrangement = Arrangement.spacedBy(MetroMetrics.gutter),
            ) {
                chunk.forEachIndexed { column, item ->
                    Thumbnail(item, Modifier.weight(1f).metroFeather(row = 2 + row, column = column)) { onOpen(source, item, row * 3 + column) }
                }
                repeat(3 - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Thumbnail(item: MediaItem, modifier: Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { 120.dp.roundToPx() }
    val interaction = remember { MutableInteractionSource() }
    AsyncImage(
        Thumbnails.gridRequest(context, item, px), null,
        modifier
            .aspectRatio(1f)
            .tiltPress(interaction, trackTouch = false)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (item.type == MediaType.VIDEO) "video" else "photo" },
        contentScale = ContentScale.Crop,
    )
}

/** The chosen photos, falling back to favorites and then everything when they're gone or empty; images only. */
internal fun backgroundCandidates(source: HubBackgroundSource, photos: PhotoRepository, sets: Map<String, PhotoSet>): List<MediaItem> {
    val chosen = when (source) {
        HubBackgroundSource.Favorites -> null
        HubBackgroundSource.Everything -> AssetSource.All
        is HubBackgroundSource.Album -> AssetSource.Album(source.id)
        is HubBackgroundSource.PhotoSet -> AssetSource.Identifiers(sets[source.id]?.photoIds.orEmpty())
        is HubBackgroundSource.Photos -> AssetSource.Identifiers(source.ids)
    }
    return listOfNotNull(chosen, AssetSource.Favorites, AssetSource.All).firstNotNullOfOrNull { s ->
        photos.items(s).take(200).filter { it.type == MediaType.IMAGE }.ifEmpty { null }
    }.orEmpty()
}

/** A photo behind the hub, picked once per launch unless the source changes. */
@Composable
private fun HubBackground(source: HubBackgroundSource, isLoaded: Boolean, photos: PhotoRepository, sets: Map<String, PhotoSet>, tiltScale: Float) {
    val pick = remember(isLoaded, source) { if (isLoaded) backgroundCandidates(source, photos, sets).randomOrNull() else null } ?: return
    var shown by remember(pick) { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(400, easing = MetroMotion.EaseOut), label = "background")
    val context = LocalContext.current
    AsyncImage(
        remember(pick) { ImageRequest.Builder(context).data(pick.contentUri).size(1800).build() }, null,
        Modifier
            .fillMaxSize()
            .tiltParallax(22.dp * tiltScale)
            // Oversized so the tilt never shows an edge.
            .graphicsLayer {
                scaleX = 1.1f
                scaleY = 1.1f
                this.alpha = alpha
            },
        contentScale = ContentScale.Crop,
        onSuccess = { shown = true },
    )
}

/** The places tile cycles through this many recent places (HubTileGrid.swift). */
private const val LIVE_PLACES = 8
