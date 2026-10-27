package com.buddhanag.luminux.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Moves every Photos tile on one photo; runs every 15 minutes (the shortest WorkManager allows) while one is placed. */
class TileShuffleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WidgetUpdater.advanceAll(applicationContext)
        return Result.success()
    }
}
