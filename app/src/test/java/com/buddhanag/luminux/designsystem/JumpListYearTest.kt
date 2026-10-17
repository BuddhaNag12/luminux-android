package com.buddhanag.luminux.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JumpListYearTest {
    @Test fun groupsMonthsByYearNewestFirst() {
        val sections = listOf(
            MonthSection(2026, 10, 0 until 3),
            MonthSection(2026, 9, 3 until 4),
            MonthSection(2025, 12, 4 until 6),
        )
        val years = JumpListYear.years(sections)

        assertEquals(listOf(2026, 2025), years.map { it.year })
        assertEquals(listOf(9, 10), years[0].sectionIds.keys.sorted())
        assertEquals(sections[2].id, years[1].sectionIds[12])
    }

    @Test fun repeatedMonthUsesFirstSection() {
        val first = MonthSection(2026, 3, 0 until 1)
        val sections = listOf(first, MonthSection(2026, 2, 1 until 2), MonthSection(2026, 3, 2 until 3))

        assertEquals(first.id, JumpListYear.years(sections)[0].sectionIds[3])
    }

    @Test fun undatedSectionsAreLeftOut() {
        assertTrue(JumpListYear.years(listOf(MonthSection(0, 0, 0 until 2))).isEmpty())
    }
}
