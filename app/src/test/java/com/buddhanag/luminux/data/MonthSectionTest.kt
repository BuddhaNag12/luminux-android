package com.buddhanag.luminux.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

class MonthSectionTest {
    private val utc = ZoneOffset.UTC
    private fun date(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(utc).toInstant().toEpochMilli()

    @Test fun emptyInputHasNoSections() {
        assertTrue(MonthSection.group(emptyList(), utc).isEmpty())
    }

    @Test fun groupsContiguousMonthsNewestFirst() {
        val sections = MonthSection.group(listOf(date(2026, 10, 6), date(2026, 10, 1), date(2026, 9, 30), date(2025, 9, 12)), utc)
        assertEquals(listOf(2026, 2026, 2025), sections.map { it.year })
        assertEquals(listOf(10, 9, 9), sections.map { it.month })
        assertEquals(listOf(0..1, 2..2, 3..3), sections.map { it.range })
    }

    @Test fun undatedAssetsGetTheirOwnSection() {
        val sections = MonthSection.group(listOf(date(2026, 1, 5), null, null), utc)
        assertEquals(2, sections.size)
        assertTrue(sections[1].isUndated)
        assertEquals(1..2, sections[1].range)
        assertEquals("undated", sections[1].title())
    }

    @Test fun titleIsLowercaseMonthAndYear() {
        assertEquals("october 2026", MonthSection(2026, 10, 0..0).title(Locale.US))
    }

    @Test fun idsStayUniqueWhenAMonthRepeats() {
        val sections = MonthSection.group(listOf(date(2026, 3, 1), date(2026, 2, 1), date(2026, 3, 1)), utc)
        assertEquals(3, sections.map { it.id }.toSet().size)
    }
}
