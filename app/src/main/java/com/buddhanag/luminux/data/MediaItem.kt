package com.buddhanag.luminux.data

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import kotlinx.serialization.Serializable

enum class MediaType { IMAGE, VIDEO }

data class MediaItem(
    /** MediaStore `_ID`; unique across images and videos. */
    val id: Long,
    val type: MediaType,
    val mimeType: String,
    /** `DATE_TAKEN` in ms, null when missing. */
    val dateTaken: Long?,
    /** `DATE_ADDED` s × 1000. */
    val dateAdded: Long,
    /** `DATE_MODIFIED` in seconds. */
    val dateModified: Long,
    /** Display size: already swapped for `ORIENTATION` 90/270. */
    val width: Int,
    val height: Int,
    /** 0 for images. */
    val durationMs: Long,
    val isFavorite: Boolean,
    /** For example `"DCIM/Camera/"`. */
    val relativePath: String,
    val displayName: String,
    val sizeBytes: Long,
) {
    // Computed, so JVM tests can build items without touching the android.net stubs.
    val contentUri: Uri
        get() = ContentUris.withAppendedId(
            if (type == MediaType.IMAGE) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            id,
        )

    /** The iOS creationDate. Null only when both columns are missing, which means "undated". */
    val effectiveDate: Long? get() = dateTaken ?: dateAdded.takeIf { it > 0 }
    val isScreenshot: Boolean get() = relativePath.contains("Screenshots", ignoreCase = true)
    val isPanorama: Boolean
        get() = type == MediaType.IMAGE && !isScreenshot &&
            maxOf(width, height) >= 2.5 * minOf(width, height).coerceAtLeast(1)
    val isEditable: Boolean get() = type == MediaType.IMAGE && mimeType != "image/gif"
    /** Grid thumbnail memory-cache key; the iOS renderKey (id + modification date). */
    val thumbKey: String get() = "$id-$dateModified"
}

/** Where a grid or the viewer gets its items from. Resolved against the snapshot on every change. */
@Serializable
sealed interface AssetSource {
    @Serializable data object All : AssetSource
    @Serializable data object Favorites : AssetSource
    @Serializable data object Videos : AssetSource
    @Serializable data object Recent : AssetSource
    /** `"user:<rowId>"`, `"smart:screenshots"`, `"smart:panoramas"` or `"folder:<relativePath>"`. */
    @Serializable data class Album(val id: String) : AssetSource
    /** A journal entry's photos, oldest first. */
    @Serializable data class Identifiers(val ids: List<Long>) : AssetSource
}

data class Album(val id: String, val title: String, val count: Int, val keyItemId: Long?, val isUserAlbum: Boolean)

object AlbumIds {
    const val SCREENSHOTS = "smart:screenshots"
    const val PANORAMAS = "smart:panoramas"
    private const val USER = "user:"
    private const val FOLDER = "folder:"

    fun user(rowId: Long) = "$USER$rowId"

    /** A device folder, e.g. `folder:DCIM/Camera/`: what Android gallery apps call albums. Read-only here. */
    fun folder(relativePath: String) = "$FOLDER$relativePath"

    fun folderPath(id: String): String? = id.takeIf { it.startsWith(FOLDER) }?.removePrefix(FOLDER)

    /** The Room row id of a user album, or null for smart or malformed ids. */
    fun userRowId(id: String): Long? = id.takeIf { it.startsWith(USER) }?.removePrefix(USER)?.toLongOrNull()
}
