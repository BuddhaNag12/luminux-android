package com.buddhanag.luminux.designsystem

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveTileSchedulerTest {
    private val scope = CoroutineScope(Job())

    @After fun tearDown() = scope.cancel()

    @Test fun countsReturns() {
        val scheduler = LiveTileScheduler(scope)
        scheduler.isPaused = true
        scheduler.isPaused = false
        scheduler.isPaused = false
        assertEquals(1, scheduler.returns.value)
        scheduler.isPaused = true
        scheduler.isPaused = false
        assertEquals(2, scheduler.returns.value)
    }
}
