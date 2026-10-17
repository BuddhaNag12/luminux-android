package com.buddhanag.luminux.feature.journal

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.buddhanag.luminux.container
import com.buddhanag.luminux.data.AssetSource
import com.buddhanag.luminux.data.MediaItem
import com.buddhanag.luminux.data.MediaType
import com.buddhanag.luminux.data.Thumbnails
import com.buddhanag.luminux.data.journal.JournalEntry
import com.buddhanag.luminux.data.journal.JournalState
import com.buddhanag.luminux.data.journal.JournalTitles
import com.buddhanag.luminux.designsystem.LocalMetro
import com.buddhanag.luminux.designsystem.MetroIcons
import com.buddhanag.luminux.designsystem.MetroMetrics
import com.buddhanag.luminux.designsystem.MetroText
import com.buddhanag.luminux.designsystem.MetroType
import com.buddhanag.luminux.designsystem.Semilight
import com.buddhanag.luminux.designsystem.metroFont
import com.buddhanag.luminux.designsystem.tiltPress
import com.buddhanag.luminux.feature.shell.Route
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** One journal entry: the mosaic, title, dates and note. Opens the entry; the hub's journal panel reuses it. */
@Composable
fun JournalEntryTile(entry: JournalEntry, journal: JournalState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val metro = LocalMetro.current
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .fillMaxWidth()
            .tiltPress(interaction, trackTouch = false)
            .clickable(interaction, indication = null, onClickLabel = "Opens this journal entry") {
                context.container.navigator.push(Route.JournalEntry(entry.id))
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        JournalMosaic(journal.keyPhotos(entry, 3), Modifier.fillMaxWidth().aspectRatio(16f / 10f).padding(bottom = 6.dp))
        MetroText(journal.title(entry), metroFont(22.sp, Semilight), maxLines = 2)
        MetroText(subtitle(context, entry), MetroType.caption, color = metro.foreground.copy(alpha = 0.7f))
        journal.note(entry)?.let { MetroText(it, MetroType.body, Modifier.padding(top = 2.dp), maxLines = 2) }
    }
}

/** Shown while the journal is built, and when there's nothing to put in it. */
@Composable
fun JournalPlaceholder(isBuilt: Boolean, modifier: Modifier = Modifier) {
    MetroText(
        if (isBuilt) "Outings, days and trips show up here on their own as you take photos." else "Putting your journal together…",
        MetroType.body, modifier, color = LocalMetro.current.secondary,
    )
}

/** "saturday, october 3, 2026 · 12 photos", or a date range for entries that span days. */
internal fun subtitle(context: Context, e: JournalEntry): String {
    val zone = ZoneId.systemDefault()
    val dates = if (e.start.atZone(zone).toLocalDate() == e.end.atZone(zone).toLocalDate()) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).format(e.start.atZone(zone))
    } else {
        DateUtils.formatDateRange(context, e.start.toEpochMilli(), e.end.toEpochMilli(), DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR)
    }
    return "${dates.lowercase()} · ${JournalTitles.photoCount(e.photoIds.size)}"
}

/** An entry's key photos: one large, two stacked beside it when there are three (JournalViews.swift:12-17). */
@Composable
private fun JournalMosaic(ids: List<Long>, modifier: Modifier) {
    val snapshot by LocalContext.current.container.photos.snapshot.collectAsStateWithLifecycle()
    val items = ids.mapNotNull { snapshot.byId[it] }
    BoxWithConstraints(modifier.clearAndSetSemantics {}) {
        val gutter = MetroMetrics.gutter
        val showsSide = items.size >= 3
        val sideWidth = if (showsSide) (maxWidth - gutter) / 3 else 0.dp
        val coverWidth = if (showsSide) maxWidth - gutter - sideWidth else maxWidth
        Row(horizontalArrangement = Arrangement.spacedBy(gutter)) {
            val height = this@BoxWithConstraints.maxHeight
            items.firstOrNull()?.let { Thumb(it, coverWidth, height) }
            if (showsSide) {
                val side = (height - gutter) / 2
                Column(verticalArrangement = Arrangement.spacedBy(gutter)) {
                    items.subList(1, 3).forEach { Thumb(it, sideWidth, side) }
                }
            }
        }
    }
}

