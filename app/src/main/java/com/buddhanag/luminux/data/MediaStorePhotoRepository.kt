package com.buddhanag.luminux.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns
import androidx.activity.result.IntentSenderRequest
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MediaStorePhotoRepository(
    private val context: Context,
    private val albumDao: AlbumDao,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) : PhotoRepository {
    private val resolver: ContentResolver = context.contentResolver
    private val generation = AtomicInteger()
    // Until DataStore answers, "asked before" reads false: a denied app briefly looks undetermined at launch.
    @Volatile private var askedBefore = false
    @Volatile private var canAskAgain = false
    private val manualReloads = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val _access = MutableStateFlow(readAccess())
    override val access: StateFlow<MediaAccess> = _access.asStateFlow()
    private val _snapshot = MutableStateFlow(LibrarySnapshot())
    override val snapshot: StateFlow<LibrarySnapshot> = _snapshot.asStateFlow()

    override val hasMediaLocation: Boolean get() = context.has(Manifest.permission.ACCESS_MEDIA_LOCATION)

    init {
        settings.askedMediaAccess.onEach { askedBefore = it; refreshAccess() }.launchIn(scope)

        // Access decides whether MediaStore is read at all; flatMapLatest drops stale loads like the iOS generation check.
        val loads: Flow<Pair<MediaAccess, List<MediaItem>>> = _access.flatMapLatest { a ->
            if (!a.canRead) flowOf(a to emptyList())
            else merge(mediaChanges(), manualReloads)
                // MediaProvider fires several notifications per insert, trash or favorite; iOS sends one per change.
                .debounce(250)
                .mapLatest { a to queryItems() }
                .flowOn(Dispatchers.IO)
        }
        combine(loads, albumDao.albums(), albumDao.members()) { (a, items), albums, members ->
            val next = LibrarySnapshot.build(generation.incrementAndGet(), items, albums, members, System.currentTimeMillis())
            if (a == MediaAccess.FULL) prune(next)
            next
        }.onEach { _snapshot.value = it }.flowOn(Dispatchers.Default).launchIn(scope)
    }

    // region Access

    @SuppressLint("InlinedApi") // On Android 13 the user-selected permission reads as not granted, which is right.
    private fun readAccess() = mediaAccess(
        images = context.has(Manifest.permission.READ_MEDIA_IMAGES),
        video = context.has(Manifest.permission.READ_MEDIA_VIDEO),
        userSelected = context.has(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED),
        askedBefore = askedBefore,
        canAskAgain = canAskAgain,
    )

    override fun refreshAccess(activity: Activity?) {
        activity?.let { canAskAgain = it.shouldShowRequestPermissionRationale(Manifest.permission.READ_MEDIA_IMAGES) }
        val next = readAccess()
        val changed = _access.value != next
        _access.value = next
        // A new partial selection sends no change notification, so reload when coming back under LIMITED.
        if (!changed && next == MediaAccess.LIMITED) manualReloads.tryEmit(Unit)
    }

    override fun onPermissionResult(activity: Activity) {
        askedBefore = true
        scope.launch { settings.set(SettingsKeys.askedMediaAccess, true) }
        refreshAccess(activity)
    }

    // endregion
    // region Loading

    private fun mediaChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .forEach { resolver.registerContentObserver(it, true, observer) }
        send(Unit) // initial load
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    // No SQL selection or sort (MediaProvider rejects functions); the default Bundle excludes pending and trashed rows.
    private fun queryItems(): List<MediaItem> =
        query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaType.IMAGE) + query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaType.VIDEO)

    private fun query(collection: Uri, type: MediaType): List<MediaItem> {
        val out = mutableListOf<MediaItem>()
        resolver.query(collection, PROJECTION, Bundle(), null)?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaColumns._ID)
            val mime = c.getColumnIndexOrThrow(MediaColumns.MIME_TYPE)
            val taken = c.getColumnIndexOrThrow(MediaColumns.DATE_TAKEN)
            val added = c.getColumnIndexOrThrow(MediaColumns.DATE_ADDED)
            val modified = c.getColumnIndexOrThrow(MediaColumns.DATE_MODIFIED)
            val width = c.getColumnIndexOrThrow(MediaColumns.WIDTH)
            val height = c.getColumnIndexOrThrow(MediaColumns.HEIGHT)
            val orientation = c.getColumnIndexOrThrow(MediaColumns.ORIENTATION)
            val duration = c.getColumnIndexOrThrow(MediaColumns.DURATION)
            val favorite = c.getColumnIndexOrThrow(MediaColumns.IS_FAVORITE)
            val path = c.getColumnIndexOrThrow(MediaColumns.RELATIVE_PATH)
            val name = c.getColumnIndexOrThrow(MediaColumns.DISPLAY_NAME)
            val size = c.getColumnIndexOrThrow(MediaColumns.SIZE)
            while (c.moveToNext()) {
                // WIDTH/HEIGHT are the stored size; rotated items show the other way round.
                val swap = c.getInt(orientation).let { it == 90 || it == 270 }
                val w = c.getInt(width)
                val h = c.getInt(height)
                out += MediaItem(
                    id = c.getLong(id),
                    type = type,
                    mimeType = c.getString(mime).orEmpty(),
                    dateTaken = if (c.isNull(taken)) null else c.getLong(taken).takeIf { it > 0 },
                    dateAdded = c.getLong(added) * 1000,
                    dateModified = c.getLong(modified),
                    width = if (swap) h else w,
                    height = if (swap) w else h,
                    durationMs = c.getLong(duration),
                    isFavorite = c.getInt(favorite) == 1,
                    relativePath = c.getString(path).orEmpty(),
                    displayName = c.getString(name).orEmpty(),
                    sizeBytes = c.getLong(size),
                )
            }
        }
        return out
    }

    /** Only under FULL access: under LIMITED a missing id is just not selected, so its membership stays. */
    private suspend fun prune(snapshot: LibrarySnapshot) {
        val orphans = snapshot.members.values.flatten().filterNot { it in snapshot.byId }.distinct()
        orphans.chunked(500).forEach { albumDao.deleteMembersFor(it) } // SQLite variable limit
    }

    // endregion
    // region Lookups

    private var memo: Pair<Int, MutableMap<AssetSource, List<MediaItem>>> = 0 to mutableMapOf()

    @Synchronized
    override fun items(source: AssetSource): List<MediaItem> {
        val snap = _snapshot.value
        if (memo.first != snap.generation) memo = snap.generation to mutableMapOf()
        return memo.second.getOrPut(source) { snap.items(source) }
    }

    // shortcut: no `_ID = ?` fallback query for an item not yet in the snapshot; callers wait ~250 ms for the refresh.
    override fun item(id: Long): MediaItem? = _snapshot.value.byId[id]
    override fun album(id: String): Album? = _snapshot.value.album(id)

    // endregion
    // region System-consent changes

    override fun favoriteRequest(items: List<MediaItem>, favorite: Boolean): IntentSenderRequest =
        IntentSenderRequest.Builder(MediaStore.createFavoriteRequest(resolver, items.map { it.contentUri }, favorite)).build()

    override fun trashRequest(items: List<MediaItem>): IntentSenderRequest =
        IntentSenderRequest.Builder(MediaStore.createTrashRequest(resolver, items.map { it.contentUri }, true)).build()

    // endregion
    // region Albums

    override suspend fun createAlbum(title: String, items: List<MediaItem>): String =
        AlbumIds.user(albumDao.create(title, items.map { it.id }, System.currentTimeMillis()))

    override suspend fun renameAlbum(id: String, title: String) {
        AlbumIds.userRowId(id)?.let { albumDao.rename(it, title) }
    }

    override suspend fun deleteAlbum(id: String) {
        AlbumIds.userRowId(id)?.let { albumDao.delete(it) }
    }

    override suspend fun add(items: List<MediaItem>, toAlbum: String) {
        AlbumIds.userRowId(toAlbum)?.let { albumDao.addIfExists(it, items.map { i -> i.id }, System.currentTimeMillis()) }
    }

    override suspend fun remove(items: List<MediaItem>, fromAlbum: String) {
        val rowId = AlbumIds.userRowId(fromAlbum) ?: return
        items.map { it.id }.chunked(500).forEach { albumDao.removeMembers(rowId, it) }
    }

    // endregion
    // region Editing

    override suspend fun editPreview(item: MediaItem, maxPixelSize: Int): Bitmap = withContext(Dispatchers.IO) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, item.contentUri)) { d, info, _ ->
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > maxPixelSize) {
                val scale = maxPixelSize.toDouble() / longest
                d.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
            }
        }
    }

    override suspend fun saveEditedCopy(item: MediaItem, recipe: EditRecipe): Uri = withContext(Dispatchers.IO) {
        require(item.isEditable) { "not editable: ${item.mimeType}" }
        // ImageDecoder applies the EXIF orientation, so the pixels come out upright.
        val src = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, item.contentUri)) { d, info, _ ->
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            // shortcut: halve until it fits in a quarter of the heap; upgrade to tiled region decoding if users report soft edits.
            var s = 1
            while (info.size.width.toLong() / s * (info.size.height / s) * 4 > Runtime.getRuntime().maxMemory() / 4) s *= 2
            d.setTargetSampleSize(s)
        }
        val out = PhotoEditing.render(src, recipe)
        // Apps may only insert under DCIM/ or Pictures/.
        val dir = item.relativePath.takeIf { it.startsWith("DCIM/") || it.startsWith("Pictures/") } ?: "Pictures/Luminux/"
        val values = ContentValues().apply {
            put(MediaColumns.DISPLAY_NAME, item.displayName.substringBeforeLast('.') + "_edited.jpg")
            put(MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaColumns.RELATIVE_PATH, dir)
            item.dateTaken?.let { put(MediaColumns.DATE_TAKEN, it) } // keeps the copy in the original's month
            put(MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: error("insert failed")
        try {
            checkNotNull(resolver.openOutputStream(uri)).use { out.compress(Bitmap.CompressFormat.JPEG, 93, it) }
            copyExif(from = if (hasMediaLocation) MediaStore.setRequireOriginal(item.contentUri) else item.contentUri, to = uri)
            resolver.update(uri, ContentValues().apply { put(MediaColumns.IS_PENDING, 0) }, null, null)
            uri
        } catch (t: Throwable) {
            resolver.delete(uri, null, null) // remove our own half-written row
            throw t
        }
    }

    private fun copyExif(from: Uri, to: Uri) {
        // A source without readable metadata still saves; the new JPEG has no orientation tag, so it reads as upright.
        val source = runCatching { resolver.openInputStream(from)?.use { ExifInterface(it) } }.getOrNull() ?: return
        checkNotNull(resolver.openFileDescriptor(to, "rw")).use { fd ->
            val target = ExifInterface(fd.fileDescriptor)
            EXIF_COPIED.forEach { tag -> source.getAttribute(tag)?.let { target.setAttribute(tag, it) } }
            // The pixels are upright now, so the file must not ask for another turn.
            target.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            target.saveAttributes()
        }
    }

    // endregion
    // region Details and share

    override suspend fun location(item: MediaItem): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        if (!hasMediaLocation) return@withContext null
        runCatching {
            val original = MediaStore.setRequireOriginal(item.contentUri)
            if (item.type == MediaType.IMAGE) {
                resolver.openInputStream(original)?.use { ExifInterface(it).latLong }?.let { it[0] to it[1] }
            } else {
                MediaMetadataRetriever().run {
                    try {
                        setDataSource(context, original)
                        extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)?.let(::parseIso6709)
                    } finally {
                        release()
                    }
                }
            }
        }.getOrNull()
    }

    override suspend fun exif(item: MediaItem): ExifSummary = withContext(Dispatchers.IO) {
        val exif = runCatching { resolver.openInputStream(item.contentUri)?.use { ExifInterface(it) } }.getOrNull()
        fun d(tag: String) = exif?.getAttributeDouble(tag, Double.NaN)?.takeUnless { it.isNaN() || it == 0.0 }
        fun i(tag: String) = exif?.getAttributeInt(tag, 0)?.takeIf { it > 0 }
        ExifSummary(
            make = exif?.getAttribute(ExifInterface.TAG_MAKE),
            model = exif?.getAttribute(ExifInterface.TAG_MODEL),
            fNumber = d(ExifInterface.TAG_F_NUMBER),
            exposureTime = d(ExifInterface.TAG_EXPOSURE_TIME),
            iso = i(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY),
            focalLength35mm = i(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM),
        )
    }

    override fun shareIntent(items: List<MediaItem>): Intent {
        val uris = items.map { it.contentUri }
        val type = when {
            items.all { it.type == MediaType.IMAGE } -> "image/*"
            items.all { it.type == MediaType.VIDEO } -> "video/*"
            else -> "*/*"
        }
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        send.type = type
        if (uris.isNotEmpty()) {
            send.clipData = ClipData.newRawUri(null, uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        }
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, null)
    }

    // endregion

    private companion object {
        val PROJECTION = arrayOf(
            MediaColumns._ID, MediaColumns.MIME_TYPE, MediaColumns.DATE_TAKEN, MediaColumns.DATE_ADDED,
            MediaColumns.DATE_MODIFIED, MediaColumns.WIDTH, MediaColumns.HEIGHT, MediaColumns.ORIENTATION,
            MediaColumns.DURATION, MediaColumns.IS_FAVORITE, MediaColumns.RELATIVE_PATH,
            MediaColumns.DISPLAY_NAME, MediaColumns.SIZE,
        )

        val EXIF_COPIED = listOf(
            ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
            ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
            ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
        )
    }
}

/** `"+37.4219-122.0840/"` (ISO 6709, as MediaMetadataRetriever reports it) to latitude, longitude. */
internal fun parseIso6709(value: String): Pair<Double, Double>? {
    val m = Regex("""([+-]\d+(?:\.\d+)?)([+-]\d+(?:\.\d+)?)""").find(value) ?: return null
    return m.groupValues[1].toDouble() to m.groupValues[2].toDouble()
}
