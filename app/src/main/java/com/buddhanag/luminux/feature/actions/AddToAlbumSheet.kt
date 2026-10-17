package com.buddhanag.luminux.feature.actions

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buddhanag.luminux.container
import com.buddhanag.luminux.data.Album
import com.buddhanag.luminux.data.MediaItem
import com.buddhanag.luminux.designsystem.LocalMetro
import com.buddhanag.luminux.designsystem.MetroMetrics
import com.buddhanag.luminux.designsystem.MetroMotion
import com.buddhanag.luminux.designsystem.MetroText
import com.buddhanag.luminux.designsystem.MetroType
import com.buddhanag.luminux.designsystem.Semilight
import com.buddhanag.luminux.designsystem.metroFont
import com.buddhanag.luminux.designsystem.tiltPress
import com.buddhanag.luminux.feature.grid.AssetThumbnail
import kotlinx.coroutines.launch

private class LastItems(var items: List<MediaItem> = emptyList())

/**
 * The Metro album picker over the whole screen while [items] isn't null (AssetActions.swift `AddToAlbumSheet`):
 * "new album" asks for a name and makes an album with the photos; a user album row adds them. Either closes it.
 */
@Composable
fun AddToAlbumSheet(items: List<MediaItem>?, onDismiss: () -> Unit) {
    // Keeps the rows on screen while the sheet slides away.
    val last = remember { LastItems() }
    if (items != null) last.items = items
    AnimatedVisibility(
        items != null,
        enter = slideInVertically(MetroMotion.standard()) { it / 3 } + fadeIn(MetroMotion.standard()),
        exit = slideOutVertically(MetroMotion.standard()) { it / 3 } + fadeOut(MetroMotion.standard()),
    ) {
        Sheet(last.items, onDismiss)
    }
}

@Composable
private fun Sheet(items: List<MediaItem>, onDismiss: () -> Unit) {
    val metro = LocalMetro.current
    val photos = LocalContext.current.container.photos
    val snapshot by photos.snapshot.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var isNaming by remember { mutableStateOf(false) }
    BackHandler(onBack = onDismiss)

    Column(
        Modifier
            .fillMaxSize()
            .background(metro.background)
            // Touches stop here instead of reaching the grid underneath.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(start = MetroMetrics.margin + 12.dp, end = MetroMetrics.margin + 12.dp, top = 24.dp),
    ) {
        MetroText("LUMINUX", MetroType.overline)
        MetroText("add to album", MetroType.title, Modifier.offset(x = (-3).dp), maxLines = 1, softWrap = false)
        LazyColumn(contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "new") {
                TiltRow({ isNaming = true }) {
                    Box(Modifier.size(64.dp).background(metro.accentColor), contentAlignment = Alignment.Center) {
                        Image(ActionIcons.add, null, Modifier.size(28.dp), colorFilter = ColorFilter.tint(Color.White))
                    }
                    MetroText("new album", metroFont(22.sp, Semilight))
                }
            }
            items(snapshot.albums.filter { it.isUserAlbum }, key = { it.id }) { album ->
                TiltRow({
                    scope.launch {
                        photos.add(items, toAlbum = album.id)
                        onDismiss()
                    }
                }) { AlbumRowContent(album) }
            }
        }
    }
    if (isNaming) {
        NameDialog("New album", "create", onConfirm = { name ->
            scope.launch {
                photos.createAlbum(name, items)
                onDismiss()
            }
        }, onDismiss = { isNaming = false })
    }
}

@Composable
private fun TiltRow(onClick: () -> Unit, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .tiltPress(interaction, trackTouch = false)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun AlbumRowContent(album: Album) {
    AlbumCover(album, 64.dp, Modifier.size(64.dp))
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        MetroText(album.title, metroFont(22.sp, Semilight), maxLines = 1)
        MetroText("${album.count}", MetroType.caption, color = LocalMetro.current.secondary)
    }
}

/** An album's key photo, or the chrome colour while it has none. [side] sets the thumbnail's pixel size. */
@Composable
fun AlbumCover(album: Album, side: Dp, modifier: Modifier = Modifier) {
    val photos = LocalContext.current.container.photos
    val item = album.keyItemId?.let(photos::item)
    val px = with(LocalDensity.current) { side.roundToPx() }
    if (item != null) AssetThumbnail(item, px, modifier) else Box(modifier.background(LocalMetro.current.chrome))
}
