package com.buddhanag.luminux.designsystem

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Hands out turns to the live tiles on screen, one tile every few seconds and never the same tile twice in a row.
 * Pauses while the hub is covered, so hidden tiles don't animate. Use one per app, on a main-thread scope.
 */
class LiveTileScheduler(private val scope: CoroutineScope, private val random: Random = Random.Default) {
    /** [count] changes every turn, so the same tile can be told twice (with others in between). */
    data class Turn(val tile: Any, val count: Int)

    private val _turn = MutableStateFlow<Turn?>(null)
    val turn: StateFlow<Turn?> = _turn.asStateFlow()

    /** Counts each time the tiles come back into view. */
    private val _returns = MutableStateFlow(0)
    val returns: StateFlow<Int> = _returns.asStateFlow()

    private val tiles = mutableSetOf<Any>()
    private var loop: Job? = null
    private var count = 0

    /** Set while something covers the tiles (another page, the viewer, the app in the background). */
    var isPaused = false
        set(value) {
            if (value == field) return
            field = value
            if (!value) _returns.value++
            restart()
        }

    /** Off in settings: no turns at all. */
    var isAnimated = true
        set(value) {
            if (value == field) return
            field = value
            restart()
        }

    fun register(tile: Any, canChange: Boolean) {
        if (canChange) tiles += tile else tiles -= tile
        if (loop == null) restart()
    }

    fun unregister(tile: Any) {
        tiles -= tile
    }

    private fun restart() {
        loop?.cancel()
        loop = null
        if (isPaused || !isAnimated) return
        loop = scope.launch {
            while (isActive) {
                delay((random.nextDouble(INTERVAL_MIN, INTERVAL_MAX) * 1000).toLong())
                giveTurn()
            }
        }
    }

    private fun giveTurn() {
        val next = tiles.filter { it != _turn.value?.tile }.randomOrNull(random) ?: tiles.firstOrNull() ?: return
        _turn.value = Turn(next, ++count)
    }

    companion object {
        /** Seconds between turns; with several tiles on screen each still changes every 10 to 20 seconds. */
        const val INTERVAL_MIN = 2.5
        const val INTERVAL_MAX = 4.0
    }
}

/** The app's scheduler. Spec 04 provides the one owned by `AppContainer`; the debug gallery provides its own. */
val LocalLiveTileScheduler = staticCompositionLocalOf<LiveTileScheduler> { error("No LiveTileScheduler provided") }
