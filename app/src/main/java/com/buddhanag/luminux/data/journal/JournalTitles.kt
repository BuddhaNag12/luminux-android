package com.buddhanag.luminux.data.journal

import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Generated entry titles in the Metro voice: "saturday afternoon", "afternoon in goa", "trip to jaipur". */
object JournalTitles {
    /** [place] only counts for entries away from home. */
    fun title(e: JournalEntry, place: String?, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String {
        val p = if (e.isAway) place?.lowercase(locale) else null
        val start = e.start.atZone(zone)
        val weekday = start.dayOfWeek.getDisplayName(TextStyle.FULL, locale).lowercase(locale)
        return when (e.kind) {
            EntryKind.Trip -> p?.let { "trip to $it" } ?: "${dayCount(e, zone)} days away"
            EntryKind.Day -> p?.let { "a day in $it" } ?: weekday
            EntryKind.Moment -> timeOfDay(start.hour).let { t -> p?.let { "$t in $it" } ?: "$weekday $t" }
        }
    }

    fun timeOfDay(hour: Int) = when (hour) {
        in 5..11 -> "morning"
        in 12..16 -> "afternoon"
        in 17..20 -> "evening"
        else -> "night"
    }

    fun dayCount(e: JournalEntry, zone: ZoneId) =
        ChronoUnit.DAYS.between(e.start.atZone(zone).toLocalDate(), e.end.atZone(zone).toLocalDate()).toInt() + 1

    fun photoCount(n: Int) = "$n ${if (n == 1) "photo" else "photos"}"
}
