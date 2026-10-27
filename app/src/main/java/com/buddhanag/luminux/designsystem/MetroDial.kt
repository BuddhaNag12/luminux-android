package com.buddhanag.luminux.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign

/** How far a drag goes, in dp, to cover the whole range. */
const val DIAL_DP_PER_RANGE = 480.0
/** How close to the resting value counts as on it, as a share of half the range. */
private const val DIAL_SNAP = 0.03

/** Dragging left raises the value, as the ruler moves with the finger. Clamped, and caught at rest. [dragged] is in dp. */
fun dialValue(start: Double, dragged: Float, range: ClosedFloatingPointRange<Double>, resting: Double): Double {
    val span = range.endInclusive - range.start
    val raw = start - dragged / DIAL_DP_PER_RANGE * span
    val clamped = raw.coerceIn(range)
    return if (abs(clamped - resting) < DIAL_SNAP * span / 2) resting else clamped
}

// Swift's .rounded() rounds halves away from zero; Kotlin's roundToInt() rounds them up (-34.5 → -34), so use this.
private fun roundHalfAway(x: Double) = (sign(x) * floor(abs(x) + 0.5)).toLong()

/** "+24", "-7" or "0". */
fun dialText(v: Double) = roundHalfAway(v * 100).let { if (it > 0) "+$it" else "$it" }

/** "+12°", "-3.5°" or "0°". */
fun dialDegrees(v: Double): String {
    val tenths = roundHalfAway(v * 10)
    // Locale.ROOT: the device locale could print "3,5".
    val s = if (tenths % 10 == 0L) "${tenths / 10}" else String.format(Locale.ROOT, "%.1f", tenths / 10.0)
    return (if (tenths > 0) "+" else "") + s + "°"
}

/** The tick nearest the line, so each one passing can click. */
internal fun dialTick(value: Double, range: ClosedFloatingPointRange<Double>, ticks: Int) =
    ((value - range.start) / (range.endInclusive - range.start) * ticks).roundToInt()

/**
 * A ruler for one value: its ticks slide under a fixed accent line as you drag, like the editing dials in Photos and
 * VSCO. Each tick passing clicks like a detent, the resting value lands with a firmer knock, and the ends bump; a
 * double tap returns to rest.
 */
@Composable
fun MetroDial(
    label: String,
    value: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Double> = -1.0..1.0,
    resting: Double = 0.0,
    /** Ticks across the whole range; every fifth is longer. */
    ticks: Int = 40,
    format: (Double) -> String = ::dialText,
) {
    val metro = LocalMetro.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(value)
    val onChange by rememberUpdatedState(onValueChange)

    // A light click for every tick that passes the line, a firmer one landing on rest, a bump at either end.
    val tick = dialTick(value, range, ticks)
    val atRest = value == resting
    val atEnd = value == range.start || value == range.endInclusive
    var last by remember(label) { mutableStateOf(Triple(tick, atRest, atEnd)) }
    LaunchedEffect(tick, atRest, atEnd) {
        val (lastTick, wasResting, wasAtEnd) = last
        if (tick != lastTick) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        if (atRest && !wasResting) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        if (atEnd && !wasAtEnd) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        last = Triple(tick, atRest, atEnd)
    }

    Column(
        modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = label
                stateDescription = format(value)
                progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), range.start.toFloat()..range.endInclusive.toFloat())
                setProgress { onValueChange(it.toDouble().coerceIn(range)); true }
            }
            .pointerInput(range, resting) {
                detectTapGestures(onDoubleTap = {
                    scope.launch { Animatable(current.toFloat()).animateTo(resting.toFloat(), MetroMotion.snappy()) { onChange(this.value.toDouble()) } }
                })
            }
            .pointerInput(range, resting) {
                // Starts at 0 distance anywhere on the control; only moves are consumed, so a double tap still lands.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = current
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.positionChange() != Offset.Zero) {
                            change.consume()
                            onChange(dialValue(start, (change.position.x - down.position.x).toDp().value, range, resting))
                        }
                    } while (change.pressed)
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MetroText(
            format(value), metroFont(17.sp, Semilight).copy(fontFeatureSettings = "tnum"),
            color = if (atRest) metro.secondary else metro.accentColor,
        )
        Canvas(Modifier.fillMaxWidth().height(28.dp)) {
            val span = range.endInclusive - range.start
            val middle = size.width / 2
            val perRange = DIAL_DP_PER_RANGE.dp.toPx()
            val fadeWidth = size.width * 0.2f
            for (index in 0..ticks) {
                val tickValue = range.start + span * index / ticks
                val x = middle + ((tickValue - value) / span).toFloat() * perRange
                if (x < -2 || x > size.width + 2) continue
                val isResting = abs(tickValue - resting) < span / ticks / 2
                val height = when {
                    isResting -> size.height
                    index % 5 == 0 -> size.height * 0.6f
                    else -> size.height * 0.35f
                }
                // Ticks fade toward the ends, so the ruler reads as running on past them.
                val fade = (minOf(x, size.width - x) / fadeWidth).coerceIn(0f, 1f)
                val w = 1.5.dp.toPx()
                drawRect(
                    metro.foreground.copy(alpha = (if (isResting) 0.9f else 0.45f) * fade),
                    Offset(x - w / 2, (size.height - height) / 2), Size(w, height),
                )
            }
            val line = Size(3.dp.toPx(), 36.dp.toPx())
            drawRect(metro.accentColor, Offset(middle - line.width / 2, (size.height - line.height) / 2), line)
        }
    }
}
