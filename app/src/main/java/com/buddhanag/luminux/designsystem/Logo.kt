package com.buddhanag.luminux.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/** Drives the mark's entrance. [baseIn], [sun] and [hills] run 0→1; [flapAngle] runs 90 (edge-on) → 28 (rest). */
data class LogoPhase(val baseIn: Float, val flapAngle: Float, val sun: Float, val hills: Float) {
    companion object {
        const val REST_ANGLE = 28f
        val Hidden = LogoPhase(0f, 90f, 0.01f, 0f)
        val Finished = LogoPhase(1f, REST_ANGLE, 1f, 1f)
    }
}

// The 120-unit grid of the app icon; the mark spans 66 × 68 units from (28, 26).
private const val ORIGIN_X = 28f
private const val ORIGIN_Y = 26f
private val hillPoints = listOf(Offset(64f, 89f), Offset(71f, 80f), Offset(77f, 85f), Offset(84f, 75f), Offset(90f, 83f))

/**
 * The "Tile L" mark: a tall tile turning on its left edge, like a live tile mid-turn, and a wide tile with hills cut
 * into it form an L; a quarter sun rises out of the L's inner corner. [unit] is the size of one grid unit; [cutout] is
 * the colour the hills are cut out in, usually the background behind the mark.
 */
@Composable
fun LogoMark(unit: Dp, cutout: Color, modifier: Modifier = Modifier, phase: () -> LogoPhase = { LogoPhase.Finished }) {
    val metro = LocalMetro.current
    Box(modifier.size(unit * 66, unit * 68)) {
        // Turns on its left edge with the same perspective as page turns; hidden once edge-on.
        Box(
            Modifier
                .size(unit * 28, unit * 68)
                .graphicsLayer { alpha = if (phase().flapAngle >= 89f) 0f else 1f }
                .perspectiveRotation({ phase().flapAngle }, axisY = 1f, origin = { TransformOrigin(0f, 0.5f) }, perspective = 0.7f)
                .background(metro.accentColor.copy(alpha = 0.85f)),
        )
        // The wide tile, rising 10 units as it fades in, with the hills drawn left to right.
        Canvas(
            Modifier
                .offset(unit * (60 - ORIGIN_X), unit * (62 - ORIGIN_Y))
                .size(unit * 34, unit * 32)
                .graphicsLayer {
                    val p = phase().baseIn
                    alpha = p.coerceIn(0f, 1f)
                    translationY = (1 - p) * (unit * 10).toPx()
                },
        ) {
            drawRect(metro.accentColor)
            val u = size.width / 34
            val path = Path().apply {
                hillPoints.forEachIndexed { i, p ->
                    val x = (p.x - 60) * u
                    val y = (p.y - 62) * u
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
            val trimmed = Path()
            PathMeasure().apply { setPath(path, false); getSegment(0f, length * phase().hills.coerceIn(0f, 1f), trimmed) }
            drawPath(trimmed, cutout, style = Stroke(3.4f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        // A quarter disc centred on its bottom-left corner, growing out of that corner.
        Canvas(
            Modifier
                .offset(unit * (60 - ORIGIN_X), unit * (58 - 22 - ORIGIN_Y))
                .size(unit * 22)
                .graphicsLayer {
                    scaleX = phase().sun
                    scaleY = phase().sun
                    transformOrigin = TransformOrigin(0f, 1f)
                },
        ) {
            val path = Path().apply {
                moveTo(0f, size.height)
                for (step in 0..24) {
                    val t = step / 24.0 * Math.PI / 2
                    lineTo((size.width * sin(t)).toFloat(), (size.height - size.height * cos(t)).toFloat())
                }
                close()
            }
            drawPath(path, metro.accentColor)
        }
    }
}

private const val NAME = "luminux"

/**
 * The mark with the name beside it. The mark plays its entrance, and "luminux" types in letter by letter as the tall
 * tile lands, each letter sliding a little as it fades in. Waits while [isSplashShowing]; Reduce Motion shows it finished.
 */
@Composable
fun LogoLockup(cutout: Color, modifier: Modifier = Modifier, unit: Float = 1f, isSplashShowing: Boolean = false) {
    val metro = LocalMetro.current
    val reduceMotion = LocalReduceMotion.current
    val baseIn = remember { Animatable(0f) }
    val flap = remember { Animatable(90f) }
    val sun = remember { Animatable(0.01f) }
    val hills = remember { Animatable(0f) }
    var lettersIn by remember { mutableIntStateOf(0) }

    LaunchedEffect(isSplashShowing, reduceMotion) {
        if (reduceMotion || isSplashShowing) return@LaunchedEffect
        launch {
            delay(420)
            for (count in 1..NAME.length) {
                lettersIn = count
                delay(45)
            }
        }
        launch { baseIn.animateTo(1f, MetroMotion.spring(0.4, 0.85f)) }
        delay(120)
        // A loose spring, so the tile overshoots a little and settles like a real turn.
        launch { flap.animateTo(LogoPhase.REST_ANGLE, MetroMotion.spring(0.45, 0.6f)) }
        delay(300)
        launch { sun.animateTo(1f, MetroMotion.spring(0.3, 0.5f)) }
        delay(120)
        launch { hills.animateTo(1f, tween(350, easing = MetroMotion.EaseOut)) }
    }

    Row(
        modifier.clearAndSetSemantics {
            contentDescription = "Luminux"
            heading()
        },
        horizontalArrangement = Arrangement.spacedBy((14 * unit).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LogoMark((0.95f * unit).dp, cutout) {
            if (reduceMotion) LogoPhase.Finished else LogoPhase(baseIn.value, flap.value, sun.value, hills.value)
        }
        Row {
            NAME.forEachIndexed { index, letter ->
                val shown by animateFloatAsState(
                    if (reduceMotion || index < lettersIn) 1f else 0f, MetroMotion.spring(0.35, 0.9f), label = "letter",
                )
                MetroText(
                    letter.toString(), metroFont((52 * unit).sp, FontWeight.Light),
                    Modifier.graphicsLayer {
                        translationX = -(1 - shown) * (10 * unit).dp.toPx()
                        alpha = shown.coerceIn(0f, 1f)
                    },
                    color = metro.foreground,
                )
            }
        }
    }
}
