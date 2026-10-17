package com.buddhanag.luminux.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private const val SNAP = 0.03

/** Clamps to the range and catches the resting value (within 0.03 in value units), so it's easy to land on exactly. */
internal fun metroSliderValue(raw: Double, range: ClosedFloatingPointRange<Double>, resting: Double): Double {
    val clamped = raw.coerceIn(range)
    return if (abs(clamped - resting) < SNAP) resting else clamped
}

/**
 * A flat track with a square thumb. The accent fills from the resting value to the thumb, so a slider that goes both
 * ways fills out from the middle. Drags move the value relative to where they start, so touching the track doesn't
 * make it jump; a double tap puts it back.
 */
@Composable
fun MetroSlider(
    label: String,
    value: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Double> = -1.0..1.0,
    resting: Double = 0.0,
) {
    val metro = LocalMetro.current
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(value)
    val onChange by rememberUpdatedState(onValueChange)
    var animatesReset by remember { mutableStateOf(false) }
    val shown by animateFloatAsState(
        value.toFloat(), if (animatesReset) MetroMotion.snappy() else snap(), label = "slider",
    )
    val span = range.endInclusive - range.start

    var wasResting by remember { mutableStateOf(value == resting) }
    LaunchedEffect(value == resting) {
        if (value == resting && !wasResting) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        wasResting = value == resting
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), range.start.toFloat()..range.endInclusive.toFloat())
                setProgress { onValueChange(metroSliderValue(it.toDouble(), range, resting)); true }
            }
            .pointerInput(range, resting) {
                detectTapGestures(onDoubleTap = {
                    animatesReset = true
                    onChange(resting)
                })
            }
            .pointerInput(range, resting) {
                var start = 0.0
                var dx = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        animatesReset = false
                        start = current
                        dx = 0f
                    },
                ) { change, amount ->
                    change.consume()
                    dx += amount
                    val width = (size.width - 12.dp.toPx()).coerceAtLeast(1f)
                    onChange(metroSliderValue(start + dx / width * span, range, resting))
                }
            },
    ) {
        val thumb = 12.dp.toPx()
        val width = (size.width - thumb).coerceAtLeast(1f)
        fun position(v: Double) = ((v - range.start) / span).toFloat() * width
        val x = ((shown - range.start) / span).toFloat() * width
        val restX = position(resting)
        val midY = size.height / 2
        val track = 4.dp.toPx()
        drawRect(metro.foreground.copy(alpha = 0.3f), Offset(thumb / 2, midY - track / 2), Size(width, track))
        drawRect(metro.accentColor, Offset(minOf(x, restX) + thumb / 2, midY - track / 2), Size(abs(x - restX), track))
        // Marks the resting value on two-way sliders.
        if (resting > range.start) {
            val tick = Size(2.dp.toPx(), 12.dp.toPx())
            drawRect(metro.foreground.copy(alpha = 0.6f), Offset(restX + thumb / 2 - tick.width / 2, midY - tick.height / 2), tick)
        }
        val thumbSize = Size(thumb, 28.dp.toPx())
        drawRect(metro.foreground, Offset(x, midY - thumbSize.height / 2), thumbSize)
    }
}
