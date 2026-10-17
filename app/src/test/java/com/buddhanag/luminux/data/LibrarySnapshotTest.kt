package com.buddhanag.luminux.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class LibrarySnapshotTest {
    private val day = 86_400_000L
    private val now = 1_800_000_000_000L

    private fun item(
        id: Long,
        taken: Long? = now - id * day,
        type: MediaType = MediaType.IMAGE,
        w: Int = 4000,
        h: Int = 3000,
        favorite: Boolean = false,
        path: String = "DCIM/Camera/",
        added: Long = 0,
    ) = MediaItem(id, type, if (type == MediaType.IMAGE) "image/jpeg" else "video/mp4", taken, added, 0, w, h, 0, favorite, path, "$id.jpg", 1)

    private fun build(items: List<MediaItem>, albums: List<AlbumRow> = emptyList(), members: List<AlbumMemberRow> = emptyList()) =
        LibrarySnapshot.build(1, items, albums, members, now, ZoneOffset.UTC)

    @Test fun sortsNewestFirstWithIdTieBreakAndUndatedLast() {
        val s = build(listOf(item(1, taken = 100), item(2, taken = 100), item(3, taken = 300), item(4, taken = null)))
        assertEquals(listOf(3L, 2L, 1L, 4L), s.all.map { it.id })
        assertTrue(s.sections.last().isUndated)
        assertTrue(s.isLoaded)
    }

    @Test fun dateAddedStandsInForAMissingDateTaken() {
        assertEquals(5_000L, item(1, taken = null, added = 5_000).effectiveDate)
        assertNull(item(1, taken = null).effectiveDate)
    }

    @Test fun recentIsTheLast30Days() {
        val s = build(listOf(item(1, taken = now - 29 * day), item(2, taken = now - 30 * day), item(3, taken = now - 31 * day)))
        assertEquals(listOf(1L), s.recent.map { it.id })
    }

    @Test fun favoritesAndVideosFilter() {
        val s = build(listOf(item(1, favorite = true), item(2, type = MediaType.VIDEO), item(3)))
        assertEquals(listOf(1L), s.favorites.map { it.id })
        assertEquals(listOf(2L), s.videos.map { it.id })
    }

    @Test fun panoramasNeedTwoAndAHalfToOne() {
        assertFalse(item(1, w = 2400, h = 1000).isPanorama)
        assertTrue(item(1, w = 3000, h = 1000).isPanorama)
        assertTrue(item(1, w = 1000, h = 3000).isPanorama)
        assertFalse(item(1, w = 1080, h = 2400, path = "Pictures/Screenshots/").isPanorama)
        assertFalse(item(1, type = MediaType.VIDEO, w = 3000, h = 1000).isPanorama)
    }

    @Test fun screenshotsMatchTheFolder() {
        assertTrue(item(1, path = "Pictures/Screenshots/").isScreenshot)
        assertTrue(item(1, path = "DCIM/screenshots/").isScreenshot)
        assertFalse(item(1).isScreenshot)
    }

    @Test fun smartAlbumsShowOnlyWhenNotEmptyAfterUserAlbums() {
        assertTrue(build(listOf(item(1))).albums.isEmpty())
        val s = build(
            listOf(item(1, path = "Pictures/Screenshots/"), item(2, w = 3000, h = 1000)),
            albums = listOf(AlbumRow(7, "Trip", createdAt = 2), AlbumRow(8, "Empty", createdAt = 1)),
        )
        assertEquals(listOf("user:8", "user:7", AlbumIds.PANORAMAS, AlbumIds.SCREENSHOTS), s.albums.map { it.id })
        assertEquals(listOf("empty", "trip", "panoramas", "screenshots"), s.albums.map { it.title })
        assertEquals(0, s.album("user:8")!!.count)
        assertEquals(listOf(1L), s.items(AssetSource.Album(AlbumIds.SCREENSHOTS)).map { it.id })
    }

    @Test fun albumCountIgnoresMissingIdsAndKeyIsNewest() {
        val s = build(
            listOf(item(1), item(2)),
            albums = listOf(AlbumRow(1, "a", 0)),
            members = listOf(AlbumMemberRow(1, 2, 0), AlbumMemberRow(1, 1, 0), AlbumMemberRow(1, 99, 0)),
        )
        val album = s.album("user:1")!!
        assertEquals(2, album.count)
        assertEquals(1L, album.keyItemId)
        assertEquals(listOf(1L, 2L), s.items(AssetSource.Album("user:1")).map { it.id })
        assertTrue(s.items(AssetSource.Album("user:42")).isEmpty())
    }

    @Test fun identifiersAreOldestFirst() {
        val s = build(listOf(item(1), item(2), item(3)))
        assertEquals(listOf(3L, 1L), s.items(AssetSource.Identifiers(listOf(1, 3, 404))).map { it.id })
    }
}
