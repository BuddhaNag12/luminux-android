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
import com.buddhanag.luminux.data.SettingsKeys
import com.buddhanag.luminux.designsystem.Accent
import com.buddhanag.luminux.designsystem.LocalMetro
import com.buddhanag.luminux.designsystem.MetroMetrics
import com.buddhanag.luminux.designsystem.MetroText
import com.buddhanag.luminux.designsystem.MetroType
import com.buddhanag.luminux.designsystem.metroFeather
import com.buddhanag.luminux.feature.settings.AboutScreen
import com.buddhanag.luminux.feature.settings.LinkRow
import com.buddhanag.luminux.feature.settings.PageHeader
import com.buddhanag.luminux.feature.settings.SettingsScreen

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
    // shortcut: proStore.refresh() (09), the journal rebuild (08) and the hub pin prune (05) join with those specs.
    LaunchedEffect(pendingDeepLink.value) {
        val uri = pendingDeepLink.value ?: return@LaunchedEffect
        navigator.handleDeepLink(uri, isProUnlocked)
        pendingDeepLink.value = null
    }

    MetroStack(navigator, root = { HubPlaceholder(navigator) }) { route ->
        when (route) {
            Route.Settings -> SettingsScreen()
            Route.About -> AboutScreen()
            is Route.Collection -> Placeholder("collection", "06")
            is Route.Album -> Placeholder("album", "06")
            Route.Pro -> Placeholder("pro", "09")
            Route.Journal -> Placeholder("journal", "08")
            is Route.JournalEntry -> Placeholder("journal entry", "08")
            is Route.PhotoSet -> Placeholder("photo set", "05")
            is Route.Place -> Placeholder("place", "08")
        }
    }
    // shortcut: the full-screen viewer over the stack (navigator.viewer) comes with spec 07.
}

// shortcut: stand-ins so every route can be opened and navigation checked; each is replaced by its spec's screen.
@Composable
private fun HubPlaceholder(navigator: Navigator) {
    PlaceholderPage("photos", "The hub comes with spec 05.") {
        LinkRow("collection", "All photos, albums, favorites, videos", Modifier.metroFeather(row = 2)) { navigator.push(Route.Collection(page = 0)) }
        LinkRow("journal", "Your days and trips", Modifier.metroFeather(row = 3)) { navigator.push(Route.Journal) }
        LinkRow("pro", "What Pro adds", Modifier.metroFeather(row = 4)) { navigator.push(Route.Pro) }
        LinkRow("settings", "Motion, look and about", Modifier.metroFeather(row = 5)) { navigator.push(Route.Settings) }
    }
}

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
