package com.buddhanag.luminux.data.hub

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** [key]: `"cameraRoll"`, `"album:<albumId>"` or `"set:<setId>"`. */
@Entity(tableName = "hub_tile")
data class HubTileEntity(@PrimaryKey val key: String, val position: Int, val size: String)

/** [photoIds] is a JSON array of MediaStore ids. */
@Entity(tableName = "photo_set")
data class PhotoSetEntity(@PrimaryKey val id: String, val title: String, val photoIds: String)

@Dao
interface HubDao {
    @Query("SELECT * FROM hub_tile ORDER BY position")
    suspend fun tiles(): List<HubTileEntity>

    @Query("SELECT * FROM photo_set")
    suspend fun sets(): List<PhotoSetEntity>

    @Query("DELETE FROM hub_tile")
    suspend fun clearTiles()

    @Query("DELETE FROM photo_set")
    suspend fun clearSets()

    @Insert
    suspend fun insertTiles(rows: List<HubTileEntity>)

    @Insert
    suspend fun insertSets(rows: List<PhotoSetEntity>)

    /** The hub is a handful of rows, so every change rewrites them all, like iOS rewrote hub.json. */
    @Transaction
    suspend fun replaceAll(tiles: List<HubTileEntity>, sets: List<PhotoSetEntity>) {
        clearTiles()
        clearSets()
        insertTiles(tiles)
        insertSets(sets)
    }
}

/**
 * The hub's tiles and photo sets (iOS `HubTiles`). The layout lives in memory and changes synchronously, so a drag sees
 * its own moves at once; every change is written to Room behind it, the latest one winning.
 */
class HubTilesRepository(private val dao: HubDao, scope: CoroutineScope, io: CoroutineDispatcher = Dispatchers.IO) {
    private val _layout = MutableStateFlow(HubLayout())
    val layout: StateFlow<HubLayout> = _layout.asStateFlow()
    private val loaded = CompletableDeferred<Unit>()

    init {
        scope.launch(io) {
            _layout.value = HubLayout.loaded(
                dao.tiles().mapNotNull { row -> kindOf(row.key)?.let { HubTile(it, sizeOf(row.size)) } },
                dao.sets().associate { it.id to PhotoSet(it.id, it.title, Json.decodeFromString<List<Long>>(it.photoIds)) },
            )
            loaded.complete(Unit)
            _layout.drop(1).conflate().collect { save(it) }
        }
    }

    fun photoSet(id: String): PhotoSet? = _layout.value.sets[id]
    fun canPin(isPro: Boolean) = _layout.value.canPin(isPro)

    fun pinAlbum(id: String) = change { it.pinAlbum(id) }

    /** Returns the new set's id. */
    fun pinPhotoSet(title: String, ids: List<Long>): String {
        var id = ""
        change { layout -> layout.pinPhotoSet(title, ids).also { id = it.second }.first }
        return id
    }

    fun unpin(kind: HubTileKind) = change { it.unpin(kind) }
    fun move(kind: HubTileKind, to: Int) = change { it.move(kind, to) }
    fun resize(kind: HubTileKind) = change { it.resize(kind) }
    fun rename(set: String, title: String) = change { it.rename(set, title) }
    fun remove(ids: Set<Long>, fromSet: String) = change { it.remove(ids, fromSet) }

    /** Waits for the stored layout, so a library that loads first can't prune tiles that haven't been read yet. */
    suspend fun prune(albumIds: Set<String>) {
        loaded.await()
        change { it.prune(albumIds) }
    }

    // Changes before the stored layout is read are dropped; it loads in milliseconds at launch.
    private fun change(op: (HubLayout) -> HubLayout) {
        if (loaded.isCompleted) _layout.update(op)
    }

    private suspend fun save(layout: HubLayout) = dao.replaceAll(
        layout.tiles.mapIndexed { index, tile -> HubTileEntity(keyOf(tile.kind), index, tile.size.name.lowercase()) },
        layout.sets.values.map { PhotoSetEntity(it.id, it.title, Json.encodeToString(it.photoIds)) },
    )

    private companion object {
        val builtIns = listOf(
            "cameraRoll" to HubTileKind.CameraRoll, "albums" to HubTileKind.Albums, "date" to HubTileKind.Date,
            "favorites" to HubTileKind.Favorites, "places" to HubTileKind.Places,
        )

        fun keyOf(kind: HubTileKind): String = when (kind) {
            is HubTileKind.Album -> "album:${kind.id}"
            is HubTileKind.PhotoSet -> "set:${kind.id}"
            else -> builtIns.first { it.second == kind }.first
        }

        fun kindOf(key: String): HubTileKind? = when {
            key.startsWith("album:") -> HubTileKind.Album(key.removePrefix("album:"))
            key.startsWith("set:") -> HubTileKind.PhotoSet(key.removePrefix("set:"))
            else -> builtIns.firstOrNull { it.first == key }?.second
        }

        fun sizeOf(raw: String) = HubTileSize.entries.firstOrNull { it.name.lowercase() == raw } ?: HubTileSize.Medium
    }
}
