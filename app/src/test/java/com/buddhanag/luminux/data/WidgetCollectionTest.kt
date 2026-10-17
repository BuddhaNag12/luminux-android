package com.buddhanag.luminux.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WidgetCollectionTest {
    @Test fun albumIdsBecomeSafeFolderNames() {
        val album = TileCollection.album("6B1F2C/L0/040", "Waterfalls")
        assertEquals("album:6B1F2C/L0/040", album.id)
        assertEquals("c-album-6B1F2C-L0-040", album.folderName)
        assertFalse(album.folderName.contains("/"))
    }

    @Test fun photoSetsGetTheirOwnPrefix() {
        assertEquals("set:A1", TileCollection.photoSet("A1", "Goa").id)
    }

    @Test fun collectionsRoundTrip() {
        val collections = listOf(TileCollection.album("x/y", "Trip"), TileCollection.photoSet("s", "Best"))
        val serializer = ListSerializer(TileCollection.serializer())
        assertEquals(collections, Json.decodeFromString(serializer, Json.encodeToString(serializer, collections)))
    }
}
