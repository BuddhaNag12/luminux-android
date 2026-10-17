package com.buddhanag.luminux.feature.hub

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.buddhanag.luminux.designsystem.LocalMetro
import com.buddhanag.luminux.designsystem.LocalReduceMotion
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** True while the hub's live tiles are paused (hub covered, another panel showing); the ambient tiles stop with them. */
internal val LocalHubTilesPaused = compositionLocalOf { false }

internal enum class AmbientPattern { Albums, Date }

/**
 * Slow abstract backgrounds for the tiles that have no photos (AmbientTile.swift), drawn at 30 fps. Stopped while
 * live tiles are paused, with "tile patterns" off, or with Reduce Motion.
 */
@Composable
internal fun AmbientTile(pattern: AmbientPattern, animates: Boolean, modifier: Modifier = Modifier) {
    val paused = LocalReduceMotion.current || !animates || LocalHubTilesPaused.current
    var time by remember { mutableDoubleStateOf(clock()) }
    LaunchedEffect(paused) {
        time = clock()
        if (paused) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameMillis { now ->
                if (now - last >= FRAME_MS) {
                    last = now
                    time = clock()
                }
            }
        }
    }
    Canvas(modifier.fillMaxSize().background(LocalMetro.current.accentColor).clearAndSetSemantics {}) {
        when (pattern) {
            AmbientPattern.Albums -> drawAlbums(time)
            AmbientPattern.Date -> drawMonth(time, LocalDate.now())
        }
    }
}

private const val FRAME_MS = 33L

/**
 * Wall-clock seconds, wrapped at 1540 s (the common period of every wave below) so the doubles stay small.
 * shortcut: wall-clock time like iOS, so the drift jumps a little after a long pause; fine at these speeds.
 */
private fun clock() = (System.currentTimeMillis() % 1_540_000L) / 1000.0

/** Three translucent squares, stacked like album covers, drifting around one another. */
private fun DrawScope.drawAlbums(time: Double) {
    val side = min(size.width, size.height) * 0.42f
    val step = side * 0.2f
    val drift = side * 0.08f
    val opacities = listOf(0.12f, 0.2f, 0.32f)
    for (index in 0..2) {
        val phase = time * 2 * PI / 14 + index * 2.1
        // Back to front, up and to the right, each circling slowly on its own.
        val back = 2 - index
        val x = center.x + back * step - step + drift * cos(phase).toFloat()
        val y = center.y - back * step + step + drift * sin(phase).toFloat()
        val tilt = 4 * sin(time * 2 * PI / 11 + index).toFloat()
        rotate(tilt, Offset(x, y)) {
            drawRect(Color.White.copy(alpha = opacities[index]), Offset(x - side / 2, y - side / 2), Size(side, side))
        }
    }
}

/** This month as squares: past days brighter than the ones ahead, today solid and pulsing, a glow sweeping through. */
private fun DrawScope.drawMonth(time: Double, today: LocalDate) {
    // A small tile only has room for the day number.
    if (min(size.width, size.height) <= 120.dp.toPx()) return
    val days = today.lengthOfMonth()
    val today0 = today.dayOfMonth - 1
    // Monday-first columns, like a wall calendar, whatever the locale.
    val lead = today.withDayOfMonth(1).dayOfWeek.value - 1
    val rows = (lead + days + 6) / 7

    // Right of the date on a wide tile, under it on a square one, so the numbers stay clear.
    val inset = 12.dp.toPx()
    val w = size.width
    val h = size.height
    val area = if (w > h * 1.5f) Rect(w * 0.42f, inset, w - inset, h - inset)
    else Rect(w * 0.3f, h * 0.46f, w - inset, h - inset)
    val cell = min(area.width / 7, area.height / rows)
    val gap = cell * 0.2f
    val origin = Offset(area.right - cell * 7, area.bottom - cell * rows)

    val pulse = 1 + 0.08f * sin(time * 2 * PI / 4).toFloat()
    for (day in 0 until days) {
        val slot = lead + day
        val topLeft = Offset(origin.x + (slot % 7) * cell + gap / 2, origin.y + (slot / 7) * cell + gap / 2)
        val side = cell - gap
        if (day == today0) {
            val grown = side * pulse
            drawRect(Color.White, topLeft - Offset((grown - side) / 2, (grown - side) / 2), Size(grown, grown))
            continue
        }
        // A glow sweeps through the month every 10 s.
        val wave = max(0.0, cos(2 * PI * (time / 10 - day.toDouble() / days)))
        val base = if (day < today0) 0.24 else 0.12
        drawRect(Color.White.copy(alpha = (base + 0.18 * wave.pow(6)).toFloat()), topLeft, Size(side, side))
    }
}
