package com.buddhanag.luminux.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileShuffleTest {
    @Test fun oneSlotChangesAtATime() {
        val steps = TileShuffle.steps((0 until 12).toList(), slots = 4, count = 60)
        assertEquals(60, steps.size)
        steps.zipWithNext { previous, next ->
            assertEquals(1, previous.slots.zip(next.slots).count { (a, b) -> a != b })
            assertNotEquals(previous.changed, next.changed)
        }
    }

    @Test fun noDuplicateOnScreen() {
        for (step in TileShuffle.steps((0 until 12).toList(), slots = 4, count = 60)) {
            assertEquals(4, step.slots.toSet().size)
        }
    }

    @Test fun singleSlotChangesEveryStep() {
        TileShuffle.steps(listOf("a", "b", "c"), slots = 1, count = 20).zipWithNext { previous, next ->
            assertNotEquals(previous.slots[0], next.slots[0])
        }
    }

    @Test fun shortPoolStillFills() {
        assertTrue(TileShuffle.steps(listOf("a", "b"), slots = 4, count = 10).all { it.slots.size == 4 })
        assertTrue(TileShuffle.steps(emptyList<Int>(), slots = 4, count = 10).isEmpty())
        assertEquals(5, TileShuffle.steps(listOf("only"), slots = 1, count = 5).size)
    }
}
