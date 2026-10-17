package com.buddhanag.luminux.data

import android.content.Context
import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Scale
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Copies a few downscaled photos for the home-screen widget: recent, favorites, and every album or photo set
 * someone made a widget from.
 */
class LiveTileExporter(
    private val context: Context,
    private val imageLoader: ImageLoader,
    private val photos: PhotoRepository,
    private val store: LiveTileStore,
    /** Hub photo set id -> (title, photo ids). shortcut: AppContainer passes `{ null }` until spec 05 brings HubTiles. */
    private val photoSet: (String) -> Pair<String, List<Long>>?,
) {
    private companion object {
        const val SIDE = 600
        const val QUALITY = 80
    }

    suspend fun export() {
        val snap = photos.snapshot.value
        // Recent stays the newest photos; favorites shuffle like collections do.
        store.replaceImages(jpegs(snap.all.take(30)), LiveTileStore.RECENT)
        store.replaceImages(jpegs(snap.favorites.take(500).shuffled()), LiveTileStore.FAVORITES)

        // Albums and sets that are gone take their widget choice with them; the rest pick up renames and new photos.
        val kept = store.collections.first().mapNotNull { collection ->
            val (title, items) = contents(collection) ?: run {
                store.removeImages(collection.folderName)
                return@mapNotNull null
            }
            store.replaceImages(jpegs(items.take(500).shuffled()), collection.folderName)
            collection.copy(title = title)
        }
        store.setCollections(kept)
        updateWidgets()
    }

    /** Adds an album or photo set to the widget's choices with its photos ready ("make a widget", spec 10). */
    suspend fun exportOne(collection: TileCollection) {
        val (title, items) = contents(collection) ?: return
        val stored = collection.copy(title = title)
        store.replaceImages(jpegs(items.take(500).shuffled()), stored.folderName)
        store.setCollections(store.collections.first().filter { it.id != stored.id } + stored)
        updateWidgets()
    }

    suspend fun remove(id: String) {
        val all = store.collections.first()
        all.filter { it.id == id }.forEach { store.removeImages(it.folderName) }
        store.setCollections(all.filter { it.id != id })
        updateWidgets()
    }

    suspend fun contains(id: String): Boolean = store.collections.first().any { it.id == id }

    /** The collection's current title and items, or null once it no longer exists. */
    private fun contents(collection: TileCollection): Pair<String, List<MediaItem>>? {
        val id = collection.id
        return when {
            id.startsWith("album:") -> {
                val albumId = id.removePrefix("album:")
                // Smart albums always exist, even when empty and so missing from the album list.
                val title = photos.album(albumId)?.title ?: when (albumId) {
                    AlbumIds.PANORAMAS -> "panoramas"
                    AlbumIds.SCREENSHOTS -> "screenshots"
                    else -> return null
                }
                title to photos.items(AssetSource.Album(albumId))
            }
            id.startsWith("set:") -> photoSet(id.removePrefix("set:"))?.let { (title, ids) -> title to photos.items(AssetSource.Identifiers(ids)) }
            else -> null
        }
    }

    /** Up to [LiveTileStore.MAX_IMAGES] photos from [pool], 600 px aspect-fill JPEGs. */
    private suspend fun jpegs(pool: List<MediaItem>): List<ByteArray> =
        pool.filter { it.type == MediaType.IMAGE }.take(LiveTileStore.MAX_IMAGES).mapNotNull { item ->
            val request = ImageRequest.Builder(context)
                .data(item.contentUri)
                .size(SIDE)
                .scale(Scale.FILL)
                .allowHardware(false)
                .build()
            val image = (imageLoader.execute(request) as? SuccessResult)?.image ?: return@mapNotNull null
            withContext(Dispatchers.Default) {
                ByteArrayOutputStream().also { image.toBitmap().compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }.toByteArray()
            }
        }

    // shortcut: spec 10 brings WidgetUpdater; call WidgetUpdater.resetAndUpdateAll(context) here once it exists.
    private fun updateWidgets() = Unit
}
