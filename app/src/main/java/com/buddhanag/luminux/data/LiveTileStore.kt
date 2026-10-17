package com.buddhanag.luminux.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** An album or photo set someone made a widget from; the widget offers these as choices. */
@Serializable
data class TileCollection(
    /** `"album:<albumId>"` or `"set:<photoSetId>"`. */
    val id: String,
    val title: String,
) {
    /** Album ids contain colons, so the folder name keeps letters and digits only. */
    val folderName: String get() = "c-" + id.map { if (it.isLetterOrDigit()) it else '-' }.joinToString("")

    companion object {
        fun album(id: String, title: String) = TileCollection("album:$id", title)
        fun photoSet(id: String, title: String) = TileCollection("set:$id", title)
    }
}

/**
 * Photos for the home-screen widget, under `filesDir/Tiles/`. The widget runs in the app's process, so it reads
 * accent, standBy and Pro straight from their own stores; only the saved collections live here.
 */
class LiveTileStore(private val filesDir: File, private val store: DataStore<Preferences>) {
    companion object {
        const val MAX_IMAGES = 12
        const val RECENT = "recent"
        const val FAVORITES = "favorites"
        private val collectionsKey = stringPreferencesKey("collections")
        private val serializer = ListSerializer(TileCollection.serializer())
    }

    val collections: Flow<List<TileCollection>> = store.data.map { p ->
        p[collectionsKey]?.let { runCatching { Json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()
    }

    suspend fun setCollections(list: List<TileCollection>) {
        store.edit { it[collectionsKey] = Json.encodeToString(serializer, list) }
    }

    /** [name]: [RECENT], [FAVORITES] or a [TileCollection.folderName]. */
    fun folder(name: String) = File(filesDir, "Tiles/$name")

    /** Sorted numerically (iOS sorts by name, which puts 10.jpg before 2.jpg), so "recent" stays newest first. */
    fun imageFiles(name: String): List<File> = folder(name).listFiles { f -> f.extension == "jpg" }.orEmpty()
        .sortedBy { it.nameWithoutExtension.toIntOrNull() ?: Int.MAX_VALUE }

    /** Deletes and recreates the folder, then writes `0.jpg …` atomically (temp file + rename). */
    fun replaceImages(jpegs: List<ByteArray>, name: String) {
        val dir = folder(name)
        dir.deleteRecursively()
        dir.mkdirs()
        jpegs.take(MAX_IMAGES).forEachIndexed { i, bytes ->
            val tmp = File(dir, "$i.jpg.tmp")
            tmp.writeBytes(bytes)
            tmp.renameTo(File(dir, "$i.jpg"))
        }
    }

    fun removeImages(name: String) {
        folder(name).deleteRecursively()
    }
}
