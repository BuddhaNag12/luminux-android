package com.buddhanag.luminux.data.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.math.abs

/** Ported from PlacesTests.swift. Ids: 1 goa-old, 2 goa-new, 3 mumbai, 4 nowhere. */
class PlaceGroupingTest {
    private fun point(id: Long, lat: Double?, lon: Double?, daysAgo: Long) =
        PhotoPoint(id, Instant.ofEpochSecond(1_700_000_000 - daysAgo * 86_400), lat?.let { Coordinate(it, lon!!) }, isFavorite = false)

    // Two photos a few hundred metres apart in Goa, one in Mumbai, one with no location.
    private val points = listOf(
        point(GOA_OLD, 15.501, 73.811, daysAgo = 30),
        point(GOA_NEW, 15.503, 73.814, daysAgo = 20),
        point(MUMBAI, 19.076, 72.877, daysAgo = 1),
        point(NOWHERE, null, null, daysAgo = 0),
    )

    @Test fun groupsNearbyPhotosAndSkipsOnesWithoutALocation() {
        val places = PlaceGrouping.places(points)
        assertEquals(2, places.size)
        assertFalse(places.flatMap { it.photoIds }.contains(NOWHERE))
    }

    @Test fun mostRecentlyVisitedFirstAndNewestPhotoFirst() {
        assertEquals(listOf(listOf(MUMBAI), listOf(GOA_NEW, GOA_OLD)), PlaceGrouping.places(points).map { it.photoIds })
    }

    /** Adapted from zoomedOutPlacesMergeIntoOneBubble: the map library merges, the bubble sums and picks the lead. */
    @Test fun clusterBubbleSumsPhotosAndLeadsWithNewest() {
        val bubble = PlaceGrouping.bubble(PlaceGrouping.places(points))
        assertEquals(3, bubble.count)
        assertEquals(listOf(MUMBAI), bubble.lead.photoIds)
    }

    @Test fun theMapFramesEveryPlace() {
        val region = PlaceGrouping.region(PlaceGrouping.places(points))!!
        assertTrue(region.latDelta > 19.076 - 15.501)
        assertTrue(abs(region.center.latitude - (19.076 + 15.502) / 2) < 0.1)
    }

    @Test fun placeTitleIsTheNameOrTheMonth() {
        val place = PlaceGrouping.places(points).first()
        val state = JournalState(placeNames = mapOf(place.id to "Mumbai"))
        assertEquals("mumbai", state.title(place, java.time.ZoneOffset.UTC, java.util.Locale.US))
        assertEquals("november 2023", state.copy(usesPlaceNames = false).title(place, java.time.ZoneOffset.UTC, java.util.Locale.US))
    }

    private companion object {
        const val GOA_OLD = 1L
        const val GOA_NEW = 2L
        const val MUMBAI = 3L
        const val NOWHERE = 4L
    }
}
