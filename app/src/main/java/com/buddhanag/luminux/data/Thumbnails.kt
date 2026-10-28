package com.buddhanag.luminux.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.CancellationSignal
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.Image
import coil3.memory.MemoryCache
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.Options
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext

/** A square grid thumbnail; image or video poster, from MediaStore's own thumbnail cache. */
data class MediaThumb(val uri: Uri, val side: Int)

/** Coil 3 core can't decode video frames; `loadThumbnail` covers images and videos without coil-video. */
class MediaThumbFetcher(private val data: MediaThumb, private val resolver: ContentResolver) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val signal = CancellationSignal()
        val cancel = currentCoroutineContext()[Job]?.invokeOnCompletion { signal.cancel() }
        try {
            val bitmap = resolver.loadThumbnail(data.uri, Size(data.side, data.side), signal)
            return ImageFetchResult(bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
        } finally {
            cancel?.dispose()
        }
    }

    class Factory(private val resolver: ContentResolver) : Fetcher.Factory<MediaThumb> {
        override fun create(data: MediaThumb, options: Options, imageLoader: ImageLoader) = MediaThumbFetcher(data, resolver)
    }
}

object Thumbnails {
    /** 72 for the 15-column overview. */
    private val buckets = listOf(72, 128, 256, 384, 512, 768, 1024, 1536)

    /** Rounds a cell's pixel side up to the first bucket, so nearby sizes share cache entries. */
    fun requestSide(px: Int): Int = buckets.firstOrNull { it >= px } ?: px

    fun memoryKey(item: MediaItem, side: Int) = "${item.thumbKey}-$side"

    /** The biggest grid thumbnail of [item] in the memory cache, if it's at least [atLeast] px, with its side. */
    fun cached(loader: ImageLoader, item: MediaItem, atLeast: Int = 0): Pair<Image, Int>? {
        val cache = loader.memoryCache ?: return null
        for (side in buckets.asReversed()) {
            if (side < atLeast) break
            cache[MemoryCache.Key(memoryKey(item, side))]?.let { return it.image to side }
        }
        return null
    }

    /** The grid cell's request. Prefetch uses the same one, so the cell hits the memory cache. */
    fun gridRequest(context: Context, item: MediaItem, cellPx: Int): ImageRequest {
        val side = requestSide(cellPx)
        return ImageRequest.Builder(context)
            .data(MediaThumb(item.contentUri, side))
            .memoryCacheKey(memoryKey(item, side))
            .build()
    }
}

/** Loads grid thumbnails ahead of the scroll and of a pinch (iOS `ImageLoader.cache` and `preload`). */
class ThumbnailPrefetcher(private val context: Context, private val imageLoader: ImageLoader) {
    private var batch: List<Disposable> = emptyList()
    private var batchKey: Any? = null
    private var preloads: List<Disposable> = emptyList()

    /**
     * Warms `index ± columns × 30` items, replacing the previous batch. Asking again for the batch already warming (a
     * pinch's start, then its preview) would throw that work away, so it's skipped.
     */
    fun cache(items: List<MediaItem>, cellPx: Int) {
        val key = Triple(items.firstOrNull()?.id, items.size, Thumbnails.requestSide(cellPx))
        if (items.isEmpty() || key == batchKey) return
        batchKey = key
        batch.forEach { it.dispose() }
        batch = items.map { imageLoader.enqueue(Thumbnails.gridRequest(context, it, cellPx)) }
    }

    /**
     * Decodes [items] ahead, nearest [around] first, so photos about to come on screen (the edges of a pinch zooming
     * out) draw on their first frame. Photos cached at any size are skipped; a newer call replaces this one.
     */
    fun preload(items: List<MediaItem>, around: MediaItem, cellPx: Int) {
        preloads.forEach { it.dispose() }
        val center = items.indexOf(around).coerceAtLeast(0)
        preloads = items.indices.sortedBy { kotlin.math.abs(it - center) }
            .map { items[it] }
            .filter { Thumbnails.cached(imageLoader, it) == null }
            .map { imageLoader.enqueue(Thumbnails.gridRequest(context, it, cellPx)) }
    }
}
