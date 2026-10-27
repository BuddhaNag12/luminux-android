package com.buddhanag.luminux.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.format.DateFormat
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.scale
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
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
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
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
            // Shared by the three sizes, so each photo is decoded and sent to the launcher once per update.
            val bitmaps = remember(state) { HashMap<File, Bitmap?>() }
            Tile(
                TileConfig.from(state, collections), state, container.liveTileStore, LocalSize.current.kind(),
                settings.accentHex, isUnlocked || container.proStore.isTestBuild,
            ) { file -> bitmaps.getOrPut(file) { decodePhoto(file, small = TileConfig.style(state) == TileStyle.Mosaic) } }
        }
    }
}

private val photoBackground = Color(0xFF0B0B0B)
private val white = ColorProvider(Color.White)
private val gutter = 3.dp

@Composable
private fun Tile(
    config: TileConfig, state: Preferences, store: LiveTileStore, kind: TileKind, accentHex: Int, isPro: Boolean,
    photo: (File) -> Bitmap?,
) {
    val context = LocalContext.current
    val folder = config.folder
    val pool = folder?.let(store::imageFiles).orEmpty()
    val face = face(kind, isPro, config.needsCollection, pool.size)
    val link = if (face == Face.Locked) "luminux://pro" else config.link
    val open = Intent(Intent.ACTION_VIEW, link.toUri(), context, MainActivity::class.java)
    var peeking = false

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
                val names = pool.map { it.name }
                val slots = slots(kind, config.style)
                // The step's photos, unless an export has since replaced them; then the first ones until the next reset.
                val step = currentStep(state, slots)?.takeIf { names.containsAll(it.slots) }
                    ?: TileShuffle.Step(List(slots) { names[it % names.size] }, null, TileShuffle.Side.Bottom, emptyList())
                peeking = step.peek
                val collage = @Composable { Collage(kind, config.style, step, names) { photo(File(store.folder(folder!!), it)) } }
                if (peeking) Peek(config.title, kind, accentHex, collage) else collage()
            }
        }
        // The peek panel shows the title itself.
        if (face != Face.Locked && !peeking) Label(config.title)
        // Not on the small tile: there a tap anywhere opens the app.
        if (face == Face.Photos && kind != TileKind.Small) ShuffleButton()
    }
}

private fun currentStep(state: Preferences, slots: Int): TileShuffle.Step<String>? {
    val steps = state[TileState.steps(slots)]?.let { runCatching { Json.decodeFromString<List<TileShuffle.Step<String>>>(it) }.getOrNull() }
    return steps?.getOrNull(state[TileState.stepIndex] ?: 0)
}

/** Each slot changes on its own clock, a little apart, so the photos take turns like the iOS tile's. */
private fun flipInterval(slot: Int) = 5000 + 700 * slot
private val flipperLayouts = mapOf(
    TileShuffle.Side.Top to R.layout.tile_flipper_top,
    TileShuffle.Side.Bottom to R.layout.tile_flipper_bottom,
    TileShuffle.Side.Leading to R.layout.tile_flipper_leading,
    TileShuffle.Side.Trailing to R.layout.tile_flipper_trailing,
)

/**
 * One layout per size, or a uniform grid for the mosaic. Every slot is a ViewFlipper the launcher runs by itself
 * (Glance can't animate), cycling the photos [slotSequences] deals it.
 */
@Composable
private fun Collage(kind: TileKind, style: TileStyle, step: TileShuffle.Step<String>, pool: List<String>, photo: (String) -> Bitmap?) {
    val isMosaic = style == TileStyle.Mosaic
    // The small tile shows only the slot that changed last, unless it's a mosaic.
    val shown = if (kind == TileKind.Small && !isMosaic) listOf(step.slots[step.changed ?: 0]) else step.slots
    // Seeded by the widget's own step, so tiles showing the same pool don't flip to the same photo together.
    val sequences = slotSequences(shown, pool, seed = step.slots.hashCode())
    @Composable fun slot(index: Int, modifier: GlanceModifier) {
        val side = TileShuffle.Side.entries[(step.side.ordinal + index) % TileShuffle.Side.entries.size]
        Slot(sequences[index].mapNotNull(photo), flipInterval(index), if (style == TileStyle.Flip) null else side, modifier)
    }
    if (isMosaic) {
        val (columns, rows) = mosaic(kind)
        Column(GlanceModifier.fillMaxSize()) {
            repeat(rows) { row ->
                if (row > 0) Spacer(GlanceModifier.height(gutter))
                Row(GlanceModifier.defaultWeight().fillMaxWidth()) {
                    repeat(columns) { column ->
                        if (column > 0) Spacer(GlanceModifier.width(gutter))
                        slot(row * columns + column, GlanceModifier.defaultWeight().fillMaxHeight())
                    }
                }
            }
        }
        return
    }
    when (kind) {
        TileKind.Small -> slot(0, GlanceModifier.fillMaxSize())
        TileKind.Medium -> Row(GlanceModifier.fillMaxSize()) {
            slot(0, GlanceModifier.defaultWeight().fillMaxHeight())
            Spacer(GlanceModifier.width(gutter))
            Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
                slot(1, GlanceModifier.defaultWeight().fillMaxWidth())
                Spacer(GlanceModifier.height(gutter))
                slot(2, GlanceModifier.defaultWeight().fillMaxWidth())
            }
        }
        TileKind.Large -> Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.defaultWeight().fillMaxWidth()) {
                slot(0, GlanceModifier.defaultWeight().fillMaxHeight())
                Spacer(GlanceModifier.width(gutter))
                slot(1, GlanceModifier.defaultWeight().fillMaxHeight())
            }
            Spacer(GlanceModifier.height(gutter))
            Row(GlanceModifier.defaultWeight().fillMaxWidth()) {
                slot(2, GlanceModifier.defaultWeight().fillMaxHeight())
                Spacer(GlanceModifier.width(gutter))
                slot(3, GlanceModifier.defaultWeight().fillMaxHeight())
            }
        }
    }
}

