package com.buddhanag.luminux.feature.gallery

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.buddhanag.luminux.designsystem.MetroTheme

/** Debug-only: `adb shell am start -n com.buddhanag.luminux/.feature.gallery.GalleryActivity --es gallery pivot`. */
class GalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The gallery is always the dark Metro theme, so the bar icons stay light.
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        val requested = intent.getStringExtra("gallery")
        val kind = GalleryKind.entries.find { it.name.equals(requested, ignoreCase = true) } ?: GalleryKind.Panorama
        setContent { MetroTheme { ComponentGallery(kind) } }
    }
}
