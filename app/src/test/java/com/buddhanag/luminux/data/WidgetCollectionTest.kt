package com.buddhanag.luminux.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WidgetCollectionTest {
    @get:Rule val tmp = TemporaryFolder()

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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun catalogPersistsAllAlbumsAndSets() = runTest {
        val ds = PreferenceDataStoreFactory.create(scope = TestScope(UnconfinedTestDispatcher())) { tmp.newFile("t.preferences_pb") }
        val store = LiveTileStore(tmp.newFolder(), ds)
        val catalog = listOf(TileCollection.album("user:1", "Trip"), TileCollection.photoSet("s1", "Best"))
        store.setCatalog(catalog)
        assertEquals(catalog, store.catalog.first())
    }
}
