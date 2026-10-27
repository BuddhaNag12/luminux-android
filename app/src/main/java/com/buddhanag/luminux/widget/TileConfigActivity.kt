package com.buddhanag.luminux.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buddhanag.luminux.container
import com.buddhanag.luminux.designsystem.Accent
import com.buddhanag.luminux.designsystem.LocalMetro
import com.buddhanag.luminux.designsystem.MetroButton
import com.buddhanag.luminux.designsystem.MetroText
import com.buddhanag.luminux.designsystem.MetroTheme
import com.buddhanag.luminux.designsystem.MetroType
import com.buddhanag.luminux.designsystem.Semilight
import com.buddhanag.luminux.designsystem.metroFont
import kotlinx.coroutines.launch

/** The widget's "Edit Widget" (TileConfigurationIntent): recent, favorites, or one of the collections made a widget. */
class TileConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        // Backing out leaves the widget as it was (or, when adding, not added).
        setResult(RESULT_CANCELED, result)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return finish()
        // White bar icons: the screen is always the accent, whatever the app theme.
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            MetroTheme(accent = Accent(settings?.accentHex ?: Accent.Cobalt.hex)) {
                ConfigScreen(appWidgetId) {
                    setResult(RESULT_OK, result)
                    finish()
                }
            }
        }
    }
}

@Composable
private fun ConfigScreen(appWidgetId: Int, onSaved: () -> Unit) {
    val context = LocalContext.current
    val collections by context.container.liveTileStore.collections.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf(TileSource.Recent) }
    var collectionId by remember { mutableStateOf<String?>(null) }
    var style by remember { mutableStateOf(TileStyle.Shuffle) }
    LaunchedEffect(appWidgetId) {
        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        val state = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        source = TileConfig.source(state)
        collectionId = state[TileState.collectionId]
        style = TileConfig.style(state)
    }

    Column(
        Modifier.fillMaxSize()
            .background(LocalMetro.current.accentColor)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        MetroText("LUMINUX", MetroType.overline, color = Color.White)
        MetroText("photos tile", MetroType.title, Modifier.padding(bottom = 16.dp), color = Color.White)
        MetroText("show", MetroType.caption, color = Color.White.copy(alpha = 0.7f))
        TileSource.entries.forEach { option -> Choice(option.label, source == option) { source = option } }

        if (source == TileSource.Collection) {
            MetroText("collection", MetroType.caption, Modifier.padding(top = 20.dp), color = Color.White.copy(alpha = 0.7f))
            if (collections.isEmpty()) {
                MetroText(
                    "None yet. In Luminux, open an album or photo set and choose \"make a widget\" from its menu.",
                    MetroType.body, Modifier.padding(top = 8.dp), color = Color.White,
                )
            }
            collections.forEach { c -> Choice(c.title.lowercase(), collectionId == c.id) { collectionId = c.id } }
        }

        MetroText("style", MetroType.caption, Modifier.padding(top = 20.dp), color = Color.White.copy(alpha = 0.7f))
        TileStyle.entries.forEach { option -> Choice(option.name.lowercase(), style == option) { style = option } }

        MetroButton("save", {
            scope.launch {
                val id = collectionId.takeIf { source == TileSource.Collection }
                WidgetUpdater.configure(context, appWidgetId, source, id, style)
                onSaved()
            }
        }, Modifier.padding(top = 32.dp))
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    MetroText(
        label, metroFont(28.sp, Semilight),
        Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 8.dp),
        color = if (selected) Color.White else Color.White.copy(alpha = 0.55f),
    )
}
