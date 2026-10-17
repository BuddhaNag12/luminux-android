package com.buddhanag.luminux.data.hub

import com.buddhanag.luminux.data.hub.HubTileKind.Album
import com.buddhanag.luminux.data.hub.HubTileKind.Albums
import com.buddhanag.luminux.data.hub.HubTileKind.CameraRoll
import com.buddhanag.luminux.data.hub.HubTileKind.Date
import com.buddhanag.luminux.data.hub.HubTileKind.Favorites
import com.buddhanag.luminux.data.hub.HubTileKind.PhotoSet
import com.buddhanag.luminux.data.hub.HubTileKind.Places
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** HubTilesTests.swift. */
class HubLayoutTest {
    private fun pinSet(layout: HubLayout, title: String, ids: List<Long>) = layout.pinPhotoSet(title, ids)

    @Test fun startsWithTheBuiltInTiles() {
        val tiles = HubLayout()
        assertEquals(listOf(CameraRoll, Albums, Date, Favorites, Places), tiles.tiles.map { it.kind })
        assertTrue(tiles.tiles.all { it.size == HubTileSize.Medium })
        assertEquals(0, tiles.pinCount)
    }

    @Test fun freeUsersPinTwoThenPro() {
        var tiles = HubLayout().pinAlbum("a")
        assertTrue(tiles.canPin(isPro = false))
        tiles = pinSet(tiles, "beach", listOf(1, 2)).first

        assertEquals(2, tiles.pinCount)
        assertFalse(tiles.canPin(isPro = false))
        assertTrue(tiles.canPin(isPro = true))
    }

    @Test fun anAlbumIsPinnedOnce() {
        val tiles = HubLayout().pinAlbum("a").pinAlbum("a")
        assertEquals(1, tiles.pinCount)
        assertTrue(tiles.isPinned(Album("a")))
    }

    @Test fun builtInTilesCantBeUnpinned() {
        assertTrue(HubLayout().unpin(Favorites).isPinned(Favorites))
    }

    @Test fun unpinningASetForgetsIt() {
        val (pinned, id) = pinSet(HubLayout(), "Beach ", listOf(1))
        assertEquals("beach", pinned.sets[id]?.title)

        val tiles = pinned.unpin(PhotoSet(id))
        assertNull(tiles.sets[id])
        assertFalse(tiles.isPinned(PhotoSet(id)))
    }

    @Test fun anUnnamedSetIsCalledPhotos() {
        val (tiles, id) = pinSet(HubLayout(), "  ", listOf(1))
        assertEquals("photos", tiles.sets[id]?.title)
    }

    @Test fun movingATileTakesTheTargetsPlace() {
        var tiles = HubLayout().move(Favorites, 0)
        assertEquals(listOf(Favorites, CameraRoll, Albums, Date, Places), tiles.tiles.map { it.kind })

        tiles = tiles.move(Favorites, 3)
        assertEquals(listOf(CameraRoll, Albums, Date, Favorites, Places), tiles.tiles.map { it.kind })
    }

    @Test fun resizeStepsMediumSmallWide() {
        var tiles = HubLayout().resize(Date)
        assertEquals(HubTileSize.Small, tiles.tiles[2].size)
        tiles = tiles.resize(Date)
        assertEquals(HubTileSize.Wide, tiles.tiles[2].size)
        tiles = tiles.resize(Date)
        assertEquals(HubTileSize.Medium, tiles.tiles[2].size)
    }

    @Test fun removingPhotosAndRenamingASet() {
        val (pinned, id) = pinSet(HubLayout(), "trip", listOf(1, 2, 3))
        val tiles = pinned.remove(setOf(2), fromSet = id).rename(id, "Road Trip").rename(id, "")

        assertEquals(listOf(1L, 3L), tiles.sets[id]?.photoIds)
        assertEquals("road trip", tiles.sets[id]?.title)
    }

    @Test fun deletedAlbumsArePruned() {
        val tiles = HubLayout().pinAlbum("kept").pinAlbum("gone").prune(setOf("kept"))

        assertTrue(tiles.isPinned(Album("kept")))
        assertFalse(tiles.isPinned(Album("gone")))
        assertTrue(tiles.isPinned(CameraRoll))
    }

    @Test fun aDamagedStoreKeepsTheBuiltIns() {
        val tiles = HubLayout.loaded(listOf(HubTile(Date, HubTileSize.Wide)), emptyMap())
        assertEquals(listOf(Date, CameraRoll, Albums, Favorites, Places), tiles.tiles.map { it.kind })
    }

    /** The iOS test relaunched from hub.json; here a second repository reads the same (fake) Room DAO. */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun layoutSurvivesARelaunch() = runTest(UnconfinedTestDispatcher()) {
        val dao = FakeHubDao()
        val io = UnconfinedTestDispatcher(testScheduler)
        val tiles = HubTilesRepository(dao, backgroundScope, io)
        val id = tiles.pinPhotoSet("beach", listOf(1))
        tiles.pinAlbum("a")
        tiles.resize(Album("a"))
        tiles.move(PhotoSet(id), 0)

        val reloaded = HubTilesRepository(dao, backgroundScope, io)

        assertEquals(tiles.layout.value.tiles, reloaded.layout.value.tiles)
        assertEquals(listOf(1L), reloaded.photoSet(id)?.photoIds)
    }

    private class FakeHubDao : HubDao {
        private var tiles = emptyList<HubTileEntity>()
        private var sets = emptyList<PhotoSetEntity>()
        override suspend fun tiles() = tiles.sortedBy { it.position }
        override suspend fun sets() = sets
        override suspend fun clearTiles() { tiles = emptyList() }
        override suspend fun clearSets() { sets = emptyList() }
        override suspend fun insertTiles(rows: List<HubTileEntity>) { tiles = tiles + rows }
        override suspend fun insertSets(rows: List<PhotoSetEntity>) { sets = sets + rows }
    }
}
