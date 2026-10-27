package com.buddhanag.luminux.feature.hub

import org.junit.Assert.assertEquals
import org.junit.Test

class EditDriftTest {
    @Test fun aTileAtRestSitsExactlyInItsSlot() {
        listOf(-8f, 0f, 5.6f, 8f).forEach { assertEquals(0f, EditDrift.offset(it, 0f), 0f) }
        assertEquals(4f, EditDrift.offset(8f, 0.5f), 1e-6f)
        assertEquals(-5.6f, EditDrift.offset(-5.6f, 1f), 1e-6f)
    }

    @Test fun neighboursDriftTheOtherWayAtTheirOwnPace() {
        assertEquals(listOf(8f to 1700, -4.8f to 2100, 0f to 1600), EditDrift.x(0).round())
        assertEquals(listOf(-8f to 1930, 4.8f to 2330, 0f to 1600), EditDrift.x(1).round())
        assertEquals(listOf(-5.6f to 1400 + 920, 8f to 2200, 0f to 1800 + 920), EditDrift.y(4).round())
        // seed % 5: the sixth tile keeps the first one's pace, and the other sign.
        assertEquals(EditDrift.x(0).map { -it.first to it.second }.round(), EditDrift.x(5).round())
    }

    @Test fun bothAxesLoopTogetherAndEndInTheSlot() {
        (0..9).forEach { seed ->
            assertEquals(EditDrift.x(seed).sumOf { it.second }, EditDrift.y(seed).sumOf { it.second })
            assertEquals(0f, EditDrift.x(seed).last().first, 0f)
            assertEquals(0f, EditDrift.y(seed).last().first, 0f)
        }
    }

    private fun List<Pair<Float, Int>>.round() = map { (Math.round(it.first * 10) / 10f + 0f) to it.second }
}
