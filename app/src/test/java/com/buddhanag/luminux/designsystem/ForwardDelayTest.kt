package com.buddhanag.luminux.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class ForwardDelayTest {
    @Test fun theTitleThenTheHeadersThenTheTilesInReadingOrder() {
        assertEquals(0.0, Feather.forwardDelay(0, 0), 1e-9)
        assertEquals(0.30, Feather.forwardDelay(1, 1), 1e-9)
        assertEquals(0.50, Feather.forwardDelay(3, 1), 1e-9)
    }
}
