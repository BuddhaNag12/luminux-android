package com.buddhanag.luminux.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import kotlin.math.PI
import kotlin.math.pow

/** Springs are critically damped unless a gesture carried momentum into them. */
object MetroMotion {
    fun <T> standard() = spring<T>(0.35, 1f)
    fun <T> snappy() = spring<T>(0.25, 1f)
    fun <T> press() = spring<T>(0.18, 1f)
    fun <T> release() = spring<T>(0.3, 0.8f)
    fun <T> fade(): TweenSpec<T> = tween(200, easing = EaseInOut)

    val EaseIn = CubicBezierEasing(0.42f, 0f, 1f, 1f)
    val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
    val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

    /** Parallax speed of the panorama background relative to the content scroll. */
    const val BACKGROUND_PARALLAX = 0.3f
    /** How much of the panorama title has slid off the screen by the last panel. */
    const val TITLE_TRAVEL = 0.56f

    /** SwiftUI `spring(response:dampingFraction:)` with mass 1: stiffness = (2π / response)². */
    fun <T> spring(response: Double, dampingFraction: Float): SpringSpec<T> =
        androidx.compose.animation.core.spring(dampingFraction, stiffness(response))

    fun stiffness(response: Double) = (2 * PI / response).pow(2).toFloat()
}
