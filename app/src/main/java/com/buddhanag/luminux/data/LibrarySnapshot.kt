package com.buddhanag.luminux.data

import java.time.ZoneId

/** One loaded state of the library. Everything is derived in [build], so it can be unit-tested. */
data class LibrarySnapshot(
    /** iOS changeToken: goes up by 1 on every applied reload; 0 = not loaded. */
    val generation: Int = 0,
    val all: List<MediaItem> = emptyList(),
    val favorites: List<MediaItem> = emptyList(),
    val videos: List<MediaItem> = emptyList(),
    val recent: List<MediaItem> = emptyList(),
    val sections: List<MonthSection> = emptyList(),
    val albums: List<Album> = emptyList(),
    val byId: Map<Long, MediaItem> = emptyMap(),
    /** User album row id -> MediaStore ids, including ids no longer present. */
    val members: Map<Long, List<Long>> = emptyMap(),
) {
    val isLoaded: Boolean get() = generation > 0

    fun album(id: String): Album? = albums.firstOrNull { it.id == id }

    /** Newest first for everything but [AssetSource.Identifiers], which is oldest first. */
    fun items(source: AssetSource): List<MediaItem> = when (source) {
        AssetSource.All -> all
        AssetSource.Favorites -> favorites
        AssetSource.Videos -> videos
        AssetSource.Recent -> recent
        is AssetSource.Album -> when (source.id) {
            AlbumIds.SCREENSHOTS -> all.filter { it.isScreenshot }
            AlbumIds.PANORAMAS -> all.filter { it.isPanorama }
            else -> AlbumIds.folderPath(source.id)?.let { path -> all.filter { it.relativePath == path } }
                ?: AlbumIds.userRowId(source.id)?.let { userAlbumItems(it) }.orEmpty()
        }
        is AssetSource.Identifiers -> source.ids.mapNotNull { byId[it] }.sortedWith(oldestFirst)
    }

    private fun userAlbumItems(rowId: Long): List<MediaItem> =
        members[rowId].orEmpty().mapNotNull { byId[it] }.sortedWith(newestFirst)

    companion object {
        const val RECENT_WINDOW_MS = 2_592_000_000L // 30 days

        val newestFirst: Comparator<MediaItem> =
            compareByDescending<MediaItem, Long?>(nullsFirst()) { it.effectiveDate }.thenByDescending { it.id }
        private val oldestFirst: Comparator<MediaItem> = newestFirst.reversed()

        fun build(
            generation: Int,
            items: List<MediaItem>,
            albumRows: List<AlbumRow>,
            memberRows: List<AlbumMemberRow>,
            nowMs: Long,
            zone: ZoneId = ZoneId.systemDefault(),
        ): LibrarySnapshot {
            val all = items.sortedWith(newestFirst)
            val members = memberRows.groupBy({ it.albumId }, { it.mediaStoreId })
            val partial = LibrarySnapshot(
                generation = generation,
                all = all,
                favorites = all.filter { it.isFavorite },
                videos = all.filter { it.type == MediaType.VIDEO },
                recent = all.filter { (it.effectiveDate ?: Long.MIN_VALUE) > nowMs - RECENT_WINDOW_MS },
                sections = MonthSection.group(all.map { it.effectiveDate }, zone),
                byId = all.associateBy { it.id },
                members = members,
            )
            // User albums come first and show even when empty; smart albums only when they have something.
            val user = albumRows.sortedWith(compareBy({ it.createdAt }, { it.id })).map { row ->
                val list = partial.userAlbumItems(row.id)
                Album(AlbumIds.user(row.id), row.title.lowercase(), list.size, list.firstOrNull()?.id, isUserAlbum = true)
            }
            val smart = listOf(AlbumIds.PANORAMAS to "panoramas", AlbumIds.SCREENSHOTS to "screenshots").mapNotNull { (id, title) ->
                val list = partial.items(AssetSource.Album(id))
                list.firstOrNull()?.let { Album(id, title, list.size, it.id, isUserAlbum = false) }
            }
            return partial.copy(albums = user + smart + folderAlbums(all))
        }

        /**
         * Android has no system albums: gallery apps show the folders photos sit in (Camera, WhatsApp Images, Download…).
         * One album per folder, the most recently used first. Screenshot folders are left out: the smart album has them.
         */
        internal fun folderAlbums(all: List<MediaItem>): List<Album> {
            val folders = all.filter { it.relativePath.isNotEmpty() && !it.isScreenshot }.groupBy { it.relativePath }
            // `all` is newest first, so each folder's first item is its cover and its newest photo.
            val ordered = folders.entries.sortedWith(compareBy(newestFirst) { it.value.first() })
            val names = ordered.associate { (path, _) -> path to folderName(path) }
            val clashes = names.values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            return ordered.map { (path, list) ->
                val name = names.getValue(path)
                // Two folders with one name (DCIM/Instagram, Pictures/Instagram) get their parent to tell them apart.
                val title = if (name in clashes) "$name · ${folderName(path, fromEnd = 1)}" else name
                Album(AlbumIds.folder(path), title, list.size, list.first().id, isUserAlbum = false)
            }
        }

        /** "DCIM/Camera/" → "camera"; [fromEnd] 1 gives the parent, "dcim". */
        private fun folderName(path: String, fromEnd: Int = 0): String {
            val parts = path.trim('/').split('/').filter { it.isNotEmpty() }
            return (parts.getOrNull(parts.size - 1 - fromEnd) ?: path).lowercase()
        }
    }
}
