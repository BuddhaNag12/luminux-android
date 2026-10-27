package com.buddhanag.luminux.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.buddhanag.luminux.data.TileCollection
import com.buddhanag.luminux.designsystem.TileShuffle
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder
import kotlin.random.Random

class TileConfigTest {
    @Test fun faceRules() {
        // Locked beats everything for the wide sizes without Pro; small is never locked.
        assertEquals(Face.Locked, face(TileKind.Medium, isPro = false, needsCollection = true, imageCount = 0))
        assertEquals(Face.Locked, face(TileKind.Large, isPro = false, needsCollection = false, imageCount = 5))
        assertEquals(Face.Photos, face(TileKind.Small, isPro = false, needsCollection = false, imageCount = 5))
        // A missing collection beats an empty pool.
        assertEquals(Face.NeedsCollection, face(TileKind.Small, isPro = false, needsCollection = true, imageCount = 0))
        assertEquals(Face.Empty, face(TileKind.Large, isPro = true, needsCollection = false, imageCount = 0))
    }

    @Test fun tileUriEncodesId() {
        val link = TileConfig(TileSource.Collection, TileCollection.album("x/y", "Trip")).link
        val query = link.substringAfter("luminux://collection?id=")
        assertFalse(query.contains('/') || query.contains(':'))
        assertEquals("album:x/y", URLDecoder.decode(query, Charsets.UTF_8))
        assertEquals("luminux://hub", TileConfig(TileSource.Collection, null).link)
        assertEquals("luminux://hub", TileConfig(TileSource.Recent, null).link)
    }

    @Test fun collectionsAreLookedUpByIdSoRenamesShow() {
        val state = mutablePreferencesOf(TileState.source to "Collection", TileState.collectionId to "album:1")
        val config = TileConfig.from(state, listOf(TileCollection.album("1", "Goa Trip")))
        assertEquals("goa trip", config.title)
        assertFalse(config.needsCollection)
        // Removed in the app: the tile asks for a collection again.
        val gone = TileConfig.from(state, emptyList())
        assertTrue(gone.needsCollection)
        assertEquals("collection", gone.title)
        assertNull(gone.folder)
    }

    @Test fun recentIsTheDefault() {
        val config = TileConfig.from(mutablePreferencesOf(), listOf(TileCollection.album("1", "A")))
        assertEquals(TileSource.Recent, config.source)
        assertEquals("photos", config.title)
        assertNull(config.stored)
    }

    @Test fun sizeBuckets() {
        assertEquals(TileKind.Small, SMALL.kind())
        assertEquals(TileKind.Medium, MEDIUM.kind())
        assertEquals(TileKind.Large, LARGE.kind())
        assertEquals(TileKind.Small, DpSize(190.dp, 400.dp).kind())
        assertEquals(4, slots(TileKind.Large))
        assertEquals(3, slots(TileKind.Small))
    }

    @Test fun stepsSurviveTheWidgetState() {
        val steps = TileShuffle.steps((0 until 12).map { "$it.jpg" }, slots = 4, count = 96, random = Random(42))
        assertEquals(steps, Json.decodeFromString<List<TileShuffle.Step<String>>>(Json.encodeToString(steps)))
    }
}
