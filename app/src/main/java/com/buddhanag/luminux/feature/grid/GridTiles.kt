package com.buddhanag.luminux.feature.grid

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.buddhanag.luminux.data.MediaItem
import com.buddhanag.luminux.data.MediaType
import com.buddhanag.luminux.data.Thumbnails
import com.buddhanag.luminux.designsystem.LocalMetro
import com.buddhanag.luminux.designsystem.MetroIcons
import com.buddhanag.luminux.designsystem.MetroText
import com.buddhanag.luminux.designsystem.metroFont
import java.util.Locale
import kotlin.math.max

/** The select-mode check box: filled ✓ when selected, filled − when part of a group is, empty otherwise. */
@Composable
fun SelectionBox(isSelected: Boolean, modifier: Modifier = Modifier, isMixed: Boolean = false) {
    val metro = LocalMetro.current
    val filled = isSelected || isMixed
    Box(
        modifier
            .size(24.dp)
            .background(if (filled) metro.accentColor else Color.Black.copy(alpha = 0.25f))
            .border(2.dp, if (filled) metro.accentColor else Color.White),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Image(MetroIcons.check, null, Modifier.size(15.dp), colorFilter = ColorFilter.tint(Color.White))
        } else if (isMixed) {
            Box(Modifier.size(10.dp, 2.dp).background(Color.White))
        }
    }
}

private class LoadedSide(var px: Int = 0)

/**
 * A square-cropped thumbnail over the chrome colour, with a video's length in the corner. Requests come in buckets so
 * neighbouring zoom levels share one image, and zooming out never swaps a sharper image for a smaller one.
 *
 * @param showsBadge off on tiles too small for the video badge (the 15-column overview), where it would cover the photo.
 */
@Composable
fun AssetThumbnail(item: MediaItem, pixelSide: Int, modifier: Modifier = Modifier, showsBadge: Boolean = true) {
    val metro = LocalMetro.current
    val context = LocalContext.current
    val loaded = remember(item.thumbKey) { LoadedSide() }
    val previous = loaded.px
    val side = max(Thumbnails.requestSide(pixelSide), previous)
    loaded.px = side
    val request = remember(item.thumbKey, side) {
        Thumbnails.gridRequest(context, item, side).newBuilder()
            // Shows the smaller image until the bigger one lands, instead of flashing the chrome colour.
            .apply { if (previous in 1 until side) placeholderMemoryCacheKey(Thumbnails.memoryKey(item, previous)) }
            .build()
    }
    Box(modifier.background(metro.chrome).clipToBounds()) {
        AsyncImage(request, contentDescription = null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        if (showsBadge && item.type == MediaType.VIDEO) {
            val seconds = item.durationMs / 1000
            MetroText(
                String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60), metroFont(12.sp, FontWeight.SemiBold),
                Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 4.dp),
                color = Color.White, maxLines = 1,
            )
        }
    }
}
