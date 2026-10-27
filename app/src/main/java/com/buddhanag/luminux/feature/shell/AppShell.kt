package com.buddhanag.luminux.feature.shell

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.buddhanag.luminux.container
import com.buddhanag.luminux.data.AlbumIds
import com.buddhanag.luminux.data.PageTransition
import com.buddhanag.luminux.data.SettingsKeys
import com.buddhanag.luminux.designsystem.Accent
import com.buddhanag.luminux.feature.collection.AlbumScreen
import com.buddhanag.luminux.feature.collection.CollectionScreen
import com.buddhanag.luminux.feature.hub.HubScreen
import com.buddhanag.luminux.feature.hub.PhotoSetScreen
import com.buddhanag.luminux.feature.settings.AboutScreen
import com.buddhanag.luminux.feature.settings.SettingsScreen
import com.buddhanag.luminux.feature.journal.JournalEntryScreen
import com.buddhanag.luminux.feature.journal.JournalScreen
import com.buddhanag.luminux.feature.places.PlaceScreen
import com.buddhanag.luminux.feature.pro.ProScreen
import kotlinx.coroutines.flow.filter
import com.buddhanag.luminux.feature.viewer.ViewerHost

/** AppShell (LuminuxApp.swift): the hub with the page stack over it, deep links and the app-wide side effects. */
@Composable
fun AppShell(pendingDeepLink: MutableState<Uri?>, pageTransition: PageTransition) {
    val container = LocalContext.current.container
    val navigator = container.navigator
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val isProUnlocked by container.proStore.isUnlocked.collectAsStateWithLifecycle()
    val unlocksPro = isProUnlocked || container.proStore.isTestBuild

    // A refunded purchase takes its custom colour with it.
    LaunchedEffect(unlocksPro, settings?.accentHex) {
        val hex = settings?.accentHex ?: return@LaunchedEffect
        if (!unlocksPro && !Accent(hex).isPreset) container.settings.set(SettingsKeys.accent, Accent.Cobalt.hex)
    }
    // Play has no refund stream, so re-read the purchases on every resume (also catches pending → purchased).
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { container.proStore.refresh() } }
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

    // The full-screen viewer over the stack, zooming out of the tile marked with Modifier.zoomSource.
    ViewerHost(navigator) {
        MetroStack(navigator, pageTransition, root = { HubScreen() }) { route ->
            when (route) {
                Route.Settings -> SettingsScreen()
                Route.About -> AboutScreen()
                is Route.Collection -> CollectionScreen(route.page, route.showsJumpList)
                is Route.Album -> AlbumScreen(route.id)
                Route.Pro -> ProScreen()
                Route.Journal -> JournalScreen()
                is Route.JournalEntry -> JournalEntryScreen(route.id)
                is Route.PhotoSet -> PhotoSetScreen(route.id)
                is Route.Place -> PlaceScreen(route.id)
            }
        }
    }
}
