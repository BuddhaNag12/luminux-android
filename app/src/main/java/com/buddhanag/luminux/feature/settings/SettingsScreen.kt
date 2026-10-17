package com.buddhanag.luminux.feature.settings

import androidx.compose.foundation.Image
import androidx.datastore.preferences.core.Preferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buddhanag.luminux.container
import com.buddhanag.luminux.data.AppSettings
import com.buddhanag.luminux.data.SettingsKeys
import com.buddhanag.luminux.data.TiltStrength
import com.buddhanag.luminux.designsystem.Accent
import com.buddhanag.luminux.designsystem.LocalMetro
import com.buddhanag.luminux.designsystem.MetroIcons
import com.buddhanag.luminux.designsystem.MetroMetrics
import com.buddhanag.luminux.designsystem.MetroText
import com.buddhanag.luminux.designsystem.MetroType
import com.buddhanag.luminux.designsystem.Pivot
import com.buddhanag.luminux.designsystem.PivotPage
import com.buddhanag.luminux.designsystem.Semilight
import com.buddhanag.luminux.designsystem.metroFeather
import com.buddhanag.luminux.designsystem.metroFont
import com.buddhanag.luminux.feature.onboarding.OnboardingScreen
import com.buddhanag.luminux.feature.shell.Route
import kotlinx.coroutines.launch
import com.buddhanag.luminux.data.MetroThemeMode as StoredTheme

/** Three short pivot pages instead of one long list: how it behaves, how it looks, and the rest (SettingsView.swift). */
@Composable
fun SettingsScreen() {
    val container = LocalContext.current.container
    val navigator = container.navigator
    val repo = container.settings
    val settings by repo.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val isProUnlocked by container.isProUnlocked.collectAsStateWithLifecycle()
    val unlocksPro = isProUnlocked || container.isTestBuild
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableIntStateOf(0) }
    var showsBackgroundPicker by rememberSaveable { mutableStateOf(false) }
    var showsTour by rememberSaveable { mutableStateOf(false) }
    var showsColorPicker by rememberSaveable { mutableStateOf(false) }
    // shortcut: spec 09 adds proStore.refresh() and, without Pro, adConsent.refreshPrivacyOptions() on appear.

    Box(Modifier.fillMaxSize()) {
        Pivot(
            pages = listOf(
                PivotPage("general") {
                    SettingsPage { GeneralPage(settings) { key, value -> scope.launch { repo.set(key, value) } } }
                },
                PivotPage("look") {
                    SettingsPage {
                        LookPage(
                            settings, unlocksPro,
                            onTheme = { scope.launch { repo.setTheme(it) } },
                            onAccent = { scope.launch { repo.set(SettingsKeys.accent, it.hex) } },
                            onCustomAccent = { if (unlocksPro) showsColorPicker = true else navigator.push(Route.Pro) },
                            onBackground = { scope.launch { repo.set(SettingsKeys.showsHubBackground, it) } },
                            onBackgroundSource = { showsBackgroundPicker = true },
                        )
                    }
                },
                PivotPage("about") {
                    SettingsPage {
                        AboutPage(
                            isProUnlocked,
                            onPro = { navigator.push(Route.Pro) },
                            onTour = { showsTour = true },
                            onAbout = { navigator.push(Route.About) },
                        )
                    }
                },
            ),
            selection = page,
            onSelectionChange = { page = it },
            overline = "LUMINUX · SETTINGS",
        )
        if (showsBackgroundPicker) {
            BackgroundPicker(
                settings, unlocksPro,
                onPick = { source -> scope.launch { repo.setHubBackground(source) }; showsBackgroundPicker = false },
                onLocked = { showsBackgroundPicker = false; navigator.push(Route.Pro) },
                onDismiss = { showsBackgroundPicker = false },
            )
        }
        if (showsColorPicker) {
            AccentPickerDialog(
                Accent(settings.accentHex),
                onChange = { scope.launch { repo.set(SettingsKeys.accent, it.hex) } },
                onDismiss = { showsColorPicker = false },
            )
        }
        if (showsTour) OnboardingScreen(isReplay = true) { showsTour = false }
    }
}

@Composable
private fun SettingsPage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = MetroMetrics.margin + 12.dp, end = MetroMetrics.margin + 12.dp, top = 24.dp, bottom = 40.dp),
        content = content,
    )
}

@Composable
private fun ColumnScope.GeneralPage(s: AppSettings, set: (Preferences.Key<Boolean>, Boolean) -> Unit) {
    val container = LocalContext.current.container
    val scope = rememberCoroutineScope()
    ToggleRow(
        "motion effect", "Tiles and the background shift as you tilt the phone. Off with Reduce Motion.",
        s.movesWithPhone, { set(SettingsKeys.movesWithPhone, it) }, Modifier.metroFeather(row = 1),
    )
    if (s.movesWithPhone) {
        Segments(
            "movement", TiltStrength.entries, s.tiltStrength, { it.raw },
            { scope.launch { container.settings.setTiltStrength(it) } },
            Modifier.metroFeather(row = 2).padding(top = 14.dp),
        )
    }
    ToggleRow(
        "live tiles",
        "Photo tiles flip to a new picture every few seconds. Off, they change each time you come back to the hub.",
        s.animatesLiveTiles, { set(SettingsKeys.animatesLiveTiles, it) }, Modifier.metroFeather(row = 3).padding(top = 32.dp),
    )
    ToggleRow(
        "tile patterns", "The albums and date tiles move slowly.",
        s.animatesTilePatterns, { set(SettingsKeys.animatesTilePatterns, it) }, Modifier.metroFeather(row = 4).padding(top = 32.dp),
    )
    // No "standby" row (Android has no StandBy) and no "live photos" row (v1 drops Live Photos): both would do nothing.
    ToggleRow(
        "place names",
        "Titles journal trips like \"trip to goa\" and names places using your phone's map service. Only locations are sent, never photos.",
        s.namesJournalPlaces, { set(SettingsKeys.namesJournalPlaces, it) }, Modifier.metroFeather(row = 7).padding(top = 32.dp),
    )
}

