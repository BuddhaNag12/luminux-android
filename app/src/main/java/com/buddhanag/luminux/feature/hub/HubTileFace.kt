package com.buddhanag.luminux.feature.hub

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.buddhanag.luminux.data.AssetSource
import com.buddhanag.luminux.data.MediaItem
import com.buddhanag.luminux.data.PhotoRepository
import com.buddhanag.luminux.data.Thumbnails
import com.buddhanag.luminux.data.hub.HubLayout
import com.buddhanag.luminux.data.hub.HubTile
import com.buddhanag.luminux.data.hub.HubTileKind
import com.buddhanag.luminux.data.hub.HubTileSize
import com.buddhanag.luminux.designsystem.LiveTile
import com.buddhanag.luminux.designsystem.LocalMetro
import com.buddhanag.luminux.designsystem.MetroText
import com.buddhanag.luminux.designsystem.MetroTile
import com.buddhanag.luminux.designsystem.Semilight
import com.buddhanag.luminux.designsystem.metroFont
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * What the faces read: the library, the hub's sets and the "tile patterns" setting. Not a data class, so a fresh one
 * per library change recomposes every face; items come from the repository's per-generation memo.
 */
/** [placeCovers]: the first photo of each of the most recent places, for the places tile. */
internal class TileFaceData(
    val photos: PhotoRepository,
    val layout: HubLayout,
    val animatesPatterns: Boolean,
    val placeCovers: List<MediaItem> = emptyList(),
)

internal fun tileTitle(kind: HubTileKind, data: TileFaceData): String = when (kind) {
    HubTileKind.CameraRoll -> "camera roll"
    HubTileKind.Albums -> "albums"
    HubTileKind.Date -> "date"
    HubTileKind.Favorites -> "favorites"
    HubTileKind.Places -> "places"
    is HubTileKind.Album -> data.photos.album(kind.id)?.title ?: "album"
    is HubTileKind.PhotoSet -> data.layout.sets[kind.id]?.title ?: "photos"
}

/** One tile: a live tile of photos, the date, or the albums pattern (HubTileGrid.swift `HubTileFace`). */
@Composable
internal fun HubTileFace(tile: HubTile, data: TileFaceData, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val isSmall = tile.size == HubTileSize.Small
    MetroTile(onClick, modifier, title = tileTitle(tile.kind, data), showsTitle = !isSmall) {
        val photos = { source: AssetSource -> data.photos.items(source).take(LIVE_ITEMS) }
        when (val kind = tile.kind) {
            HubTileKind.CameraRoll -> Photos(photos(AssetSource.All), HubIcons.photo, tile.size)
            HubTileKind.Albums -> AmbientTile(AmbientPattern.Albums, data.animatesPatterns)
            HubTileKind.Date -> {
                AmbientTile(AmbientPattern.Date, data.animatesPatterns)
                DateFace(isSmall)
            }
            HubTileKind.Favorites -> Photos(photos(AssetSource.Favorites), HubIcons.heart, tile.size)
            HubTileKind.Places -> Photos(data.placeCovers, HubIcons.map, tile.size)
            is HubTileKind.Album -> Photos(photos(AssetSource.Album(kind.id)), HubIcons.albumStack, tile.size)
            is HubTileKind.PhotoSet -> Photos(
                photos(AssetSource.Identifiers(data.layout.sets[kind.id]?.photoIds.orEmpty())),
                HubIcons.photoStack, tile.size,
            )
        }
    }
}

private const val LIVE_ITEMS = 8

@Composable
private fun Photos(items: List<MediaItem>, fallback: ImageVector, size: HubTileSize) {
    if (items.isEmpty()) return Glyph(fallback, size == HubTileSize.Small)
    val context = LocalContext.current
    val side = when (size) {
        HubTileSize.Small -> 100.dp
        HubTileSize.Medium -> 200.dp
        HubTileSize.Wide -> 400.dp
    }
    val px = with(LocalDensity.current) { side.roundToPx() }
    // No style: each change picks flip, slide or zoom at random.
    LiveTile(items, key = { it.id }) { item ->
        AsyncImage(Thumbnails.gridRequest(context, item, px), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun Glyph(icon: ImageVector, isSmall: Boolean) {
    Box(Modifier.fillMaxSize().background(LocalMetro.current.accentColor), contentAlignment = Alignment.Center) {
        Image(icon, null, Modifier.size(if (isSmall) 22.dp else 34.dp), colorFilter = ColorFilter.tint(Color.White))
    }
}

private val monthFormat = DateTimeFormatter.ofPattern("MMM")

@Composable
private fun DateFace(isSmall: Boolean) {
    val now = LocalDate.now()
    Column(Modifier.padding(if (isSmall) 6.dp else 10.dp)) {
        if (!isSmall) MetroText(monthFormat.format(now).lowercase(), metroFont(22.sp, Semilight), color = Color.White)
        MetroText(now.dayOfMonth.toString(), metroFont(if (isSmall) 34.sp else 56.sp, FontWeight.Light), color = Color.White)
    }
}
