package com.buddhanag.luminux.designsystem

import com.buddhanag.luminux.designsystem.FeatherState.Above
import com.buddhanag.luminux.designsystem.FeatherState.Below
import com.buddhanag.luminux.designsystem.FeatherState.Shown
import org.junit.Assert.assertEquals
import org.junit.Test

class FeatherTimingTest {
    @Test fun enterFromBelowWaitsForTheOldPageThenGoesLineByLine() {
        val timing = Feather.timing(Shown, Below, row = 3, column = 2)
        assertEquals(364, timing.rotation.delay) // 0.22 + 3 × 0.04 + 2 × 0.012
        assertEquals(350, timing.rotation.durationMillis)
        assertEquals(364, timing.alpha.delay)
        assertEquals(40, timing.alpha.durationMillis)
    }

    @Test fun revealUsesTheShorterBase() {
        assertEquals(120, Feather.timing(Shown, Above, row = 0, column = 0).rotation.delay)
    }

    @Test fun rowsClampToTheVisibleLines() {
        assertEquals(Feather.timing(Shown, Below, 10, 0), Feather.timing(Shown, Below, 20, 0))
        assertEquals(Feather.timing(Shown, Below, 0, 0), Feather.timing(Shown, Below, -1, 0))
    }

    @Test fun exitFadesAsTheTurnEnds() {
        val timing = Feather.timing(Above, Shown, row = 2, column = 2)
        assertEquals(62, timing.rotation.delay) // 2 × 0.025 + 2 × 0.006
        assertEquals(220, timing.rotation.durationMillis)
        assertEquals(232, timing.alpha.delay) // + 0.22 − 0.05
    }

    @Test fun exitTimeIs345Milliseconds() {
        assertEquals(0.345, Feather.exitTime, 1e-9)
    }

    @Test fun enterTimeIs970Milliseconds() {
        assertEquals(0.97, Feather.enterTime, 1e-9)
    }

    @Test fun aZoomingPageDoesNotWaitForTheOutgoingOne() {
        assertEquals(132, Feather.timing(Shown, Below, row = 3, column = 1, waitsForOutgoing = false).rotation.delay)
        assertEquals(Feather.timing(Shown, Above, 3, 1), Feather.timing(Shown, Above, 3, 1, waitsForOutgoing = false))
    }

    @Test fun scatterGoesOneItemAtATimeAndFadesAtTheEnd() {
        val out = Feather.scatterTiming(out = true, row = 2, column = 1) // step 5
        assertEquals(150, out.rotation.delay)
        assertEquals(220, out.rotation.durationMillis)
        assertEquals(290, out.alpha.delay) // 0.15 + 0.22 − 0.08
        assertEquals(80, out.alpha.durationMillis)
        val back = Feather.scatterTiming(out = false, row = 2, column = 1)
        assertEquals(270, back.rotation.delay) // 0.12 + 0.15
        assertEquals(350, back.rotation.durationMillis)
        assertEquals(270, back.alpha.delay)
        assertEquals(60, back.alpha.durationMillis)
        assertEquals(0, Feather.scatterTiming(out = true, row = -1, column = 0).rotation.delay)
    }

    @Test fun theTitleComesForwardSlowerThanTheRest() {
        assertEquals(700, Feather.forwardTiming(0, 0).rotation.durationMillis)
        assertEquals(300, Feather.forwardTiming(0, 0).alpha.durationMillis)
        val tile = Feather.forwardTiming(3, 1)
        assertEquals(550, tile.rotation.durationMillis)
        assertEquals(200, tile.alpha.durationMillis)
        assertEquals(500, tile.rotation.delay)
        assertEquals(500, tile.alpha.delay)
    }
}
