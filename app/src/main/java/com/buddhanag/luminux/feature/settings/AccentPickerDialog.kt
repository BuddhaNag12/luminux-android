package com.buddhanag.luminux.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.buddhanag.luminux.designsystem.Accent
import com.buddhanag.luminux.designsystem.LocalMetro
import com.buddhanag.luminux.designsystem.MetroButton
import com.buddhanag.luminux.designsystem.MetroSlider
import com.buddhanag.luminux.designsystem.MetroText
import com.buddhanag.luminux.designsystem.MetroType
import android.graphics.Color as AndroidColor

/**
 * Stands in for UIColorPickerViewController: hue, saturation and brightness, no opacity. Every change is reported, so
 * the app's accent follows the drag; [Accent.custom] keeps it in the Metro brightness range.
 */
@Composable
fun AccentPickerDialog(initial: Accent, onChange: (Accent) -> Unit, onDismiss: () -> Unit) {
    val metro = LocalMetro.current
    val start = remember { FloatArray(3).also { AndroidColor.colorToHSV(initial.hex, it) } }
    var hue by remember { mutableDoubleStateOf(start[0].toDouble()) }
    var saturation by remember { mutableDoubleStateOf(start[1].toDouble()) }
    var brightness by remember { mutableDoubleStateOf(start[2].toDouble()) }

    fun report() {
        val rgb = AndroidColor.HSVToColor(floatArrayOf(hue.toFloat(), saturation.toFloat(), brightness.toFloat()))
        onChange(Accent.custom(AndroidColor.red(rgb) / 255.0, AndroidColor.green(rgb) / 255.0, AndroidColor.blue(rgb) / 255.0))
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().background(metro.chrome).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MetroText("accent colour", MetroType.section)
            Box(Modifier.fillMaxWidth().height(48.dp).background(metro.accentColor))
            Channel("hue", hue, 360.0) { hue = it; report() }
            Channel("saturation", saturation, 1.0) { saturation = it; report() }
            Channel("brightness", brightness, 1.0) { brightness = it; report() }
            MetroButton("done", onDismiss, Modifier.align(Alignment.End))
        }
    }
}

/** Rests at 0, so the accent fills from the left like a plain slider. */
@Composable
private fun Channel(label: String, value: Double, max: Double, onChange: (Double) -> Unit) {
    Column {
        MetroText(label, MetroType.caption, color = LocalMetro.current.secondary)
        MetroSlider(label, value, onChange, range = 0.0..max, resting = 0.0)
    }
}
