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
            else -> AlbumIds.userRowId(source.id)?.let { userAlbumItems(it) }.orEmpty()
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
            return partial.copy(albums = user + smart)
        }
    }
}
