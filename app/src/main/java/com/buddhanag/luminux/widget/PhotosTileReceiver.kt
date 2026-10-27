package com.buddhanag.luminux.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.buddhanag.luminux.container
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class PhotosTileReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = LuminuxWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SHUFFLE_WORK, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<TileShuffleWorker>(15, TimeUnit.MINUTES).build(),
        )
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WorkManager.getInstance(context).cancelUniqueWork(SHUFFLE_WORK)
    }

    private companion object {
        const val SHUFFLE_WORK = "luminux-tile-shuffle"
    }
}

/** Called back once a tile pinned from "make a widget" is placed: it shows that collection straight away. */
class TilePinnedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val collectionId = intent.getStringExtra(EXTRA_COLLECTION_ID) ?: return
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val pending = goAsync()
        context.container.appScope.launch {
            try {
                WidgetUpdater.configure(context, appWidgetId, TileSource.Collection, collectionId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_COLLECTION_ID = "collectionId"
    }
}
