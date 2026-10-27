package com.buddhanag.luminux.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

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

    @Test fun peeksFollowEveryThirdStep() {
        assertEquals(listOf(1, 2, 3, -3, 4, 5, 6, -6), TileShuffle.withPeeks((1..6).toList(), every = 3) { -it })
        assertTrue(TileShuffle.withPeeks(emptyList<Int>(), every = 3) { 0 }.isEmpty())
        // The widget's peek list: 72 changes and 24 peeks fill the same 96 steps.
        val steps = TileShuffle.withPeeks(TileShuffle.steps((0 until 12).toList(), 4, 72), every = 3) { it.copy(peek = true) }
        assertEquals(96, steps.size)
        assertEquals(24, steps.count { it.peek })
        steps.forEachIndexed { i, step -> if (step.peek) assertEquals(steps[i - 1].copy(peek = true), step) }
    }

    @Test fun refillUsesFreshShuffles() {
        val pool = listOf("a", "b", "c")
        for (seed in 0 until 50) {
            val first = TileShuffle.steps(pool, slots = 9, count = 1, random = Random(seed)).single().slots
            assertEquals(9, first.size)
            val orders = first.chunked(3)
            // Each refill holds the whole pool, in a different order from the one before it.
            orders.forEach { assertEquals(pool.toSet(), it.toSet()) }
            orders.zipWithNext { a, b -> assertNotEquals(a, b) }
        }
        // One photo can only repeat; it must still fill every slot without looping.
        assertEquals(List(9) { "only" }, TileShuffle.steps(listOf("only"), slots = 9, count = 3, random = Random(1))[0].slots)
    }
}
