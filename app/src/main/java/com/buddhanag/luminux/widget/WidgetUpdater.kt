package com.buddhanag.luminux.widget

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
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
    suspend fun resetAndUpdateAll(context: Context) = forEachWidget(context) { state, config, pool ->
        reset(state, config.style, pool)
        true
    }

    /** One tick: the next step, or a new list at the end. A tile with one photo or none has nothing to change. */
    suspend fun advanceAll(context: Context) = forEachWidget(context) { state, config, pool ->
        if (pool.size <= 1) return@forEachWidget false
        val next = (state[TileState.stepIndex] ?: 0) + 1
        // Added with no configuration, it has no steps until the first tick.
        val hasSteps = state[TileState.steps(slots(TileKind.Small, config.style))] != null
        if (next >= STEPS || !hasSteps) reset(state, config.style, pool) else state[TileState.stepIndex] = next
        true
    }

    /** The tile's shuffle button: a fresh list of steps for this widget only. */
    suspend fun shuffle(context: Context, id: GlanceId) = change(context, id) { state, config, pool ->
        reset(state, config.style, pool)
        pool.size > 1
    }

    /** Saves a widget's source and style (the configure screen, or "make a widget" pinning one), then resets them all. */
    suspend fun configure(
        context: Context, appWidgetId: Int, source: TileSource, collectionId: String?, style: TileStyle = TileStyle.Shuffle,
    ) {
        updateAppWidgetState(context, GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)) {
            it[TileState.source] = source.name
            it[TileState.style] = style.name
            if (collectionId == null) it.remove(TileState.collectionId) else it[TileState.collectionId] = collectionId
        }
        resetAndUpdateAll(context)
    }

    /** Steps for each slot count the style's sizes use. A peek style repeats every third step as a peek. */
    private fun reset(state: MutablePreferences, style: TileStyle, pool: List<String>) {
        for (slots in TileKind.entries.map { slots(it, style) }.distinct()) {
            val steps = when {
                pool.size <= 1 -> emptyList()
                // Three quarters change a photo, so the list still lasts a day once the peeks are in.
                style == TileStyle.Peek -> TileShuffle.withPeeks(TileShuffle.steps(pool, slots, STEPS * 3 / 4), every = 3) { it.copy(peek = true) }
                else -> TileShuffle.steps(pool, slots, STEPS)
            }
            state[TileState.steps(slots)] = Json.encodeToString(steps)
        }
        state[TileState.stepIndex] = 0
    }

    private suspend fun forEachWidget(context: Context, change: (MutablePreferences, TileConfig, List<String>) -> Boolean) {
        for (id in GlanceAppWidgetManager(context).getGlanceIds(LuminuxWidget::class.java)) change(context, id, change)
    }

    /** [change] gets the widget's state, its config and its pool of file names, and says whether to redraw it. */
    private suspend fun change(context: Context, id: GlanceId, change: (MutablePreferences, TileConfig, List<String>) -> Boolean) {
        val store = context.container.liveTileStore
        val collections = store.collections.first()
        var redraw = false
        updateAppWidgetState(context, id) { state ->
            val config = TileConfig.from(state, collections)
            redraw = change(state, config, config.folder?.let(store::imageFiles).orEmpty().map { it.name })
        }
        if (redraw) LuminuxWidget().update(context, id)
    }
}

/** The shuffle button's action (ShuffleTileIntent); on Android it reshuffles directly. */
class ShuffleCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        WidgetUpdater.shuffle(context, glanceId)
}
