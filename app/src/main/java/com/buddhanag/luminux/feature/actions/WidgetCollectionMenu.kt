package com.buddhanag.luminux.feature.actions

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.buddhanag.luminux.container
import com.buddhanag.luminux.data.TileCollection
import com.buddhanag.luminux.designsystem.AppBarMenuItem
import com.buddhanag.luminux.designsystem.MetroButton
import com.buddhanag.luminux.designsystem.MetroText
import com.buddhanag.luminux.designsystem.MetroType
import com.buddhanag.luminux.widget.PhotosTileReceiver
import com.buddhanag.luminux.widget.TilePinnedReceiver
import kotlinx.coroutines.launch

/**
 * "make a widget" / "remove from widgets" in an album's or photo set's menu (WidgetCollection.swift): readies the
 * collection's photos for the widget, or drops them. Whether it's added is read when the page opens. Adding shows the
 * "Widget ready" note, which can also put a tile showing this collection on the Home screen.
 */
@Composable
fun widgetMenuItem(collection: TileCollection): AppBarMenuItem {
    val context = LocalContext.current
    val container = context.container
    var isAdded by remember(collection.id) { mutableStateOf(false) }
    var showsNote by remember(collection.id) { mutableStateOf(false) }
    LaunchedEffect(collection.id) { isAdded = container.liveTileExporter.contains(collection.id) }
    if (showsNote) WidgetReadyDialog(collection) { showsNote = false }
    // On the app scope, so the export finishes even if the page is closed meanwhile.
    return if (isAdded) {
        AppBarMenuItem("remove from widgets") {
            isAdded = false
            container.appScope.launch { container.liveTileExporter.remove(collection.id) }
        }
    } else {
        AppBarMenuItem("make a widget") {
            isAdded = true
            showsNote = true
            container.appScope.launch { container.liveTileExporter.exportOne(collection) }
        }
    }
}

@Composable
private fun WidgetReadyDialog(collection: TileCollection, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val widgets = AppWidgetManager.getInstance(context)
    MetroDialog("Widget ready", onDismiss) {
        MetroText(
            "Touch and hold an empty spot on your Home screen, tap Widgets and find Luminux. Once the Photos tile is " +
                "placed, touch and hold it, choose Customize, set Show to “a collection” and choose “${collection.title}”.",
            MetroType.body,
        )
        Buttons {
            MetroButton("ok", onDismiss)
            if (widgets.isRequestPinAppWidgetSupported) {
                MetroButton("add to home", {
                    onDismiss()
                    widgets.requestPinAppWidget(ComponentName(context, PhotosTileReceiver::class.java), null, pinned(context, collection.id))
                })
            }
        }
    }
}

/** The launcher fills in the new widget's id; [TilePinnedReceiver] then points it at the collection. */
private fun pinned(context: Context, collectionId: String): PendingIntent = PendingIntent.getBroadcast(
    context, 0,
    Intent(context, TilePinnedReceiver::class.java).putExtra(TilePinnedReceiver.EXTRA_COLLECTION_ID, collectionId),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
)
