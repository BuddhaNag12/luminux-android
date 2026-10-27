package com.buddhanag.luminux.widget

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.buddhanag.luminux.container
import com.buddhanag.luminux.designsystem.TileShuffle
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/**
 * Android's `reloadAllTimelines()`. iOS draws a new photo every 5 s; a widget can't be redrawn that often, so each
 * one keeps a day of steps and WorkManager moves it on one step every 15 minutes.
 */
object WidgetUpdater {
    /** 24 hours at 15 minutes a step (iOS: 120 entries 5 s apart). */
    private const val STEPS = 96

    /** A fresh list of steps for every widget from the photos it now has, then a redraw. */
    suspend fun resetAndUpdateAll(context: Context) = forEachWidget(context) { state, pool ->
        reset(state, pool)
        true
    }

    /** One tick: the next step, or a new list at the end. A tile with one photo or none has nothing to change. */
    suspend fun advanceAll(context: Context) = forEachWidget(context) { state, pool ->
        if (pool.size <= 1) return@forEachWidget false
        val next = (state[TileState.stepIndex] ?: 0) + 1
        // Added with no configuration, it has no steps until the first tick.
        if (next >= STEPS || state[TileState.steps(3)] == null) reset(state, pool) else state[TileState.stepIndex] = next
        true
    }

    /** Saves a widget's source (the configure screen, or "make a widget" pinning one), then resets them all. */
    suspend fun configure(context: Context, appWidgetId: Int, source: TileSource, collectionId: String?) {
        updateAppWidgetState(context, GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)) {
            it[TileState.source] = source.name
            if (collectionId == null) it.remove(TileState.collectionId) else it[TileState.collectionId] = collectionId
        }
        resetAndUpdateAll(context)
    }

    private fun reset(state: MutablePreferences, pool: List<String>) {
        for (slots in listOf(3, 4)) {
            val steps = if (pool.size > 1) TileShuffle.steps(pool, slots, STEPS) else emptyList()
            state[TileState.steps(slots)] = Json.encodeToString(steps)
        }
        state[TileState.stepIndex] = 0
    }

    /** [change] gets each widget's state and its pool of file names, and says whether to redraw it. */
    private suspend fun forEachWidget(context: Context, change: (MutablePreferences, List<String>) -> Boolean) {
        val store = context.container.liveTileStore
        val collections = store.collections.first()
        val widget = LuminuxWidget()
        for (id in GlanceAppWidgetManager(context).getGlanceIds(LuminuxWidget::class.java)) {
            var redraw = false
            updateAppWidgetState(context, id) { state ->
                val pool = TileConfig.from(state, collections).folder?.let(store::imageFiles).orEmpty().map { it.name }
                redraw = change(state, pool)
            }
            if (redraw) widget.update(context, id)
        }
    }
}