/** [side] is where new photos slide in from; null flips them instead. */
@Composable
private fun Slot(photos: List<Bitmap>, interval: Int, side: TileShuffle.Side?, modifier: GlanceModifier) {
    // One photo stays put: a flipper with one child would slide the same photo in again every few seconds.
    if (photos.size <= 1) {
        photos.firstOrNull()?.let { Image(ImageProvider(it), null, modifier, contentScale = ContentScale.Crop) } ?: Spacer(modifier)
        return
    }
    val pkg = LocalContext.current.packageName
    val flipper = RemoteViews(pkg, side?.let(flipperLayouts::getValue) ?: R.layout.tile_flipper_flip).apply {
        photos.forEach { addView(R.id.tile_flipper, RemoteViews(pkg, R.layout.tile_photo).apply { setImageViewBitmap(R.id.tile_photo, it) }) }
        setInt(R.id.tile_flipper, "setFlipInterval", interval)
    }
    AndroidRemoteViews(flipper, modifier)
}

/**
 * The 600 px exports at 300 px. The launcher's limit is screen pixels × 4 × 1.5 bytes (≈ 15.7 MB here), and each
 * Responsive size carries its own copies, so a pool of 12 can cost 3 × 12 × ~470 KB. The mosaic shows all 12 in every
 * size and its cells are a third of the tile, so it uses [small] photos (200 px, ≈ 7.5 MB in all; 300 px overflowed).
 */
private fun decodePhoto(file: File, small: Boolean): Bitmap? {
    val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = 2 }) ?: return null
    return if (small) bitmap.scale(bitmap.width * 2 / 3, bitmap.height * 2 / 3) else bitmap
}

/** RemoteViews can't load the bundled Selawik, so the system's light sans stands in. */
private fun metro(alpha: Float = 1f, size: Int = 15) =
    TextStyle(color = ColorProvider(Color.White.copy(alpha = alpha)), fontSize = size.sp, fontFamily = FontFamily("sans-serif-light"))

@Composable
private fun Label(title: String) {
    Text(title, GlanceModifier.padding(12.dp), metro(), maxLines = 1)
}

/**
 * A peek step (Windows Phone's peek tile): the accent panel under the photos with the title and today's date. iOS
 * slides the photos up by 55 % and back; a widget can't animate an update, so the step draws this state.
 * shortcut: photos and panel split the tile 50/50 (Glance weights are all equal, and Glance children inside an
 * AndroidRemoteViews container lost the collage); go to 45/55 if Glance gains weight ratios.
 */
@Composable
private fun Peek(title: String, kind: TileKind, accentHex: Int, photos: @Composable () -> Unit) {
    val locale = LocalContext.current.resources.configuration.locales[0]
    val date = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale).format(LocalDate.now())
    Column(GlanceModifier.fillMaxSize()) {
        Box(GlanceModifier.defaultWeight().fillMaxWidth()) { photos() }
        Column(GlanceModifier.defaultWeight().fillMaxWidth().background(Color(0xFF000000.toInt() or accentHex)).padding(12.dp)) {
            Text(title, style = metro(size = if (kind == TileKind.Small) 22 else 28), maxLines = 1)
            Spacer(GlanceModifier.height(2.dp))
            Text(date.lowercase(locale), style = metro(0.8f, size = 13), maxLines = 1)
        }
    }
}

/** Deals a fresh shuffle for this tile (ShuffleTileIntent). */
@Composable
private fun ShuffleButton() {
    Box(GlanceModifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.TopEnd) {
        Box(
            GlanceModifier.size(30.dp).cornerRadius(15.dp).background(Color.Black.copy(alpha = 0.45f))
                .clickable(actionRunCallback<ShuffleCallback>())
                .semantics { contentDescription = "Shuffle photos" },
            contentAlignment = Alignment.Center,
        ) {
            Image(ImageProvider(R.drawable.ic_widget_shuffle), null, GlanceModifier.size(14.dp), colorFilter = ColorFilter.tint(white))
        }
    }
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
