package com.buddhanag.luminux.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddhanag.luminux.BuildConfig
import com.buddhanag.luminux.container
import com.buddhanag.luminux.designsystem.LocalMetro
import com.buddhanag.luminux.designsystem.LogoLockup
import com.buddhanag.luminux.designsystem.MetroMetrics
import com.buddhanag.luminux.designsystem.MetroText
import com.buddhanag.luminux.designsystem.MetroType
import com.buddhanag.luminux.designsystem.Semilight
import com.buddhanag.luminux.designsystem.metroFeather
import com.buddhanag.luminux.designsystem.metroFont

/** The file is hard-wrapped at 70 columns; rejoin each paragraph so it wraps to the screen, and drop the rules. */
fun reflowLicense(text: String): String = text.replace("\r\n", "\n")
    .split("\n\n")
    // An empty line also counts as "all dashes", which drops it like Swift's split does.
    .map { paragraph -> paragraph.split("\n").filterNot { line -> line.all { it == '-' } }.joinToString(" ") }
    .filter { it.isNotEmpty() }
    .joinToString("\n\n")

/** The app version and the licence the bundled Selawik font ships under (the OFL asks for its text to go with it). */
@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val metro = LocalMetro.current
    val license = remember {
        runCatching { context.assets.open("licenses/Selawik-OFL.txt").bufferedReader().use { it.readText() } }
            .map(::reflowLicense).getOrDefault("")
    }
    val version = "version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

    Column(
        Modifier
            .fillMaxSize()
            .background(metro.background)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(start = MetroMetrics.margin + 12.dp, end = MetroMetrics.margin + 12.dp, top = 16.dp, bottom = 40.dp),
    ) {
        PageHeader("about", Modifier.metroFeather(row = 0).padding(bottom = 24.dp))
        LogoLockup(metro.background, Modifier.padding(bottom = 24.dp), unit = 0.8f)
        MetroText(
            if (context.container.proStore.isTestBuild) "$version · test build, custom colours unlocked" else version,
            MetroType.body, Modifier.metroFeather(row = 1).padding(bottom = 28.dp), color = metro.secondary,
        )
        MetroText("Selawik font", metroFont(20.sp, Semilight), Modifier.metroFeather(row = 2).padding(bottom = 10.dp), color = metro.secondary)
        SelectionContainer(Modifier.metroFeather(row = 3)) {
            MetroText(license, MetroType.caption, color = metro.secondary)
        }
    }
}
