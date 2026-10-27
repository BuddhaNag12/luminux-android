package com.buddhanag.luminux.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontFamily
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.buddhanag.luminux.MainActivity
import com.buddhanag.luminux.R
import com.buddhanag.luminux.container
import com.buddhanag.luminux.data.LiveTileStore
import com.buddhanag.luminux.designsystem.TileShuffle
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.io.File

/** The Photos live tile (PhotosLiveTile): recent photos, favorites or a collection, one photo changing per step. */
class LuminuxWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = context.container
        val initialSettings = container.settings.settings.first()
        val initialCollections = container.liveTileStore.collections.first()
        provideContent {
            // Read in the content: a session that's still running recomposes on update() without calling provideGlance.
            val settings by container.settings.settings.collectAsState(initialSettings)
            val collections by container.liveTileStore.collections.collectAsState(initialCollections)
            val isUnlocked by container.proStore.isUnlocked.collectAsState()
            val state = currentState<Preferences>()
            Tile(
                TileConfig.from(state, collections), state, container.liveTileStore, LocalSize.current.kind(),
                settings.accentHex, isUnlocked || container.proStore.isTestBuild,
            )
        }
    }
}

private val photoBackground = Color(0xFF0B0B0B)
private val white = ColorProvider(Color.White)
private val gutter = 3.dp

@Composable
private fun Tile(config: TileConfig, state: Preferences, store: LiveTileStore, kind: TileKind, accentHex: Int, isPro: Boolean) {
    val context = LocalContext.current
    val folder = config.folder
    val pool = folder?.let(store::imageFiles).orEmpty()
    val face = face(kind, isPro, config.needsCollection, pool.size)
    val link = if (face == Face.Locked) "luminux://pro" else config.link
    val open = Intent(Intent.ACTION_VIEW, link.toUri(), context, MainActivity::class.java)

    Box(
        GlanceModifier.fillMaxSize()
            .background(if (face == Face.Photos) photoBackground else Color(0xFF000000.toInt() or accentHex))
            .appWidgetBackground()
            .cornerRadius(android.R.dimen.system_app_widget_background_radius)
            .clickable(actionStartActivity(open)),
        contentAlignment = Alignment.BottomStart,
    ) {
        when (face) {
            Face.Locked -> LockedFace()
            Face.NeedsCollection -> Note("Touch and hold the tile, then tap Settings to choose a collection.")
            Face.Empty -> Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Image(ImageProvider(R.drawable.ic_widget_photos), null, GlanceModifier.size(40.dp), colorFilter = ColorFilter.tint(white))
            }
            Face.Photos -> {
                val step = currentStep(state, slots(kind))
                // The step's files, unless an export has since replaced them; then the first photos until the next reset.
                val shown = step?.slots?.map { File(store.folder(folder!!), it) }?.takeIf { it.all(File::exists) }
                    ?: List(slots(kind)) { pool[it % pool.size] }
                Collage(kind, shown, step?.changed)
            }
        }
        if (face != Face.Locked) Label(config.title)
    }
}

private fun currentStep(state: Preferences, slots: Int): TileShuffle.Step<String>? {
    val steps = state[TileState.steps(slots)]?.let { runCatching { Json.decodeFromString<List<TileShuffle.Step<String>>>(it) }.getOrNull() }
    return steps?.getOrNull(state[TileState.stepIndex] ?: 0)
}

/**
 * One layout per size; a per-widget style (spec 12 E) would add its own collage here.
 * shortcut: no slide-in or fade, Glance draws static RemoteViews; a ViewFlipper could animate the small tile later.
 */
@Composable
private fun Collage(kind: TileKind, photos: List<File>, changed: Int?) {
    // Small and medium slots get the 600 px exports at half size; the 2×2 grid keeps them whole where the
    // RemoteViews bitmap budget (screen pixels × 4 × 1.5 bytes) has room for four of them.
    val metrics = LocalContext.current.resources.displayMetrics
    val sample = if (kind == TileKind.Large && metrics.widthPixels * metrics.heightPixels * 6L > 12_000_000) 1 else 2
    when (kind) {
        TileKind.Small -> Photo(photos[changed ?: 0], sample, GlanceModifier.fillMaxSize())
        TileKind.Medium -> Row(GlanceModifier.fillMaxSize()) {
            Photo(photos[0], sample, GlanceModifier.defaultWeight().fillMaxHeight())
            Spacer(GlanceModifier.width(gutter))
            Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
                Photo(photos[1], sample, GlanceModifier.defaultWeight().fillMaxWidth())
                Spacer(GlanceModifier.height(gutter))
                Photo(photos[2], sample, GlanceModifier.defaultWeight().fillMaxWidth())
            }
        }
        TileKind.Large -> Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.defaultWeight().fillMaxWidth()) {
                Photo(photos[0], sample, GlanceModifier.defaultWeight().fillMaxHeight())
                Spacer(GlanceModifier.width(gutter))
                Photo(photos[1], sample, GlanceModifier.defaultWeight().fillMaxHeight())
            }
            Spacer(GlanceModifier.height(gutter))
            Row(GlanceModifier.defaultWeight().fillMaxWidth()) {
                Photo(photos[2], sample, GlanceModifier.defaultWeight().fillMaxHeight())
                Spacer(GlanceModifier.width(gutter))
                Photo(photos[3], sample, GlanceModifier.defaultWeight().fillMaxHeight())
            }
        }
    }
}

@Composable
private fun Photo(file: File, sample: Int, modifier: GlanceModifier) {
    val bitmap: Bitmap? = remember(file, sample) {
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
    if (bitmap == null) Spacer(modifier) else Image(ImageProvider(bitmap), null, modifier, contentScale = ContentScale.Crop)
}

/** RemoteViews can't load the bundled Selawik, so the system's light sans stands in. */
private fun metro(alpha: Float = 1f) =
    TextStyle(color = ColorProvider(Color.White.copy(alpha = alpha)), fontSize = 15.sp, fontFamily = FontFamily("sans-serif-light"))

@Composable
private fun Label(title: String) {
    Text(title, GlanceModifier.padding(12.dp), metro(), maxLines = 1)
}

@Composable
private fun Note(text: String) {
    Box(GlanceModifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.TopStart) { Text(text, style = metro()) }
}

@Composable
private fun LockedFace() {
    Column(GlanceModifier.fillMaxSize().padding(12.dp)) {
        Image(ImageProvider(R.drawable.ic_widget_lock), null, GlanceModifier.size(22.dp), colorFilter = ColorFilter.tint(white))
        Spacer(GlanceModifier.defaultWeight())
        Text("Wide tiles come with Luminux Pro. Tap to see what it adds.", style = metro())
        Spacer(GlanceModifier.height(6.dp))
        Text("photos", style = metro(0.7f))
    }
}
