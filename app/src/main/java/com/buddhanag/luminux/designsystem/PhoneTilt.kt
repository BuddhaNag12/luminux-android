package com.buddhanag.luminux.designsystem

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * How far the phone has tilted from the way it's being held, smoothed: about −1…1 on each axis, zero at rest. Fed
 * per sample at ~60 Hz with gravity in iOS units (g, y = −1 upright in portrait); the smoothing is per sample.
 */
class PhoneTiltFilter {
    var value = Offset.Zero
        private set
    private var rest: DoubleArray? = null

    /** Returns false when the change is too small to see, so a phone lying still doesn't redraw every frame. */
    fun update(gx: Double, gy: Double, gz: Double): Boolean {
        val gravity = doubleArrayOf(gx, gy, gz)
        val previous = rest ?: gravity
        val rest = DoubleArray(3) { previous[it] + (gravity[it] - previous[it]) * RECENTRE }
        this.rest = rest
        val targetX = ((gx - rest[0]) / FULL_TILT).coerceIn(-1.0, 1.0)
        val targetY = ((gy - rest[1]) / FULL_TILT).coerceIn(-1.0, 1.0)
        val nextX = value.x + (targetX - value.x) * FOLLOW
        val nextY = value.y + (targetY - value.y) * FOLLOW
        if (abs(nextX - value.x) <= 0.002 && abs(nextY - value.y) <= 0.002) return false
        value = Offset(nextX.toFloat(), nextY.toFloat())
        return true
    }

    fun reset() {
        rest = null
        value = Offset.Zero
    }

    private companion object {
        /** Gravity change (in g) that counts as a full tilt. */
        const val FULL_TILT = 0.25
        /** How quickly the effect follows the phone, and how quickly a new way of holding it becomes the centre. */
        const val FOLLOW = 0.18
        const val RECENTRE = 0.01
    }
}

/** Gravity sensor feeding a [PhoneTiltFilter]. Devices without `TYPE_GRAVITY` get no effect. */
class PhoneTilt(context: Context) : SensorEventListener {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val gravity: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val filter = PhoneTiltFilter()
    private val _value = MutableStateFlow(Offset.Zero)
    val value: StateFlow<Offset> = _value.asStateFlow()

    fun start() {
        gravity?.let { sensors?.registerListener(this, it, 16_667) }
    }

    /** Stops the sensor and drops to zero; [rememberPhoneTilt] springs the shown value back. */
    fun stop() {
        sensors?.unregisterListener(this)
        filter.reset()
        _value.value = Offset.Zero
    }

    override fun onSensorChanged(event: SensorEvent) {
        // Android reports m/s² with y = +9.81 upright; iOS gravity is in g with y = −1, so flip and scale.
        val g = SensorManager.STANDARD_GRAVITY.toDouble()
        val v = event.values
        if (filter.update(-v[0] / g, -v[1] / g, -v[2] / g)) _value.value = filter.value
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

/** The tilt that [tiltParallax] reads; provide [rememberPhoneTilt]'s state here. */
val LocalPhoneTilt = compositionLocalOf<State<Offset>?> { null }

/**
 * Runs the gravity sensor while [enabled] (the hub passes movesWithPhone, no Reduce Motion, app active, nothing
 * covering it) and springs back to zero with the standard spring when it stops.
 */
@Composable
fun rememberPhoneTilt(enabled: Boolean): State<Offset> {
    val context = LocalContext.current
    val tilt = remember { PhoneTilt(context) }
    val shown = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    DisposableEffect(enabled) {
        if (enabled) tilt.start()
        onDispose { if (enabled) tilt.stop() }
    }
    LaunchedEffect(enabled) {
        if (enabled) tilt.value.collect { shown.snapTo(it) } else shown.animateTo(Offset.Zero, MetroMotion.standard())
    }
    return shown.asState()
}

/**
 * Shifts this view up to [distance] as the phone tilts; a negative distance moves against the tilt, so things at
 * different depths drift apart. Hub: tile grid `-8.dp × s`, background `22.dp × s` (pre-scaled 1.1).
 */
fun Modifier.tiltParallax(distance: Dp): Modifier = composed {
    val tilt = LocalPhoneTilt.current
    offset {
        val v = tilt?.value ?: Offset.Zero
        val d = distance.toPx()
        IntOffset((v.x * d).roundToInt(), (-v.y * d).roundToInt())
    }
}
