package com.buddhanag.luminux.data

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A contiguous run of items taken in the same calendar month, as indexes into the newest-first list. */
data class MonthSection(val year: Int, val month: Int, val range: IntRange) {
    val id: String get() = "$year-$month-${range.first}"
    val count: Int get() = range.count()
    /** Year 0 holds undated items. */
    val isUndated: Boolean get() = year == 0

    fun title(locale: Locale = Locale.getDefault()): String =
        if (isUndated) "undated"
        else YearMonth.of(year, month).format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)).lowercase(locale)

    companion object {
        /** [dates]: epoch ms, already sorted newest first; null = undated. */
        fun group(dates: List<Long?>, zone: ZoneId = ZoneId.systemDefault()): List<MonthSection> {
            val out = mutableListOf<MonthSection>()
            var start = 0
            var current: Pair<Int, Int>? = null
            dates.forEachIndexed { i, date ->
                val key = date?.let { Instant.ofEpochMilli(it).atZone(zone).let { z -> z.year to z.monthValue } } ?: (0 to 0)
                current?.let { if (it != key) { out += MonthSection(it.first, it.second, start until i); start = i } }
                current = key
            }
            current?.let { out += MonthSection(it.first, it.second, start until dates.size) }
            return out
        }
    }
}
