package com.buddhanag.luminux.data.hub

import java.util.UUID

/** What a tile on the hub's collection panel shows and opens (HubTiles.swift). */
sealed interface HubTileKind {
    data object CameraRoll : HubTileKind
    data object Albums : HubTileKind
    data object Date : HubTileKind
    data object Favorites : HubTileKind
    data object Places : HubTileKind
    /** A pinned album, `"user:<rowId>"` or `"smart:*"`. */
    data class Album(val id: String) : HubTileKind
    /** Photos picked in select mode and pinned together. */
    data class PhotoSet(val id: String) : HubTileKind

    /** The five tiles the hub ships with; they can be moved and resized but not unpinned. */
    val isBuiltIn: Boolean get() = this !is Album && this !is PhotoSet
}

/** Windows Phone's tile sizes on a grid four cells wide. */
enum class HubTileSize(val width: Int, val height: Int) {
    Small(1, 1), Medium(2, 2), Wide(4, 2);

    /** The resize button steps medium → small → wide → medium, like the original. */
    val next: HubTileSize get() = when (this) {
        Medium -> Small
        Small -> Wide
        Wide -> Medium
    }
}

data class HubTile(val kind: HubTileKind, val size: HubTileSize = HubTileSize.Medium)

/** [photoIds] in the order they were pinned; the grid shows them oldest first. */
data class PhotoSet(val id: String, val title: String, val photoIds: List<Long>)

/** The collection panel's tiles in order and the photo sets pinned there. Pure, so every rule is JVM-tested. */
data class HubLayout(val tiles: List<HubTile> = DEFAULT, val sets: Map<String, PhotoSet> = emptyMap()) {
    companion object {
        /** Albums and photo sets pinned without Pro; Pro pins as many as you like. */
        const val FREE_PIN_LIMIT = 2

        val DEFAULT = listOf(
            HubTileKind.CameraRoll, HubTileKind.Albums, HubTileKind.Date, HubTileKind.Favorites, HubTileKind.Places,
        ).map { HubTile(it) }

        /** A built-in tile is never lost, even from a damaged store. */
        fun loaded(tiles: List<HubTile>, sets: Map<String, PhotoSet>) =
            if (tiles.isEmpty()) HubLayout(sets = sets)
            else HubLayout(tiles + DEFAULT.filter { d -> tiles.none { it.kind == d.kind } }, sets)
    }

    /** The built-in tiles don't count. */
    val pinCount: Int get() = tiles.count { !it.kind.isBuiltIn }

    fun canPin(isPro: Boolean) = isPro || pinCount < FREE_PIN_LIMIT

    fun isPinned(kind: HubTileKind) = tiles.any { it.kind == kind }

    fun pinAlbum(id: String) = if (isPinned(HubTileKind.Album(id))) this else copy(tiles = tiles + HubTile(HubTileKind.Album(id)))

    /** Returns the new layout and the set's id. An empty title becomes "photos". */
    fun pinPhotoSet(title: String, ids: List<Long>, id: String = UUID.randomUUID().toString()): Pair<HubLayout, String> {
        val name = title.trim().ifEmpty { "photos" }.lowercase()
        return copy(tiles = tiles + HubTile(HubTileKind.PhotoSet(id)), sets = sets + (id to PhotoSet(id, name, ids))) to id
    }

    /** Built-in tiles stay. A photo set only lives on its tile, so unpinning one forgets it. */
    fun unpin(kind: HubTileKind) = if (kind.isBuiltIn) this else copy(
        tiles = tiles.filter { it.kind != kind },
        sets = if (kind is HubTileKind.PhotoSet) sets - kind.id else sets,
    )

    fun move(kind: HubTileKind, to: Int): HubLayout {
        val from = tiles.indexOfFirst { it.kind == kind }.takeIf { it >= 0 } ?: return this
        val list = tiles.toMutableList()
        val tile = list.removeAt(from)
        list.add(to.coerceIn(0, list.size), tile)
        return copy(tiles = list)
    }

    fun resize(kind: HubTileKind) = copy(tiles = tiles.map { if (it.kind == kind) it.copy(size = it.size.next) else it })

    /** An empty title keeps the old one. */
    fun rename(set: String, title: String): HubLayout {
        val name = title.trim().takeIf { it.isNotEmpty() } ?: return this
        val old = sets[set] ?: return this
        return copy(sets = sets + (set to old.copy(title = name.lowercase())))
    }

    fun remove(ids: Set<Long>, fromSet: String): HubLayout {
        val old = sets[fromSet] ?: return this
        return copy(sets = sets + (fromSet to old.copy(photoIds = old.photoIds.filter { it !in ids })))
    }

    /** Drops tiles for albums that are gone from the library. Photo sets are never pruned. */
    fun prune(albumIds: Set<String>) = copy(tiles = tiles.filter { val k = it.kind; k !is HubTileKind.Album || k.id in albumIds })
}
