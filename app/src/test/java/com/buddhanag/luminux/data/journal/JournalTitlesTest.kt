package com.buddhanag.luminux.data.journal

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

class JournalTitlesTest {
    private val away = Coordinate(15.49, 73.83)

    private fun date(day: Int = 0, hour: Int) = LocalDateTime.of(2026, 10, 3, hour, 0).plusDays(day.toLong()).toInstant(ZoneOffset.UTC)

    private fun title(kind: EntryKind, isAway: Boolean, place: String?, days: Int = 0, hour: Int = 15): String {
        val e = JournalEntry("e", kind, date(hour = hour), date(days, hour + 1), listOf(1L), emptyList(), away, isAway)
        return JournalTitles.title(e, place, ZoneOffset.UTC, Locale.US)
    }

    @Test fun titlesReadLikeTheOriginal() {
        assertEquals("saturday afternoon", title(EntryKind.Moment, isAway = false, place = "Kolkata"))
        assertEquals("afternoon in goa", title(EntryKind.Moment, isAway = true, place = "Goa"))
        assertEquals("saturday morning", title(EntryKind.Moment, isAway = true, place = null, hour = 8))
        assertEquals("trip to jaipur", title(EntryKind.Trip, isAway = true, place = "Jaipur", days = 3))
        assertEquals("4 days away", title(EntryKind.Trip, isAway = true, place = null, days = 3))
        assertEquals("saturday", title(EntryKind.Day, isAway = false, place = null))
    }

    @Test fun timeOfDayBuckets() {
        assertEquals(listOf("night", "morning", "morning", "afternoon", "afternoon", "evening", "evening", "night"),
            listOf(4, 5, 11, 12, 16, 17, 20, 21).map(JournalTitles::timeOfDay))
    }

    @Test fun onePhotoIsSingular() {
        assertEquals("1 photo", JournalTitles.photoCount(1))
        assertEquals("12 photos", JournalTitles.photoCount(12))
    }
}
