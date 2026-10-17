package com.buddhanag.luminux.feature.shell

import android.graphics.Color.TRANSPARENT
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buddhanag.luminux.container
import com.buddhanag.luminux.data.SettingsKeys
import com.buddhanag.luminux.designsystem.Accent
import com.buddhanag.luminux.designsystem.LocalLiveTileScheduler
import com.buddhanag.luminux.designsystem.LocalMetro
import com.buddhanag.luminux.designsystem.MetroMotion
import com.buddhanag.luminux.designsystem.MetroTheme
import com.buddhanag.luminux.designsystem.MetroThemeMode
import com.buddhanag.luminux.feature.onboarding.OnboardingScreen
import com.buddhanag.luminux.feature.onboarding.PermissionScreen
import kotlinx.coroutines.launch
import com.buddhanag.luminux.data.MetroThemeMode as StoredTheme

private enum class Gate { Onboarding, Shell, Permission }

/**
 * RootView (LuminuxApp.swift): the tour until it's been seen, then the app once photos can be read, else the access
 * page. The launch animation plays once per launch, over whatever opens first.
 */
@Composable
fun LuminuxRoot(pendingDeepLink: MutableState<Uri?>, forcesOnboarding: Boolean) {
    val container = LocalContext.current.container
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val access by container.photos.access.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var forced by rememberSaveable { mutableStateOf(forcesOnboarding) }
    var showsSplash by rememberSaveable { mutableStateOf(true) }

    val theme = if (settings?.theme == StoredTheme.Light) MetroThemeMode.Light else MetroThemeMode.Dark
    val accent = Accent(settings?.accentHex ?: Accent.Cobalt.hex)
    SystemBars(theme)
    MetroTheme(theme, accent) {
        CompositionLocalProvider(
            LocalLiveTileScheduler provides container.liveTileScheduler,
            LocalLaunchSplashShowing provides showsSplash,
        ) {
            Box(Modifier.fillMaxSize().background(LocalMetro.current.background)) {
                // Nothing until settings load (a few ms, under the splash), so the tour never flashes for returning users.
                settings?.let { s ->
                    val gate = when {
                        !s.hasSeenOnboarding || forced -> Gate.Onboarding
                        access.canRead -> Gate.Shell
                        else -> Gate.Permission
                    }
                    Crossfade(gate, animationSpec = MetroMotion.fade(), label = "gate") {
                        when (it) {
                            Gate.Onboarding -> OnboardingScreen(isReplay = false) {
                                forced = false
                                scope.launch { container.settings.set(SettingsKeys.hasSeenOnboarding, true) }
                            }
                            Gate.Shell -> AppShell(pendingDeepLink)
                            Gate.Permission -> PermissionScreen()
                        }
                    }
                }
                if (showsSplash) LaunchSplash { showsSplash = false }
            }
        }
    }
}

/** The app picks its own theme, so the status and navigation bar icons follow it rather than the system's. */
@Composable
private fun SystemBars(theme: MetroThemeMode) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    LaunchedEffect(theme) {
        val style = if (theme == MetroThemeMode.Dark) SystemBarStyle.dark(TRANSPARENT)
        else SystemBarStyle.light(TRANSPARENT, TRANSPARENT)
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}
