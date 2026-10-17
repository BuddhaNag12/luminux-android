package com.buddhanag.luminux.feature.shell

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buddhanag.luminux.container
import com.buddhanag.luminux.data.AlbumIds
import com.buddhanag.luminux.data.SettingsKeys
import com.buddhanag.luminux.designsystem.Accent
import com.buddhanag.luminux.designsystem.LocalMetro
import com.buddhanag.luminux.designsystem.MetroMetrics
import com.buddhanag.luminux.designsystem.MetroText
import com.buddhanag.luminux.designsystem.MetroType
import com.buddhanag.luminux.designsystem.metroFeather
import com.buddhanag.luminux.feature.collection.AlbumScreen
import com.buddhanag.luminux.feature.collection.CollectionScreen
import com.buddhanag.luminux.feature.hub.HubScreen
import com.buddhanag.luminux.feature.hub.PhotoSetScreen
import com.buddhanag.luminux.feature.settings.AboutScreen
import com.buddhanag.luminux.feature.settings.PageHeader
import com.buddhanag.luminux.feature.settings.SettingsScreen
import com.buddhanag.luminux.feature.journal.JournalEntryScreen
import com.buddhanag.luminux.feature.journal.JournalScreen
import com.buddhanag.luminux.feature.places.PlaceScreen
import kotlinx.coroutines.flow.filter

/** AppShell (LuminuxApp.swift): the hub with the page stack over it, deep links and the app-wide side effects. */
@Composable
fun AppShell(pendingDeepLink: MutableState<Uri?>) {
    val container = LocalContext.current.container
    val navigator = container.navigator
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val isProUnlocked by container.isProUnlocked.collectAsStateWithLifecycle()
    val unlocksPro = isProUnlocked || container.isTestBuild

    // A refunded purchase takes its custom colour with it.
    LaunchedEffect(unlocksPro, settings?.accentHex) {
        val hex = settings?.accentHex ?: return@LaunchedEffect
        if (!unlocksPro && !Accent(hex).isPreset) container.settings.set(SettingsKeys.accent, Accent.Cobalt.hex)
    }
    // shortcut: proStore.refresh() joins with spec 09.
    LaunchedEffect(Unit) { container.journal.keepBuilt() }
    // Pinned albums that are gone take their tiles with them; smart albums always exist, even when empty and unlisted.
    LaunchedEffect(Unit) {
        container.photos.snapshot.filter { it.isLoaded }.collect { snapshot ->
            container.hubTiles.prune(snapshot.albums.map { it.id }.toSet() + AlbumIds.SCREENSHOTS + AlbumIds.PANORAMAS)
        }
    }
    LaunchedEffect(pendingDeepLink.value) {
        val uri = pendingDeepLink.value ?: return@LaunchedEffect
        navigator.handleDeepLink(uri, isProUnlocked)
        pendingDeepLink.value = null
    }

    MetroStack(navigator, root = { HubScreen() }) { route ->
        when (route) {
            Route.Settings -> SettingsScreen()
            Route.About -> AboutScreen()
            is Route.Collection -> CollectionScreen(route.page, route.showsJumpList)
            is Route.Album -> AlbumScreen(route.id)
            Route.Pro -> Placeholder("pro", "09")
            Route.Journal -> JournalScreen()
            is Route.JournalEntry -> JournalEntryScreen(route.id)
            is Route.PhotoSet -> PhotoSetScreen(route.id)
            is Route.Place -> PlaceScreen(route.id)
        }
    }
    // shortcut: the full-screen viewer over the stack (navigator.viewer) comes with spec 07.
}

// shortcut: stand-ins so every route can be opened and navigation checked; each is replaced by its spec's screen.
@Composable
private fun Placeholder(title: String, spec: String) = PlaceholderPage(title, "This page comes with spec $spec.") {}

@Composable
private fun PlaceholderPage(title: String, note: String, links: @Composable () -> Unit) {
    val metro = LocalMetro.current
    Column(
        Modifier
            .fillMaxSize()
            .background(metro.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = MetroMetrics.margin + 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        PageHeader(title, Modifier.metroFeather(row = 0))
        MetroText(note, MetroType.body, Modifier.metroFeather(row = 1), color = metro.secondary)
        links()
    }
}