/** A scaled-to-fill, clipped thumbnail on a chrome backdrop. */
@Composable
internal fun Thumb(item: MediaItem?, width: Dp, height: Dp = width) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { max(width, height).roundToPx() }
    Box(Modifier.size(width, height).background(LocalMetro.current.chrome)) {
        if (item != null) {
            AsyncImage(remember(item, px) { Thumbnails.gridRequest(context, item, px) }, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

/**
 * The photos of an entry or place, oldest first. Tapping opens the viewer; with [selected] it picks photos instead.
 * shortcut: swap for 06's AssetGrid at merge (no zoom, jump list, drag-select or zoom into the viewer).
 */
@Composable
internal fun PhotoGrid(ids: List<Long>, empty: String, contentPadding: PaddingValues, selected: SnapshotStateList<Long>?, modifier: Modifier = Modifier) {
    val container = LocalContext.current.container
    val metro = LocalMetro.current
    val snapshot by container.photos.snapshot.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val source = remember(ids) { AssetSource.Identifiers(ids) }
    val items = remember(snapshot, source) { snapshot.items(source) }
    if (items.isEmpty()) {
        MetroText(empty, MetroType.body, modifier.padding(horizontal = MetroMetrics.margin + 4.dp), color = metro.secondary)
        return
    }
    BoxWithConstraints(modifier) {
        val columns = settings?.gridColumns ?: return@BoxWithConstraints
        val gap = 4.dp
        val cell = (maxWidth - gap * (columns - 1)) / columns
        LazyVerticalGrid(
            GridCells.Fixed(columns), Modifier.fillMaxSize(), contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                val isSelected = selected?.contains(item.id) == true
                Box(
                    Modifier
                        .clickable {
                            when {
                                selected == null -> container.navigator.openViewer(source, item.id, index)
                                isSelected -> selected.remove(item.id)
                                else -> selected.add(item.id)
                            }
                        }
                        .semantics {
                            contentDescription = if (item.type == MediaType.VIDEO) "Video" else "Photo"
                            if (selected != null) this.selected = isSelected
                        },
                ) {
                    Thumb(item, cell)
                    if (isSelected) {
                        Box(Modifier.matchParentSize().background(metro.accentColor.copy(alpha = 0.35f)))
                        Image(
                            MetroIcons.check, null,
                            Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp).background(metro.accentColor),
                            colorFilter = ColorFilter.tint(Color.White),
                        )
                    }
                }
            }
        }
    }
}

/** Line stand-ins for the SF Symbols these pages use, on the 24 grid of [MetroIcons]. */
internal object JournalIcons {
    val pencil = icon("pencil") { moveTo(4f, 20f); lineTo(5f, 15f); lineTo(16f, 4f); lineTo(20f, 8f); lineTo(9f, 19f); close(); moveTo(14f, 6f); lineTo(18f, 10f) }
    val note = icon("square.and.pencil") {
        moveTo(12f, 4f); lineTo(4f, 4f); lineTo(4f, 20f); lineTo(20f, 20f); lineTo(20f, 12f)
        moveTo(10f, 14f); lineTo(10.5f, 11f); lineTo(18f, 3.5f); lineTo(20.5f, 6f); lineTo(13f, 13.5f); close()
    }
    val checklist = icon("checklist") {
        for (y in listOf(6f, 12f, 18f)) {
            moveTo(3f, y); lineTo(4.5f, y + 1.5f); lineTo(7.5f, y - 1.5f)
            moveTo(10f, y); lineTo(21f, y)
        }
    }
    val share = icon("square.and.arrow.up") {
        moveTo(12f, 3f); lineTo(12f, 15f); moveTo(8f, 7f); lineTo(12f, 3f); lineTo(16f, 7f)
        moveTo(9f, 10f); lineTo(5f, 10f); lineTo(5f, 21f); lineTo(19f, 21f); lineTo(19f, 10f); lineTo(15f, 10f)
    }
    val heart = icon("heart") {
        moveTo(12f, 20f); lineTo(4.5f, 12.5f); arcTo(4.2f, 4.2f, 0f, false, true, 12f, 6.5f)
        arcTo(4.2f, 4.2f, 0f, false, true, 19.5f, 12.5f); close()
    }
    val trash = icon("trash") {
        moveTo(4f, 6f); lineTo(20f, 6f); moveTo(9f, 6f); lineTo(9f, 3f); lineTo(15f, 3f); lineTo(15f, 6f)
        moveTo(6f, 6f); lineTo(7f, 21f); lineTo(17f, 21f); lineTo(18f, 6f)
    }

    private fun icon(name: String, block: PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .path(stroke = SolidColor(Color.White), strokeLineWidth = 1.2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block)
            .build()
}
