package com.buddhanag.luminux.feature.actions

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buddhanag.luminux.container
import com.buddhanag.luminux.data.AssetSource
import com.buddhanag.luminux.data.TileCollection
import com.buddhanag.luminux.designsystem.AppBarMenuItem
import com.buddhanag.luminux.designsystem.LocalMetro
import com.buddhanag.luminux.designsystem.MetroButton
import com.buddhanag.luminux.designsystem.MetroText
import com.buddhanag.luminux.designsystem.MetroType
import com.buddhanag.luminux.feature.settings.ChoiceHeader
import com.buddhanag.luminux.feature.settings.ChoicePage
import com.buddhanag.luminux.feature.settings.ChoiceRow
import com.buddhanag.luminux.widget.PhotosTileReceiver
import com.buddhanag.luminux.widget.TilePinnedReceiver
import kotlinx.coroutines.launch

/** How to show a collection on the Home screen (WidgetCollection.steps). */
internal fun widgetSteps(title: String?): String = buildString {
    appendLine("1. Touch and hold an empty spot on your Home screen and tap Widgets.")
    appendLine("2. Find Luminux and add the Photos tile.")
    appendLine("3. Touch and hold the tile and tap Settings.")
    append("4. Set Show to “a collection” and pick ${title?.let { "“$it”" } ?: "an album or photo set"}.")
}

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
        MetroText(widgetSteps(collection.title), MetroType.body)
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

/**
 * Settings' "widget collections" page (`WidgetCollectionsPicker`): pre-loads albums and photo sets for the Photos
 * widget and shows the numbered setup steps.
 */
@Composable
fun WidgetCollectionsScreen() {
    val container = LocalContext.current.container
    val metro = LocalMetro.current
    val snapshot by container.photos.snapshot.collectAsStateWithLifecycle()
    val sets = container.hubTiles.layout.collectAsStateWithLifecycle().value.sets.values.sortedBy { it.title }
    val collections by container.liveTileStore.collections.collectAsStateWithLifecycle(initialValue = emptyList())
    val added = collections.mapTo(HashSet()) { it.id }
    var busy by remember { mutableStateOf(setOf<String>()) }
    val scope = rememberCoroutineScope()

    @Composable
    fun row(collection: TileCollection, coverId: Long?, count: Int) {
        val isAdded = collection.id in added
        val isBusy = collection.id in busy
        ChoiceRow(
            cover = coverId?.let(snapshot.byId::get),
            title = collection.title,
            detail = if (isAdded) "on the widget" else if (count == 1) "1 photo" else "$count photos",
            isSelected = isAdded,
            isBusy = isBusy,
            onClick = {
                if (isBusy) return@ChoiceRow
                if (isAdded) {
                    scope.launch { container.liveTileExporter.remove(collection.id) }
                    return@ChoiceRow
                }
                busy = busy + collection.id
                scope.launch {
                    container.liveTileExporter.exportOne(collection)
                    busy = busy - collection.id
                }
            },
        )
    }

    ChoicePage(
        "widget",
        "Any album or photo set can go on the Photos widget. Ticked ones are pre-loaded so they show at once.",
    ) {
        MetroText(
            widgetSteps(null), MetroType.caption,
            Modifier.fillMaxWidth().background(metro.chrome).padding(12.dp),
            color = metro.secondary,
        )

        if (snapshot.albums.isNotEmpty()) {
            ChoiceHeader("albums")
            snapshot.albums.forEach { album ->
                row(TileCollection.album(album.id, album.title), album.keyItemId, album.count)
            }
        }
        if (snapshot.albums.isEmpty() && sets.isEmpty()) {
            MetroText(
                "No albums or photo sets yet. Make an album, or pin a few photos to the hub as a set.",
                MetroType.body, Modifier.padding(top = 24.dp), color = metro.secondary,
            )
        }
        if (sets.isNotEmpty()) {
            ChoiceHeader("photo sets")
            sets.forEach { set ->
                val count = container.photos.items(AssetSource.Identifiers(set.photoIds)).size
                row(TileCollection.photoSet(set.id, set.title), set.photoIds.firstOrNull(), count)
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
