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
}
