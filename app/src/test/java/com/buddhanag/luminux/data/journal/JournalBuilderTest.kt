package com.buddhanag.luminux.data.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Ported from JournalTests.swift. Photo ids are Longs on Android, so readable names map to their index in [names]. */
class JournalBuilderTest {
    private val home = Coordinate(22.57, 88.36)
    private val away = Coordinate(15.49, 73.83)
    private val names = mutableListOf<String>()

    private fun id(name: String): Long = names.indexOf(name).takeIf { it >= 0 }?.toLong() ?: names.size.toLong().also { names += name }
    private fun JournalEntry.names() = photoIds.map { names[it.toInt()] }

    /** Saturday 3 October 2026 (the Swift comment says 4th; its code builds the 3rd), plus whole days, in UTC. */
    private fun date(day: Int = 0, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 10, 3, hour, 0).plusDays(day.toLong()).plusMinutes(minute.toLong()).toInstant(ZoneOffset.UTC)

    private fun photos(count: Int, day: Int = 0, hour: Int, at: Coordinate?, prefix: String) =
        (0 until count).map { PhotoPoint(id("$prefix$it"), date(day, hour, it * 5), at, isFavorite = false) }

    /** Everyday photos at home over a few weeks, so the builder knows where home is. */
    private val homeBase get() = (1..6).flatMap { photos(1, day = -7 * it, hour = 10, at = home, prefix = "base$it-") }

    private fun entries(points: List<PhotoPoint>) = JournalBuilder.entries(points, ZoneOffset.UTC)

    @Test fun photosCloseTogetherMakeOneMoment() {
        val moment = entries(homeBase + photos(4, hour = 14, at = home, prefix = "a")).first { "a0" in it.names() }
        assertEquals(EntryKind.Moment, moment.kind)
        assertEquals(listOf("a0", "a1", "a2", "a3"), moment.names())
        assertFalse(moment.isAway)
    }

    @Test fun aLongGapStartsANewMoment() {
        val result = entries(homeBase + photos(3, hour = 9, at = home, prefix = "a") + photos(3, hour = 18, at = home, prefix = "b"))
        assertTrue(result.any { it.names() == listOf("a0", "a1", "a2") })
        assertTrue(result.any { it.names() == listOf("b0", "b1", "b2") })
    }

    @Test fun movingFarStartsANewMoment() {
        val nearby = Coordinate(22.9, 88.36)
        val result = entries(homeBase + photos(3, hour = 9, at = home, prefix = "a") + photos(3, hour = 10, at = nearby, prefix = "b"))
        assertTrue(result.any { it.names() == listOf("a0", "a1", "a2") })
        assertTrue(result.any { it.names() == listOf("b0", "b1", "b2") })
    }

    @Test fun strayPhotosMakeADayOnlyWhenNothingElseHappened() {
        val stray = listOf(9, 13, 19).mapIndexed { i, hour -> PhotoPoint(id("s$i"), date(hour = hour), home, isFavorite = false) }
        assertTrue(entries(homeBase + stray).any { it.kind == EntryKind.Day && it.names() == listOf("s0", "s1", "s2") })

        val busyDay = entries(homeBase + stray + photos(4, hour = 15, at = home, prefix = "m"))
        assertFalse(busyDay.any { it.kind == EntryKind.Day })
    }

    @Test fun daysAwayBecomeOneTrip() {
        val trip = photos(4, day = 0, hour = 10, at = away, prefix = "t0-") +
            photos(4, day = 1, hour = 11, at = away, prefix = "t1-") +
            photos(4, day = 2, hour = 12, at = away, prefix = "t2-")
        val result = entries(homeBase + trip)
        val entry = result.first { it.kind == EntryKind.Trip }
        assertEquals(12, entry.photoIds.size)
        assertTrue(entry.isAway)
        assertFalse(result.any { it.kind == EntryKind.Moment && it.isAway })
    }

    @Test fun oneDayAwayIsAnAwayMomentNotATrip() {
        val result = entries(homeBase + photos(4, hour = 15, at = away, prefix = "x"))
        assertFalse(result.any { it.kind == EntryKind.Trip })
        assertTrue(result.any { it.kind == EntryKind.Moment && it.isAway })
    }

    @Test fun entriesComeNewestFirst() {
        val result = entries(homeBase + photos(3, day = 0, hour = 9, at = home, prefix = "old") + photos(3, day = 5, hour = 9, at = home, prefix = "new"))
        assertEquals("new0", result.first().names().first())
    }

    @Test fun spreadPicksEvenlyFromStartToEnd() {
        assertEquals(listOf(0L, 5L, 9L), JournalBuilder.spread((0L..9L).toList(), 3))
        assertEquals(listOf(7L, 8L), JournalBuilder.spread(listOf(7L, 8L), 3))
    }

    @Test fun homeIsWherePhotosAreTakenOnTheMostDays() {
        // More photos on a two-day trip than at home, but home has more days.
        val points = homeBase + photos(10, day = 0, hour = 10, at = away, prefix = "t0-") + photos(10, day = 1, hour = 10, at = away, prefix = "t1-")
        val found = JournalBuilder.homePlace(points, ZoneOffset.UTC)
        assertNotNull(found)
        assertTrue(found!!.distanceTo(home) < 1000)
    }

    @Test fun cellKeyFloorsNegatives() {
        assertEquals("-1,-1", JournalBuilder.cellKey(Coordinate(-0.01, -0.01), 0.05))
    }

    @Test fun keyPhotosLeadWithFavouritesThenSpread() {
        val e = JournalEntry("1", EntryKind.Moment, date(hour = 9), date(hour = 10), (1L..10L).toList(), listOf(5L), null, false)
        assertEquals(listOf(5L, 1L, 6L), JournalState().keyPhotos(e, 3))
    }
}
