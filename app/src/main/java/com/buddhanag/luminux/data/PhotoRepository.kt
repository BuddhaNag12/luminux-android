package com.buddhanag.luminux.data

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.flow.StateFlow

data class ExifSummary(
    val make: String?,
    val model: String?,
    val fNumber: Double?,
    /** Seconds. */
    val exposureTime: Double?,
    val iso: Int?,
    val focalLength35mm: Int?,
)

interface PhotoRepository {
    val access: StateFlow<MediaAccess>
    val snapshot: StateFlow<LibrarySnapshot>
    /** `ACCESS_MEDIA_LOCATION`; never affects [MediaAccess.canRead]. Needed for location, EXIF GPS copy and Places. */
    val hasMediaLocation: Boolean

    /** Re-reads the permissions. The app calls it on every activity resume; pass the activity to learn "can ask again". */
    fun refreshAccess(activity: Activity? = null)
    /** Call with the `RequestMultiplePermissions(MEDIA_PERMISSIONS)` result. */
    fun onPermissionResult(activity: Activity)

    /** Resolved against [snapshot]; memoized per generation. */
    fun items(source: AssetSource): List<MediaItem>
    fun item(id: Long): MediaItem?
    fun album(id: String): Album?

    // Need system consent: the UI launches these with StartIntentSenderForResult; the observer refreshes.
    fun favoriteRequest(items: List<MediaItem>, favorite: Boolean): IntentSenderRequest
    fun trashRequest(items: List<MediaItem>): IntentSenderRequest

    // App-only albums (Room). Smart album ids are ignored by add/remove/rename/delete.
    /** Returns the new album's id, `"user:<rowId>"`. */
    suspend fun createAlbum(title: String, items: List<MediaItem> = emptyList()): String
    suspend fun renameAlbum(id: String, title: String)
    /** Photos stay. */
    suspend fun deleteAlbum(id: String)
    suspend fun add(items: List<MediaItem>, toAlbum: String)
    suspend fun remove(items: List<MediaItem>, fromAlbum: String)

    /** The photo upright, longest side ≤ [maxPixelSize], as a software bitmap for [PhotoEditing.render]. */
    suspend fun editPreview(item: MediaItem, maxPixelSize: Int = 2048): Bitmap
    /** Writes a new JPEG next to the original, which is never touched. Returns the new item's uri. */
    suspend fun saveEditedCopy(item: MediaItem, recipe: EditRecipe): Uri
    suspend fun rotateLeftCopy(item: MediaItem): Uri = saveEditedCopy(item, EditRecipe().rotatedLeft())

    /** Latitude, longitude; null without [hasMediaLocation] or when the file has none. */
    suspend fun location(item: MediaItem): Pair<Double, Double>?
    suspend fun exif(item: MediaItem): ExifSummary
    fun shareIntent(items: List<MediaItem>): Intent
}
