package com.buddhanag.luminux.feature.actions

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
import kotlinx.coroutines.launch

/**
 * "make a widget" / "remove from widgets" in an album's or photo set's menu (WidgetCollection.swift): readies the
 * collection's photos for the widget, or drops them. Whether it's added is read when the page opens.
 */
@Composable
fun widgetMenuItem(collection: TileCollection): AppBarMenuItem {
    val container = LocalContext.current.container
    var isAdded by remember(collection.id) { mutableStateOf(false) }
    LaunchedEffect(collection.id) { isAdded = container.liveTileExporter.contains(collection.id) }
    // On the app scope, so the export finishes even if the page is closed meanwhile.
    return if (isAdded) {
        AppBarMenuItem("remove from widgets") {
            isAdded = false
            container.appScope.launch { container.liveTileExporter.remove(collection.id) }
        }
    } else {
        AppBarMenuItem("make a widget") {
            isAdded = true
            // shortcut: no "Widget ready" note yet; spec 10 offers AppWidgetManager.requestPinAppWidget here.
            container.appScope.launch { container.liveTileExporter.exportOne(collection) }
        }
    }
}
