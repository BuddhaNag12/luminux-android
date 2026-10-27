package com.buddhanag.luminux.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.buddhanag.luminux.data.LiveTileStore
import com.buddhanag.luminux.data.TileCollection
import java.net.URLEncoder

/** What a Photos tile shows (TileSourceOption). */
enum class TileSource(val label: String) { Recent("recent"), Favorites("favorites"), Collection("a collection") }

/** How the tile changes its photos (TileStyle), after the hub's live tiles and Windows Phone's Photos tile. */
enum class TileStyle { Shuffle, Mosaic, Flip, Peek }

/** Each widget's own settings and shuffle, in its Glance state. */
object TileState {
    val source = stringPreferencesKey("source")
    val collectionId = stringPreferencesKey("collectionId")
    val style = stringPreferencesKey("style")
    val stepIndex = intPreferencesKey("stepIndex")
    /** A day of [com.buddhanag.luminux.designsystem.TileShuffle] steps as JSON, one list per slot count. */
    fun steps(slots: Int) = stringPreferencesKey("steps$slots")
}

/** TileConfigurationIntent's derived values. [stored] is looked up in the app's current list, so renames show up. */
data class TileConfig(val source: TileSource, val stored: TileCollection?, val style: TileStyle = TileStyle.Shuffle) {
    val needsCollection get() = source == TileSource.Collection && stored == null

    /** The `Tiles/` folder whose photos this tile shows; null while it needs a collection. */
    val folder: String? get() = when (source) {
        TileSource.Recent -> LiveTileStore.RECENT
        TileSource.Favorites -> LiveTileStore.FAVORITES
        TileSource.Collection -> stored?.folderName
    }

    val title: String get() = when (source) {
        TileSource.Recent -> "photos"
        TileSource.Favorites -> "favorites"
        TileSource.Collection -> stored?.title?.lowercase() ?: "collection"
    }

    /** Opens what the tile shows: the album or photo set, or the hub. The id is percent-encoded; MainActivity decodes it. */
    val link: String get() = stored?.let { "luminux://collection?id=" + URLEncoder.encode(it.id, Charsets.UTF_8) } ?: "luminux://hub"

    companion object {
        fun source(state: Preferences) = TileSource.entries.firstOrNull { it.name == state[TileState.source] } ?: TileSource.Recent
        fun style(state: Preferences) = TileStyle.entries.firstOrNull { it.name == state[TileState.style] } ?: TileStyle.Shuffle

        fun from(state: Preferences, collections: List<TileCollection>): TileConfig {
            val source = source(state)
            val id = state[TileState.collectionId]
            return TileConfig(source, collections.firstOrNull { it.id == id }.takeIf { source == TileSource.Collection }, style(state))
        }
    }
}

enum class TileKind { Small, Medium, Large }

/** Glance's Responsive buckets. Launchers report different dp for the same cells, so the kinds split at 200 dp. */
val SMALL = DpSize(110.dp, 110.dp)
val MEDIUM = DpSize(250.dp, 110.dp)
val LARGE = DpSize(250.dp, 250.dp)

fun DpSize.kind() = when {
    width < 200.dp -> TileKind.Small
    height < 200.dp -> TileKind.Medium
    else -> TileKind.Large
}

/** The mosaic's columns and rows: roughly square cells, no more than the 12 photos the app exports. */
fun mosaic(kind: TileKind) = if (kind == TileKind.Medium) 4 to 2 else 3 to 3

/**
 * A mosaic fills its grid; otherwise large shows 4 photos, and small keeps 3 like iOS (for its StandBy face) but
 * shows only the one that changed.
 */
fun slots(kind: TileKind, style: TileStyle = TileStyle.Shuffle) = when {
    style == TileStyle.Mosaic -> mosaic(kind).let { (columns, rows) -> columns * rows }
    kind == TileKind.Large -> 4
    else -> 3
}

enum class Face { Locked, NeedsCollection, Empty, Photos }

/** TileView's faces, first match wins. Small is free; the wider ones come with Pro. */
fun face(kind: TileKind, isPro: Boolean, needsCollection: Boolean, imageCount: Int) = when {
    kind != TileKind.Small && !isPro -> Face.Locked
    needsCollection -> Face.NeedsCollection
    imageCount == 0 -> Face.Empty
    else -> Face.Photos
}

/**
 * What each slot's flipper cycles through: its photo in [shown] (the current step) first, then the photos not on
 * screen dealt out in turn. The flippers keep their own time, so dealing disjoint sets is what keeps two slots from
 * ever showing the same photo at once. [seed] shuffles the deal, so each step and each widget gets its own order.
 */
fun <P> slotSequences(shown: List<P>, pool: List<P>, seed: Int, max: Int = 8): List<List<P>> {
    val rest = pool.filter { it !in shown }.shuffled(kotlin.random.Random(seed))
    return shown.mapIndexed { slot, first -> (listOf(first) + rest.filterIndexed { i, _ -> i % shown.size == slot }).take(max) }
}
