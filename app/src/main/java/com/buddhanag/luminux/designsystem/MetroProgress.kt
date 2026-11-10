package com.buddhanag.luminux.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.max

/** Pure math for the Windows Phone "working" dots (MetroProgress.swift). */
object MetroProgressMath {
    const val DOTS = 5
    /** One sweep of all the dots, including the pause before the next. */
    const val PERIOD_S = 2.6
    /** Each dot's time on screen as a share of the period, and the gap between consecutive dots. */
    const val TRAVEL = 0.62
    const val STAGGER = 0.07

    /** Where along the row (0..1) a dot sits at [phase] (0..1 of its own travel): fast in, slow middle, fast out. */
    fun position(phase: Double): Double {
        val t = 2 * phase - 1
        return 0.5 + 0.5 * t * t * t
    }
}

/**
 * The Windows Phone "working" dots: five small accent squares that rush in from the left, drift through the middle,
 * and rush out to the right, one after another. Still centered row with Reduce Motion.
 */
@Composable
fun MetroProgress(modifier: Modifier = Modifier) {
    val accent = LocalMetro.current.accentColor
    val reduceMotion = LocalReduceMotion.current
    var timeS by remember { mutableDoubleStateOf(0.0) }

    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        val startMs = System.currentTimeMillis()
        while (true) {
            withFrameMillis {
                timeS = (System.currentTimeMillis() - startMs) / 1000.0
            }
        }
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .semantics { contentDescription = "Loading" },
    ) {
        val side = 4.dp.toPx()
        val span = max(size.width - side, 0f)
        for (dot in 0 until MetroProgressMath.DOTS) {
            if (reduceMotion) {
                val x = (0.5 + (dot - 2) * 0.02) * span
                drawRect(accent, Offset(x.toFloat(), 0f), Size(side, side))
            } else {
                var phase = (timeS / MetroProgressMath.PERIOD_S - dot * MetroProgressMath.STAGGER) % 1.0
                if (phase < 0) phase += 1.0
                val local = phase / MetroProgressMath.TRAVEL
                if (local < 1.0) {
                    val x = MetroProgressMath.position(local) * span
                    drawRect(accent, Offset(x.toFloat(), 0f), Size(side, side))
                }
            }
        }
    }
}
