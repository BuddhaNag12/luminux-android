package com.buddhanag.luminux.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class LiveTileStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun store(scope: CoroutineScope) =
        LiveTileStore(tmp.newFolder("files"), PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("t.preferences_pb") })

    @Test fun folderNameKeepsLettersAndDigits() {
        assertEquals("c-album-user-3", TileCollection.album("user:3", "trip").folderName)
        assertEquals("c-album-smart-panoramas", TileCollection("album:smart:panoramas", "panoramas").folderName)
    }

    @Test fun filesReadBackInNumericOrderAndCapAtTwelve() = runTest(UnconfinedTestDispatcher()) {
        val store = store(backgroundScope)
        store.replaceImages(List(14) { byteArrayOf(it.toByte()) }, LiveTileStore.RECENT)
        val files = store.imageFiles(LiveTileStore.RECENT)
        assertEquals((0 until 12).map { "$it.jpg" }, files.map { it.name })
        // Rewriting replaces the folder.
        store.replaceImages(listOf(byteArrayOf(9)), LiveTileStore.RECENT)
        assertEquals(listOf("0.jpg"), store.imageFiles(LiveTileStore.RECENT).map { it.name })
    }

    @Test fun collectionsPersist() = runTest(UnconfinedTestDispatcher()) {
        val store = store(backgroundScope)
        val list = listOf(TileCollection.album("user:1", "trip"), TileCollection.photoSet("s", "best"))
        store.setCollections(list)
        assertEquals(list, store.collections.first())
    }
}
