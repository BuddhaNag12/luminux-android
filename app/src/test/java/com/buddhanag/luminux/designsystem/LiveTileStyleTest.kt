package com.buddhanag.luminux.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.random.Random

class LiveTileStyleTest {
    @Test fun aSlideNeverComesFromTheSameEdgeTwice() {
        val random = Random(7)
        var edge = SlideEdge.Bottom
        val seen = mutableSetOf<SlideEdge>()
        repeat(500) {
            val next = nextSlideEdge(edge, random)
            assertNotEquals(edge, next)
            seen += next
            edge = next
        }
        assertEquals(SlideEdge.entries.toSet(), seen)
    }

    @Test fun noStylePicksAtRandomAndRepeatsWithTheSameSeed() {
        val first = List(60) { pickLiveTileStyle(null, Random(it)) }
        assertEquals(first, List(60) { pickLiveTileStyle(null, Random(it)) })
        assertEquals(LiveTileStyle.entries.toSet(), first.toSet())
    }

    @Test fun aFixedStyleIsKept() {
        val random = Random(3)
        LiveTileStyle.entries.forEach { style -> repeat(10) { assertEquals(style, pickLiveTileStyle(style, random)) } }
    }
}