@Composable
private fun ColumnScope.LookPage(
    s: AppSettings,
    unlocksPro: Boolean,
    onTheme: (StoredTheme) -> Unit,
    onAccent: (Accent) -> Unit,
    onCustomAccent: () -> Unit,
    onBackground: (Boolean) -> Unit,
    onBackgroundSource: () -> Unit,
) {
    val metro = LocalMetro.current
    val container = LocalContext.current.container
    val accent = Accent(s.accentHex)
    MetroText("theme", MetroType.body, Modifier.metroFeather(row = 1).padding(bottom = 10.dp))
    Segments(
        "theme", StoredTheme.entries, s.theme, { it.name.lowercase() }, onTheme,
        Modifier.metroFeather(row = 1).padding(bottom = 24.dp),
    )

    Row(Modifier.fillMaxWidth().metroFeather(row = 2).padding(bottom = 10.dp)) {
        MetroText("accent colour", MetroType.body, Modifier.alignByBaseline())
        Spacer(Modifier.weight(1f))
        MetroText(accent.name, MetroType.caption, Modifier.alignByBaseline(), color = metro.secondary)
    }
    // The six presets and the custom colour fit one row.
    Row(Modifier.fillMaxWidth().padding(bottom = 28.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Accent.presets.forEachIndexed { index, preset ->
            val selected = preset == accent
            Box(
                Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .metroFeather(row = 3, column = index)
                    .tiltClickable { onAccent(preset) }
                    .background(preset.color)
                    .then(if (selected) Modifier.border(3.dp, metro.foreground) else Modifier)
                    .semantics { contentDescription = preset.name; this.selected = selected },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Image(MetroIcons.check, null, Modifier.size(16.dp), colorFilter = ColorFilter.tint(Color.White))
            }
        }
        val isCustom = !accent.isPreset
        Box(
            Modifier
                .weight(1f)
                .aspectRatio(1f)
                .metroFeather(row = 3, column = Accent.presets.size)
                .tiltClickable(onClick = onCustomAccent)
                .background(if (isCustom) accent.color else metro.chrome)
                .then(if (isCustom) Modifier.border(3.dp, metro.foreground) else Modifier)
                .semantics {
                    contentDescription = if (unlocksPro) "Custom colour" else "Custom colour, needs Luminux Pro"
                    selected = isCustom
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                if (unlocksPro) MetroIcons.eyedropper else MetroIcons.lock, null,
                Modifier.size(if (unlocksPro) 18.dp else 14.dp),
                colorFilter = ColorFilter.tint(if (isCustom) Color.White else metro.foreground),
            )
        }
    }

    ToggleRow(
        "background photo", "A photo behind the hub, picked each time Luminux opens.",
        s.showsHubBackground, onBackground, Modifier.metroFeather(row = 4),
    )
    if (s.showsHubBackground) {
        val source = effectiveBackground(s, unlocksPro)
        LinkRow(
            "background from", backgroundName(source, album = { container.photos.album(it)?.title }, photoSet = { null }),
            Modifier.metroFeather(row = 5).padding(top = 20.dp), onClick = onBackgroundSource,
        )
    }
}

@Composable
private fun ColumnScope.AboutPage(isProUnlocked: Boolean, onPro: () -> Unit, onTour: () -> Unit, onAbout: () -> Unit) {
    ProCard(isProUnlocked, onPro, Modifier.metroFeather(row = 1).padding(bottom = 28.dp))
    Column(Modifier.metroFeather(row = 2), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        // shortcut: "ad privacy choices" joins with spec 09's AdConsent (needsPrivacyOptions && !isUnlocked).
        LinkRow("take the tour", "What Luminux can do, page by page", onClick = onTour)
        LinkRow("about and licences", "Version, fonts and their licences", onClick = onAbout)
    }
}

/** An accent block that opens the Pro page, or a quiet thank-you once it's bought. */
@Composable
private fun ProCard(isProUnlocked: Boolean, onPro: () -> Unit, modifier: Modifier) {
    val metro = LocalMetro.current
    if (isProUnlocked) {
        Row(
            modifier.fillMaxWidth().background(metro.chrome).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(48.dp).background(metro.accentColor), contentAlignment = Alignment.Center) {
                Image(MetroIcons.check, null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(Color.White))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MetroText("pro unlocked", metroFont(20.sp, Semilight))
                MetroText("Thank you for supporting Luminux.", MetroType.caption, color = metro.secondary)
            }
        }
    } else {
        Column(
            modifier
                .fillMaxWidth()
                .tiltClickable(onClick = onPro)
                .background(metro.accentColor)
                .padding(20.dp)
                .semantics { contentDescription = "Luminux Pro" },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetroText("LUMINUX PRO", MetroType.overline, color = Color.White)
                Spacer(Modifier.weight(1f))
                Image(MetroIcons.arrowRight, null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(Color.White))
            }
            MetroText("no ads, more of you", MetroType.section, Modifier.padding(top = 14.dp), color = Color.White)
            MetroText(
                "Any accent colour, wide live tiles, unlimited pins, slideshow and editing.", MetroType.caption,
                Modifier.padding(top = 4.dp), color = Color.White.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(16.dp))
            // shortcut: spec 09 shows "<price>, once. No subscription." once Play Billing has the product.
            MetroText("One payment. No subscription.", metroFont(15.sp, FontWeight.SemiBold), color = Color.White)
        }
    }
}
