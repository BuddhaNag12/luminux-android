package com.buddhanag.luminux.designsystem

import android.animation.ValueAnimator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

/** The accent colour: one of the six classic presets, or any colour from the picker (a Pro feature). */
@JvmInline
value class Accent(val hex: Int) {
    val color: Color get() = Color(0xFF000000.toInt() or hex)
    val isPreset: Boolean get() = hex in NAMES
    /** The preset's name, or the hex code of a custom colour. */
    val name: String get() = NAMES[hex] ?: String.format(Locale.ROOT, "#%06X", hex)

    companion object {
        val Cobalt = Accent(0x0050EF)
        val Lime = Accent(0xA4C400)
        val Teal = Accent(0x00ABA9)
        val Magenta = Accent(0xD80073)
        val Red = Accent(0xE51400)
        val Mango = Accent(0xFA6800)

        val presets = listOf(Cobalt, Lime, Teal, Magenta, Red, Mango)
        private val NAMES = mapOf(
            0x0050EF to "cobalt", 0xA4C400 to "lime", 0x00ABA9 to "teal",
            0xD80073 to "magenta", 0xE51400 to "red", 0xFA6800 to "mango",
        )

        /** Relative luminance bounds of the 20 Windows Phone accents (crimson to yellow). */
        const val MIN_LUMINANCE = 0.07
        const val MAX_LUMINANCE = 0.58

        /**
         * A picked colour, nudged into the brightness range of the original Metro accents so white tile labels stay
         * readable on it and it still shows on a black background.
         */
        fun custom(red: Double, green: Double, blue: Double): Accent {
            val linear = listOf(red, green, blue).map { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
            val luminance = 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2]
            val adjusted = when {
                luminance > MAX_LUMINANCE -> linear.map { it * MAX_LUMINANCE / luminance }
                // Mix toward white; scaling up would leave pure black black.
                luminance < MIN_LUMINANCE -> {
                    val mix = (MIN_LUMINANCE - luminance) / (1 - luminance)
                    linear.map { it + (1 - it) * mix }
                }
                else -> linear
            }
            val bytes = adjusted.map {
                val encoded = if (it <= 0.0031308) it * 12.92 else 1.055 * it.pow(1 / 2.4) - 0.055
                (encoded.coerceIn(0.0, 1.0) * 255).roundToInt()
            }
            return Accent(bytes[0] shl 16 or (bytes[1] shl 8) or bytes[2])
        }
    }
}

enum class MetroThemeMode { Dark, Light }

/** Resolved colors for the current theme and accent, read through [LocalMetro]. */
data class MetroPalette(val theme: MetroThemeMode, val accent: Accent) {
    private val dark get() = theme == MetroThemeMode.Dark
    val background get() = if (dark) Color.Black else Color.White
    val foreground get() = if (dark) Color.White else Color.Black
    val secondary get() = if (dark) Color(0xFFA0A0A0) else Color(0xFF666666)
    /** App bar and empty-tile fill. */
    val chrome get() = if (dark) Color(0xFF1F1F1F) else Color(0xFFDDDDDD)
    /** Lets the photos scrolling underneath show through, darkened, like the original app bar. */
    val appBar get() = if (dark) chrome.copy(alpha = 0.72f) else chrome.copy(alpha = 0.8f)
    /** The open app bar is nearly solid, so its labels and menu read clearly over busy photos. */
    val appBarExpanded get() = chrome.copy(alpha = 0.95f)
    val accentColor get() = accent.color
}

object MetroMetrics {
    val margin = 12.dp
    val gutter = 4.dp
    val appBarHeight = 56.dp
    val appBarButton = 48.dp
}

val LocalMetro = staticCompositionLocalOf { MetroPalette(MetroThemeMode.Dark, Accent.Cobalt) }

/** Android's stand-in for iOS Reduce Motion: "Remove animations" or an animator duration scale of 0. */
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun MetroTheme(
    theme: MetroThemeMode = MetroThemeMode.Dark,
    accent: Accent = Accent.Cobalt,
    content: @Composable () -> Unit,
) {
    var reduceMotion by remember { mutableStateOf(!ValueAnimator.areAnimatorsEnabled()) }
    // The setting can change while the app is in the background.
    LifecycleResumeEffect(Unit) {
        reduceMotion = !ValueAnimator.areAnimatorsEnabled()
        onPauseOrDispose {}
    }
    CompositionLocalProvider(
        LocalMetro provides MetroPalette(theme, accent),
        LocalReduceMotion provides reduceMotion,
        content = content,
    )
}
