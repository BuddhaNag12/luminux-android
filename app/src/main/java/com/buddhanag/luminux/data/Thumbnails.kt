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
    private val buckets = listOf(128, 256, 384, 512, 768, 1024, 1536)

    /** Rounds a cell's pixel side up to the first bucket, so nearby sizes share cache entries. */
    fun requestSide(px: Int): Int = buckets.firstOrNull { it >= px } ?: px

    fun memoryKey(item: MediaItem, side: Int) = "${item.thumbKey}-$side"

    /** The grid cell's request. Prefetch uses the same one, so the cell hits the memory cache. */
    fun gridRequest(context: Context, item: MediaItem, cellPx: Int): ImageRequest {
        val side = requestSide(cellPx)
        return ImageRequest.Builder(context)
            .data(MediaThumb(item.contentUri, side))
            .memoryCacheKey(memoryKey(item, side))
            .build()
    }
}

/** Loads grid thumbnails ahead of the scroll, replacing the previous batch (iOS `ImageLoader.cache`). */
class ThumbnailPrefetcher(private val context: Context, private val imageLoader: ImageLoader) {
    private var batch: List<Disposable> = emptyList()

    /** The grid passes `index ± columns × 30` items. */
    fun cache(items: List<MediaItem>, cellPx: Int) {
        batch.forEach { it.dispose() }
        batch = items.map { imageLoader.enqueue(Thumbnails.gridRequest(context, it, cellPx)) }
    }
}
